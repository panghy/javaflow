# JavaFlow Phase 6: Remote Future Cancellation Design

## Overview

Phase 6 focuses on implementing comprehensive cancellation propagation for remote futures in JavaFlow's RPC system. The goal is to ensure that when a client cancels a future representing a remote operation, the cancellation properly propagates to the server executing that operation, allowing for immediate cleanup and resource reclamation.

## Current State Analysis

### Existing Cancellation Infrastructure

JavaFlow already has a robust local cancellation system in place:

1. **Task-Level Cancellation**: The `Task` class implements parent/child cancellation relationships where cancelling a parent automatically cancels all child tasks.

2. **Future Cancellation**: `FlowFuture.cancel()` properly marks futures as cancelled and propagates `CancellationException` to awaiting actors.

3. **RPC Cancellation Messages**: The RPC transport layer already supports sending cancellation notifications via `sendCancellation()` method.

4. **Remote Promise Tracking**: The `RemotePromiseTracker` class handles cancellation propagation for remote promises and includes cleanup logic for disconnected endpoints.

### Gap Analysis

The primary gap is in **immediate cancellation detection** within executing actors. Currently:

- Cancellation only propagates when an actor hits an `await()` point
- Long-running computations cannot detect cancellation until they yield
- There's no standardized `FlowCancellationException` type
- Limited testing of end-to-end remote cancellation scenarios

## Design Goals

1. **Immediate Cancellation Detection**: Actors should be able to detect cancellation even during CPU-intensive work
2. **Standardized Exception Type**: Introduce `FlowCancellationException` for consistent cancellation handling
3. **Comprehensive Testing**: Ensure remote cancellation works reliably across network boundaries
4. **Backward Compatibility**: Maintain existing cancellation behavior while adding new capabilities

## Core Components

### 1. FlowCancellationException

A specialized exception type for cancellation that should not be caught by normal exception handling:

```java
/**
 * Exception thrown when a Flow operation is cancelled.
 * This exception should generally not be caught by user code,
 * as it indicates the operation should be aborted immediately.
 */
public class FlowCancellationException extends RuntimeException {
    public FlowCancellationException(String message) {
        super(message);
    }
    
    public FlowCancellationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 2. Enhanced Cancellation Detection

Add utility methods to check for cancellation during long-running operations:

```java
public final class Flow {
    /**
     * Checks if the current task has been cancelled.
     * This can be called during CPU-intensive operations to detect cancellation.
     * 
     * @throws FlowCancellationException if the current task is cancelled
     */
    public static void checkCancellation() {
        Task currentTask = FlowScheduler.CURRENT_TASK.get();
        if (currentTask != null && currentTask.isCancelled()) {
            throw new FlowCancellationException("Task was cancelled");
        }
    }
    
