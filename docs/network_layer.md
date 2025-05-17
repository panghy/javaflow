# JavaFlow Network Layer

The JavaFlow Network Layer provides a complete asynchronous networking API that integrates seamlessly with the actor model. All operations return `FlowFuture` objects that can be awaited within actors, ensuring that network I/O works cooperatively with the rest of the system.

## Key Features

- **Fully Asynchronous**: All network operations return futures rather than blocking, ensuring cooperative multitasking
- **ByteBuffer-Based**: Uses Java NIO's ByteBuffer for efficient memory management
- **Location Transparency**: Code written against the network interfaces works the same whether in real or simulation mode
- **Deterministic Simulation**: Network behavior can be simulated deterministically for testing
- **Stream Support**: Built-in support for continuous streams of data

## Core Components

### Endpoints

The network layer uses the concept of endpoints to represent network addresses:

- **`Endpoint`**: Represents a network location (host and port)
- **`LocalEndpoint`**: Extends `Endpoint` to represent an endpoint on the local machine that can be bound to

### Transport

The transport layer provides the core networking facilities:

- **`FlowTransport`**: Main interface for network operations (connect, listen)
- **`RealFlowTransport`**: Implementation using Java NIO's `AsynchronousSocketChannel`
- **`SimulatedFlowTransport`**: In-memory implementation for deterministic testing
- **`TransportProvider`**: Factory for obtaining the appropriate transport based on the current mode

### Connections

Connections represent established communication channels:

- **`FlowConnection`**: Interface for connection operations (send, receive)
- **`RealFlowConnection`**: Implementation using Java NIO
- **`SimulatedFlowConnection`**: In-memory implementation for testing

### Simulation Parameters

The network simulation can be configured with various parameters:

- **`NetworkSimulationParameters`**: Controls delays, throughput, and error injection for network simulation

## Usage Examples

### Starting a Server

```java
// Get the default transport (real or simulated based on mode)
FlowTransport transport = FlowTransport.getDefault();

// Create a local endpoint to listen on
LocalEndpoint endpoint = LocalEndpoint.localhost(8080);

// Start listening for connections
FlowStream<FlowConnection> connectionStream = transport.listen(endpoint);

// Accept connections in an actor
Flow.startActor(() -> {
    while (true) {
        // Wait for a connection
        FlowConnection connection = Flow.await(connectionStream.nextAsync());
        
        // Handle the connection (perhaps in a new actor)
        Flow.startActor(() -> handleConnection(connection));
    }
    return null;
});
```

### Connecting to a Server

```java
// Get the default transport
FlowTransport transport = FlowTransport.getDefault();

// Define the server endpoint
Endpoint serverEndpoint = new Endpoint("example.com", 8080);

// Connect to the server
Flow.startActor(() -> {
    try {
        FlowConnection connection = Flow.await(transport.connect(serverEndpoint));
        
        // Use the connection
        // ...
        
        // Close when done
        Flow.await(connection.close());
    } catch (Exception e) {
        // Handle connection errors
    }
    return null;
});
```

### Sending and Receiving Data

```java
// Sending data
String message = "Hello, server!";
ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
Flow.await(connection.send(buffer));

// Receiving data
ByteBuffer received = Flow.await(connection.receive(1024));
byte[] bytes = new byte[received.remaining()];
received.get(bytes);
String receivedMessage = new String(bytes, StandardCharsets.UTF_8);
```

### Using the Receive Stream

```java
// Get the receive stream
FlowStream<ByteBuffer> receiveStream = connection.receiveStream();

// Process messages as they arrive
Flow.startActor(() -> {
    while (true) {
        try {
            ByteBuffer buffer = Flow.await(receiveStream.nextAsync());
            
            // Process the data
            // ...
        } catch (Exception e) {
            // Stream closed or error
            break;
        }
    }
    return null;
});
```

## Testing with Network Simulation

The network layer includes a simulated implementation for testing:

```java
// Create simulation parameters
NetworkSimulationParameters params = new NetworkSimulationParameters()
    .setConnectDelay(0.05)            // 50ms delay for connection establishment
    .setSendDelay(0.01)               // 10ms delay for send operations
    .setSendBytesPerSecond(1_000_000) // 1 MB/s send throughput
    .setSendErrorProbability(0.01);   // 1% chance of send errors

// Create a simulated transport with these parameters
SimulatedFlowTransport transport = new SimulatedFlowTransport(params);

// Set as the default transport for testing
TransportProvider.setDefaultTransport(transport);

// Now all network code will use the simulated transport
// ...

// Create network partitions for testing failure scenarios
transport.createPartition(endpoint1, endpoint2);

// Heal partitions
transport.healPartition(endpoint1, endpoint2);
```

## Integration with Flow Scheduler

The network layer integrates with Flow's scheduler to ensure proper cooperative multitasking:

1. All network operations return futures that can be awaited
2. Awaiting a network operation suspends the actor until the operation completes
3. The scheduler can run other actors while waiting for I/O
4. When I/O completes, the scheduler resumes the waiting actor

This integration means network code feels synchronous but behaves asynchronously, making it easier to write and reason about.

## Real vs. Simulated Mode

The network layer has two implementations:

- **Real Mode**: Uses Java NIO's asynchronous channels for actual network I/O
- **Simulation Mode**: Uses in-memory representation with controllable timing

Code written against the network interfaces works identically in both modes, allowing for easy testing of network-dependent code.

## Examples

For complete examples, see:

- `NetworkEchoExample.java`: A simple echo server and client
- `SimulatedFlowTransportTest.java`: Tests demonstrating simulated network behavior
- `RealFlowTransportTest.java`: Integration tests using real network I/O