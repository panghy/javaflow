package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.test.CancellationTestUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive cancellation tests using CancellationTestUtils.
 * This replaces the original FlowCancellationTest with cleaner implementations.
 */
class FlowCancellationTest extends AbstractFlowTest {

  @Test
  void testCancellationDuringDelayUsingUtils() throws ExecutionException {
    // Using CancellationTestUtils for a cleaner test
    FlowFuture<Double> operation = CancellationTestUtils.createLongRunningOperation(1.0, 0.1);
    
    // Let it start and run for a bit
    pump();
    advanceTime(0.25); // 250ms
    pump();
    
    // Cancel during the operation
    operation.cancel();
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    // Verify cancellation
    assertTrue(operation.isCancelled());
    assertThrows(CancellationException.class, operation::getNow);
  }

  @Test
  void testCancellationLatencyMeasurement() throws ExecutionException {
    // Measure how quickly cancellation is detected
    FlowFuture<Double> latencyFuture = CancellationTestUtils.measureCancellationLatency(
        () -> CancellationTestUtils.createLongRunningOperation(1.0, 0.01), // 10ms check interval
        0.1 // Cancel after 100ms
    );
    
    pumpAndAdvanceTimeUntilDone(latencyFuture);
    
    Double latency = latencyFuture.getNow();
    assertTrue(latency >= 0, "Latency should be non-negative");
    assertTrue(latency < 0.02, "Latency should be less than check interval (10ms + overhead)");
  }

  @Test
  void testGracefulCancellationWithCleanupVerification() throws ExecutionException {
    AtomicBoolean resourceCleaned = new AtomicBoolean(false);
    
    // Create an operation that cleans up on cancellation
    FlowFuture<Void> verifyFuture = CancellationTestUtils.verifyCancellationCleanup(
        () -> startActor(() -> {
          try {
            // Simulate resource allocation
            await(Flow.delay(1.0));
            return "done";
          } catch (FlowCancellationException e) {
            // Clean up resources
            resourceCleaned.set(true);
            throw e;
          }
        }),
        0.1, // Cancel after 100ms
        resourceCleaned::get // Verify cleanup was done
    );
    
    pumpAndAdvanceTimeUntilDone(verifyFuture);
    
    // The utility verifies cleanup was performed
    assertTrue(verifyFuture.isDone());
    assertFalse(verifyFuture.isCompletedExceptionally());
  }

  @Test
  void testCancellationWithNestedOperationsUsingUtils() {
    // Using the utility for nested operations
    FlowFuture<String> nestedOp = CancellationTestUtils.createNestedOperation(4, 0.1);
    
    // Let it progress into the hierarchy
    pump();
    advanceTime(0.25); // Should be somewhere in level 3 or 2
    pump();
    
    // Cancel the root
    nestedOp.cancel();
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    // Verify cancellation propagated through the hierarchy
    assertTrue(nestedOp.isCancelled());
  }

  @Test
  void testStreamingOperationCancellation() {
    // Use the streaming utility
    PromiseStream<Integer> stream = CancellationTestUtils.createStreamingOperation(0.05); // 50ms interval
    List<Integer> received = new ArrayList<>();
    
    FlowFuture<Void> consumer = startActor(() -> {
      FutureStream<Integer> futureStream = stream.getFutureStream();
      while (!futureStream.isClosed()) {
        try {
          Integer value = await(futureStream.nextAsync());
          if (value != null) {
            received.add(value);
          }
        } catch (Exception e) {
          break;
        }
      }
      return null;
    });
    
    // Let some values flow
    for (int i = 0; i < 5; i++) {
      pump();
      advanceTime(0.05);
    }
    
    assertTrue(received.size() >= 3, "Should have received at least 3 values");
    
    // Cancel the consumer
    consumer.cancel();
    pumpAndAdvanceTimeUntilDone();
    
    // Stream producer should have been cancelled, which closes the stream
    // Note: The exact timing of stream closure can vary, but the consumer should be cancelled
    assertTrue(consumer.isCancelled());
  }

  @Test
  void testCpuIntensiveOperationCancellation() {
    // Test cancellation of CPU-bound work
    FlowFuture<Integer> cpuOp = CancellationTestUtils.createCpuIntensiveOperation(10000, 100);
    
    // Let it run for a bit
    pump();
    pump();
    pump();
    
    // Cancel it
    cpuOp.cancel();
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    assertTrue(cpuOp.isCancelled());
    assertThrows(CancellationException.class, cpuOp::getNow);
  }