    /**
     * Returns true if the current task has been cancelled.
     * This is a non-throwing version for conditional logic.
     */
    public static boolean isCancelled() {
        Task currentTask = FlowScheduler.CURRENT_TASK.get();
        return currentTask != null && currentTask.isCancelled();
    }
}
```

### 3. Integration with Existing Systems

The existing cancellation infrastructure requires minimal changes:

- **Task.cancel()**: Already propagates to children and calls cancellation callbacks
- **FlowFuture.cancel()**: Already completes with `CancellationException`
- **RemotePromiseTracker**: Already sends cancellation messages to remote endpoints
- **RPC Transport**: Already handles cancellation message routing

### 4. Enhanced await() Behavior

Update the `await()` method to throw `FlowCancellationException` instead of generic `CancellationException`:

```java
public static <T> T await(FlowFuture<T> future) throws Exception {
    if (futureReadyOrThrow(future)) {
        return future.getNow();
    }
    
    // Check for cancellation before awaiting
    checkCancellation();
    
    try {
        return scheduler.await(future);
    } catch (CancellationException e) {
        throw new FlowCancellationException("Future was cancelled", e);
    }
}
```

## Remote Cancellation Flow

### Client-Side Cancellation

1. Client calls `future.cancel()` on a remote future
2. `FlowFuture.cancel()` marks the future as cancelled
3. `RemotePromiseTracker` detects the cancellation and sends a cancellation message
4. Client-side awaiting actors receive `FlowCancellationException`

### Server-Side Cancellation Handling

1. Server receives cancellation message via RPC transport
2. `RemotePromiseTracker` marks the corresponding local promise as cancelled
3. Server-side actor awaiting the promise receives `FlowCancellationException`
4. Actor should cleanup and exit (not catch the exception)
5. Any child tasks are automatically cancelled via existing Task hierarchy

### Network Partition Handling

The existing `RemotePromiseTracker.cancelPromisesForEndpoint()` method already handles connection failures by cancelling all pending promises for a disconnected endpoint.

## Testing Strategy

### Unit Tests

1. **Local Cancellation Tests**: Verify `Flow.checkCancellation()` works correctly
2. **Exception Type Tests**: Ensure `FlowCancellationException` is thrown appropriately
3. **Task Hierarchy Tests**: Verify parent/child cancellation still works

### Integration Tests

1. **Remote Cancellation Tests**: End-to-end tests of client cancelling server operations
2. **Long-Running Operation Tests**: Verify cancellation during CPU-intensive work
3. **Network Failure Tests**: Ensure cancellation works when connections drop

### Example Test Scenarios

```java
@Test
void testRemoteCancellationPropagation() {
    // Start a server with a long-running operation
    FlowFuture<String> serverResult = Flow.startActor(() -> {
        for (int i = 0; i < 1000000; i++) {
            Flow.checkCancellation(); // Check for cancellation periodically
            // Simulate work
            if (i % 10000 == 0) {
                Flow.await(Flow.yield()); // Yield occasionally
            }
        }
        return "completed";
    });
    
    // Client cancels the operation
    serverResult.cancel();
    
    // Verify the server operation was cancelled
    assertThrows(FlowCancellationException.class, () -> Flow.await(serverResult));
}
```

## Implementation Plan

### Phase 6.1: Core Cancellation Infrastructure
- Implement `FlowCancellationException`
- Add `Flow.checkCancellation()` and `Flow.isCancelled()` methods
- Update `Flow.await()` to throw `FlowCancellationException`

### Phase 6.2: Enhanced Testing
- Create comprehensive test suite for remote cancellation scenarios
- Add performance tests for cancellation detection overhead
- Test edge cases like rapid cancellation and network failures

### Phase 6.3: Documentation and Examples
- Update API documentation with cancellation best practices
- Create examples showing proper cancellation handling
- Document performance considerations for `checkCancellation()` calls

## Performance Considerations

1. **Minimal Overhead**: `Flow.checkCancellation()` should be a simple boolean check
2. **Strategic Placement**: Recommend calling `checkCancellation()` in loops and before expensive operations
3. **Existing Yield Points**: All existing `await()` calls already check for cancellation

## Backward Compatibility

- Existing code continues to work unchanged
- New `FlowCancellationException` is a `RuntimeException`, so it doesn't require catch blocks
- Existing `CancellationException` handling still works but is discouraged

## Benefits

1. **Immediate Responsiveness**: Long-running operations can be cancelled immediately
2. **Resource Efficiency**: Cancelled operations stop consuming CPU and memory quickly
3. **Better User Experience**: Client applications can cancel slow operations reliably
4. **Robust Error Handling**: Standardized cancellation exception type improves error handling

## Conclusion

Phase 6 builds upon JavaFlow's existing robust cancellation infrastructure to provide comprehensive remote cancellation capabilities. The design is minimal and focused, requiring only a few new utility methods and a specialized exception type. The existing Task hierarchy and RPC transport already handle the complex aspects of cancellation propagation, making this phase primarily about providing better tools for actors to detect and respond to cancellation.
