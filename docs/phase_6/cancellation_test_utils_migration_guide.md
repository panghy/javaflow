# CancellationTestUtils Migration Guide

This guide demonstrates how to migrate existing cancellation tests to use the new `CancellationTestUtils` utilities for cleaner, more maintainable test code.

## Overview of CancellationTestUtils

The `CancellationTestUtils` class provides standardized utilities for testing cancellation behavior:

- **Long-running operations**: `createLongRunningOperation(duration, checkInterval)`
- **CPU-intensive operations**: `createCpuIntensiveOperation(iterations, yieldInterval)`
- **Streaming operations**: `createStreamingOperation(interval)`
- **Nested operations**: `createNestedOperation(depth, delayPerLevel)`
- **Cancellation latency measurement**: `measureCancellationLatency(operation, cancelAfter)`
- **Cleanup verification**: `verifyCancellationCleanup(operation, cancelAfter, cleanupChecker)`
- **Concurrent cancellation testing**: `testConcurrentCancellation(count, operationSupplier, cancelAfter)`
- **Mock service**: `MockCancellableService` with built-in tracking

## Migration Examples

### Example 1: FlowCancellationTest.testCancellationDuringDelay()

**Before:**
```java
@Test
void testCancellationDuringDelay() {
    AtomicReference<String> status = new AtomicReference<>("not started");
    
    FlowFuture<Void> future = Flow.startActor(() -> {
        status.set("before delay");
        Flow.await(Flow.delay(1.0)); // 1 second delay
        status.set("after delay");
        return null;
    });
    
    // Let it start and begin the delay
    pump();
    assertEquals("before delay", status.get());
    
    // Advance time partially
    advanceTime(0.5);
    pump();
    
    // Cancel during the delay
    future.cancel();
    
    // Try to complete the delay
    advanceTime(0.6);
    pump();
    
    // Status should not have progressed past the delay
    assertEquals("before delay", status.get());
    assertTrue(future.isCancelled());
}
```

**After:**
```java
@Test
void testCancellationDuringDelay() {
    // Use utility for cleaner test
    FlowFuture<Double> operation = CancellationTestUtils.createLongRunningOperation(1.0, 0.1);
    
    // Let it start and run for a bit
    pump();
    advanceTime(0.5);
    pump();
    
    // Cancel during the operation
    operation.cancel();
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    // Verify cancellation
    assertTrue(operation.isCancelled());
}
```

**Benefits:**
- Less boilerplate code
- Built-in cancellation checking at regular intervals
- Returns actual runtime for performance testing

### Example 2: FlowCancellationTest.testGracefulCancellationHandling()

**Before:**
```java
@Test
void testGracefulCancellationHandling() {
    AtomicInteger workCompleted = new AtomicInteger(0);
    AtomicBoolean cleanupDone = new AtomicBoolean(false);
    
    FlowFuture<String> future = Flow.startActor(() -> {
        try {
            for (int i = 0; i < 10; i++) {
                if (Flow.isCancelled()) {
                    return "cancelled at " + i;
                }
                workCompleted.incrementAndGet();
                Flow.await(Flow.delay(0.1));
            }
            return "completed";
        } finally {
            cleanupDone.set(true);
        }
    });
    
    // Test logic...
}
```

**After:**
```java
@Test
void testGracefulCancellationHandling() {
    AtomicBoolean resourceCleaned = new AtomicBoolean(false);
    
    FlowFuture<Void> verifyFuture = CancellationTestUtils.verifyCancellationCleanup(
        () -> startActor(() -> {
            try {
                await(Flow.delay(1.0));
                return "done";
            } catch (FlowCancellationException e) {
                resourceCleaned.set(true);
                throw e;
            }
        }),
        0.1, // Cancel after 100ms
        resourceCleaned::get // Verify cleanup
    );
    
    pumpAndAdvanceTimeUntilDone(verifyFuture);
    
    // Utility verifies cleanup was performed
    assertTrue(verifyFuture.isDone());
    assertFalse(verifyFuture.isCompletedExceptionally());
}
```

