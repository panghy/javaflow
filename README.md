# JavaFlow

> A Java-based actor concurrency framework designed to support highly concurrent, asynchronous programming with deterministic execution for testing.

JavaFlow reimagines the core ideas of [FoundationDB's Flow](https://github.com/apple/foundationdb/tree/main/flow) actor framework in idiomatic Java, leveraging JDK continuations instead of any custom compiler or preprocessor.

## Overview

JavaFlow allows you to write asynchronous code in a linear, sequential style through the use of futures and actors, while maintaining deterministic execution for testing purposes. The framework is specifically designed to combine the simplicity of writing sequential code with the performance benefits of event-driven systems.

### Key Features

- **Actor Model & Futures**: Lightweight actors (implemented using JDK Continuations) communicate via futures and promises for asynchronous operations
- **Cooperative Scheduling**: Single-threaded event loop that schedules actors in a cooperative manner
- **Prioritized Execution**: Task prioritization to ensure time-critical actors run before lower-priority work
- **Non-blocking I/O**: All I/O is integrated via asynchronous operations that yield futures
- **Deterministic Simulation**: Run the entire system in a controlled scheduler and clock for reproducible testing
- **Error Handling & Cancellation**: Integrated exception handling and automatic cancellation for unwanted operations
- **Idiomatic Java**: Pure Java implementation using JDK 21 features with minimal external dependencies

## Project Status

JavaFlow is in the early stages of development. Below are the major development phases and our current status:

### Implementation Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | **Core Futures and Actors** - Basic async infrastructure | ✅ Completed |
| 2 | **Event Loop and Scheduling** - Cooperative scheduler with priorities | ✅ Completed |
| 3 | **Timers and Clock** - Time-based waits and controllable clock | ✅ Completed |
| 4 | **Asynchronous I/O and RPC Framework** - Network, disk operations, and remote communication | ✅ Completed |
| 5 | **Deterministic Simulation Mode** - Simulation environment | ✅ Completed |
| 6 | **Error Handling and Propagation** - Error model | 🚧 In Progress |
| 7 | **Advanced Actor Patterns and Library** - Enhanced API for usability | 📅 Planned |
| 8 | **Testing and Simulation at Scale** - Complex scenario testing | 📅 Planned |
| 9 | **Performance Optimization and Polishing** - Optimization and refinement | 📅 Planned |
| 10 | **Production Hardening and Documentation** - Production readiness | 📅 Planned |

Phases 1, 2, 3, and 4 have been completed, establishing the core future and actor abstractions, implementing the cooperative scheduling system, adding timer and clock functionality for time-based operations, and delivering a complete asynchronous I/O and RPC framework. Below are the detailed tasks that were completed in these phases:

#### Phase 1: Core Futures and Actors

| Subtask | Description | Status |
|---------|-------------|--------|
| 1.1 | **Future/Promise API** - Core interfaces and implementation | ✅ Completed |
| 1.2 | **Single-threaded Scheduler** - Thread control and scheduling | ✅ Completed |
| 1.3 | **Actor Framework** - Continuation-based actor implementation | ✅ Completed |
| 1.4 | **Await Mechanism** - Suspend/resume functionality | ✅ Completed |
| 1.5 | **Basic Error Model** - Exception propagation through futures | ✅ Completed |
| 1.6 | **Cooperative Yield** - Explicit yield mechanism | ✅ Completed |
| 1.7 | **Actor Cancellation** - Automatic propagation of cancellation | ✅ Completed |
| 1.8 | **Unit Tests** - Test harness for core components | ✅ Completed |
| 1.9 | **Example Actors** - Sample actors demonstrating patterns | ✅ Completed |
| 1.10 | **Basic Documentation** - Initial Javadoc and usage docs | ✅ Completed |

#### Phase 2: Event Loop and Scheduling

| Subtask | Description | Status |
|---------|-------------|--------|
| 2.1 | **Task Prioritization** - Priority-based task scheduling | ✅ Completed |
| 2.2 | **Flow Context Tracking** - ThreadLocal-based context tracking | ✅ Completed |
| 2.3 | **Non-blocking Get API** - Modified Future.get() to be non-blocking | ✅ Completed |
| 2.4 | **Enhanced Yield** - Yield with priority changing capability | ✅ Completed |
| 2.5 | **Cooperative Multitasking** - Interleaving of tasks with explicit yields | ✅ Completed |
| 2.6 | **Improved Error Handling** - Better error propagation in futures | ✅ Completed |
| 2.7 | **Scheduler Loop Optimization** - Efficient task selection and execution | ✅ Completed |
| 2.8 | **Continuation Management** - Proper resumption of suspended tasks | ✅ Completed |
| 2.9 | **Integration Tests** - Multi-actor coordination testing | ✅ Completed |
| 2.10 | **Actor Example** - Example demonstrating cooperative scheduling | ✅ Completed |
| 2.11 | **Pump Method** - Manual task processing for deterministic testing | ✅ Completed |

#### Phase 3: Timers and Clock

| Subtask | Description | Status |
|---------|-------------|--------|
| 3.1 | **FlowClock Interface** - Clock abstraction with real and simulated implementations | ✅ Completed |
| 3.2 | **Timer Event Queue** - Priority queue for scheduled time-based events | ✅ Completed |
| 3.3 | **Flow.delay API** - Method to create futures that resolve after time delays | ✅ Completed |
| 3.4 | **Flow.now API** - Method to retrieve current time (wall or simulated) | ✅ Completed |
| 3.5 | **Timeout Functionality** - Ability to cancel futures after a timeout period | ✅ Completed |
| 3.6 | **Event Loop Integration** - Seamless integration of timers with scheduler | ✅ Completed |
| 3.7 | **Simulation Time Control** - API to advance or manipulate simulated time | ✅ Completed |
| 3.8 | **Timer Cancellation** - Proper cleanup of cancelled timer operations | ✅ Completed |
| 3.9 | **Timer Unit Tests** - Comprehensive testing of timer functionality | ✅ Completed |
| 3.10 | **Timer Example** - Sample code demonstrating timer usage patterns | ✅ Completed |

These subtasks represent the foundation of JavaFlow's actor model and form the building blocks for all subsequent phases.

#### Phase 4: Asynchronous I/O and RPC Framework

| Subtask | Description | Status |
|---------|-------------|--------|
| 4.1 | **Promise Stream Primitives** - Implementation of PromiseStream and FutureStream | ✅ Completed |
| 4.2 | **Non-blocking I/O Framework** - Core abstractions for async I/O operations | ✅ Completed |
| 4.3 | **Network Channel Interfaces** - Asynchronous TCP/UDP socket operations | ✅ Completed |
| 4.4 | **File I/O Operations** - Non-blocking file read/write operations | ✅ Completed |
| 4.5 | **I/O Event Integration** - Integration of I/O events with the event loop | ✅ Completed |
| 4.6 | **Flow Transport Layer** - Message-based communication between components | ✅ Completed |
| 4.7 | **RPC Framework Interface Design** - Promise/Future-based remote procedure calls | ✅ Completed |
| 4.8 | **RPC Framework Implementation** - Complete implementation of RPC transport | ✅ Completed |
| 4.9 | **Serialization Infrastructure** - Data serialization for network operations | ✅ Completed |
| 4.10 | **Timeout Handling** - I/O operation timeout management | ✅ Completed |
| 4.11 | **I/O Error Propagation** - Proper error handling for I/O operations | ✅ Completed |
| 4.12 | **ByteBuffer-Based I/O** - Efficient memory management for I/O operations | ✅ Completed |
| 4.13 | **Simulation Compatibility** - Design for deterministic testing in Phase 5 | ✅ Completed |
| 4.14 | **Utilities and Testing** - Helper utilities for I/O operations | ✅ Completed |

Phases 4 and 5 have been completed, with all major components now implemented and fully functional. Both the file system I/O and network transport implementations are complete, along with a comprehensive RPC framework that enables distributed actor programming with location transparency. The simulation framework now includes advanced fault injection capabilities for robust testing.

#### Phase 5: Deterministic Simulation Mode

| Subtask | Description | Status |
|---------|-------------|--------|
| 5.1 | **Simulation Architecture** - Core simulation context and configuration | ✅ Completed |
| 5.2 | **Deterministic Random Number Generation** - Seeded random operations | ✅ Completed |
| 5.3 | **Simulated Scheduler with Priority Randomization** - Non-deterministic scheduling | ✅ Completed |
| 5.4 | **Enhanced Network Simulation with Fault Injection** - Packet loss, reordering, errors | ✅ Completed |
| 5.5 | **Enhanced File System Simulation with Fault Injection** - Corruption, disk full errors | ✅ Completed |
| 5.6 | **Race Condition Debugging Example** - Demonstrates seed-based bug reproduction | ✅ Completed |
| 5.7 | **BUGGIFY-style Fault Injection** - Dynamic fault injection framework | 📅 Planned |

Key completed functionality includes:
- Asynchronous file read and write operations that return futures
- File system operations (create, delete, move, list) with non-blocking behavior
- Network transport layer with connection-oriented messaging
- TCP socket abstraction with non-blocking send/receive operations
- ByteBuffer-based data handling for efficient memory management
- Error propagation and proper resource cleanup
- Timeout handling for I/O operations
- I/O event integration with the scheduler
- Simulated implementations for deterministic testing
- Utility classes for common I/O operations
- Complete RPC framework implementation with promise/stream-based remote procedure calls
- Advanced serialization infrastructure with generic type preservation
- Connection management with automatic reconnection and endpoint resolution
- Loopback optimization for local communication
- Configurable buffer sizes and other transport parameters via FlowRpcConfiguration
- Comprehensive timeout configuration for unary RPCs, stream inactivity, and connections
- Round-robin load balancing for multiple physical endpoints
- Comprehensive test coverage for all RPC components

The RPC framework provides location transparency where the same code can work for both local and remote communication. The architecture supports loopback endpoints for maximum efficiency in local communication, dynamic endpoint resolution for service discovery, and a robust serialization framework that preserves generic type information across network boundaries. All I/O operations are designed to be non-blocking and return futures that can be awaited by actors, maintaining the cooperative multitasking model that is central to JavaFlow.

Phase 5 has enhanced the simulation framework with advanced fault injection capabilities:
- Priority randomization in the scheduler for non-deterministic task scheduling
- Network fault injection including packet loss, packet reordering, and network errors
- File system fault injection including data corruption and disk full errors  
- Integration of all simulation parameters with the unified SimulationConfiguration
- Factory methods for easy creation of simulated components from configuration
- Race condition debugging example showing how deterministic seeds enable bug reproduction

## Requirements

- JDK 21 or later
- Gradle 8.14 or compatible version
- Requires JDK with Continuations support (uses internal JDK classes for continuation-based scheduling)

## Building and Testing

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a specific test
./gradlew test --tests "fully.qualified.TestClassName"

# Run checkstyle validation
./gradlew checkstyleMain checkstyleTest

# Clean build
./gradlew clean build

# Generate JaCoCo test coverage report
./gradlew jacocoTestReport

# Verify test coverage meets thresholds
./gradlew jacocoTestCoverageVerification
```

## Maven Central Artifacts

JavaFlow is available on Maven Central. To use it in your projects:

### Gradle

```groovy
implementation 'io.github.panghy:javaflow:1.2.0'
```

### Maven

```xml
<dependency>
  <groupId>io.github.panghy</groupId>
  <artifactId>javaflow</artifactId>
  <version>1.2.0</version>
</dependency>
```

See [Release Notes](RELEASE_NOTES.md) for details about changes in each version.

See [Release Process](docs/release_process.md) for information on how releases are managed.

### Code Style and Quality

The project uses Checkstyle to enforce Java coding standards based on the Google Java Style Guide:

- Indentation: 2 spaces (no tabs)
- Max line length: 100 characters
- One statement per line
- No wildcard imports
- Proper bracing (always use braces with if/for/while)
- Consistent naming conventions

Checkstyle validation is automatically part of the build process. To see detailed checkstyle reports, check the build/reports/checkstyle directory after running the build.

### Test Coverage Requirements

JaCoCo is used to enforce code coverage requirements:

- Line Coverage: Minimum 85%
- Branch Coverage: Minimum 75% 

Coverage reports are generated in the build/reports/jacoco directory after running the build.

## Features and Components

JavaFlow provides:

### Core Components
- **FlowFuture & FlowPromise**: For managing asynchronous operations
- **SingleThreadedScheduler**: Cooperative multitasking with one task active at a time
- **Task & TaskPriority**: Prioritized operations for optimal scheduling
- **Flow API**: Simple entry point for creating and scheduling asynchronous tasks
- **Pump Method**: Deterministic task processing for testing and simulation
- **FlowClock & Timers**: Time-based operations and controllable clock for testing
- **FlowFile & FlowFileSystem**: Asynchronous file I/O operations with real and simulated implementations
- **FlowTransport & FlowConnection**: Network transport layer for asynchronous communication
- **PromiseStream & FutureStream**: Stream-based communication between actors
- **Endpoint & LocalEndpoint**: Addressing mechanism for network communications
- **SimulationParameters**: Configurable simulation behavior for realistic testing
- **IOUtil**: Utility classes for I/O operations
- **RPC Framework Implementation**: Complete implementation of promise-based remote procedure calls
- **ConnectionManager**: Connection management for RPC with automatic reconnection and endpoint resolution
- **EndpointId & DefaultEndpointResolver**: Service discovery and addressing for RPC services with round-robin load balancing
- **FlowRpcTransportImpl**: Full RPC transport implementation with message handling and connection management
- **Serialization Framework**: Advanced serialization with generic type preservation and system type handling
- **FlowRpcConfiguration**: Configurable transport parameters including buffer sizes and comprehensive timeout settings

### Design Principles
1. A programming model where asynchronous code is written in a sequential style
2. A single-threaded event loop for deterministic scheduling of actors
3. High concurrency through cooperative multitasking rather than preemptive threading
4. Deterministic simulation for testing distributed system behaviors
5. Automatic cancellation of unwanted operations and proper error propagation
6. Comprehensive logging and debugging tools for asynchronous actors

### Example Usage
```java
// Using static imports for cleaner code
import static io.github.panghy.javaflow.Flow.*;

// Create a simple actor using Flow
FlowFuture<String> result = startActor(() -> {
    // Do some work
    String partialResult = doSomeWork();

    // Yield to let other tasks run
    await(yieldF());

    // Continue processing after yielding
    return finalizeWork(partialResult);
});

// Use the result when it's ready
result.whenComplete((value, error) -> {
    if (error == null) {
        System.out.println(value);
    }
});

// Using timer functionality
FlowFuture<Void> delayedOperation = startActor(() -> {
    // Do initial work
    initialSetup();

    // Wait for 5 seconds
    await(delay(5.0));

    // Perform operation after delay
    return finalOperation();
});

// Using asynchronous file I/O
FlowFuture<ByteBuffer> fileReadOperation = startActor(() -> {
    // Get the default file system (real or simulated based on Flow.isSimulated())
    FlowFileSystem fs = FlowFileSystem.getDefault();
    
    // Create a directory if it doesn't exist
    await(fs.createDirectories(Path.of("/path/to/directory")));
    
    // Open a file asynchronously with specific options
    FlowFile file = await(fs.open(
        Path.of("/path/to/directory/file.txt"), 
        OpenOptions.CREATE, 
        OpenOptions.WRITE
    ));

    // Write data asynchronously
    String content = "Hello, JavaFlow File I/O!";
    ByteBuffer writeBuffer = ByteBuffer.wrap(content.getBytes());
    await(file.write(0, writeBuffer));
    
    // Sync data to disk
    await(file.sync());
    
    // Get the file size
    long size = await(file.size());
    System.out.println("File size: " + size);
    
    // Close the file after writing
    await(file.close());
    
    // Re-open for reading
    file = await(fs.open(Path.of("/path/to/directory/file.txt"), OpenOptions.READ));
    
    // Read data asynchronously
    ByteBuffer readBuffer = await(file.read(0, (int)size));
    
    // Close the file
    await(file.close());
    
    return readBuffer;
});

// Simulated file I/O testing
void testFileOperations() {
    // Create a test scheduler for simulation
    TestScheduler testScheduler = new TestScheduler();
    testScheduler.startSimulation();
    
    try {
        // Configure simulation parameters
        SimulationParameters params = new SimulationParameters()
            .setReadDelay(0.001)            // 1ms delay for reads
            .setWriteDelay(0.002)           // 2ms delay for writes
            .setMetadataDelay(0.0005)       // 0.5ms for metadata ops
            .setReadErrorProbability(0.01); // 1% chance of read errors
        
        // Create a simulated file system
        SimulatedFlowFileSystem fs = new SimulatedFlowFileSystem(params);
        
        // Start test actor using the simulated file system
        FlowFuture<String> testFuture = Flow.startActor(() -> {
            // Create directory and open file
            await(fs.createDirectories(Path.of("/test")));
            FlowFile file = await(fs.open(Path.of("/test/data.txt"), OpenOptions.CREATE, OpenOptions.WRITE));
            
            // Write data
            await(file.write(0, ByteBuffer.wrap("test data".getBytes())));
            await(file.close());
            
            // Read data back
            file = await(fs.open(Path.of("/test/data.txt"), OpenOptions.READ));
            ByteBuffer buffer = await(file.read(0, 9));
            await(file.close());
            
            // Convert buffer to string
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes);
        });
        
        // Execute the test with deterministic timing
        testScheduler.pump();
        
        // Advance time to allow I/O operations to complete
        testScheduler.advanceTime(0.01); // 10ms
        testScheduler.pump();
        
        // Get result and verify
        assertEquals("test data", testFuture.getNow());
    } finally {
        testScheduler.endSimulation();
    }
}

