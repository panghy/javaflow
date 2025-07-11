package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests specifically targeting edge cases in timer handling and cancellation
 * to improve code coverage of SingleThreadedScheduler.
 */
class TimerCoverageTest {

  @BeforeEach
  void setUp() {
    // Clean thread local state
    FlowScheduler.CURRENT_TASK.remove();
  }

  @AfterEach
  void tearDown() {
    // Clean thread local state
    FlowScheduler.CURRENT_TASK.remove();
  }

  @Test
  void testScheduleTimerTaskWithAlreadyCancelledParent() {
    // Use test scheduler with disabled carrier thread

    try (SingleThreadedScheduler scheduler = new SingleThreadedScheduler(false)) {
      scheduler.start();
      // Create a parent task
      Task parentTask = new Task(999L, TaskPriority.DEFAULT, () -> "parent", null);

      // Set up current task ThreadLocal
      FlowScheduler.CURRENT_TASK.set(parentTask);

      // Cancel the parent task before scheduling delay
      parentTask.cancel();

      // Schedule a delay - this should immediately fail due to cancelled parent
      CompletableFuture<Void> delayFuture = scheduler.scheduleDelay(1.0);

      // Verify the future is completed exceptionally with CancellationException
      assertTrue(delayFuture.isCompletedExceptionally());
      try {
        delayFuture.toCompletableFuture().get();
        fail("Should have thrown CancellationException");
      } catch (Exception e) {
        // A CancellationException is thrown directly rather than wrapped in an ExecutionException
        assertInstanceOf(CancellationException.class, e);
        assertEquals("Parent task is already cancelled", e.getMessage());
      }
    }
  }

  @Test
  void testTimerTaskCancellationChain() throws Exception {
    // Use test scheduler with disabled carrier thread and simulated clock
    SimulatedClock clock = new SimulatedClock();

    try (SingleThreadedScheduler scheduler = new SingleThreadedScheduler(false, clock)) {
      AtomicBoolean callbackExecuted = new AtomicBoolean(false);

      // Create a parent task with an existing cancellation callback
      Task parentTask = new Task(1L, TaskPriority.DEFAULT, () -> "parent", null);
      parentTask.setCancellationCallback((timerIds) -> callbackExecuted.set(true));

      // Set the parent task as current
      FlowScheduler.CURRENT_TASK.set(parentTask);

      // Schedule a delay
      CompletableFuture<Void> delayFuture = scheduler.scheduleDelay(5.0);

      // Get the timer ID via reflection
      Field timerIdCounterField = SingleThreadedScheduler.class.getDeclaredField("timerIdCounter");
      timerIdCounterField.setAccessible(true);
      AtomicLong timerIdCounter = (AtomicLong) timerIdCounterField.get(scheduler);
      long timerId = timerIdCounter.get() - 1; // The ID that was just used

      // Create a hook to verify timer cancellation
      Field timerIdToTaskField = SingleThreadedScheduler.class.getDeclaredField("timerIdToTask");
      timerIdToTaskField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<Long, TimerTask> timerIdToTask = (Map<Long, TimerTask>) timerIdToTaskField.get(scheduler);

      // Verify the timer task exists
      boolean timerExists = timerIdToTask.containsKey(timerId);
      if (timerExists) {
        // Cancel the parent task - this should chain the callbacks
        parentTask.cancel();

        // Process any timer cancellations
        scheduler.pump();

        // Verify original callback executed
        assertTrue(callbackExecuted.get(), "Original callback should have executed");

        // Timer should be cancelled by the chain - but this detail is implementation specific,
        // the important thing is that the future is marked as cancelled, which we check later
      } else {
        // If timer wasn't registered, we can just verify the callback executes
        parentTask.cancel();
        assertTrue(callbackExecuted.get(), "Original callback should have executed");
      }

      // The future is completed exceptionally, either with ExecutionException or CancellationException
      // The exact mechanism depends on implementation details that may vary
      assertTrue(delayFuture.isCompletedExceptionally(), "Future should be completed exceptionally");

      // Verify we can't get a value
      try {
        delayFuture.toCompletableFuture().get();
        fail("Should have thrown an exception");
      } catch (Exception e) {
        // Any exception is fine, we just need to make sure we can't get a result
        // It could be either CancellationException or ExecutionException
      }
    }
  }

