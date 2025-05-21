# JavaFlow RPC Framework Design

## Introduction

This document details the design for JavaFlow's RPC (Remote Procedure Call) framework, which builds on the async I/O layer to enable distributed communication between JavaFlow actors. Following the design principles of the original Flow system, this RPC framework will focus on:

1. **Location Transparency**: Actors should not need to change code whether communicating within a process or across the network
2. **Promise-Based Communication**: The ability to pass promises and futures across the network
3. **Stream Support**: Native support for bidirectional streaming via PromiseStream/FutureStream
4. **Serialization Agnosticism**: Allowing users to supply their own serialization mechanism
5. **Simulation Compatibility**: The same RPC code should run transparently in both real and simulation modes
6. **Endpoint Resolution**: Flexible mapping between logical services and physical network locations

## Core Concepts

### Interface Design Pattern

The RPC framework centers around a key design pattern adapted from Flow: interfaces are defined as structs containing PromiseStreams for each request type. For example:

```java
public class UserServiceInterface {
    // One-shot request with reply
    public final PromiseStream<Pair<GetUserRequest, FlowPromise<UserInfo>>> getUser;
    
    // One-way notification
    public final PromiseStream<UserStatusUpdate> statusUpdate;
    
    // Stream of data
    public final PromiseStream<Pair<ListUsersRequest, PromiseStream<UserInfo>>> listUsers;
    
    public UserServiceInterface() {
        // Initialize the promise streams
        FlowFuture<Void> future = new FlowFuture<>();
        this.getUser = new PromiseStream<>();
        this.statusUpdate = new PromiseStream<>();
        this.listUsers = new PromiseStream<>();
    }
    
    // Method to register this interface with the network layer
    public void registerRemote(EndpointId endpointId) {
        FlowTransport.getInstance().registerEndpoint(endpointId, this);
    }
}
```

This interface pattern provides several advantages:
- Clearly defines service contract
- Handles different message patterns (request/reply, notifications, streams)
- Unifies local and remote calling conventions

### Promise Passing Over Network

A key capability of Flow's RPC model is the ability to pass promises over the network. When a client calls a remote service method, it often passes along a promise for the reply. The server fulfills this promise when it completes the operation.

```java
// Client code
FlowFuture<UserInfo> responseFuture = new FlowFuture<>();
FlowPromise<UserInfo> responsePromise = responseFuture.getPromise();
userService.getUser.send(new Pair<>(new GetUserRequest(userId), responsePromise));
UserInfo user = Flow.await(responseFuture);

// Server code (somewhere handling the getUser stream)
void handleGetUserRequests() {
    return Flow.startActor(() -> {
        while (true) {
            Pair<GetUserRequest, FlowPromise<UserInfo>> request = 
                Flow.await(userService.getUser.getFutureStream().nextAsync());
            
            GetUserRequest req = request.first;
            FlowPromise<UserInfo> responsePromise = request.second;
            
            try {
                UserInfo info = retrieveUser(req.getUserId());
                responsePromise.complete(info);
            } catch (Exception e) {
                responsePromise.completeExceptionally(e);
            }
        }
        return null;
    });
}
```

The network layer needs to handle the serialization and routing of these promises, tracking which remote endpoint should receive the result when a promise is fulfilled.

### Convenience Methods

While the core pattern uses PromiseStreams directly, convenience methods make common patterns easier:

```java
// Helper method for request-response pattern
public FlowFuture<UserInfo> getUserAsync(GetUserRequest request) {
    FlowFuture<UserInfo> future = new FlowFuture<>();
    FlowPromise<UserInfo> promise = future.getPromise();
    getUser.send(new Pair<>(request, promise));
    return future;
}

// Usage
UserInfo user = Flow.await(userService.getUserAsync(new GetUserRequest(userId)));
```

## System Architecture

### Components

The RPC framework consists of several key components:

1. **Transport Layer**: Provides low-level network connectivity
2. **Endpoint Resolver**: Maps logical service IDs to physical network locations
3. **Promise Registry**: Tracks pending promises that cross network boundaries
4. **Serialization Layer**: Converts Java objects to/from wire format
5. **Service Interfaces**: The user-defined PromiseStream-based interfaces

#### Transport Layer

The transport layer provides reliable, ordered delivery of messages between endpoints. It builds on the async I/O infrastructure from Phase 4, using `FlowConnection` for the actual data transfer.

```java
public interface FlowTransport {
    // Register an endpoint (service)
    void registerEndpoint(EndpointId id, Object endpointInterface);
    
    // Resolve an endpoint to get its interface
    <T> T getEndpoint(EndpointId id, Class<T> interfaceClass);
    
    // Close all connections
    FlowFuture<Void> close();
}
```

