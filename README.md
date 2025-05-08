# JavaFlow

> A Java-based actor concurrency framework designed to support highly concurrent, asynchronous programming with deterministic execution for testing.

JavaFlow reimagines the core ideas of [FoundationDB's Flow](https://github.com/apple/foundationdb/tree/main/flow) actor framework in idiomatic Java, leveraging JDK virtual threads and structured concurrency instead of any custom compiler or preprocessor.

## Overview

JavaFlow allows you to write asynchronous code in a linear, sequential style through the use of futures and actors, while maintaining deterministic execution for testing purposes. The framework is specifically designed to combine the simplicity of writing sequential code with the performance benefits of event-driven systems.

### Key Features

- **Actor Model & Futures**: Lightweight actors (implemented as virtual threads) communicate via futures and promises for asynchronous operations
- **Cooperative Scheduling**: Single-threaded event loop that schedules actors in a cooperative manner
- **Prioritized Execution**: Task prioritization to ensure time-critical actors run before lower-priority work
- **Non-blocking I/O**: All I/O is integrated via asynchronous operations that yield futures
- **Deterministic Simulation**: Run the entire system in a controlled scheduler and clock for reproducible testing
- **Error Handling & Cancellation**: Integrated exception handling and automatic cancellation for unwanted operations
- **Idiomatic Java**: Pure Java implementation using JDK 25 features with minimal external dependencies

## Project Status

JavaFlow is in the early stages of development. Below are the major development phases and our current status:

### Implementation Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | **Core Futures and Actors** - Basic async infrastructure | ✅ Completed |
| 2 | **Event Loop and Scheduling** - Cooperative scheduler with priorities | ✅ Completed |
| 3 | **Timers and Clock** - Time-based waits and controllable clock | 📅 Planned |
| 4 | **Asynchronous I/O Integration** - Network and disk operations as futures | 📅 Planned |
| 5 | **Deterministic Simulation Mode** - Simulation environment | 📅 Planned |
| 6 | **Error Handling and Propagation** - Error model | 📅 Planned |
| 7 | **Advanced Actor Patterns and Library** - Enhanced API for usability | 📅 Planned |
| 8 | **Testing and Simulation at Scale** - Complex scenario testing | 📅 Planned |
| 9 | **Performance Optimization and Polishing** - Optimization and refinement | 📅 Planned |
| 10 | **Production Hardening and Documentation** - Production readiness | 📅 Planned |

Phases 1 and 2 have been completed, establishing the core future and actor abstractions and implementing the cooperative scheduling system. Below are the detailed tasks that were completed in these phases:

#### Phase 1: Core Futures and Actors

| Subtask | Description | Status |
|---------|-------------|--------|
| 1.1 | **Future/Promise API** - Core interfaces and implementation | ✅ Completed |
| 1.2 | **Single-threaded Scheduler** - Thread control and scheduling | ✅ Completed |
| 1.3 | **Actor Framework** - Virtual thread-based actor implementation | ✅ Completed |
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

These subtasks represent the foundation of JavaFlow's actor model and form the building blocks for all subsequent phases.

## Requirements

- JDK 24 (updated from earlier versions)
- Gradle 8.14 or compatible version
- Support for virtual threads (introduced in JDK 21 and improved in JDK 24)
- Note: We plan to leverage more advanced features as they become available in JDK 25+

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

### Design Principles
1. A programming model where asynchronous code is written in a sequential style
2. A single-threaded event loop for deterministic scheduling of actors
3. High concurrency through cooperative multitasking rather than preemptive threading
4. Deterministic simulation for testing distributed system behaviors
5. Automatic cancellation of unwanted operations and proper error propagation
6. Comprehensive logging and debugging tools for asynchronous actors

### Example Usage
```java
// Create a simple actor using Flow
FlowFuture<String> result = Flow.start(() -> {
    // Do some work
    String partialResult = doSomeWork();
    
    // Yield to let other tasks run
    Flow.yield().await();
    
    // Continue processing after yielding
    return finalizeWork(partialResult);
});

// Use the result when it's ready
result.thenAccept(System.out::println);
```

## Contributing

As this project is in its early stages, contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Inspiration

JavaFlow is inspired by FoundationDB's Flow framework, which has proven to be an effective model for building reliable distributed systems. We're reimagining this approach in idiomatic Java, leveraging modern JDK features to achieve similar benefits without the need for a custom compiler.

## License

This project is licensed under the [Apache License 2.0](LICENSE).