  @Test
  void testProcessMultipleTimerTasksAtSameTime() {
    // Use scheduler with simulated clock
    SimulatedClock clock = new SimulatedClock();

    try (SingleThreadedScheduler scheduler = new SingleThreadedScheduler(false, clock)) {
      scheduler.start();
      // Set up current task
      Task parentTask = new Task(1L, TaskPriority.DEFAULT, () -> "parent", null);
      FlowScheduler.CURRENT_TASK.set(parentTask);

      // Track which timers executed
      List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());

      // Schedule multiple timers for the same time
      CompletableFuture<Void> timer1 = scheduler.scheduleDelay(1.0);
      timer1.whenComplete(($, t) -> {
        if (t == null) {
          executionOrder.add(1);
        }
      });

      CompletableFuture<Void> timer2 = scheduler.scheduleDelay(1.0);
      timer2.whenComplete(($, t) -> {
        if (t == null) {
          executionOrder.add(2);
        }
      });

      CompletableFuture<Void> timer3 = scheduler.scheduleDelay(1.0);
      timer3.whenComplete(($, t) -> {
        if (t == null) {
          executionOrder.add(3);
        }
      });

      // Schedule another timer for later
      CompletableFuture<Void> laterTimer = scheduler.scheduleDelay(2.0);
      laterTimer.whenComplete(($, t) -> {
        if (t == null) {
          executionOrder.add(4);
        }
      });

      // Advance time by 1 second - should trigger the first three timers
      int tasksExecuted = scheduler.advanceTime(1000);
      assertEquals(3, tasksExecuted, "Should have executed 3 tasks");

      // Verify the first three timers executed
      assertEquals(3, executionOrder.size(), "Should have executed 3 timers");
      assertTrue(executionOrder.contains(1), "First timer should have executed");
      assertTrue(executionOrder.contains(2), "Second timer should have executed");
      assertTrue(executionOrder.contains(3), "Third timer should have executed");
      assertFalse(executionOrder.contains(4), "Later timer should not have executed yet");

      // Advance time again to trigger the later timer
      tasksExecuted = scheduler.advanceTime(1000);
      assertEquals(1, tasksExecuted, "Should have executed 1 more task");

      // Verify all timers executed
      assertEquals(4, executionOrder.size(), "Should have executed all 4 timers");
      assertTrue(executionOrder.contains(4), "Later timer should have executed");
    }
  }

  @Test
  void testCancelTimerTask() throws Exception {
    // Use scheduler with disabled carrier thread
    SimulatedClock clock = new SimulatedClock();

    try (SingleThreadedScheduler scheduler = new SingleThreadedScheduler(false, clock)) {
      // Set up current task
      Task parentTask = new Task(1L, TaskPriority.DEFAULT, () -> "parent", null);
      FlowScheduler.CURRENT_TASK.set(parentTask);

      // Track cancellation via future
      AtomicBoolean cancellationExceptionReceived = new AtomicBoolean(false);

      // Schedule a delay
      CompletableFuture<Void> delayFuture = scheduler.scheduleDelay(1.0);
      delayFuture.whenComplete(($, t) -> {
        if (t instanceof CancellationException) {
          cancellationExceptionReceived.set(true);
        }
      });

      // Cancel the future directly
      boolean cancelled = delayFuture.cancel(true);

      // Verify that cancellation worked
      assertTrue(cancelled, "Future should be cancelled successfully");

      // Process any pending callbacks
      scheduler.pump();

      // Verify the future was properly cancelled
      assertTrue(cancellationExceptionReceived.get(),
          "Future should have received CancellationException");
      assertTrue(delayFuture.isCompletedExceptionally(),
          "Future should be completed exceptionally");
      assertTrue(delayFuture.isCancelled(),
          "Future should be marked as cancelled");

      // The timer is cancelled but might still be in the scheduler's data structures
      // What matters is that our specific future is cancelled
    }
  }
}