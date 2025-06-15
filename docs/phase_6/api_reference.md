# JavaFlow Phase 6: API Reference

## Overview

This document provides comprehensive API reference for Phase 6's remote cancellation functionality. The API is designed to be minimal and intuitive while providing powerful cancellation capabilities.

## Core Classes and Methods

### FlowCancellationException

A specialized exception for Flow cancellation operations.

```java
package io.github.panghy.javaflow.core;

public class FlowCancellationException extends RuntimeException
```

#### Constructors

##### `FlowCancellationException(String message)`

Creates a new cancellation exception with the specified message.

**Parameters:**
- `message` - The detail message explaining the cancellation

**Example:**
```java
throw new FlowCancellationException("Operation was cancelled by user");
```

##### `FlowCancellationException(String message, Throwable cause)`

Creates a new cancellation exception with the specified message and cause.

**Parameters:**
- `message` - The detail message explaining the cancellation
- `cause` - The underlying cause of the cancellation

**Example:**
```java
try {
    // Some operation
} catch (CancellationException e) {
    throw new FlowCancellationException("Future was cancelled", e);
}
```

#### Usage Guidelines

- **Do NOT catch** this exception in normal application code
- This exception indicates the operation should be aborted immediately
- Let the exception propagate to properly cancel the actor
- Only catch for cleanup purposes, then re-throw

### Flow Utility Methods

Enhanced methods in the `Flow` class for cancellation detection.

#### `Flow.checkCancellation()`

Checks if the current task has been cancelled and throws an exception if so.

```java
public static void checkCancellation() throws FlowCancellationException
```

**Throws:**
- `FlowCancellationException` - if the current task is cancelled
- `IllegalStateException` - if called outside a flow context

**Usage:**
```java
Flow.startActor(() -> {
    for (int i = 0; i < 1000000; i++) {
        // Check for cancellation every 1000 iterations
        if (i % 1000 == 0) {
            Flow.checkCancellation();
        }
        // Do CPU-intensive work
        performComputation(i);
    }
    return "completed";
});
```

**Best Practices:**
- Call periodically in long-running loops
- Call before expensive operations
- Don't call too frequently (impacts performance)
- Typical frequency: every 1000-10000 iterations

#### `Flow.isCancelled()`

Returns true if the current task has been cancelled.

```java
public static boolean isCancelled()
```

**Returns:**
- `true` if the current task is cancelled
- `false` if the current task is not cancelled or if called outside a flow context

**Usage:**
```java
Flow.startActor(() -> {
    while (!Flow.isCancelled()) {
        // Do work
        if (someCondition()) {
            break; // Normal exit
        }
        performWork();
    }
    
    if (Flow.isCancelled()) {
        // Perform cleanup
        cleanup();
        return null; // Exit gracefully
    }
    
    return "completed";
});
```

**Best Practices:**
- Use for conditional logic based on cancellation
- Prefer `checkCancellation()` for immediate abort behavior
- Useful when you need to perform cleanup before exiting

#### Enhanced `Flow.await()`

The `await` method now throws `FlowCancellationException` instead of `CancellationException`.

```java
public static <T> T await(FlowFuture<T> future) throws Exception
```

**Changes:**
- Now throws `FlowCancellationException` when a future is cancelled
- Checks for task cancellation before awaiting
- Maintains backward compatibility for non-cancellation exceptions

**Example:**
```java
Flow.startActor(() -> {
    try {
        FlowFuture<String> future = someAsyncOperation();
        String result = Flow.await(future);
        return result;
    } catch (FlowCancellationException e) {
        // Don't catch this unless you need cleanup
        performCleanup();
        throw e; // Re-throw to properly cancel
    }
});
```

## Remote Cancellation Patterns

### Client-Side Cancellation

```java
// Start a remote operation
RemoteService service = rpcTransport.getRpcStub(endpoint, RemoteService.class);
FlowFuture<String> remoteFuture = service.longRunningOperation();

// Cancel the operation
Flow.startActor(() -> {
    Flow.await(Flow.delay(5.0)); // Wait 5 seconds
    remoteFuture.cancel(); // Cancel the remote operation
    return null;
});

// Handle the result
try {
    String result = Flow.await(remoteFuture);
    System.out.println("Result: " + result);
} catch (FlowCancellationException e) {
    System.out.println("Operation was cancelled");
}
```

### Server-Side Cancellation Handling

```java
public class RemoteServiceImpl implements RemoteService {
    @Override
    public FlowFuture<String> longRunningOperation() {
        return Flow.startActor(() -> {
            for (int i = 0; i < 1000000; i++) {
                // Check for cancellation from client
                Flow.checkCancellation();
                
                // Perform work
                processItem(i);
                
                // Yield occasionally for responsiveness
                if (i % 10000 == 0) {
                    Flow.await(Flow.yield());
                }
            }
            return "Operation completed";
        });
    }
}
```

