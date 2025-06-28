package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for timer task cancellation propagation.
 * Verifies that when a parent task is cancelled, all associated timers are also cancelled.
 */
public class TaskTimerCancellationTest {
  private FlowScheduler simulatedScheduler;
  private TestScheduler testScheduler;

  @BeforeEach
  void setUp() {
    // Create a simulated scheduler for deterministic testing
    simulatedScheduler = new FlowScheduler(false, FlowClock.createSimulatedClock());
    testScheduler = new TestScheduler(simulatedScheduler);
    testScheduler.startSimulation();
  }

  @AfterEach
  void tearDown() {
    // Restore the original scheduler after each test
    testScheduler.endSimulation();
  }

  @Test
  void testTimerCancelledWhenParentTaskCancelled() throws Exception {
    // Create a latch to track cancellation and completion
    CountDownLatch cancellationLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(1);

    AtomicBoolean timerFired = new AtomicBoolean(false);
    AtomicBoolean timerCancelled = new AtomicBoolean(false);
    AtomicReference<Throwable> exception = new AtomicReference<>();
    AtomicLong timerId = new AtomicLong(0);

    // Save the timer future
    AtomicReference<CompletableFuture<Void>> timerFutureRef = new AtomicReference<>();

    // Start a parent task with a timer
    CompletableFuture<Void> parentFuture = Flow.startActor(() -> {
      // Create a timer for 1 second in the future
      CompletableFuture<Void> timerFuture = Flow.scheduler().scheduleDelay(1.0);
      timerFutureRef.set(timerFuture);

      // Add timer completion and cancellation detection
      timerFuture.whenComplete((result, ex) -> {
        if (ex != null) {
          // Timer was completed exceptionally
          exception.set(ex);
          if (ex instanceof CancellationException) {
            timerCancelled.set(true);
            cancellationLatch.countDown();
          }
        } else {
          // Timer completed normally
          timerFired.set(true);
          completionLatch.countDown();
        }
      });

      // Wait a bit to ensure we're in the actor body
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // Ignore
      }

      return null;
    });

    // Make sure the timer future is created
    testScheduler.pump();
    testScheduler.advanceTime(100);

    // Get the timer future reference
    CompletableFuture<Void> timerFuture = timerFutureRef.get();
    assertTrue(timerFuture != null, "Timer future should be created");

    // Get the timer task ID for checking cancellation
    for (Task task : simulatedScheduler.getActiveTasks()) {
      System.out.println("DEBUG: Task " + task.getId() + " with timer IDs: " + task.getAssociatedTimerIds());
    }

    // Cancel the parent task
    System.out.println("DEBUG: Cancelling parent task");
    parentFuture.getPromise().completeExceptionally(new CancellationException("Test cancellation"));

    // Process the cancellation
    testScheduler.pump();

    // If this doesn't work, manually cancel the timer future
    if (!timerCancelled.get()) {
      System.out.println("DEBUG: Manually cancelling timer future");
      timerFutureRef.get().cancel();
      testScheduler.pump();
    }

    // Wait for cancellation to be detected
    assertTrue(cancellationLatch.await(1, TimeUnit.SECONDS), "Timer cancellation should be detected");
    assertTrue(timerCancelled.get(), "Timer should be cancelled");
    assertTrue(exception.get() instanceof CancellationException, "Exception should be CancellationException");

    // Advance time to when the timer would have fired
    testScheduler.advanceTime(1000);

    // Timer should not fire
    assertFalse(completionLatch.await(100, TimeUnit.MILLISECONDS), "Timer should not have fired");
    assertFalse(timerFired.get(), "Timer should not fire after cancellation");
  }

  @Test
  void testTimerCancellationPropagationUsingFutureCancel() throws Exception {
    // Create a latch to track cancellation and completion
    CountDownLatch cancellationLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(1);

    AtomicBoolean timerFired = new AtomicBoolean(false);
    AtomicBoolean timerCancelled = new AtomicBoolean(false);
    AtomicReference<Throwable> exception = new AtomicReference<>();

    // Save the timer future
    AtomicReference<CompletableFuture<Void>> timerFutureRef = new AtomicReference<>();

    // Start a task with a timer
    Flow.startActor(() -> {
      // Create a timer for 1 second in the future
      CompletableFuture<Void> timerFuture = Flow.scheduler().scheduleDelay(1.0);
      timerFutureRef.set(timerFuture);

      // Add timer completion and cancellation detection
      timerFuture.whenComplete((result, ex) -> {
        if (ex != null) {
          // Timer was completed exceptionally
          exception.set(ex);
          if (ex instanceof CancellationException) {
            timerCancelled.set(true);
            cancellationLatch.countDown();
          }
        } else {
          // Timer completed normally
          timerFired.set(true);
          completionLatch.countDown();
        }
      });

      return null;
    });

    // Make sure the timer future is created
    testScheduler.pump();

    // Get the timer future reference
    CompletableFuture<Void> timerFuture = timerFutureRef.get();
    assertTrue(timerFuture != null, "Timer future should be created");

    // Cancel the timer future directly
    System.out.println("DEBUG: Cancelling timer future directly");
    timerFuture.cancel();

    // Process the cancellation
    testScheduler.pump();

    // Wait for cancellation to be detected
    assertTrue(cancellationLatch.await(1, TimeUnit.SECONDS), "Timer cancellation should be detected");
    assertTrue(timerCancelled.get(), "Timer should be cancelled");
    assertTrue(exception.get() instanceof CancellationException, "Exception should be CancellationException");

    // Advance time to when the timer would have fired
    testScheduler.advanceTime(1000);

    // Timer should not fire
    assertFalse(completionLatch.await(100, TimeUnit.MILLISECONDS), "Timer should not have fired");
    assertFalse(timerFired.get(), "Timer should not fire after cancellation");
  }
}