// Using RPC framework
// Configure RPC transport with custom settings
FlowRpcConfiguration config = FlowRpcConfiguration.builder()
    .receiveBufferSize(128 * 1024)  // 128KB buffer
    .unaryRpcTimeoutMs(60_000)      // 60s RPC timeout
    .streamInactivityTimeoutMs(120_000) // 2min stream inactivity timeout
    .connectionTimeoutMs(5_000)      // 5s connection timeout
    .build();
FlowRpcTransportImpl transport = new FlowRpcTransportImpl(
    FlowTransport.getDefault(), config);

// Register a service endpoint with implementation
UserServiceImpl userServiceImpl = new UserServiceImpl();
transport.registerServiceAndListen(
    new EndpointId("user-service"),
    userServiceImpl,
    UserService.class,
    new LocalEndpoint("localhost", 8080));

// Get reference to remote service
UserServiceInterface userService = transport.getRpcStub(
    new EndpointId("user-service"), UserServiceInterface.class);

// Call remote service using promise-based API in an actor
FlowFuture<UserInfo> userLookup = startActor(() -> {
    UserInfo user = await(userService.getUserAsync(new GetUserRequest("user123")));
    return processUserInfo(user);
});
```

### BUGGIFY Fault Injection Framework

JavaFlow includes a BUGGIFY-style fault injection framework inspired by FoundationDB's testing methodology. This allows you to inject faults and unusual conditions into your code during simulation runs to test edge cases that would be difficult to reproduce in real systems.

```java
// Example: Using BUGGIFY for fault injection
import io.github.panghy.javaflow.simulation.*;