## Performance Considerations

### Cancellation Check Frequency

The frequency of cancellation checks affects both responsiveness and performance:

```java
// Too frequent - impacts performance
for (int i = 0; i < 1000000; i++) {
    Flow.checkCancellation(); // Called 1M times
    doWork(i);
}

// Good balance - responsive but efficient
for (int i = 0; i < 1000000; i++) {
    if (i % 1000 == 0) {
        Flow.checkCancellation(); // Called 1K times
    }
    doWork(i);
}

// Too infrequent - poor responsiveness
for (int i = 0; i < 1000000; i++) {
    if (i % 100000 == 0) {
        Flow.checkCancellation(); // Called 10 times
    }
    doWork(i);
}
```

### Recommended Frequencies

| Operation Type | Check Frequency | Rationale |
|----------------|-----------------|-----------|
| CPU-intensive loops | Every 1000-10000 iterations | Balance performance and responsiveness |
| I/O operations | Before each I/O call | I/O is already expensive |
| Network operations | Before each network call | Network latency dominates |
| File operations | Before each file operation | Disk I/O is expensive |

## Error Handling Patterns

### Proper Exception Handling

```java
// CORRECT: Let cancellation propagate
Flow.startActor(() -> {
    try {
        return performOperation();
    } catch (IOException e) {
        // Handle specific exceptions
        throw new RuntimeException("I/O error", e);
    }
    // FlowCancellationException propagates automatically
});

// INCORRECT: Catching cancellation exception
Flow.startActor(() -> {
    try {
        return performOperation();
    } catch (FlowCancellationException e) {
        // DON'T DO THIS - prevents proper cancellation
        return "default value";
    }
});
```

### Cleanup with Cancellation

```java
// CORRECT: Cleanup then re-throw
Flow.startActor(() -> {
    Resource resource = acquireResource();
    try {
        return performOperation(resource);
    } catch (FlowCancellationException e) {
        // Cleanup is OK
        resource.cleanup();
        throw e; // Must re-throw
    } finally {
        // Finally blocks also work
        resource.close();
    }
});
```

## Migration Guide

### From CancellationException to FlowCancellationException

**Before:**
```java
try {
    String result = Flow.await(future);
} catch (CancellationException e) {
    // Handle cancellation
}
```

**After:**
```java
try {
    String result = Flow.await(future);
} catch (FlowCancellationException e) {
    // Handle cancellation (but usually don't catch)
    throw e; // Re-throw to properly cancel
}
```

### Adding Cancellation Checks to Existing Code

**Before:**
```java
Flow.startActor(() -> {
    for (int i = 0; i < 1000000; i++) {
        processItem(i);
    }
    return "done";
});
```

**After:**
```java
Flow.startActor(() -> {
    for (int i = 0; i < 1000000; i++) {
        if (i % 1000 == 0) {
            Flow.checkCancellation(); // Add cancellation check
        }
        processItem(i);
    }
    return "done";
});
```

## Common Pitfalls

### 1. Catching FlowCancellationException

```java
// WRONG - prevents proper cancellation
try {
    Flow.checkCancellation();
} catch (FlowCancellationException e) {
    return "cancelled"; // Don't do this
}

// RIGHT - let it propagate
Flow.checkCancellation(); // Will throw if cancelled
```

### 2. Not Checking Cancellation in Loops

```java
// WRONG - can't be cancelled
for (int i = 0; i < 1000000; i++) {
    expensiveOperation(i);
}

// RIGHT - can be cancelled
for (int i = 0; i < 1000000; i++) {
    if (i % 1000 == 0) {
        Flow.checkCancellation();
    }
    expensiveOperation(i);
}
```

### 3. Checking Cancellation Too Frequently

```java
// WRONG - performance impact
for (int i = 0; i < 1000000; i++) {
    Flow.checkCancellation(); // Every iteration
    quickOperation(i);
}

// RIGHT - balanced approach
for (int i = 0; i < 1000000; i++) {
    if (i % 1000 == 0) {
        Flow.checkCancellation(); // Every 1000 iterations
    }
    quickOperation(i);
}
```

## Debugging and Troubleshooting

### Enable Cancellation Logging

```java
// Add logging to track cancellation
Flow.startActor(() -> {
    try {
        return performOperation();
    } catch (FlowCancellationException e) {
        logger.info("Operation cancelled: " + e.getMessage());
        throw e;
    }
});
```

### Verify Cancellation Propagation

```java
// Test that cancellation works
@Test
void testCancellation() {
    AtomicBoolean cancelled = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
        try {
            Flow.await(Flow.delay(10.0)); // Long delay
        } catch (FlowCancellationException e) {
            cancelled.set(true);
            throw e;
        }
        return null;
    });
    
    future.cancel();
    
    assertThrows(FlowCancellationException.class, () -> Flow.await(future));
    assertTrue(cancelled.get());
}
```