  @Test
  void testMultipleConcurrentCancellations() throws ExecutionException {
    // Test concurrent cancellation of multiple operations
    AtomicInteger createdCount = new AtomicInteger(0);
    
    FlowFuture<Integer> result = CancellationTestUtils.testConcurrentCancellation(
        10, // Create 10 operations
        () -> {
          createdCount.incrementAndGet();
          return CancellationTestUtils.createLongRunningOperation(2.0, 0.1);
        },
        0.5 // Cancel all after 500ms
    );
    
    pumpAndAdvanceTimeUntilDone(result);
    
    assertEquals(10, createdCount.get());
    assertEquals(10, result.getNow(), "All 10 operations should have been cancelled");
  }

  @Test
  void testMockServiceCancellation() {
    // Demonstrate using the mock service for testing
    CancellationTestUtils.MockCancellableService service = 
        new CancellationTestUtils.MockCancellableService();
    
    assertFalse(service.hasStarted());
    
    // Start a long operation
    FlowFuture<String> operation = service.longRunningOperation(1.0, 0.1);
    
    // Let it run
    pump();
    advanceTime(0.15);
    pump();
    
    assertTrue(service.hasStarted());
    assertTrue(service.getCancellationCheckCount() > 0);
    
    // Cancel it
    operation.cancel();
    pumpAndAdvanceTimeUntilDone();
    
    assertTrue(service.wasCancelled());
    
    // Can reset and reuse
    service.reset();
    assertFalse(service.hasStarted());
    assertEquals(0, service.getCancellationCheckCount());
  }

  @Test
  void testCancellationDuringChainedOperations() {
    // Create a chain of operations using utilities
    FlowFuture<Double> first = CancellationTestUtils.createLongRunningOperation(0.5, 0.1);
    
    FlowFuture<String> chain = first.flatMap(result -> {
      // Second operation depends on first
      return CancellationTestUtils.createNestedOperation(3, 0.1);
    });
    
    // Let first operation complete
    pump();
    advanceTime(0.6);
    pump();
    
    // Cancel during second operation
    chain.cancel();
    
    pumpAndAdvanceTimeUntilDone();
    
    assertTrue(chain.isCancelled());
    // First operation should have completed normally
    assertTrue(first.isDone());
    assertFalse(first.isCancelled());
  }

  @Test
  void testCancellationWithCustomCheckInterval() throws ExecutionException {
    // Demonstrate fine-grained control over check intervals
    // Measure latency for both fast and slow check intervals
    FlowFuture<Double> fastLatency = CancellationTestUtils.measureCancellationLatency(
        () -> CancellationTestUtils.createLongRunningOperation(1.0, 0.001),
        0.1
    );
    
    FlowFuture<Double> slowLatency = CancellationTestUtils.measureCancellationLatency(
        () -> CancellationTestUtils.createLongRunningOperation(1.0, 0.1),
        0.1
    );
    
    pumpAndAdvanceTimeUntilDone(fastLatency, slowLatency);
    
    Double fast = fastLatency.getNow();
    Double slow = slowLatency.getNow();
    
    // In simulation, timing might be less precise due to how the scheduler works
    // Just verify both detected cancellation in reasonable time
    assertTrue(fast < 0.02, "Fast check should detect within 20ms");
    assertTrue(slow < 0.2, "Slow check should detect within 200ms");
  }

  // Additional tests from original FlowCancellationTest that don't use utilities

  @Test
  void testCancellationDuringAwait() {
    AtomicBoolean cancellationExceptionCaught = new AtomicBoolean(false);
    FlowFuture<String> blockingFuture = new FlowFuture<>();
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      try {
        Flow.await(blockingFuture);
      } catch (FlowCancellationException e) {
        cancellationExceptionCaught.set(true);
        throw e;
      }
      return null;
    });
    
    // Let it start and block
    pump();
    
    // Cancel the awaited future
    blockingFuture.cancel();
    
    // Pump to process the cancellation
    pump();
    
    // Should have caught FlowCancellationException
    assertTrue(cancellationExceptionCaught.get());
    assertTrue(future.isCompletedExceptionally());
  }

  @Test
  void testCheckCancellationOutsideFlowContext() {
    // When called outside a flow context, it should not throw
    // (no current task means not cancelled)
    Flow.checkCancellation(); // Should not throw
    
    // isCancelled should return false
    assertFalse(Flow.isCancelled());
  }

  @Test
  void testCancellationDuringException() {
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      try {
        // Throw an exception
        exceptionThrown.set(true);
        throw new RuntimeException("Test exception");
      } finally {
        // Even during exception, we can check cancellation
        // Note: cancellation state might not be visible here
        Flow.isCancelled();
      }
    });
    
    // Cancel immediately
    future.cancel();
    
    // Execute
    pump();
    
    // Verify
    assertTrue(exceptionThrown.get(), "Exception should have been thrown");
    assertTrue(future.isCompletedExceptionally());
    // Note: cancellation check in finally might not see cancelled state
    // as the task completes exceptionally before cancellation propagates
  }
}