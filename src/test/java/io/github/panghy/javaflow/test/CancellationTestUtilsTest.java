package io.github.panghy.javaflow.test;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
/**
 * Tests for CancellationTestUtils to ensure the utilities work correctly.
 */
class CancellationTestUtilsTest extends AbstractFlowTest {

  @Test
  void testCreateLongRunningOperation() throws ExecutionException {
    // Test normal completion
    CompletableFuture<Double> operation = CancellationTestUtils.createLongRunningOperation(0.1, 0.02);
    
    pumpAndAdvanceTimeUntilDone(operation);
    
    assertTrue(operation.isDone());
    assertFalse(operation.isCancelled());
    
    Double runtime = operation.getNow(null);
    assertTrue(runtime >= 0.1, "Operation should run for at least the specified duration");
  }

  @Test
  void testCreateLongRunningOperationCancellation() {
    // Test cancellation
    CompletableFuture<Double> operation = CancellationTestUtils.createLongRunningOperation(1.0, 0.1);
    
    // Let it run for a bit
    pump();
    advanceTime(0.15);
    pump();
    
    // Cancel it
    operation.cancel(true);
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    assertTrue(operation.isCancelled());
    // When cancelled, getNow(null) throws CancellationException
    assertThrows(CancellationException.class, () -> operation.getNow(null));
  }

  @Test
  void testCreateCpuIntensiveOperation() throws ExecutionException {
    // Test normal completion
    CompletableFuture<Integer> operation = CancellationTestUtils.createCpuIntensiveOperation(100, 10);
    
    pumpAndAdvanceTimeUntilDone(operation);
    
    assertEquals(100, operation.getNow(null));
  }

  @Test
  void testCreateCpuIntensiveOperationCancellation() {
    // Test cancellation
    CompletableFuture<Integer> operation = CancellationTestUtils.createCpuIntensiveOperation(1000, 50);
    
    // Let it run for a bit
    pump();
    pump();
    
    // Cancel it
    operation.cancel(true);
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    assertTrue(operation.isCancelled());
    // When cancelled, getNow(null) throws CancellationException
    assertThrows(CancellationException.class, () -> operation.getNow(null));
  }

  @Test
  void testCreateStreamingOperation() {
    PromiseStream<Integer> stream = CancellationTestUtils.createStreamingOperation(0.1);
    List<Integer> received = new ArrayList<>();
    
    CompletableFuture<Void> consumer = startActor(() -> {
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
      advanceTime(0.1);
    }
    
    // Cancel the consumer (which should close the stream)
    consumer.cancel(true);
    pumpAndAdvanceTimeUntilDone();
    
    // Should have received some values
    assertTrue(received.size() >= 4, "Should have received at least 4 values");
    assertEquals(1, received.get(0));
    assertEquals(2, received.get(1));
  }

  @Test
  void testCreateNestedOperation() throws ExecutionException {
    // Test normal completion
    CompletableFuture<String> operation = CancellationTestUtils.createNestedOperation(3, 0.1);
    
    pumpAndAdvanceTimeUntilDone(operation);
    
    assertEquals("level-3->level-2->level-1->leaf", operation.getNow(null));
  }

  @Test
  void testCreateNestedOperationCancellation() {
    // Test cancellation propagation
    CompletableFuture<String> operation = CancellationTestUtils.createNestedOperation(5, 0.1);
    
    // Let it run for a bit
    pump();
    advanceTime(0.25); // Should be in the middle of the hierarchy
    pump();
    
    // Cancel it
    operation.cancel(true);
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    assertTrue(operation.isCancelled());
  }

  @Test
  void testMeasureCancellationLatency() throws ExecutionException {
    CompletableFuture<Double> latencyFuture = CancellationTestUtils.measureCancellationLatency(
        () -> CancellationTestUtils.createLongRunningOperation(1.0, 0.01),
        0.1
    );
    
    pumpAndAdvanceTimeUntilDone(latencyFuture);
    
    Double latency = latencyFuture.getNow(null);
    assertTrue(latency >= 0, "Latency should be non-negative");
    assertTrue(latency < 0.02, "Latency should be less than check interval");
  }

  @Test
  void testMockCancellableService() {
    CancellationTestUtils.MockCancellableService service = 
        new CancellationTestUtils.MockCancellableService();
    
    assertFalse(service.hasStarted());
    assertFalse(service.wasCancelled());
    assertEquals(0, service.getCancellationCheckCount());
    
    // Start operation
    CompletableFuture<String> operation = service.longRunningOperation(0.5, 0.1);
    
    // Let it run a bit
    pump();
    advanceTime(0.05);
    pump();
    
    assertTrue(service.hasStarted());
    assertTrue(service.getCancellationCheckCount() > 0);
    
    // Cancel it
    operation.cancel(true);
    pumpAndAdvanceTimeUntilDone();
    
    assertTrue(service.wasCancelled());
    
    // Test reset
    service.reset();
    assertFalse(service.hasStarted());
    assertFalse(service.wasCancelled());
    assertEquals(0, service.getCancellationCheckCount());
  }

  @Test
  void testVerifyCancellationCleanup() {
    AtomicBoolean resourceCleaned = new AtomicBoolean(false);
    
    CompletableFuture<Void> verifyFuture = CancellationTestUtils.verifyCancellationCleanup(
        () -> startActor(() -> {
          try {
            await(Flow.delay(1.0));
            return "done";
          } catch (FlowCancellationException e) {
            resourceCleaned.set(true);
            throw e;
          }
        }),
        0.1,
        resourceCleaned::get
    );
    
    pumpAndAdvanceTimeUntilDone(verifyFuture);
    
    assertTrue(verifyFuture.isDone());
    assertFalse(verifyFuture.isCompletedExceptionally());
    assertTrue(resourceCleaned.get());
  }

  @Test
  void testConcurrentCancellation() throws ExecutionException {
    AtomicInteger createdCount = new AtomicInteger(0);
    
    CompletableFuture<Integer> testFuture = CancellationTestUtils.testConcurrentCancellation(
        5,
        () -> {
          createdCount.incrementAndGet();
          return CancellationTestUtils.createLongRunningOperation(1.0, 0.1);
        },
        0.2
    );
    
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    assertEquals(5, createdCount.get());
    assertEquals(5, testFuture.getNow(null), "All operations should have been cancelled");
  }
}