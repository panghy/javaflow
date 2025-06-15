# JavaFlow Phase 6: Remote Cancellation Testing Guide

## Overview

This document outlines the comprehensive testing strategy for Phase 6's remote cancellation functionality. The tests are designed to verify that cancellation properly propagates across network boundaries and that long-running operations can be cancelled immediately.

## Test Categories

### 1. Local Cancellation Enhancement Tests

These tests verify the new cancellation detection mechanisms work correctly within a single process.

#### Basic Cancellation Detection

```java
@Test
void testCheckCancellationThrowsWhenCancelled() {
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
        try {
            // Simulate some work
            for (int i = 0; i < 100; i++) {
                Flow.checkCancellation();
                Thread.sleep(10); // Simulate CPU work
            }
        } catch (FlowCancellationException e) {
            exceptionThrown.set(true);
            throw e; // Re-throw to properly cancel the actor
        }
        return null;
    });
    
    // Cancel after a short delay
    Flow.startActor(() -> {
        Flow.await(Flow.delay(0.05)); // 50ms delay
        future.cancel();
        return null;
    });
    
    // Verify the actor was cancelled
    assertThrows(FlowCancellationException.class, () -> Flow.await(future));
    assertTrue(exceptionThrown.get(), "FlowCancellationException should have been thrown");
}
```

#### Non-Throwing Cancellation Check

```java
@Test
void testIsCancelledReturnsTrueWhenCancelled() {
    AtomicBoolean cancellationDetected = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
        for (int i = 0; i < 1000; i++) {
            if (Flow.isCancelled()) {
                cancellationDetected.set(true);
                return null; // Exit gracefully
            }
            // Simulate work
            Thread.sleep(1);
        }
        return null;
    });
    
    // Cancel after a short delay
    Flow.startActor(() -> {
        Flow.await(Flow.delay(0.01)); // 10ms delay
        future.cancel();
        return null;
    });
    
    Flow.await(future);
    assertTrue(cancellationDetected.get(), "Cancellation should have been detected");
}
```

### 2. Remote Cancellation Tests

These tests verify that cancellation properly propagates across network boundaries.

#### Basic Remote Cancellation

```java
@Test
void testRemoteFutureCancellation() {
    // Set up server with a long-running operation
    TestRpcServer server = new TestRpcServer();
    server.registerService(new LongRunningService() {
        @Override
        public FlowFuture<String> longOperation() {
            return Flow.startActor(() -> {
                for (int i = 0; i < 1000000; i++) {
                    Flow.checkCancellation(); // Check for cancellation
                    if (i % 10000 == 0) {
                        Flow.await(Flow.yield()); // Yield periodically
                    }
                }
                return "completed";
            });
        }
    });
    
    // Client calls the remote service
    LongRunningService client = rpcTransport.getRpcStub(
        server.getEndpoint(), LongRunningService.class);
    FlowFuture<String> remoteFuture = client.longOperation();
    
    // Cancel the remote operation after a short delay
    Flow.startActor(() -> {
        Flow.await(Flow.delay(0.1)); // 100ms delay
        remoteFuture.cancel();
        return null;
    });
    
    // Verify the remote operation was cancelled
    assertThrows(FlowCancellationException.class, () -> Flow.await(remoteFuture));
}
```

#### Cancellation During Network I/O

```java
@Test
void testCancellationDuringNetworkIO() {
    // Server that performs network I/O
    TestRpcServer server = new TestRpcServer();
    server.registerService(new NetworkService() {
        @Override
        public FlowFuture<String> fetchData() {
            return Flow.startActor(() -> {
                // Simulate multiple network calls
                for (int i = 0; i < 10; i++) {
                    Flow.checkCancellation();
                    // Simulate network I/O with delay
                    Flow.await(Flow.delay(0.5)); // 500ms per call
                }
                return "data";
            });
        }
    });
    
    NetworkService client = rpcTransport.getRpcStub(
        server.getEndpoint(), NetworkService.class);
    FlowFuture<String> future = client.fetchData();
    
    // Cancel after 1 second (should interrupt during I/O)
    Flow.startActor(() -> {
        Flow.await(Flow.delay(1.0));
        future.cancel();
        return null;
    });
    
    assertThrows(FlowCancellationException.class, () -> Flow.await(future));
}
```

### 3. Stress Tests

These tests verify cancellation works under high load and edge conditions.

#### Rapid Cancellation Test

```java
@Test
void testRapidCancellation() {
    List<FlowFuture<String>> futures = new ArrayList<>();
    
    // Start many long-running operations
    for (int i = 0; i < 100; i++) {
        FlowFuture<String> future = Flow.startActor(() -> {
            for (int j = 0; j < 1000000; j++) {
                Flow.checkCancellation();
                if (j % 1000 == 0) {
                    Flow.await(Flow.yield());
                }
            }
            return "completed";
        });
        futures.add(future);
    }
    
    // Cancel all operations rapidly
    Flow.startActor(() -> {
        Flow.await(Flow.delay(0.01)); // Small delay
        for (FlowFuture<String> future : futures) {
            future.cancel();
        }
        return null;
    });
    
    // Verify all were cancelled
    for (FlowFuture<String> future : futures) {
        assertThrows(FlowCancellationException.class, () -> Flow.await(future));
    }
}
```

