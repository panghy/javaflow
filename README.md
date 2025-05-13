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
| 4 | **Asynchronous I/O and RPC Framework** - Network, disk operations, and remote communication | 🔄 In Progress (Design Phase) |
| 5 | **Deterministic Simulation Mode** - Simulation environment | 📅 Planned |
| 6 | **Error Handling and Propagation** - Error model | 📅 Planned |
| 7 | **Advanced Actor Patterns and Library** - Enhanced API for usability | 📅 Planned |
| 8 | **Testing and Simulation at Scale** - Complex scenario testing | 📅 Planned |
| 9 | **Performance Optimization and Polishing** - Optimization and refinement | 📅 Planned |
| 10 | **Production Hardening and Documentation** - Production readiness | 📅 Planned |

Phases 1, 2 and 3 have been completed, establishing the core future and actor abstractions, implementing the cooperative scheduling system, and adding timer and clock functionality for time-based operations. Below are the detailed tasks that were completed in these phases:

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
| 4.1 | **Promise Stream Primitives** - Implementation of PromiseStream and FutureStream | 📅 Planned |
| 4.2 | **Non-blocking I/O Framework** - Core abstractions for async I/O operations | 🔄 In Progress (Design) |
| 4.3 | **Network Channel Interfaces** - Asynchronous TCP/UDP socket operations | 📅 Planned |
| 4.4 | **File I/O Operations** - Non-blocking file read/write operations | 📅 Planned |
| 4.5 | **I/O Event Integration** - Integration of I/O events with the event loop | 📅 Planned |
| 4.6 | **Flow Transport Layer** - Message-based communication between components | 📅 Planned |
| 4.7 | **RPC Framework** - Promise/Future-based remote procedure calls | 🔄 In Progress (Design) |
| 4.8 | **Serialization Infrastructure** - Data serialization for network operations | 📅 Planned |
| 4.9 | **Timeout Handling** - I/O operation timeout management | 📅 Planned |
| 4.10 | **I/O Error Propagation** - Proper error handling for I/O operations | 📅 Planned |
| 4.11 | **ByteBuffer-Based I/O** - Efficient memory management for I/O operations | 📅 Planned |
| 4.12 | **Simulation Compatibility** - Design for deterministic testing in Phase 5 | 🔄 In Progress (Design) |

Phase 4 is currently in the design stage, with comprehensive design documents completed for both the asynchronous I/O infrastructure and RPC framework. These designs detail how network and disk operations will seamlessly integrate with the existing actor model. The architecture will allow promises to cross network boundaries, providing location transparency where the same code can work for both local and remote communication. All I/O operations will be non-blocking and return futures that can be awaited by actors. The system is being designed to support both real-world operation and easy substitution with simulated components in Phase 5 for deterministic testing.

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
- **I/O Interfaces** (coming in Phase 4): Non-blocking network and file operations returning futures
- **FlowTransport** (coming in Phase 4): Message-passing layer for distributed communication
- **RPC Framework** (coming in Phase 4): Promise-based remote procedure calls with location transparency

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

// Using asynchronous I/O (coming in Phase 4)
FlowFuture<ByteBuffer> fileReadOperation = startActor(() -> {
    // Open a file asynchronously
    FlowFile file = await(FlowFileSystem.getInstance().open(Path.of("/path/to/file"), OpenOptions.READ));

    // Read data asynchronously
    ByteBuffer data = await(file.read(0, 1024));

    // Close the file
    await(file.close());

    return data;
});

// Using RPC framework (coming in Phase 4)
FlowFuture<UserInfo> userLookup = startActor(() -> {
    // Get reference to remote service
    UserServiceInterface userService = FlowTransport.getInstance()
        .getEndpoint(new EndpointId("user-service"), UserServiceInterface.class);
    
    // Call remote service using promise-based API
    UserInfo user = await(userService.getUserAsync(new GetUserRequest("user123")));
    
    // Process result
    return processUserInfo(user);
});
```

### Asynchronous I/O and RPC Framework (Phase 4)

In Phase 4, JavaFlow will implement non-blocking I/O operations and a robust RPC framework that integrate seamlessly with the actor model. The design for this phase has been completed, and implementation is planned to begin soon. Key aspects of this phase include:

1. **Java NIO Integration**: Leveraging Java's non-blocking I/O capabilities (java.nio) while ensuring all operations are properly managed by the Flow scheduler
2. **Unified I/O Abstraction**: Providing a consistent API for all I/O operations that return futures which can be awaited by actors
3. **Event Loop Integration**: Ensuring I/O events are processed by the single-threaded event loop in a deterministic manner
4. **Location Transparency**: Using the same API for local and remote communication, enabling seamless transition to simulation mode
5. **RPC Framework**: Building a robust, promise-based remote procedure call mechanism for actor communication across network boundaries

I/O operations in JavaFlow will never block the main thread. When an actor awaits an I/O operation, it will yield control to other actors until the operation completes. This design ensures maximum concurrency while maintaining the deterministic, single-threaded execution model that makes Flow-based systems both highly performant and easily testable. The RPC framework will extend this model across network boundaries, allowing promises to be sent between distributed components while maintaining the same programming model.

## Contributing

As this project is in its early stages, contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Inspiration

JavaFlow is inspired by FoundationDB's Flow framework, which has proven to be an effective model for building reliable distributed systems. We're reimagining this approach in idiomatic Java, leveraging modern JDK features to achieve similar benefits without the need for a custom compiler.

## License

This project is licensed under the [Apache License 2.0](LICENSE).