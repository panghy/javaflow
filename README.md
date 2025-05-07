# JavaFlow

> A Java-based actor concurrency framework designed to support highly concurrent, asynchronous programming with deterministic execution for testing.

JavaFlow reimagines the core ideas of [FoundationDB's Flow](https://github.com/apple/foundationdb/tree/main/flow) actor framework in idiomatic Java, leveraging JDK 25's virtual threads and structured concurrency instead of any custom compiler or preprocessor.

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
| 1 | **Core Futures and Actors** - Basic async infrastructure | 🚧 In Progress |
| 2 | **Event Loop and Scheduling** - Cooperative scheduler with priorities | 📅 Planned |
| 3 | **Timers and Clock** - Time-based waits and controllable clock | 📅 Planned |
| 4 | **Asynchronous I/O Integration** - Network and disk operations as futures | 📅 Planned |
| 5 | **Deterministic Simulation Mode** - Simulation environment | 📅 Planned |
| 6 | **Error Handling and Propagation** - Error model | 📅 Planned |
| 7 | **Advanced Actor Patterns and Library** - Enhanced API for usability | 📅 Planned |
| 8 | **Testing and Simulation at Scale** - Complex scenario testing | 📅 Planned |
| 9 | **Performance Optimization and Polishing** - Optimization and refinement | 📅 Planned |
| 10 | **Production Hardening and Documentation** - Production readiness | 📅 Planned |

Currently, we are in Phase 1, establishing the core future and actor abstractions. The detailed tasks for Phase 1 are:

#### Phase 1: Core Futures and Actors

| Subtask | Description | Status |
|---------|-------------|--------|
| 1.1 | **Future/Promise API** - Core interfaces and implementation | 📅 Planned |
| 1.2 | **Single-threaded Scheduler** - Thread control and scheduling | 📅 Planned |
| 1.3 | **Actor Framework** - Virtual thread-based actor implementation | 📅 Planned |
| 1.4 | **Await Mechanism** - Suspend/resume functionality | 📅 Planned |
| 1.5 | **Basic Error Model** - Exception propagation through futures | 📅 Planned |
| 1.6 | **Cooperative Yield** - Explicit yield mechanism | 📅 Planned |
| 1.7 | **Actor Cancellation** - Automatic propagation of cancellation | 📅 Planned |
| 1.8 | **Unit Tests** - Test harness for core components | 📅 Planned |
| 1.9 | **Example Actors** - Sample actors demonstrating patterns | 📅 Planned |
| 1.10 | **Basic Documentation** - Initial Javadoc and usage docs | 📅 Planned |

These subtasks represent the foundation of JavaFlow's actor model and form the building blocks for all subsequent phases.

## Requirements

- JDK 25 or later (uses Project Loom virtual threads and structured concurrency)
- Gradle 8.10 or later

## Building and Testing

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a specific test
./gradlew test --tests "fully.qualified.TestClassName"
```

## Design Goals

JavaFlow aims to provide:

1. A programming model where asynchronous code is written in a sequential style using `await`-like operations
2. A single-threaded event loop for deterministic scheduling of actors
3. High concurrency through cooperative multitasking rather than preemptive threading
4. Deterministic simulation for testing distributed system behaviors
5. Automatic cancellation of unwanted operations and proper error propagation
6. Comprehensive logging and debugging tools for asynchronous actors

## Contributing

As this project is in its early stages, contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Inspiration

JavaFlow is inspired by FoundationDB's Flow framework, which has proven to be an effective model for building reliable distributed systems. We're reimagining this approach in idiomatic Java, leveraging modern JDK features to achieve similar benefits without the need for a custom compiler.

## License

This project is licensed under the [Apache License 2.0](LICENSE).