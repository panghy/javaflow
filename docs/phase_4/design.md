# JavaFlow Phase 4: Asynchronous I/O Framework Design

## Introduction

Phase 4 of JavaFlow focuses on building robust, non-blocking I/O capabilities. This design document outlines the architecture, interfaces, implementation details, and testing strategies for integrating asynchronous I/O operations with the existing actor model, ensuring deterministic behavior in both real and simulation modes.

## Design Goals

1. **Non-blocking I/O**: Provide asynchronous interfaces for network and file operations that integrate with the existing Future/Promise paradigm.
2. **ByteBuffer-Based I/O**: Use ByteBuffers as the core data format for all I/O operations, allowing efficient memory management.
3. **Simulation Compatibility**: Ensure all I/O operations can be simulated in a deterministic environment for testing.
4. **Simulation Support**: Design I/O interfaces to be compatible with future deterministic simulation and fault injection capabilities (to be implemented in Phase 5).
5. **Seamless Integration**: Maintain the same programming model as the core actor framework, allowing easy transition between real and simulated I/O.
6. **Minimal Dependencies**: Avoid heavy external dependencies, preferring to build on Java NIO and standard libraries.

## Core Abstractions

### 1. I/O Transport Layer

The transport layer provides low-level abstractions for sending and receiving data:

```java
public interface FlowTransport {
  /**
   * Opens a connection to the specified endpoint.
   * @return Future that completes when connection is established
   */
  FlowFuture<FlowConnection> connect(Endpoint endpoint);
  
  /**
   * Listens for incoming connections on the specified address.
   * @return A FutureStream of incoming connections
   */
  FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint);
  
  /**
   * Closes the transport and all associated connections.
   */
  FlowFuture<Void> close();
}

public interface FlowConnection {
  /**
   * Sends data over the connection.
   * @return Future that completes when data is sent (not necessarily received)
   */
  FlowFuture<Void> send(ByteBuffer data);
  
  /**
   * Reads available data from the connection.
   * @return Future that completes when data is available
   */
  FlowFuture<ByteBuffer> receive();
  
  /**
   * Provides a stream of incoming data packets.
   * @return FutureStream of received data
   */
  FlowStream<ByteBuffer> receiveStream();
  
  /**
   * Closes this connection.
   */
  FlowFuture<Void> close();
  
  /**
   * Gets the local endpoint of this connection.
   */
  LocalEndpoint getLocalEndpoint();
  
  /**
   * Gets the remote endpoint of this connection.
   */
  Endpoint getRemoteEndpoint();
}
```

### 2. Endpoint Abstraction

Endpoints represent network addresses with a clear distinction between local and remote endpoints:

```java
public interface Endpoint {
  /**
   * A unique identifier for the endpoint for equality comparison.
   */
  String getId();
}

public interface LocalEndpoint extends Endpoint {
  /**
   * The network address this endpoint is bound to.
   */
  InetSocketAddress getAddress();
}
```

### 3. File I/O Abstraction

File operations are abstracted through the `FlowFile` interface:

```java
public interface FlowFile {
  /**
   * Reads data from the specified position.
   * @return Future that completes with the data read
   */
  FlowFuture<ByteBuffer> read(long position, int length);
  
  /**
   * Writes data to the specified position.
   * @return Future that completes when data is durably stored
   */
  FlowFuture<Void> write(long position, ByteBuffer data);
  
  /**
   * Syncs data to storage.
   * @return Future that completes when sync is complete
   */
  FlowFuture<Void> sync();
  
  /**
   * Truncates the file to the specified length.
   */
  FlowFuture<Void> truncate(long newLength);
  
  /**
   * Closes the file.
   */
  FlowFuture<Void> close();
  
  /**
   * Gets the current size of the file.
   */
  FlowFuture<Long> size();
}

public interface FlowFileSystem {
  /**
   * Opens or creates a file.
   * @return Future that completes with the opened file
   */
  FlowFuture<FlowFile> open(Path path, OpenOptions options);
  
  /**
   * Deletes a file.
   */
  FlowFuture<Void> delete(Path path);
  
  /**
   * Checks if a file exists.
   */
  FlowFuture<Boolean> exists(Path path);
  
  /**
   * Lists files in a directory.
   */
  FlowFuture<List<Path>> list(Path directory);
}
```


