# Release Notes

## Version 1.3.0

### Major Features

#### Phase 6: Core Cancellation Infrastructure
- **FlowCancellationException**: New runtime exception for consistent cancellation signaling
- **Cooperative Cancellation API**: Added `Flow.checkCancellation()` and `Flow.isCancelled()` methods
- **Automatic Propagation**: Cancellation propagates through future chains and across RPC boundaries
- **Enhanced Suspension Points**: All await, yield, and delay operations now properly handle cancellation

#### Phase 5: Deterministic Simulation Mode
- **BUGGIFY-style Fault Injection**: Framework for introducing controlled faults in tests
- **Simulation Context**: Unified configuration for all simulation parameters
- **Deterministic Random**: Infrastructure for reproducible randomness in simulations
- **Priority Randomization**: Non-deterministic task scheduling for finding race conditions
- **Network Fault Injection**: Simulate packet loss, reordering, and network errors
- **File System Fault Injection**: Simulate data corruption and disk full errors

#### RPC Framework Enhancements
- **Comprehensive RPC Timeout Configuration**: Fine-grained control over timeouts
- **Simplified Endpoint Model**: Refactored from three-tier to two-tier architecture
- **FlowRpcConfiguration**: Builder pattern for customizable transport settings

### API Additions
- `Flow.checkCancellation()` - Throws FlowCancellationException if current task is cancelled
- `Flow.isCancelled()` - Returns cancellation status without throwing
- `Flow.isSimulated()` - Determines if running in simulation mode
- `FlowCancellationException` - New exception type extending RuntimeException
- `CancellationTestUtils` - Comprehensive utilities for testing cancellation behavior
- `SimulationContext` - Central configuration for simulation parameters
- `FlowRandom` - Deterministic random number generator for simulations
- `Buggify` - Fault injection utilities
- `FlowScheduler.getCurrentTask()` - Package-private helper for cancellation support

### Breaking Changes
- `Flow.await()` now throws `FlowCancellationException` instead of `CancellationException`
  - Since FlowCancellationException extends RuntimeException, most code continues to work unchanged
  - Only affects code that explicitly catches CancellationException
- Endpoint model simplified from three-tier to two-tier architecture (affects RPC internals)

### Improvements
- Enhanced Javadoc for all Flow methods documenting cancellation behavior
- Converted FlowTest to use AbstractFlowTest simulation framework
- Added comprehensive cancellation examples and documentation
- Improved test reliability by eliminating Thread.sleep() usage
- Better test coverage across RPC, simulation, and core components
- Added race condition debugging example demonstrating simulation capabilities
- Enhanced timer cancellation with proper parent-child propagation
- Improved scheduler shutdown and task cancellation mechanisms
- Added Apache License 2.0 file

### Documentation
- Added comprehensive cancellation examples in `docs/phase_6/cancellation_examples.md`
- Added Phase 5 deterministic simulation design documentation
- Added race condition debugging example to README
- Created migration guide for handling the breaking change
- Updated README.md to reflect Phase 5 and 6 completion
- Enhanced Javadoc with detailed cancellation behavior descriptions

### Internal Improvements
- Fixed code coverage gaps and checkstyle issues
- Improved numeric type conversion in RPC serialization
- Enhanced test stability and FlowFuture support
- Refactored RPC implementation for better type handling
- Added comprehensive test coverage for edge cases

## Version 1.2.0

### Major Improvements
- **Enhanced RPC Framework**: Complete implementation with FlowRpcConfiguration for customizable transport settings
- **Improved Test Coverage**: Comprehensive test suites for RPC transport, serialization, and error handling
- **Code Quality**: Refactored RPC implementation for better type handling and maintainability
- **Utilities Package**: Added comprehensive utility components derived from FoundationDB
- **Test Stability**: Enhanced test infrastructure with better async operation handling

### Key Features Added
- FlowRpcConfiguration with builder pattern for transport customization
- Improved numeric type conversion in RPC serialization
- Enhanced error handling for edge cases in RPC transport
- Character support in Tuple encoding utilities
- Streamlined RPC serialization architecture

### Dependency Updates
- Mockito updated to 5.18.0 for improved testing capabilities
- AssertJ updated to 3.27.3 for enhanced assertions
- Vanniktech Maven Publish plugin updated to 0.32.0
- GitHub Actions updated to latest versions for CI/CD

### Bug Fixes
- Fixed checkstyle issues in FlowRpcConfiguration and tests
- Improved test stability by removing Thread.sleep() usage
- Fixed code coverage gaps in RPC implementation
- Resolved issues with private class access in tests

## Version 1.1.1

### Bug Fixes
- Fixed test method timeouts
- Improved coverage for RealFlowConnection.send() partial writes
- Reduced code branching complexity
- Fixed thread pool size issues in unit tests

## Version 1.1.0

### Major Features
- **Network Layer Implementation**: Complete asynchronous network I/O support
- **File System I/O**: Comprehensive asynchronous file operations
- **Transport Layer**: Message-based communication between components
- **Promise Streams**: Implementation of PromiseStream and FutureStream

### Improvements
- Added IOUtil for common I/O operations
- Enhanced FlowFuture.getNow() to handle interruptions
- Improved test coverage for edge cases
- Added comprehensive integration tests

## Version 1.0.1

### Bug Fixes
- Fixed Sonatype OSS repository authentication
- Updated Maven repository URL for publishing
- Improved javadoc generation

## Version 1.0.0

### Initial Release
- **Core Actor Framework**: Lightweight actors using JDK Continuations
- **Futures and Promises**: Asynchronous operation management
- **Single-threaded Scheduler**: Cooperative multitasking with priorities
- **Timer Support**: Time-based operations with controllable clock
- **Basic Error Handling**: Exception propagation through futures
- **Cancellation Support**: Automatic cancellation propagation

### Core Components
- FlowFuture & FlowPromise for async operations
- SingleThreadedScheduler for cooperative multitasking
- Task & TaskPriority for operation scheduling
- Flow API as main entry point
- FlowClock for time operations
- Deterministic testing support with pump method