**Benefits:**
- Built-in cleanup verification
- Automatic timing of cancellation
- Clear separation of setup and verification

### Example 3: RemoteCancellationTest.CancellableServiceImpl

**Before:**
```java
public static class CancellableServiceImpl implements CancellableService {
    private final AtomicBoolean cooperativeOpStarted = new AtomicBoolean(false);
    private final AtomicBoolean cooperativeOpCancelled = new AtomicBoolean(false);
    
    @Override
    public FlowFuture<String> cooperativeCancellableOperation() {
        return startActor(() -> {
            cooperativeOpStarted.set(true);
            try {
                for (int i = 0; i < 100; i++) {
                    if (Flow.isCancelled()) {
                        cooperativeOpCancelled.set(true);
                        return "cancelled at " + i;
                    }
                    await(Flow.delay(0.01));
                }
                return "completed";
            } finally {
                cooperativeOpCancelled.set(Flow.isCancelled());
            }
        });
    }
}
```

**After:**
```java
// Use the provided MockCancellableService
CancellationTestUtils.MockCancellableService service = 
    new CancellationTestUtils.MockCancellableService();

// In test:
FlowFuture<String> operation = service.longRunningOperation(1.0, 0.01);

// Verify with built-in tracking
assertTrue(service.hasStarted());
assertTrue(service.wasCancelled());
assertEquals(expectedCount, service.getCancellationCheckCount());
```

**Benefits:**
- Reusable mock service
- Built-in tracking of cancellation behavior
- Cancellation check counting
- Reset capability for test reuse

### Example 4: Testing Nested Operations

**Before (custom implementation):**
```java
FlowFuture<String> createNested(int depth) {
    return startActor(() -> {
        if (depth == 0) return "done";
        FlowFuture<String> child = createNested(depth - 1);
        await(Flow.delay(0.1));
        return "level-" + depth + "-" + await(child);
    });
}
```

**After:**
```java
// Use the utility
FlowFuture<String> nested = CancellationTestUtils.createNestedOperation(4, 0.1);
```

**Benefits:**
- Standard implementation
- Consistent delay handling
- Built-in cancellation propagation testing

## Performance Testing with CancellationTestUtils

The utilities enable easy performance testing of cancellation:

```java
@Test
void testCancellationLatency() throws ExecutionException {
    // Measure how quickly cancellation is detected
    FlowFuture<Double> latency = CancellationTestUtils.measureCancellationLatency(
        () -> CancellationTestUtils.createLongRunningOperation(1.0, 0.01),
        0.1 // Cancel after 100ms
    );
    
    pumpAndAdvanceTimeUntilDone(latency);
    
    Double latencySeconds = latency.getNow();
    assertTrue(latencySeconds < 0.02, "Should detect cancellation quickly");
}
```

## Best Practices

1. **Use appropriate check intervals**: Balance between responsiveness and overhead
   - Fast checks (1-10ms): For latency-sensitive operations
   - Medium checks (50-100ms): For general operations
   - Slow checks (500ms+): For background operations

2. **Verify cleanup**: Always use `verifyCancellationCleanup()` when testing resource cleanup

3. **Test concurrent scenarios**: Use `testConcurrentCancellation()` for testing multiple operations

4. **Measure performance**: Use `measureCancellationLatency()` to ensure cancellation is responsive

5. **Reuse mock services**: Use `MockCancellableService` and reset between tests

## Summary

CancellationTestUtils provides:
- **Consistency**: Standard patterns across all cancellation tests
- **Simplicity**: Less boilerplate code
- **Metrics**: Built-in performance measurement
- **Reusability**: Common utilities for all test scenarios
- **Maintainability**: Centralized implementation of test patterns

By migrating to these utilities, tests become more readable, maintainable, and comprehensive in their coverage of cancellation scenarios.