## Implementation Strategy

### 1. Real Mode Implementation

#### Network I/O

The real-world implementation will be built on Java NIO:

- `FlowTransport` will be implemented using `AsynchronousSocketChannel` and `AsynchronousServerSocketChannel`
- I/O operations will be integrated with the Flow scheduler using `CompletionHandler` callbacks that fulfill promises
- Connection management will include proper error handling and cleanup

```java
public class RealFlowTransport implements FlowTransport {
  private final AsynchronousChannelGroup channelGroup;
  private final Map<LocalEndpoint, AsynchronousServerSocketChannel> serverChannels;
  private final FlowScheduler scheduler;
  
  // Implementation details...
  
  @Override
  public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
    // Create a future for the connection
    FlowFuture<FlowConnection> future = new FlowFuture<>();
    FlowPromise<FlowConnection> promise = future.getPromise();
    
    try {
      // Create an async socket channel
      AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(channelGroup);
      
      // Convert endpoint to InetSocketAddress
      InetSocketAddress address = convertEndpointToAddress(endpoint);
      
      // Connect asynchronously
      channel.connect(address, null, new CompletionHandler<Void, Void>() {
        @Override
        public void completed(Void result, Void attachment) {
          // Create a FlowConnection from the channel
          FlowConnection connection = new RealFlowConnection(channel, endpoint);
          // Complete the promise on the Flow scheduler
          scheduler.execute(() -> promise.complete(connection));
        }
        
        @Override
        public void failed(Throwable exc, Void attachment) {
          // Complete the promise with an exception on the Flow scheduler
          scheduler.execute(() -> promise.completeExceptionally(exc));
        }
      });
    } catch (IOException e) {
      promise.completeExceptionally(e);
    }
    
    return future;
  }
  
  // Other methods...
}
```

#### File I/O

File operations will use `AsynchronousFileChannel`:

- `FlowFile` operations will be implemented using `AsynchronousFileChannel`
- File operations will be integrated with the Flow scheduler via callbacks
- Proper resource management and error handling will be included

```java
public class RealFlowFile implements FlowFile {
  private final AsynchronousFileChannel channel;
  private final Path path;
  private final FlowScheduler scheduler;
  
  // Implementation details...
  
  @Override
  public FlowFuture<ByteBuffer> read(long position, int length) {
    FlowFuture<ByteBuffer> future = new FlowFuture<>();
    FlowPromise<ByteBuffer> promise = future.getPromise();
    ByteBuffer buffer = ByteBuffer.allocate(length);
    
    try {
      channel.read(buffer, position, null, new CompletionHandler<Integer, Void>() {
        @Override
        public void completed(Integer bytesRead, Void attachment) {
          if (bytesRead < 0) {
            scheduler.execute(() -> promise.completeExceptionally(new EOFException()));
          } else {
            buffer.flip();
            scheduler.execute(() -> promise.complete(buffer));
          }
        }
        
        @Override
        public void failed(Throwable exc, Void attachment) {
          scheduler.execute(() -> promise.completeExceptionally(exc));
        }
      });
    } catch (Exception e) {
      promise.completeExceptionally(e);
    }
    
    return future;
  }
  
  // Other methods...
}
```

### 2. Simulation Mode Implementation

The simulation implementations will replace real I/O with in-memory equivalents:

#### Network Simulation

