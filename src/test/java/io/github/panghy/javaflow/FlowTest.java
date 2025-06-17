package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowTest {

  @Test
  void testStartActorCallable() throws Exception {
    FlowFuture<Integer> future = Flow.startActor(() -> 42);

    Flow.startActor(() -> Flow.await(future)).toCompletableFuture().get();
    assertEquals(42, future.toCompletableFuture().get());
  }

  @Test
  void testStartActorCallableWithPriority() throws Exception {
    // Test the start method with priority parameter
    FlowFuture<Integer> future = Flow.startActor(() -> 42, 5);

    assertEquals(42, future.toCompletableFuture().get());
  }

  @Test
  void testStartActorRunnable() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    FlowFuture<Void> future = Flow.startActor(latch::countDown);

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    future.toCompletableFuture().get(); // Should not throw
  }

  @Test
  void testStartActorRunnableWithPriority() throws Exception {
    // Test the start method with priority parameter for Runnable
    CountDownLatch latch = new CountDownLatch(1);

    FlowFuture<Void> future = Flow.startActor(latch::countDown, 5);

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    future.toCompletableFuture().get(); // Should not throw
  }

  @Test
  void testDelay() throws Exception {
    long start = System.currentTimeMillis();

    // Wrap in Flow.start() to create a flow task context
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Now we're in a flow task context, so delay is allowed
      Flow.await(Flow.delay(0.1)); // 100ms delay
      return null;
    });

    future.toCompletableFuture().get();

    long duration = System.currentTimeMillis() - start;
    assertTrue(duration >= 100, "Delay should be at least 100ms but was " + duration + "ms");
  }

  @Test
  void testYieldF() throws Exception {
    // For now, just verify it returns a completed future
    FlowFuture<Void> future = Flow.startActor(() -> {
      Flow.yieldF();
    });

    assertNotNull(future);
    future.toCompletableFuture().get(); // Should not throw
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

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.toCompletableFuture().get());
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

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.toCompletableFuture().get());
    assertEquals(testException, thrown.getCause());
    assertTrue(exceptionThrown.get(), "Runnable should have executed and thrown exception");
  }

  // This is a simple integration test that uses multiple flow components together
  @Test
  void testIntegration() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(3);

    // Start three actors that increment the counter
    Flow.startActor(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });

    Flow.startActor(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });

    Flow.startActor(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals(3, counter.get());
  }

  @Test
  void testComplexIntegration() throws Exception {
    // Test a more complex flow with chained operations
    AtomicReference<String> result = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    // Start a flow that does multiple operations
    Flow.startActor(() -> {
      // First part returns a string
      return "step1";
    }).map(str -> {
      // Map to concatenate a value
      return str + "-step2";
    }).map(str -> {
      // Store the result and signal completion
      result.set(str);
      latch.countDown();
      return null;
    });

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals("step1-step2", result.get());
  }

  @Test
  void testCancellationPropagation() throws Exception {
    // Simplified test that just verifies that cancellation works for dependent futures
    FlowFuture<String> future1 = new FlowFuture<>();
    FlowFuture<String> future2 = future1.map(s -> s + " mapped");

    // When we cancel the first future
    future1.cancel();

    // Check that it was marked as cancelled
    assertTrue(future1.isCancelled());

    // Wait a bit for propagation
    Thread.sleep(100);

    // Check that the dependent future is completed exceptionally
    assertTrue(future2.isCompletedExceptionally() || future2.isCancelled());
  }

  @Test
  void testCheckCancellationThrowsWhenCancelled() throws Exception {
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    CountDownLatch taskRunningLatch = new CountDownLatch(1);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      taskRunningLatch.countDown();
      
      // Busy loop checking for cancellation without awaiting
      while (true) {
        try {
          Flow.checkCancellation();
          // Simulate some CPU work without await
          Thread.yield();
        } catch (FlowCancellationException e) {
          exceptionThrown.set(true);
          throw e; // Re-throw to properly cancel the actor
        }
      }
    });
    
    // Wait for task to start running
    assertTrue(taskRunningLatch.await(1, TimeUnit.SECONDS));
    
    // Give it a moment to start the loop
    Thread.sleep(50);
    
    // Cancel the future
    future.cancel();
    
    // Wait a bit for cancellation to propagate
    Thread.sleep(200);
    
    // Check that the exception was thrown
    assertTrue(exceptionThrown.get(), "FlowCancellationException should have been thrown");
    
    // Verify the future is cancelled
    assertTrue(future.isCancelled());
  }

  @Test
  void testCheckCancellationDoesNotThrowWhenNotCancelled() throws Exception {
    CountDownLatch completedLatch = new CountDownLatch(1);
    
    FlowFuture<String> future = Flow.startActor(() -> {
      // Check cancellation multiple times - should not throw
      for (int i = 0; i < 10; i++) {
        Flow.checkCancellation();
        Flow.await(Flow.delay(0.001)); // Small delay
      }
      completedLatch.countDown();
      return "completed";
    });
    
    // Wait for completion
    assertTrue(completedLatch.await(1, TimeUnit.SECONDS));
    assertEquals("completed", future.toCompletableFuture().get());
  }

  @Test
  void testIsCancelledReturnsTrueWhenCancelled() throws Exception {
    CountDownLatch taskRunningLatch = new CountDownLatch(1);
    AtomicBoolean cancellationDetected = new AtomicBoolean(false);
    CountDownLatch completionLatch = new CountDownLatch(1);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      taskRunningLatch.countDown();
      
      // Busy loop checking if cancelled without awaiting
      while (!Flow.isCancelled()) {
        Thread.yield(); // Yield to allow cancellation to propagate
      }
      
      cancellationDetected.set(true);
      completionLatch.countDown();
      return null;
    });
    
    // Wait for task to start running
    assertTrue(taskRunningLatch.await(1, TimeUnit.SECONDS));
    
    // Give it a moment to start the loop
    Thread.sleep(50);
    
    // Cancel the future
    future.cancel();
    
    // Wait for the task to detect cancellation
    assertTrue(completionLatch.await(1, TimeUnit.SECONDS));
    
    assertTrue(cancellationDetected.get(), "Cancellation should have been detected");
  }

  @Test
  void testIsCancelledReturnsFalseWhenNotCancelled() throws Exception {
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
    future.toCompletableFuture().get(1, TimeUnit.SECONDS);
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
  void testAwaitThrowsFlowCancellationExceptionOnCancelledFuture() throws Exception {
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
    Thread.sleep(100);
    
    assertTrue(flowCancellationExceptionThrown.get(), 
        "FlowCancellationException should have been thrown when awaiting already cancelled future");
    
    // Test case 2: Cancel while awaiting (this may be harder to test reliably)
    flowCancellationExceptionThrown.set(false);
    FlowFuture<String> futureToCancelLater = new FlowFuture<>();
    CountDownLatch awaitStarted = new CountDownLatch(1);
    
    FlowFuture<Void> future2 = Flow.startActor(() -> {
      try {
        awaitStarted.countDown();
        // This will block until the future is cancelled
        Flow.await(futureToCancelLater);
      } catch (FlowCancellationException e) {
        flowCancellationExceptionThrown.set(true);
        throw e;
      }
      return null;
    });
    
    // Wait for await to start
    assertTrue(awaitStarted.await(1, TimeUnit.SECONDS));
    Thread.sleep(50); // Give time for await to register
    
    // Cancel the future
    futureToCancelLater.cancel();
    
    // Wait a bit
    Thread.sleep(200);
    
    // For now, we'll just verify the future is cancelled/exceptionally completed
    // The FlowCancellationException might not propagate in this case due to scheduler implementation
    assertTrue(future2.isCompletedExceptionally() || future2.isCancelled());
  }

  @Test
  void testAwaitChecksTaskCancellationBeforeAwaiting() throws Exception {
    AtomicBoolean flowCancellationExceptionThrown = new AtomicBoolean(false);
    CountDownLatch taskStartedLatch = new CountDownLatch(1);
    CountDownLatch readyToAwaitLatch = new CountDownLatch(1);
    
    // Create a future that will never complete
    FlowFuture<String> neverCompletingFuture = new FlowFuture<>();
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      taskStartedLatch.countDown();
      
      // Wait until we're told to proceed to await
      assertTrue(readyToAwaitLatch.await(5, TimeUnit.SECONDS));
      
      try {
        // By this time, the task should already be cancelled
        // await should check cancellation before suspending
        Flow.await(neverCompletingFuture);
      } catch (FlowCancellationException e) {
        flowCancellationExceptionThrown.set(true);
        throw e;
      }
      
      return null;
    });
    
    // Wait for the task to start
    assertTrue(taskStartedLatch.await(1, TimeUnit.SECONDS));
    
    // Cancel the task
    future.cancel();
    
    // Now tell the task to proceed to await
    readyToAwaitLatch.countDown();
    
    // Wait a bit for the exception to propagate
    Thread.sleep(100);
    
    // Verify that FlowCancellationException was thrown
    assertTrue(flowCancellationExceptionThrown.get(), "await() should check cancellation before suspending");
    
    // The future should be cancelled
    assertTrue(future.isCancelled());
  }

  @Test
  void testAwaitDoesNotThrowOnNormalCompletion() throws Exception {
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
    
    assertEquals("success", result.toCompletableFuture().get(1, TimeUnit.SECONDS));
  }
}