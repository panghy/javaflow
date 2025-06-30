package io.github.panghy.javaflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional tests for Flow class to improve code coverage.
 */
@Timeout(30)
public class FlowAdditionalCoverageTest extends AbstractFlowTest {

  @Test
  void testDelayWithPriority() {
    double start = currentTimeSeconds();
    AtomicBoolean completed = new AtomicBoolean(false);

    // Test delay with custom priority
    CompletableFuture<Void> future = Flow.startActor(() -> {
      // Use delay with priority parameter
      Flow.await(Flow.delay(0.2, 10)); // 200ms delay with priority 10
      completed.set(true);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(future);

    double duration = currentTimeSeconds() - start;
    assertTrue(duration >= 0.2, "Delay should be at least 0.2s but was " + duration + "s");
    assertTrue(completed.get(), "Task should have completed");
  }

  @Test
  void testDelayWithNegativePriority() {
    // Test that negative priority throws IllegalArgumentException
    CompletableFuture<Void> future = Flow.startActor(() -> {
      assertThrows(IllegalArgumentException.class, () -> {
        Flow.delay(0.1, -1); // Negative priority should throw
      });
      return null;
    });

    pumpAndAdvanceTimeUntilDone(future);
    future.getNow(null); // Should complete successfully
  }

  @Test
  void testYieldWithPriority() {
    AtomicBoolean firstPartExecuted = new AtomicBoolean(false);
    AtomicBoolean secondPartExecuted = new AtomicBoolean(false);

    // Test yield with custom priority
    CompletableFuture<String> future = Flow.startActor(() -> {
      firstPartExecuted.set(true);
      
      // Yield with priority 5
      Flow.await(Flow.yield(5));
      
      secondPartExecuted.set(true);
      return "completed";
    });

    // After first pump, first part should be executed
    pump();
    assertTrue(firstPartExecuted.get(), "First part should have executed");
    assertFalse(secondPartExecuted.get(), "Second part should not have executed yet");

    // Complete the execution
    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(secondPartExecuted.get(), "Second part should have executed");
    assertEquals("completed", future.getNow(null));
  }

  @Test
  void testYieldWithNegativePriority() {
    // Test that negative priority throws IllegalArgumentException
    CompletableFuture<Void> future = Flow.startActor(() -> {
      assertThrows(IllegalArgumentException.class, () -> {
        Flow.yield(-1); // Negative priority should throw
      });
      return null;
    });

    pumpAndAdvanceTimeUntilDone(future);
    future.getNow(null); // Should complete successfully
  }

  @Test
  void testIsInFlowContext() {
    // Outside flow context should return false
    assertFalse(Flow.isInFlowContext(), "Should be false outside flow context");

    AtomicBoolean insideContextResult = new AtomicBoolean(false);
    
    // Inside flow context should return true
    CompletableFuture<Void> future = Flow.startActor(() -> {
      insideContextResult.set(Flow.isInFlowContext());
      return null;
    });

    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(insideContextResult.get(), "Should be true inside flow context");
  }

  @Test
  void testAwaitWithExecutionException() throws Exception {
    // Test the ExecutionException handling path in await()
    Exception innerException = new RuntimeException("Inner exception");
    CompletableFuture<String> failedFuture = new CompletableFuture<>();
    
    // Create an ExecutionException by completing exceptionally
    CompletableFuture<Void> wrappedFuture = Flow.startActor(() -> {
      failedFuture.completeExceptionally(innerException);
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(wrappedFuture);
    
    // Now test await with a future that will throw ExecutionException
    CompletableFuture<String> testFuture = Flow.startActor(() -> {
      try {
        // This should trigger the ExecutionException path
        return Flow.await(failedFuture);
      } catch (Exception e) {
        // Re-throw to test the exception propagation
        throw new RuntimeException("Wrapped", e);
      }
    });
    
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    assertTrue(testFuture.isCompletedExceptionally());
    Exception thrown = assertThrows(Exception.class, () -> testFuture.get());
    assertNotNull(thrown.getCause());
  }

  @Test
  void testIsSimulatedBranches() {
    // This test aims to cover the branches in isSimulated() method
    
    // In test context with TestScheduler, should return true
    assertTrue(Flow.isSimulated(), "Should be simulated in test context");
    
    // Test within a flow task
    AtomicBoolean simulatedInTask = new AtomicBoolean(false);
    CompletableFuture<Void> future = Flow.startActor(() -> {
      simulatedInTask.set(Flow.isSimulated());
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(simulatedInTask.get(), "Should be simulated within task");
  }

  @Test
  void testTimeMethods() {
    // Test Flow.now() and Flow.nowSeconds()
    long nowMillis = Flow.now();
    double nowSeconds = Flow.nowSeconds();
    
    assertTrue(nowMillis >= 0, "now() should return non-negative value");
    assertTrue(nowSeconds >= 0, "nowSeconds() should return non-negative value");
    
    // Test within a flow task
    CompletableFuture<Void> future = Flow.startActor(() -> {
      long taskNowMillis = Flow.now();
      double taskNowSeconds = Flow.nowSeconds();
      
      assertTrue(taskNowMillis >= nowMillis, "Time should not go backwards");
      assertTrue(taskNowSeconds >= nowSeconds, "Time should not go backwards");
      
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
  }

  @Test
  void testGetClock() {
    // Test Flow.getClock()
    assertNotNull(Flow.getClock(), "getClock() should not return null");
    
    // Test within a flow task
    CompletableFuture<Void> future = Flow.startActor(() -> {
      assertNotNull(Flow.getClock(), "getClock() should not return null in task");
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
  }
}