```java
public class SimulatedFlowTransport implements FlowTransport {
  private final Map<Endpoint, SimulatedNode> nodes = new ConcurrentHashMap<>();
  private final FlowClock clock;
  
  // Implementation details...
  
  @Override
  public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
    FlowFuture<FlowConnection> future = new FlowFuture<>();
    FlowPromise<FlowConnection> promise = future.getPromise();
    
    // Check if the endpoint exists
    SimulatedNode targetNode = nodes.get(endpoint);
    if (targetNode == null) {
      promise.completeExceptionally(new ConnectException("Connection refused"));
      return future;
    }
    
    // Create local connection
    SimulatedNode localNode = getCurrentNode();
    SimulatedConnection localEnd = new SimulatedConnection(localNode.getEndpoint(), endpoint);
    SimulatedConnection remoteEnd = new SimulatedConnection(endpoint, localNode.getEndpoint());
    
    // Connect the two ends via in-memory queues
    localEnd.setPeer(remoteEnd);
    remoteEnd.setPeer(localEnd);
    
    // Simulate a basic network delay (fixed for now, will be enhanced in Phase 5)
    double delay = 0.010; // 10ms base network latency
    Flow.delay(delay).whenComplete((v, ex) -> {
      targetNode.addIncomingConnection(remoteEnd);
      promise.complete(localEnd);
    });
    
    return future;
  }
  
  // Other methods...
}
```

#### File Simulation

```java
public class SimulatedFlowFile implements FlowFile {
  private final Map<Long, ByteBuffer> blocks = new ConcurrentHashMap<>();
  private final Path path;
  private final FlowClock clock;
  private long fileSize = 0;
  private boolean open = true;
  
  // Implementation details...
  
  @Override
  public FlowFuture<ByteBuffer> read(long position, int length) {
    if (!open) {
      return FlowFuture.failed(new ClosedChannelException());
    }
    
    // Simulate a basic read delay (fixed for now, will be enhanced in Phase 5)
    double delay = 0.001 + (length / 1_000_000.0) * 0.1; // Base latency + transfer time
    return Flow.delay(delay).flatMap(v -> {
      // Read from in-memory blocks
      ByteBuffer result = ByteBuffer.allocate(length);
      // Read logic...
      
      return FlowFuture.completed(result);
    });
  }
  
  // Other methods...
}
```


## Simulation Compatibility

The I/O interfaces are designed to be compatible with future deterministic simulation and fault injection capabilities (to be implemented in Phase 5):

1. **Interface Separation**: The transport and file interfaces are separated from their implementations, allowing easy swapping between real and simulated versions.

2. **Future-Ready Design**: The implementation includes hooks for later integration with a deterministic randomness source and fault injector.

3. **Controllable Timing**: All operations that involve time use the FlowClock abstraction, which can be either real-world or simulated.

Phase 5 will build on this foundation to implement a comprehensive deterministic simulation environment with controllable randomness for thorough testing of distributed systems.

## Simulation Architecture

To ensure deterministic testing, we'll implement a comprehensive simulation environment:

```java
public class NetworkSimulation {
  private final Map<Endpoint, SimulatedNode> nodes = new HashMap<>();
  private final FlowClock simulationClock;
  private final PriorityQueue<ScheduledEvent> eventQueue = new PriorityQueue<>();
  
  // Methods to manage simulated network
  public void addNode(Endpoint endpoint) {
    // Add a node to the simulation...
  }
  
  public void removeNode(Endpoint endpoint) {
    // Remove a node from the simulation...
  }
  
  public void partitionNetwork(Set<Endpoint> partition1, Set<Endpoint> partition2) {
    // Create a network partition between two sets of nodes...
  }
  
  public void healPartition(Set<Endpoint> partition1, Set<Endpoint> partition2) {
    // Heal a network partition...
  }
  
  // Event scheduling
  public void scheduleEvent(ScheduledEvent event) {
    // Add event to priority queue...
  }
  
  public void processNextEvent() {
    // Process the next event in the queue...
  }
  
  public void runUntil(double time) {
    // Run the simulation until the specified time...
  }
}
```

## Interaction with Flow Scheduler

The I/O subsystem will integrate with the existing Flow scheduler:

1. **Event Loop Integration**: All I/O completions will be routed through the Flow scheduler to maintain the single-threaded execution model.
2. **Task Prioritization**: I/O operations will respect the priority system, with appropriate priorities for different types of I/O.
3. **Cancellation Propagation**: Cancellation of actors will properly propagate to pending I/O operations.

