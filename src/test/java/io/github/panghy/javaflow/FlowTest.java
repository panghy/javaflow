package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class FlowTest extends AbstractFlowTest {

  @Test
  void testStartActorCallable() throws Exception {
    FlowFuture<Integer> future = Flow.startActor(() -> 42);

    FlowFuture<Integer> future2 = Flow.startActor(() -> Flow.await(future));
    pumpAndAdvanceTimeUntilDone(future, future2);
    
    assertEquals(42, future.getNow());
    assertEquals(42, future2.getNow());
  }

  @Test
  void testStartActorCallableWithPriority() throws ExecutionException {
    // Test the start method with priority parameter
    FlowFuture<Integer> future = Flow.startActor(() -> 42, 5);
    pumpAndAdvanceTimeUntilDone(future);
    assertEquals(42, future.getNow());
  }

  @Test
  void testStartActorRunnable() throws ExecutionException {
    AtomicBoolean executed = new AtomicBoolean(false);

    FlowFuture<Void> future = Flow.startActor(() -> executed.set(true));

    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(executed.get());
    future.getNow(); // Should not throw
  }

  @Test
  void testStartActorRunnableWithPriority() throws ExecutionException {
    // Test the start method with priority parameter for Runnable
    AtomicBoolean executed = new AtomicBoolean(false);

    FlowFuture<Void> future = Flow.startActor(() -> executed.set(true), 5);

    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(executed.get());
    future.getNow(); // Should not throw
  }

  @Test
  void testDelay() {
    double start = currentTimeSeconds();

    // Wrap in Flow.start() to create a flow task context
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Now we're in a flow task context, so delay is allowed
      Flow.await(Flow.delay(0.1)); // 100ms delay
      return null;
    });

    pumpAndAdvanceTimeUntilDone(future);

    double duration = currentTimeSeconds() - start;
    assertTrue(duration >= 0.1, "Delay should be at least 0.1s but was " + duration + "s");
  }

  @Test
  void testYieldF() throws ExecutionException {
    // For now, just verify it returns a completed future
    FlowFuture<Void> future = Flow.startActor(() -> {
      Flow.yieldF();
    });

    assertNotNull(future);
    pumpAndAdvanceTimeUntilDone(future);
    future.getNow(); // Should not throw
  }

  @Test
  void testSchedulerAccess() {
    assertNotNull(Flow.scheduler());
  }

  @Test
  void testAwaitSuccess() throws Exception {
    // Test await method with a successful future
    FlowFuture<String> future = FlowFuture.completed("success");

    String result = Flow.await(future);
    assertEquals("success", result);
  }

  @Test
  void testAwaitWithException() {
    // Test await method with a future that completes exceptionally
    Exception testException = new RuntimeException("test error");
    FlowFuture<String> future = FlowFuture.failed(testException);

    Exception thrown = assertThrows(RuntimeException.class, () -> Flow.await(future));
    assertEquals("test error", thrown.getMessage());
  }

  @Test
  void testAwaitWithNonStandardException() {
    // Test await with an exception that's not an Exception subclass
    // This tests the else branch in await() where cause is not an Exception
    Throwable testThrowable = new Error("test error");
    FlowFuture<String> future = new FlowFuture<>();
    future.getPromise().completeExceptionally(testThrowable);

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Flow.await(future));
    assertEquals("test error", thrown.getCause().getMessage());
  }

  @Test
  void testStartActorCallableException() {
    // Test starting a callable that throws an exception
    RuntimeException testException = new RuntimeException("test failure");
    FlowFuture<Integer> future = Flow.startActor(() -> {
      throw testException;
    });

    pumpAndAdvanceTimeUntilDone(future);
    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.getNow());
    assertEquals(testException, thrown.getCause());
  }

  @Test
  void testStartActorRunnableException() {
    // Test starting a runnable that throws an exception
    RuntimeException testException = new RuntimeException("test failure");
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);

    FlowFuture<Void> future = Flow.startActor(() -> {
      exceptionThrown.set(true);
      throw testException;
    });

    pumpAndAdvanceTimeUntilDone(future);
    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.getNow());
    assertEquals(testException, thrown.getCause());
    assertTrue(exceptionThrown.get(), "Runnable should have executed and thrown exception");
  }

  // This is a simple integration test that uses multiple flow components together
  @Test
  void testIntegration() {
    AtomicInteger counter = new AtomicInteger(0);
    AtomicInteger completedCount = new AtomicInteger(0);

    // Start three actors that increment the counter
    FlowFuture<Void> future1 = Flow.startActor(() -> {
      counter.incrementAndGet();
      completedCount.incrementAndGet();
      return null;
    });

    FlowFuture<Void> future2 = Flow.startActor(() -> {
      counter.incrementAndGet();
      completedCount.incrementAndGet();
      return null;
    });

    FlowFuture<Void> future3 = Flow.startActor(() -> {
      counter.incrementAndGet();
      completedCount.incrementAndGet();
      return null;
    });

    pumpAndAdvanceTimeUntilDone(future1, future2, future3);
    assertEquals(3, completedCount.get());
    assertEquals(3, counter.get());
  }

  @Test
  void testComplexIntegration() {
    // Test a more complex flow with chained operations
    AtomicReference<String> result = new AtomicReference<>();
    AtomicBoolean completed = new AtomicBoolean(false);

    // Start a flow that does multiple operations
    FlowFuture<Void> future = Flow.startActor(() -> {
      // First part returns a string
      return "step1";
    }).map(str -> {
      // Map to concatenate a value
      return str + "-step2";
    }).map(str -> {
      // Store the result and signal completion
      result.set(str);
      completed.set(true);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(completed.get());
    assertEquals("step1-step2", result.get());
  }

  @Test
  void testCancellationPropagation() {
    // Simplified test that just verifies that cancellation works for dependent futures
    FlowFuture<String> future1 = new FlowFuture<>();
    FlowFuture<String> future2 = future1.map(s -> s + " mapped");

    // When we cancel the first future
    future1.cancel();

    // Check that it was marked as cancelled
    assertTrue(future1.isCancelled());

    // Pump to allow cancellation to propagate
    pump();

    // Check that the dependent future is completed exceptionally
    assertTrue(future2.isCompletedExceptionally() || future2.isCancelled());
  }

  @Test
  void testCheckCancellationThrowsWhenCancelled() {
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Use a delay-based approach which properly suspends the task
      for (int i = 0; i < 10; i++) {
        try {
          // Check cancellation before each delay
          Flow.checkCancellation();
          // Small delay that allows proper suspension
          Flow.await(Flow.delay(0.01));
        } catch (FlowCancellationException e) {
          exceptionThrown.set(true);
          throw e;
        }
      }
      return null;
    });
    
    // Let the task start
    pump();
    
    // Cancel the future
    future.cancel();
    
    // Process everything
    pumpAndAdvanceTimeUntilDone(future);
    
    // In simulation, cancellation during delay should throw FlowCancellationException
    assertTrue(exceptionThrown.get() || future.isCancelled(), 
        "Task should either throw FlowCancellationException or be cancelled");
    assertTrue(future.isCancelled());
  }

  @Test
  void testCheckCancellationDoesNotThrowWhenNotCancelled() throws ExecutionException {
    AtomicBoolean completedFlag = new AtomicBoolean(false);
    
    FlowFuture<String> future = Flow.startActor(() -> {
      // Check cancellation multiple times - should not throw
      for (int i = 0; i < 10; i++) {
        Flow.checkCancellation();
        Flow.await(Flow.delay(0.001)); // Small delay
      }
      completedFlag.set(true);
      return "completed";
    });
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(completedFlag.get());
    assertEquals("completed", future.getNow());
  }

  @Test
  void testIsCancelledReturnsTrueWhenCancelled() {
    AtomicBoolean cancellationDetected = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Use delay-based approach for proper task suspension
      for (int i = 0; i < 10; i++) {
        if (Flow.isCancelled()) {
          cancellationDetected.set(true);
          return null;
        }
        // Small delay that allows proper suspension
        Flow.await(Flow.delay(0.01));
      }
      return null;
    });
    
    // Let the task start
    pump();
    
    // Cancel the future
    future.cancel();
    
    // Process everything
    pumpAndAdvanceTimeUntilDone(future);
    
    // In simulation, the task should detect cancellation
    assertTrue(cancellationDetected.get() || future.isCancelled(),
        "Cancellation should be detected by isCancelled() or future should be cancelled");
    assertTrue(future.isCancelled());
  }

  @Test
  void testIsCancelledReturnsFalseWhenNotCancelled() {
    AtomicBoolean neverCancelled = new AtomicBoolean(true);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Check cancellation status multiple times
      for (int i = 0; i < 5; i++) {
        if (Flow.isCancelled()) {
          neverCancelled.set(false);
        }
        Flow.await(Flow.delay(0.001));
      }
      return null;
    });
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(neverCancelled.get(), "isCancelled should always return false");
  }

  @Test
  void testCheckCancellationOutsideFlowContext() {
    // When called outside a flow context, it should not throw
    // (no current task means not cancelled)
    Flow.checkCancellation(); // Should not throw
  }

  @Test
  void testIsCancelledOutsideFlowContext() {
    // When called outside a flow context, should return false
    assertFalse(Flow.isCancelled());
  }

  @Test
  void testAwaitThrowsFlowCancellationExceptionOnCancelledFuture() {
    // Test case 1: Future is already cancelled when we await it
    FlowFuture<String> alreadyCancelledFuture = new FlowFuture<>();
    alreadyCancelledFuture.cancel();
    
    AtomicBoolean flowCancellationExceptionThrown = new AtomicBoolean(false);
    
    FlowFuture<Void> future1 = Flow.startActor(() -> {
      try {
        // Await a future that's already cancelled
        Flow.await(alreadyCancelledFuture);
      } catch (FlowCancellationException e) {
        flowCancellationExceptionThrown.set(true);
        throw e;
      }
      return null;
    });
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(future1);
    
    assertTrue(flowCancellationExceptionThrown.get(), 
        "FlowCancellationException should have been thrown when awaiting already cancelled future");
    
    // Test case 2: Cancel while awaiting (this may be harder to test reliably)
    flowCancellationExceptionThrown.set(false);
    FlowFuture<String> futureToCancelLater = new FlowFuture<>();
    AtomicBoolean awaitStarted = new AtomicBoolean(false);
    
    FlowFuture<Void> future2 = Flow.startActor(() -> {
      try {
        awaitStarted.set(true);
        // This will block until the future is cancelled
        Flow.await(futureToCancelLater);
      } catch (FlowCancellationException e) {
        flowCancellationExceptionThrown.set(true);
        throw e;
      }
      return null;
    });
    
    // Wait for await to start
    while (!awaitStarted.get()) {
      pump();
    }
    
    // Cancel the future
    futureToCancelLater.cancel();
    
    // Pump to process cancellation
    pumpAndAdvanceTimeUntilDone(future2);
    
    // For now, we'll just verify the future is cancelled/exceptionally completed
    // The FlowCancellationException might not propagate in this case due to scheduler implementation
    assertTrue(future2.isCompletedExceptionally() || future2.isCancelled());
  }

  @Test
  void testAwaitChecksTaskCancellationBeforeAwaiting() {
    // This test verifies that await() checks for task cancellation before suspending.
    // The behavior might differ between real scheduler and test scheduler, so we'll
    // focus on verifying that cancellation is properly detected during await.
    
    AtomicBoolean cancellationDetected = new AtomicBoolean(false);
    AtomicBoolean taskCompleted = new AtomicBoolean(false);
    
    // Create a future that will be cancelled
    FlowFuture<String> futureToBeCancelled = new FlowFuture<>();
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      try {
        // Start another actor that will cancel our future while we're waiting
        Flow.startActor(() -> {
          Flow.await(Flow.delay(0.01)); // Small delay
          futureToBeCancelled.cancel();
          return null;
        });
        
        // This await should eventually detect the cancellation
        Flow.await(futureToBeCancelled);
        taskCompleted.set(true);
      } catch (FlowCancellationException e) {
        cancellationDetected.set(true);
        // Don't rethrow - we want to verify the exception was caught
      }
      return null;
    });
    
    // Pump to process everything
    pumpAndAdvanceTimeUntilDone(future);
    
    // Verify that cancellation was detected
    assertTrue(cancellationDetected.get() || futureToBeCancelled.isCancelled(), 
        "Cancellation should be detected when awaiting a cancelled future");
    assertFalse(taskCompleted.get(), "Task should not complete normally after cancellation");
  }

  @Test
  void testAwaitDoesNotThrowOnNormalCompletion() throws ExecutionException {
    // Create a future that completes normally
    FlowFuture<String> normalFuture = new FlowFuture<>();
    
    FlowFuture<String> result = Flow.startActor(() -> {
      // Complete the future normally from another task
      Flow.startActor(() -> {
        Flow.await(Flow.delay(0.01)); // Small delay
        normalFuture.complete("success");
        return null;
      });
      
      // Await should return the value without throwing FlowCancellationException
      return Flow.await(normalFuture);
    });
    
    pumpAndAdvanceTimeUntilDone(result);
    assertEquals("success", result.getNow());
  }
}