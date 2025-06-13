# Local Endpoints in JavaFlow RPC

> **Note**: This document has been updated to reflect the simplified endpoint model in JavaFlow RPC. The previous three-tier model (loopback, local, remote) has been replaced with a two-tier model (local, remote).

## Overview

Local endpoints in the JavaFlow RPC framework are services that run on the local machine with network exposure. They provide efficient communication for both in-process and cross-process scenarios, automatically optimizing for local invocations while remaining accessible over the network.

## Two Types of Endpoints

The JavaFlow RPC framework now supports two types of endpoints:

1. **Local Endpoints**: Services mounted on the local machine with network exposure
2. **Remote Endpoints**: Services located on other machines in the network

## Benefits of the Simplified Model

The current model offers several advantages:

1. **Simplicity**: One way to register local services
2. **Automatic Optimization**: Local invocations are automatically optimized
3. **Network Capability**: All services can be accessed remotely when needed
4. **Consistent Behavior**: Services behave the same regardless of access method
5. **Better Testing**: No special cases for different endpoint types

## When to Use Local Endpoints

Local endpoints are ideal for:

1. **Microservices**: Services that need both local and remote access
2. **Service Composition**: Building higher-level services from lower-level ones
3. **Testing**: Creating test services that mirror production behavior
4. **Development**: Services that may be accessed locally or remotely
5. **Distributed Applications**: Services that form part of a larger distributed system

## API Usage

### Registering a Local Endpoint

```java
// Get the RPC transport
FlowRpcTransport transport = FlowRpcTransport.getInstance();

// Create a service implementation
UserServiceImpl userService = new UserServiceImpl();

// Register it as a local endpoint with network exposure
transport.registerServiceAndListen(
    new EndpointId("user-service"),
    userService,
    UserService.class,
    new LocalEndpoint("localhost", 8080)
);
```

### Accessing an Endpoint

```java
// Get the RPC transport
FlowRpcTransport transport = FlowRpcTransport.getInstance();

// Get an RPC stub (works for both local and remote endpoints)
UserService service = transport.getRpcStub(
    new EndpointId("user-service"), 
    UserService.class
);

// Use it like any other service
FlowFuture<UserInfo> userFuture = service.getUserInfo(userId);
UserInfo user = Flow.await(userFuture);
```

## How Local Endpoints Work

When a method is called on a local endpoint:

1. The system checks if the service is registered locally
2. If local, it optimizes the call to avoid unnecessary serialization
3. The method is invoked efficiently on the local implementation
4. Results are returned with minimal overhead

For services that are registered as local endpoints:
- The `isLocalEndpoint()` method in `EndpointResolver` returns `true`
- A physical network endpoint is associated with the service
- The service is accessible both locally and remotely
- Local calls are automatically optimized

## Implementation Details

Local endpoints are implemented in the `DefaultEndpointResolver` with network information:

```java
// In DefaultEndpointResolver
private final Map<EndpointId, LocalEndpointInfo> localEndpoints = new ConcurrentHashMap<>();

private static class LocalEndpointInfo {
    private final Object implementation;
    private final Endpoint physicalEndpoint;
    // ... constructor and getters
}
```

When retrieving a local implementation:

```java
@Override
public Optional<Object> getLocalImplementation(EndpointId id) {
    LocalEndpointInfo info = localEndpoints.get(id);
    return info != null ? Optional.of(info.getImplementation()) : Optional.empty();
}
```

## Comparison with Remote Endpoints

| Feature | Local | Remote |
|---------|-------|--------|
| Network Exposure | Yes | Yes |
| Physical Endpoint | Required | Required |
| Method Call Type | Optimized locally, RPC remotely | Always RPC |
| Serialization | Only for remote calls | Always |
| Network I/O | Only for remote calls | Always |
| Load Balancing | N/A (single instance) | Supported |

## Example: Mixed Local and Remote Services

```java
// Get the RPC transport
FlowRpcTransport transport = FlowRpcTransport.getInstance();

// Create service implementations
UserServiceImpl userService = new UserServiceImpl();
InventoryServiceImpl inventoryService = new InventoryServiceImpl();

// Register as local endpoints with different ports
transport.registerServiceAndListen(
    new EndpointId("user-service"),
    userService,
    UserService.class,
    new LocalEndpoint("localhost", 8080)
);

transport.registerServiceAndListen(
    new EndpointId("inventory-service"),
    inventoryService,
    InventoryService.class,
    new LocalEndpoint("localhost", 8081)
);

// Register remote endpoints with load balancing
transport.getEndpointResolver().registerRemoteEndpoints(
    new EndpointId("payment-service"), 
    Arrays.asList(
        new Endpoint("payment-1.example.com", 9001),
        new Endpoint("payment-2.example.com", 9001),
        new Endpoint("payment-3.example.com", 9001)
    )
);

// Access all services uniformly
UserService users = transport.getRpcStub(
    new EndpointId("user-service"),
    UserService.class
);  // Automatically optimized for local calls

InventoryService inventory = transport.getRpcStub(
    new EndpointId("inventory-service"),
    InventoryService.class
);  // Automatically optimized for local calls

PaymentService payments = transport.getRpcStub(
    new EndpointId("payment-service"),
    PaymentService.class
);  // Remote proxy with round-robin load balancing
```

## Best Practices

1. **Always Use Network Endpoints**: Register all services with network endpoints for flexibility
2. **Consistent Interfaces**: Use the same interfaces for local and remote services
3. **Service Discovery**: Use the endpoint resolver for dynamic service location
4. **Port Management**: Assign unique ports to different local services
5. **Documentation**: Clearly document which services are available locally vs remotely

## Migration from Three-Tier Model

If you're migrating from the previous three-tier model:

1. Replace `registerLoopbackEndpoint()` calls with `registerServiceAndListen()`
2. Replace `getLocalEndpoint()` calls with `getRpcStub()`
3. Remove any special handling for loopback endpoints
4. Update tests to use the new API

See [Endpoint Model Changes](endpoint_model_changes.md) for detailed migration instructions.

## Conclusion

The simplified two-tier endpoint model in JavaFlow RPC provides a cleaner, more intuitive API while maintaining all performance benefits. Local endpoints with network exposure offer the best of both worlds - efficient local invocation and network accessibility - making it easier to build distributed systems with JavaFlow.