#### Endpoint Resolution

The endpoint resolution system provides a flexible way to map between logical service identifiers (EndpointId) and physical network locations (Endpoint). This enables service discovery, load balancing, and failover capabilities.

```java
public interface EndpointResolver {
    // Register a local endpoint (service on this node)
    void registerLocalEndpoint(EndpointId id, Object implementation, Endpoint physicalEndpoint);
    
    // Register a remote endpoint (service on another node)
    void registerRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint);
    
    // Register multiple endpoints for a service (for load balancing)
    void registerRemoteEndpoints(EndpointId id, List<Endpoint> physicalEndpoints);
    
    // Check if an endpoint is registered locally
    boolean isLocalEndpoint(EndpointId id);
    
    // Get the local implementation of an endpoint
    Optional<Object> getLocalImplementation(EndpointId id);
    
    // Resolve an endpoint (using load balancing if multiple endpoints exist)
    Optional<Endpoint> resolveEndpoint(EndpointId id);
    
    // Resolve a specific endpoint instance by index
    Optional<Endpoint> resolveEndpoint(EndpointId id, int index);
    
    // Get all physical endpoints for a logical service
    List<Endpoint> getAllEndpoints(EndpointId id);
    
    // Unregister endpoints
    boolean unregisterLocalEndpoint(EndpointId id);
    boolean unregisterRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint);
    boolean unregisterAllEndpoints(EndpointId id);
}
```

The default implementation (`DefaultEndpointResolver`) provides:
- In-memory storage of endpoint mappings
- Round-robin load balancing for multiple physical endpoints
- Distinction between local and remote endpoints
- Thread-safe operations

#### Promise Registry

When a promise crosses the network, the system must track it so that when fulfilled locally, the result can be sent to the correct remote endpoint. 

```java
class RemotePromiseTracker {
    // Maps local promise IDs to information about remote endpoints waiting for results
    private final Map<UUID, RemotePromiseInfo> pendingPromises = new ConcurrentHashMap<>();
    
    // Register a promise that was sent to a remote endpoint
    public UUID registerRemotePromise(FlowPromise<?> promise, EndpointId destination) {
        UUID promiseId = UUID.randomUUID();
        pendingPromises.put(promiseId, new RemotePromiseInfo(destination));
        
        // When future completes, send result to remote endpoint
        promise.getFuture().whenComplete((result, error) -> {
            RemotePromiseInfo info = pendingPromises.remove(promiseId);
            if (info != null) {
                sendResultToEndpoint(info.destination, promiseId, result, error);
            }
        });
        
        // Return the ID that will be serialized and sent to the remote endpoint
        return promiseId;
    }
    
    // Used when receiving a message with a promise from a remote endpoint
    public FlowPromise<?> createLocalPromiseForRemote(UUID remotePromiseId, EndpointId source) {
        FlowFuture<Object> future = new FlowFuture<>();
        FlowPromise<Object> localPromise = future.getPromise();
        // Store mapping so when fulfilled it can notify the source
        // ...
        return localPromise;
    }
}
```

### Message Format

Messages between endpoints will have the following structure:

```
+----------------+------------------+---------------+----------------+
| Message Header | Interface Method | Promise IDs   | Payload        |
+----------------+------------------+---------------+----------------+
```

Where:
- **Message Header**: Contains metadata like message type, length, etc.
- **Interface Method**: Identifies which PromiseStream to deliver to
- **Promise IDs**: For tracking promises that cross the network 
- **Payload**: The serialized request/response data

### Network Transparency

The key to making network operations transparent is having the same code path for both local and remote interactions. When a sender calls a method on an interface:

1. If the receiver is local, the PromiseStream directly delivers the message and promise
2. If the receiver is remote, the framework:
   - Serializes the request
   - Tracks any promises in the request
   - Sends the message to the remote endpoint
   - The remote side deserializes and recreates local promises
   - Delivers to the target PromiseStream
   - When promises are fulfilled, the results flow back across the network

## Serialization

The RPC framework is payload-agnostic, allowing users to supply their own serialization mechanisms. It provides a simple interface:

```java
public interface Serializer {
    ByteBuffer serialize(Object obj);
    Object deserialize(ByteBuffer buffer, Class<?> expectedType);
}
```

Users can implement this interface for their preferred serialization technology (Protocol Buffers, Jackson, custom binary, etc.).