```java
public class IoEventProcessor {
  private final FlowScheduler scheduler;
  private final Queue<IoEvent> pendingEvents = new ConcurrentLinkedQueue<>();
  
  // Process pending I/O events in the Flow scheduler
  public void processPendingEvents() {
    IoEvent event;
    while ((event = pendingEvents.poll()) != null) {
      final IoEvent finalEvent = event;
      scheduler.execute(() -> {
        finalEvent.process();
      }, finalEvent.getPriority());
    }
  }
  
  // Add an I/O event from an external thread
  public void enqueueEvent(IoEvent event) {
    pendingEvents.add(event);
    scheduler.wake(); // Wake up the scheduler if it's sleeping
  }
}
```

## Testing Strategy

Testing the I/O layer will involve:

1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test the interaction between I/O and the actor system
3. **Simulation Tests**: Test complex scenarios in a simulated environment
4. **Fault Injection Tests**: Test system resilience to various failures

Example test scenarios:

```java
@Test
public void testNetworkMessageDelivery() {
  // Set up simulated network with two nodes
  NetworkSimulation simulation = new NetworkSimulation();
  Endpoint node1 = new SimulatedEndpoint("node1");
  Endpoint node2 = new SimulatedEndpoint("node2");
  simulation.addNode(node1);
  simulation.addNode(node2);
  
  // Initialize network connections
  FlowTransport transport = new SimulatedFlowTransport(simulation);
  
  // Create connections between nodes
  FlowConnection connection1to2 = Flow.await(transport.connect(node2));
  
  // Set up message receiving on node2
  AtomicReference<String> receivedMessage = new AtomicReference<>();
  FlowFuture<ByteBuffer> receiveTask = connection1to2.receive();
  
  // Send a message from node1 to node2
  ByteBuffer message = ByteBuffer.wrap("Hello".getBytes());
  FlowFuture<Void> sendTask = connection1to2.send(message);
  
  // Run the simulation until the message is received
  simulation.runUntil(receiveTask::isReady);
  
  // Verify the message was delivered
  ByteBuffer received = receiveTask.get();
  assertEquals("Hello", new String(received.array(), 0, received.limit()));
}

@Test
public void testNetworkPartition() {
  // Similar test but with a network partition...
}

@Test
public void testFileReadWrite() {
  // Test simulated file operations...
}
```

## Performance Considerations

1. **Buffer Management**: Implement efficient buffer pooling to reduce allocation overhead
2. **Connection Pooling**: Maintain connection pools for frequently accessed endpoints
3. **Batch Processing**: Support batching of small messages for efficiency
4. **Zero-Copy**: Utilize zero-copy techniques where possible to minimize data copying

## Security Considerations

1. **TLS Support**: Provide easy integration with TLS for secure communication
2. **Authentication**: Include hooks for authentication protocols
3. **Authorization**: Allow for access control on RPC endpoints

## Backward Compatibility

The I/O layer will be designed to evolve over time:

1. **Versioned Protocols**: Support protocol versioning for backward compatibility
2. **Interface Stability**: Maintain stable interfaces while allowing implementation changes
3. **Migration Support**: Provide tools for migrating between protocol versions

## Development Roadmap

Phase 4 development will proceed with these milestones:

1. **Core I/O Abstractions** (Weeks 1-2)
   - Define interfaces for network and file I/O
   - Implement basic real-mode functionality

2. **Simulation Framework** (Weeks 3-4)
   - Implement simulated network and file I/O
   - Design interfaces compatible with future fault injection (Phase 5)

3. **Protocol Utilities** (Weeks 5-6)
   - Build common utilities for message framing
   - Implement connection pooling and management

4. **Testing and Optimization** (Weeks 7-8)
   - Develop comprehensive test suite
   - Performance optimization

## Conclusion

The Phase 4 I/O framework design provides a solid foundation for building distributed systems with JavaFlow. By combining non-blocking I/O operations with the existing actor model, and ensuring compatibility with both real and simulation modes, we enable developers to write efficient, testable, and reliable asynchronous applications.

The ByteBuffer-based approach allows flexibility in how data is structured and processed, while the foundation is laid for future simulation capabilities (Phase 5) that will ensure thorough testing of complex distributed scenarios.