// Configure chaos scenario
ChaosScenarios.networkChaos(); // Enables network-related faults

// Or register specific bugs
BugRegistry.getInstance()
    .register(BugIds.DISK_SLOW, 0.1)      // 10% slow disk
    .register(BugIds.PACKET_LOSS, 0.05)   // 5% packet loss
    .register(BugIds.PROCESS_CRASH, 0.01); // 1% process crash

// In your code, inject faults
if (Buggify.isEnabled(BugIds.DISK_SLOW)) {
    await(Flow.delay(5.0)); // Simulate slow disk
}

if (Buggify.isEnabled(BugIds.WRITE_FAILURE)) {
    throw new IOException("Simulated disk write failure");
}

// Use time-aware injection (reduces after 300s for recovery testing)
if (Buggify.isEnabledWithRecovery(BugIds.NETWORK_PARTITION)) {
    throw new NetworkException("Network partition");
}

// Random injection without pre-registration
if (Buggify.sometimes(0.01)) { // 1% chance
    // Inject rare condition
}
```

See [BuggifyExample](src/test/java/io/github/panghy/javaflow/examples/BuggifyExample.java) for a complete example demonstrating fault injection in a distributed key-value store.

### Race Condition Debugging with Deterministic Simulation

JavaFlow's deterministic simulation mode can help find and debug concurrency issues that would be difficult to reproduce in real systems. See the [RaceConditionBugExample](src/test/java/io/github/panghy/javaflow/examples/RaceConditionBugExample.java) for a complete example that demonstrates:

- How race conditions manifest in distributed systems
- Using deterministic seeds to reproduce bugs consistently
- Debugging concurrency issues with execution logging
- Comparing buggy vs. fixed implementations

```java
// Example: Finding race conditions with different seeds
for (long seed = 1; seed <= 100; seed++) {
  SimulationContext context = new SimulationContext(seed, true, config);
  SimulationContext.setCurrent(context);
  
  // Run test scenario...
  int result = runTest();
  
  if (result != expected) {
    System.out.println("Found race condition with seed " + seed);
    // Same seed can be used to debug the issue
  }
}
```

### Asynchronous I/O and RPC Framework (Phase 4)

Phase 4 implementation is complete. All I/O operations in JavaFlow are non-blocking and integrate seamlessly with the actor model.

#### File System I/O (Completed)

The file system component provides a comprehensive asynchronous API for file operations:

1. **Interfaces and Abstractions**:
   - `FlowFile`: Interface for file operations (read, write, truncate, sync, size, close)
   - `FlowFileSystem`: Interface for file system operations (open, delete, exists, createDirectory, list, move)
   - All operations return `FlowFuture` objects that can be awaited by actors

2. **Real Implementations**:
   - `RealFlowFile`: Implementation using Java NIO's `AsynchronousFileChannel`
   - `RealFlowFileSystem`: Implementation using Java's standard file system operations
   - Proper resource management with complete error handling

3. **Simulated Implementations**:
   - `SimulatedFlowFile`: In-memory implementation for testing
   - `SimulatedFlowFileSystem`: Simulated file system with directory tree
   - Configurable delays and error injection for testing edge cases
   - Complete tracking of closed state and resource management

4. **Key Features**:
   - ByteBuffer-based I/O for efficient memory management
   - Proper propagation of errors through futures
   - Comprehensive testing including error scenarios
   - Seamless integration with Flow's cooperative scheduling

#### Network I/O and RPC

The network component of the I/O framework is now largely complete:

1. **Flow Transport Layer**: Complete implementation of network transport with both real and simulated implementations
2. **Java NIO Integration**: Leveraging Java's non-blocking I/O via AsynchronousSocketChannel for network operations
3. **Connection Abstraction**: FlowConnection interface with send/receive operations returning futures
4. **Endpoint Addressing**: Flexible addressing system for network endpoints
5. **Stream-Based API**: Support for both discrete messaging and continuous stream-based communication
6. **Error Handling**: Comprehensive error propagation and connection state management
7. **Simulation Support**: Deterministic testing of network code with configurable parameters

The RPC Framework is now complete with the following features:
- Core interfaces and implementations for RPC functionality with extensive test coverage
- Promise-based remote procedure calls for cross-network actor communication
- Location transparency for seamless distributed programming
- Message structure and endpoint addressing mechanism with round-robin load balancing
- Serialization infrastructure for efficient data exchange
- Comprehensive timeout configuration for unary RPCs, stream inactivity, and connection establishment
- Core simulated implementation for deterministic testing
- Simplified two-tier endpoint architecture for easier service discovery

All I/O operations in JavaFlow never block the main thread. When an actor awaits an I/O operation, it yields control to other actors until the operation completes. This design ensures maximum concurrency while maintaining the deterministic, single-threaded execution model that makes Flow-based systems both highly performant and easily testable.

## Contributing

As this project is in its early stages, contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Inspiration

JavaFlow is inspired by FoundationDB's Flow framework, which has proven to be an effective model for building reliable distributed systems. We're reimagining this approach in idiomatic Java, leveraging modern JDK features to achieve similar benefits without the need for a custom compiler.

## License

This project is licensed under the [Apache License 2.0](LICENSE.md).