Built-in special handlers will be provided for system types:
- `FlowPromise<T>`: Serialized as promise IDs
- `PromiseStream<T>`: Serialized as stream IDs
- Core data types: Primitive wrappers, strings, etc.

### Multi-Format Support

Given that protobuf or other systems may be preferred for certain applications, the framework will support registration of multiple serialization systems:

```java
// Register a specific serializer for a specific type
FlowSerialization.registerSerializer(UserInfo.class, new ProtobufSerializer<>(UserInfo.class));

// Or register a serializer factory for a package
FlowSerialization.registerSerializerFactory("com.example.model", new ProtobufSerializerFactory());
```

## Stream Support

In addition to single-message exchanges, the framework supports continuous streams of data:

### PromiseStream Over Network

When a `PromiseStream` is passed across the network, the framework:

1. Assigns the stream a unique ID
2. Creates a proxy on the receiving side
3. Forwards all messages sent to the stream across the network
4. Delivers to the local stream on the destination

For example:

```java
// Server exposes a stream of events
public class NotificationService {
    public final PromiseStream<Pair<SubscribeRequest, PromiseStream<Event>>> subscribe;
    
    // ...
}

// Server handler
void handleSubscriptions() {
    return Flow.startActor(() -> {
        while (true) {
            Pair<SubscribeRequest, PromiseStream<Event>> request = 
                Flow.await(notificationService.subscribe.getFutureStream().nextAsync());
            
            String topic = request.first.getTopic();
            PromiseStream<Event> clientStream = request.second;
            
            // Now can send events to the client stream
            // These will be forwarded across the network
            registerTopicListener(topic, event -> clientStream.send(event));
        }
        return null;
    });
}

// Client code
PromiseStream<Event> myEvents = new PromiseStream<>();
notificationService.subscribe.send(new Pair<>(new SubscribeRequest("topic1"), myEvents));

// Process events as they arrive
Flow.startActor(() -> {
    while (true) {
        Event event = Flow.await(myEvents.getFutureStream().nextAsync());
        processEvent(event);
    }
    return null;
});
```

### Bi-directional Streaming

This model naturally supports bi-directional streaming, as both sides can send promises or streams to the other side.

## Integration with Simulation Mode

A crucial aspect of this design is compatibility with JavaFlow's simulation capabilities. The RPC framework will work transparently in simulation by:

1. Using an in-memory network transport in simulation mode
2. Simulating network latency and conditions as specified in Phase 5
3. Ensuring deterministic message delivery order

The same service interface code will work in both production and simulation.

## Endpoint Resolution System

The endpoint resolution system is a key component for building scalable distributed applications. It provides:

### 1. Logical to Physical Mapping

The system maps logical endpoint identifiers (EndpointId) to physical network locations (Endpoint):

```java
// Define a logical service ID
EndpointId authServiceId = new EndpointId("authentication-service");

// Register physical locations for the service
resolver.registerRemoteEndpoints(authServiceId, Arrays.asList(
    new Endpoint("auth-1.example.com", 9001),
    new Endpoint("auth-2.example.com", 9001),
    new Endpoint("auth-3.example.com", 9001)
));

// The system will handle routing to the appropriate physical endpoint
AuthService auth = transport.getEndpoint(authServiceId, AuthService.class);
```

### 2. Load Balancing

When multiple physical endpoints are registered for a service, the system provides automatic load balancing:

```java
// Get a proxy that uses round-robin load balancing
UserService userService = transport.getRoundRobinEndpoint(
    userServiceId, UserService.class);

// Each call may go to a different physical endpoint
userService.getUserInfo(userId1);  // Goes to endpoint 1
userService.getUserInfo(userId2);  // Goes to endpoint 2
userService.getUserInfo(userId3);  // Goes to endpoint 3
userService.getUserInfo(userId4);  // Cycles back to endpoint 1
```

### 3. Specific Endpoint Targeting

When needed, clients can target specific instances of a service:

```java
// Target a specific endpoint by index
UserService specificInstance = transport.getSpecificEndpoint(
    userServiceId, UserService.class, 2);  // Always use endpoint at index 2

// All calls go to the same physical endpoint
specificInstance.getUserInfo(userId1);  // Goes to endpoint 2
specificInstance.getUserInfo(userId2);  // Goes to endpoint 2
```

### 4. Local Optimization

The system optimizes for local endpoints, avoiding network serialization when possible:

```java
// Check if a service is local
if (resolver.isLocalEndpoint(serviceId)) {
    // Get direct access to the local implementation
    ServiceInterface localService = transport.getLocalEndpoint(
        serviceId, ServiceInterface.class);
    
    // Direct method call with no network overhead
    localService.performOperation();
}
```

