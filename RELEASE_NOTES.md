# Release Notes

## Version 1.3.0 (Upcoming)

_To be determined_

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