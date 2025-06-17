package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Flow cancellation functionality.
 * Focuses on the core cancellation API added in Phase 6.
 */
@Timeout(30)
class FlowCancellationTest extends AbstractFlowTest {

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

  @Test
  void testGracefulCancellationHandling() {
    AtomicInteger workCompleted = new AtomicInteger(0);
    AtomicBoolean cleanupDone = new AtomicBoolean(false);
    
    FlowFuture<String> future = Flow.startActor(() -> {
      try {
        for (int i = 0; i < 10; i++) {
          if (Flow.isCancelled()) {
            // Graceful exit
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
    
    // Let it progress a bit
    pump();
    advanceTime(0.25); // Allow 2 iterations
    pump();
    
    assertTrue(workCompleted.get() >= 2, "Should have completed some work");
    
    // Cancel
    future.cancel();
    
    // Continue execution
    pumpAndAdvanceTimeUntilDone();
    
    // Verify graceful handling
    assertTrue(cleanupDone.get(), "Cleanup should have been done");
    assertTrue(workCompleted.get() < 10, "Should not have completed all work");
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
  void testCancellationWithNestedActors() {
    AtomicBoolean innerStarted = new AtomicBoolean(false);
    AtomicBoolean innerCancelled = new AtomicBoolean(false);
    
    FlowFuture<Void> outerFuture = Flow.startActor(() -> {
      FlowFuture<Void> innerFuture = Flow.startActor(() -> {
        try {
          innerStarted.set(true);

          while (!Flow.isCancelled()) {
            Flow.await(Flow.delay(0.1));
          }

        } finally {
          innerCancelled.set(true);
        }
        return null;
      });
      
      // Parent waits for child
      Flow.await(innerFuture);
      return null;
    });
    
    // Let both start
    pump();
    pump();
    assertTrue(innerStarted.get());
    
    // Cancel the outer future
    outerFuture.cancel();
    
    // Process cancellation
    pumpAndAdvanceTimeUntilDone();
    
    // Both should be cancelled
    assertTrue(outerFuture.isCancelled());
    assertTrue(innerCancelled.get(), "Inner actor should detect cancellation");
  }

  @Test
  void testCancellationWithChainedFutures() {
    AtomicBoolean secondStarted = new AtomicBoolean(false);
    
    FlowFuture<String> firstFuture = Flow.startActor(() -> {
      Flow.await(Flow.delay(0.1));
      return "first";
    });
    
    FlowFuture<String> secondFuture = Flow.startActor(() -> {
      secondStarted.set(true);
      String result = Flow.await(firstFuture);
      return result + " second";
    });
    
    // Let both start
    pump();
    assertTrue(secondStarted.get(), "Second actor should start");
    
    // Cancel the first future
    firstFuture.cancel();
    
    // Process cancellation
    advanceTime(0.2);
    pump();
    pump();
    
    // First should be cancelled, second should fail due to awaiting cancelled future
    assertTrue(firstFuture.isCancelled());
    assertTrue(secondFuture.isCompletedExceptionally());
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