### 5. Dynamic Service Discovery

Services can be added, removed, or modified at runtime:

```java
// Register a new service node
resolver.registerRemoteEndpoint(serviceId, new Endpoint("new-node", 9001));

// Unregister a failed node
resolver.unregisterRemoteEndpoint(serviceId, failedEndpoint);
```

## Reliability and Error Handling

### Delivery Guarantees

The RPC framework offers these delivery guarantees:

1. **At-most-once delivery**: No duplicates of a single message
2. **Ordered delivery**: Messages from one source to one destination arrive in order sent
3. **Best-effort delivery**: When connections break, inflight messages may be lost

Error handling is integrated with JavaFlow's futures model:

1. Network failures complete futures with exceptions
2. Timeouts can be applied at the application level using `Flow.withTimeout`
3. Service failures propagate exceptions back to clients

### Connection Management

The transport layer handles connection establishment, monitoring, and reestablishment:

```java
public class NetworkEndpoint {
    // Establish or reuse connection
    public FlowFuture<FlowConnection> getConnection(EndpointId destination) {
        // Check for existing
        FlowConnection conn = activeConnections.get(destination);
        if (conn != null && conn.isActive()) {
            return FlowFuture.completed(conn);
        }
        
        // Create new connection
        return FlowTransport.getInstance().connect(destination.getAddress())
            .thenApply(newConn -> {
                activeConnections.put(destination, newConn);
                // Set up monitoring
                monitorConnection(destination, newConn);
                return newConn;
            });
    }
    
    private void monitorConnection(EndpointId dest, FlowConnection conn) {
        Flow.startActor(() -> {
            try {
                await(conn.closeFuture()); // Future completed when connection closes
            } catch (Exception e) {
                logConnectionFailure(dest, e);
            } finally {
                activeConnections.remove(dest);
                // Notify any waiting operations
                handleDisconnect(dest);
            }
            return null;
        });
    }
}
```

## Example Usage

### Defining a Service Interface

```java
public class KeyValueStoreInterface {
    // Get a single value (request-response)
    public final PromiseStream<Pair<GetRequest, FlowPromise<Optional<ByteBuffer>>>> get;
    
    // Set a value (one-way)
    public final PromiseStream<SetRequest> set;
    
    // Watch a key for changes (streaming)
    public final PromiseStream<Pair<WatchRequest, PromiseStream<KeyChangeEvent>>> watch;
    
    // Convenient helper methods
    public FlowFuture<Optional<ByteBuffer>> getAsync(String key) {
        FlowFuture<Optional<ByteBuffer>> future = new FlowFuture<>();
        FlowPromise<Optional<ByteBuffer>> promise = future.getPromise();
        get.send(new Pair<>(new GetRequest(key), promise));
        return future;
    }
    
    public void setAsync(String key, ByteBuffer value) {
        set.send(new SetRequest(key, value));
    }
    
    public FutureStream<KeyChangeEvent> watchAsync(String keyPrefix) {
        PromiseStream<KeyChangeEvent> events = new PromiseStream<>();
        watch.send(new Pair<>(new WatchRequest(keyPrefix), events));
        return events.getFutureStream();
    }
}
```

### Implementing a Service

```java
public class KeyValueStoreImpl {
    private final KeyValueStoreInterface iface = new KeyValueStoreInterface();
    private final Map<String, ByteBuffer> store = new ConcurrentHashMap<>();
    private final Map<String, List<PromiseStream<KeyChangeEvent>>> watchers = new ConcurrentHashMap<>();
    
    public KeyValueStoreImpl() {
        // Start actor to handle get requests
        handleGetRequests();
        
        // Start actor to handle set requests
        handleSetRequests();
        
        // Start actor to handle watch requests
        handleWatchRequests();
    }
    
    private FlowFuture<Void> handleGetRequests() {
        return Flow.startActor(() -> {
            while (true) {
                Pair<GetRequest, FlowPromise<Optional<ByteBuffer>>> request = 
                    Flow.await(iface.get.getFutureStream().nextAsync());
                
                String key = request.first.getKey();
                FlowPromise<Optional<ByteBuffer>> promise = request.second;
                
                try {
                    ByteBuffer value = store.get(key);
                    promise.complete(Optional.ofNullable(value));
                } catch (Exception e) {
                    promise.completeExceptionally(e);
                }
            }
        });
    }
    
    // Similar implementations for set and watch handlers
    // ...
    
    public KeyValueStoreInterface getInterface() {
        return iface;
    }
}
```