#### Network Partition During Cancellation

```java
@Test
void testCancellationDuringNetworkPartition() {
    TestRpcServer server = new TestRpcServer();
    server.registerService(new LongRunningService() {
        @Override
        public FlowFuture<String> longOperation() {
            return Flow.startActor(() -> {
                for (int i = 0; i < 1000000; i++) {
                    Flow.checkCancellation();
                    if (i % 10000 == 0) {
                        Flow.await(Flow.yield());
                    }
                }
                return "completed";
            });
        }
    });
    
    LongRunningService client = rpcTransport.getRpcStub(
        server.getEndpoint(), LongRunningService.class);
    FlowFuture<String> future = client.longOperation();
    
    // Simulate network partition by closing the server
    Flow.startActor(() -> {
        Flow.await(Flow.delay(0.1));
        server.close(); // This should trigger cancellation
        return null;
    });
    
    // Should get cancellation due to connection loss
    assertThrows(Exception.class, () -> Flow.await(future));
}
```

### 4. Performance Tests

These tests verify that cancellation detection doesn't significantly impact performance.

#### Cancellation Check Overhead

```java
@Test
void testCancellationCheckOverhead() {
    long startTime = System.nanoTime();
    
    Flow.await(Flow.startActor(() -> {
        for (int i = 0; i < 1000000; i++) {
            Flow.checkCancellation();
        }
        return null;
    }));
    
    long duration = System.nanoTime() - startTime;
    double durationMs = duration / 1_000_000.0;
    
    // Should complete quickly (less than 100ms for 1M checks)
    assertTrue(durationMs < 100, 
        "Cancellation checks took too long: " + durationMs + "ms");
}
```

### 5. Edge Case Tests

#### Cancellation After Completion

```java
@Test
void testCancellationAfterCompletion() {
    FlowFuture<String> future = Flow.startActor(() -> {
        Flow.await(Flow.delay(0.01));
        return "completed";
    });
    
    // Wait for completion
    String result = Flow.await(future);
    assertEquals("completed", result);
    
    // Try to cancel after completion (should have no effect)
    boolean cancelled = future.cancel();
    assertFalse(cancelled, "Should not be able to cancel completed future");
}
```

#### Nested Cancellation

```java
@Test
void testNestedCancellation() {
    AtomicInteger cancellationCount = new AtomicInteger(0);
    
    FlowFuture<Void> parentFuture = Flow.startActor(() -> {
        FlowFuture<Void> childFuture = Flow.startActor(() -> {
            try {
                for (int i = 0; i < 1000000; i++) {
                    Flow.checkCancellation();
                }
            } catch (FlowCancellationException e) {
                cancellationCount.incrementAndGet();
                throw e;
            }
            return null;
        });
        
        try {
            Flow.await(childFuture);
        } catch (FlowCancellationException e) {
            cancellationCount.incrementAndGet();
            throw e;
        }
        return null;
    });
    
    // Cancel the parent
    Flow.startActor(() -> {
        Flow.await(Flow.delay(0.01));
        parentFuture.cancel();
        return null;
    });
    
    assertThrows(FlowCancellationException.class, () -> Flow.await(parentFuture));
    assertTrue(cancellationCount.get() >= 1, "At least one cancellation should be detected");
}
```

## Test Execution Strategy

### Continuous Integration

1. **Unit Tests**: Run on every commit to verify basic functionality
2. **Integration Tests**: Run on pull requests to verify remote cancellation
3. **Stress Tests**: Run nightly to catch performance regressions
4. **Edge Case Tests**: Run weekly to verify robustness

### Manual Testing

1. **Interactive Cancellation**: Manual tests with user-initiated cancellation
2. **Network Simulation**: Tests with simulated network conditions
3. **Load Testing**: High-concurrency cancellation scenarios

## Success Criteria

1. **Correctness**: All cancellation scenarios work as expected
2. **Performance**: Cancellation detection adds < 1% overhead
3. **Reliability**: No race conditions or deadlocks in cancellation logic
4. **Completeness**: 100% test coverage of new cancellation code paths

## Debugging and Troubleshooting

### Common Issues

1. **Cancellation Not Detected**: Ensure `Flow.checkCancellation()` is called regularly
2. **Performance Degradation**: Reduce frequency of cancellation checks
3. **Race Conditions**: Use proper synchronization in test setup

### Debugging Tools

1. **Logging**: Enable debug logging for cancellation events
2. **Profiling**: Use profilers to measure cancellation overhead
3. **Simulation**: Use deterministic simulation mode for reproducible tests
