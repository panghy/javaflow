# Endpoint Model Changes in JavaFlow RPC

## Overview

The JavaFlow RPC framework has been simplified from a three-tier endpoint model to a two-tier model. This document explains the changes and how to migrate existing code.

## Previous Model (Three-Tier)

The previous model supported three types of endpoints:

1. **Loopback Endpoints**: Services with no network exposure, only accessible in-process
2. **Local Endpoints**: Services on the local machine with network exposure
3. **Remote Endpoints**: Services on other machines in the network

## Current Model (Two-Tier)

The current simplified model supports two types of endpoints:

1. **Local Endpoints**: Services on the local machine with network exposure (can be invoked both locally and remotely)
2. **Remote Endpoints**: Services on other machines in the network

## Why the Change?

The three-tier model added unnecessary complexity:

- The distinction between loopback and local endpoints was confusing
- Local endpoints can already be optimized for in-process calls
- The simplified model is easier to understand and use

## Key Benefits of the New Model

1. **Simplified API**: Fewer methods and concepts to learn
2. **Automatic Optimization**: Local calls are automatically optimized without special configuration
3. **Unified Registration**: One way to register services locally
4. **Better Testing**: Services always have the same behavior regardless of how they're accessed

## Migration Guide

### Registering Services

**Before:**
```java
// Loopback endpoint (no network exposure)
transport.registerLoopbackEndpoint(endpointId, implementation);

// Local endpoint (with network exposure)
transport.registerLocalEndpoint(endpointId, implementation, physicalEndpoint);
```

**After:**
```java
// All local services now have network exposure
transport.registerServiceAndListen(
    endpointId,
    implementation,
    interfaceClass,
    localEndpoint
);
```

### Accessing Services

**Before:**
```java
// Get local service (direct reference)
Service service = transport.getLocalEndpoint(endpointId, Service.class);

// Get remote service
Service service = transport.getRoundRobinEndpoint(endpointId, Service.class);
```

**After:**
```java
// Unified access for all services
Service service = transport.getRpcStub(endpointId, Service.class);
```

### Checking Endpoint Type

**Before:**
```java
if (resolver.isLoopbackEndpoint(id)) {
    // Handle loopback
} else if (resolver.isLocalEndpoint(id)) {
    // Handle local with network
} else {
    // Handle remote
}
```

**After:**
```java
if (resolver.isLocalEndpoint(id)) {
    // Handle local (with network exposure)
} else {
    // Handle remote
}
```

## Implementation Details

When a service is registered locally:
- It's automatically available for both local and remote invocation
- Local invocations are optimized to use direct method calls when possible
- The service is exposed on the specified network endpoint for remote access
- The RPC framework handles serialization only when necessary

## Performance Considerations

The new model maintains optimal performance:
- Local calls to services registered on the same node are automatically optimized
- No network serialization occurs for local invocations
- Remote calls use the standard RPC serialization and transport

## Conclusion

The simplified two-tier endpoint model makes JavaFlow RPC easier to use while maintaining all the performance benefits. Services are now always network-capable, with the framework automatically optimizing local calls for maximum efficiency.