### Using Endpoint Resolution

```java
// Get the RPC transport and its implementation
FlowRpcTransport transport = FlowRpcTransport.getInstance();
FlowRpcTransportImpl rpcTransport = (FlowRpcTransportImpl) transport;

// Register a service with multiple instances
EndpointId storageServiceId = new EndpointId("storage-service");
rpcTransport.registerRemoteEndpoints(storageServiceId, Arrays.asList(
    new Endpoint("storage-1", 9001),
    new Endpoint("storage-2", 9002),
    new Endpoint("storage-3", 9003)
));

// Get a proxy with round-robin load balancing
StorageService storageService = rpcTransport.getRoundRobinEndpoint(
    storageServiceId, StorageService.class);

// Make some calls that will be distributed across all instances
for (int i = 0; i < 5; i++) {
    Flow.await(storageService.store("key" + i, "value" + i));
}

// Target a specific instance for special operations
StorageService storageNode2 = rpcTransport.getSpecificEndpoint(
    storageServiceId, StorageService.class, 1);  // Index 1 = second node

// This call always goes to storage-2
Flow.await(storageNode2.performMaintenance());
```

### Client Usage

```java
// Get reference to remote service
KeyValueStoreInterface kvStore = FlowTransport.getInstance()
    .getEndpoint(new EndpointId("kv-store-1"), KeyValueStoreInterface.class);

// Use the service
Flow.startActor(() -> {
    // Simple get operation
    Optional<ByteBuffer> value = Flow.await(kvStore.getAsync("user:123"));
    
    // Set a value
    kvStore.setAsync("counter:visits", ByteBuffer.wrap(new byte[]{1, 0, 0, 0}));
    
    // Watch a prefix for changes
    FutureStream<KeyChangeEvent> changes = kvStore.watchAsync("counter:");
    while (true) {
        KeyChangeEvent event = Flow.await(changes.nextAsync());
        processChange(event);
    }
});
```

## Performance Considerations

To ensure high performance in the RPC system:

1. **Connection Pooling**: Reuse connections between endpoints
2. **Message Batching**: Optionally combine small messages into larger network packets
3. **Backpressure**: Apply flow control on streams to prevent overwhelming receivers
4. **Zero-Copy**: When possible, use zero-copy buffers for large payloads
5. **Efficient Serialization**: Support for efficient binary formats like Protocol Buffers
6. **Load Balancing**: Distribute work across multiple physical endpoints
7. **Local Optimization**: Bypass network layer for local endpoints

## Implementation Plan

The RPC framework will be implemented in these stages:

1. **Core Interface Types**: Define the base PromiseStream and serialization interfaces (✅ Completed)
2. **API Design**: Design the FlowRpcTransport interface and API structures (✅ Completed)  
3. **Serialization Framework**: Implement the core serialization infrastructure (🔄 In Progress)
4. **Endpoint Resolution**: Implement the endpoint resolution system with load balancing (✅ Completed)
5. **Local Transport**: Implement in-process message passing via loopback endpoints (🔄 In Progress)
6. **Network Transport**: Build the transport layer on top of async I/O (🔄 In Progress)
7. **Promise/Stream Networking**: Enable promises and streams to cross the network (🔄 In Progress)
8. **Integration with Simulation**: Ensure compatibility with future simulation capabilities (🔄 In Progress)
9. **Full Implementation**: Complete the implementation of FlowRpcTransport (⏳ Planned)

## Conclusion

The JavaFlow RPC framework provides a powerful model for distributed actor communication that maintains the simplicity of the Flow programming model across network boundaries. By centering on PromiseStreams and allowing promises to travel over the network, it enables the same natural coding style whether communicating within a process or across machines.

This design emphasizes:

1. **Consistency**: The same actor code works locally or remotely
2. **Flexibility**: Support for different message patterns (request/reply, notifications, streams)
3. **Scalability**: Advanced endpoint resolution with load balancing and failover
4. **Integration**: Seamless incorporation with the rest of the JavaFlow framework
5. **Performance**: Efficient communication with minimal overhead
6. **Testability**: Compatible with deterministic simulation for thorough testing

**Current Implementation Status:**
- Core interface design and API structures are complete
- Serialization framework implementation has begun
- ConnectionManager implementation is in progress
- EndpointResolver system is implemented
- The actual implementation of FlowRpcTransport is still pending

Following this design, once fully implemented, the JavaFlow RPC framework will allow developers to build distributed systems with the same ease and confidence that the actor model brings to concurrent programming.