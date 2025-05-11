package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TimerTask implementation.
 */
class TimerTaskTest {

  private FlowPromise<Void> createPromise() {
    FlowFuture<Void> future = new FlowFuture<>();
    return future.getPromise();
  }

  @Test
  void testConstructorAndGetters() {
    // Create dependencies
    Runnable task = () -> { };
    FlowPromise<Void> promise = createPromise();
    Task parentTask = new Task(1, TaskPriority.DEFAULT, (Callable<Void>) () -> null, null);
    
    // Create the timer task
    TimerTask timerTask = new TimerTask(42, 1000, task, TaskPriority.HIGH, promise, parentTask);
    
    // Verify all getters return the correct values
    assertEquals(42, timerTask.getId());
    assertEquals(1000, timerTask.getScheduledTimeMillis());
    assertSame(task, timerTask.getTask());
    assertEquals(TaskPriority.HIGH, timerTask.getPriority());
    assertSame(promise, timerTask.getPromise());
    assertSame(parentTask, timerTask.getParentTask());
  }

  @Test
  void testConstructorNullChecks() {
    // Task cannot be null
    FlowPromise<Void> promise = createPromise();
    assertThrows(NullPointerException.class, () -> 
        new TimerTask(1, 1000, null, TaskPriority.DEFAULT, promise, null));
    
    // Promise cannot be null
    Runnable task = () -> { };
    assertThrows(NullPointerException.class, () -> 
        new TimerTask(1, 1000, task, TaskPriority.DEFAULT, null, null));
  }

  @Test
  void testExecute() {
    // Create a task that sets a flag when executed
    AtomicBoolean executed = new AtomicBoolean(false);
    Runnable task = () -> executed.set(true);
    
    // Create the promise that will be completed
    FlowPromise<Void> promise = createPromise();
    
    // Create and execute the timer task
    TimerTask timerTask = new TimerTask(1, 1000, task, TaskPriority.DEFAULT, promise, null);
    timerTask.execute();
    
    // Verify the task was executed
    assertTrue(executed.get());
    
    // Verify the promise was completed
    assertTrue(promise.isCompleted());
  }

  @Test
  void testExecuteWithException() {
    // Create a task that throws an exception
    RuntimeException exception = new RuntimeException("Test exception");
    Runnable task = () -> {
      throw exception;
    };
    
    // Create the promise that will be completed exceptionally
    FlowPromise<Void> promise = createPromise();
    
    // Create and execute the timer task
    TimerTask timerTask = new TimerTask(1, 1000, task, TaskPriority.DEFAULT, promise, null);
    timerTask.execute();
    
    // Verify the promise was completed exceptionally
    assertTrue(promise.isCompleted());
  }

  @Test
  void testCompareToByTime() {
    // Create timer tasks with different scheduled times
    TimerTask task1 = new TimerTask(1, 1000, () -> { }, TaskPriority.DEFAULT, createPromise(), null);
    TimerTask task2 = new TimerTask(2, 2000, () -> { }, TaskPriority.DEFAULT, createPromise(), null);
    
    // Earlier time should compare less than later time
    assertTrue(task1.compareTo(task2) < 0);
    assertTrue(task2.compareTo(task1) > 0);
  }

  @Test
  void testCompareToByPriority() {
    // Create timer tasks with same scheduled time but different priorities
    TimerTask task1 = new TimerTask(1, 1000, () -> { }, TaskPriority.HIGH, createPromise(), null);
    TimerTask task2 = new TimerTask(2, 1000, () -> { }, TaskPriority.LOW, createPromise(), null);
    
    // Higher priority (lower number) should compare less than lower priority
    assertTrue(task1.compareTo(task2) < 0);
    assertTrue(task2.compareTo(task1) > 0);
  }

  @Test
  void testCompareToById() {
    // Create timer tasks with same scheduled time and priority but different IDs
    TimerTask task1 = new TimerTask(1, 1000, () -> { }, TaskPriority.DEFAULT, createPromise(), null);
    TimerTask task2 = new TimerTask(2, 1000, () -> { }, TaskPriority.DEFAULT, createPromise(), null);
    
    // Lower ID should compare less than higher ID
    assertTrue(task1.compareTo(task2) < 0);
    assertTrue(task2.compareTo(task1) > 0);
  }

  @Test
  void testEquals() {
    // Create two tasks with the same ID but different everything else
    TimerTask task1 = new TimerTask(1, 1000, () -> { }, TaskPriority.HIGH, createPromise(), null);
    TimerTask task2 = new TimerTask(1, 2000, () -> { }, TaskPriority.LOW, createPromise(), null);
    
    // Tasks with same ID should be equal
    assertEquals(task1, task2);
    assertEquals(task2, task1);
    assertEquals(task1.hashCode(), task2.hashCode());
    
    // Task is equal to itself
    assertEquals(task1, task1);
    
    // Create a task with different ID
    TimerTask task3 = new TimerTask(2, 1000, () -> { }, TaskPriority.HIGH, createPromise(), null);
    
    // Tasks with different IDs should not be equal
    assertNotEquals(task1, task3);
    assertNotEquals(task3, task1);
    
    // Task is not equal to null or other types
    assertNotEquals(task1, null);
    assertNotEquals(task1, "not a timer task");
  }

  @Test
  void testHashCode() {
    // Create two tasks with the same ID but different everything else
    TimerTask task1 = new TimerTask(1, 1000, () -> { }, TaskPriority.HIGH, createPromise(), null);
    TimerTask task2 = new TimerTask(1, 2000, () -> { }, TaskPriority.LOW, createPromise(), null);
    
    // Hash codes should be the same if IDs are the same
    assertEquals(task1.hashCode(), task2.hashCode());
    
    // Create a task with different ID
    TimerTask task3 = new TimerTask(2, 1000, () -> { }, TaskPriority.HIGH, createPromise(), null);
    
    // Hash codes should be different if IDs are different
    assertNotEquals(task1.hashCode(), task3.hashCode());
  }

  @Test
  void testToString() {
    // Create a timer task
    TimerTask task = new TimerTask(42, 1000, () -> { }, TaskPriority.HIGH, createPromise(), null);

    // Verify toString contains key information
    String str = task.toString();
    assertNotNull(str);
    assertTrue(str.contains("id=42"));
    assertTrue(str.contains("scheduledTime=1000"));
    assertTrue(str.contains("priority=" + TaskPriority.HIGH));
  }

  @Test
  void testParentTaskCancellation() {
    // Create a parent task
    Task parentTask = new Task(1, TaskPriority.DEFAULT, (Callable<Void>) () -> null, null);

    // Create a promise to detect cancellation
    FlowPromise<Void> promise = createPromise();
    AtomicBoolean promiseCancelled = new AtomicBoolean(false);

    // Set up to detect cancellation completion
    // We'll use a lambda when completing the promise exceptionally to check if it's a cancellation

    // Create a timer task with the parent task
    TimerTask timerTask = new TimerTask(42, 1000, () -> { }, TaskPriority.DEFAULT, promise, parentTask);

    // Register the timer with the parent (normally done by scheduler)
    parentTask.registerTimerTask(timerTask.getId());

    // Set up cancellation callback (normally done by scheduler)
    parentTask.setCancellationCallback((timerIds) -> {
      // Complete the promise exceptionally with cancellation
      CancellationException ce = new CancellationException("Parent task cancelled");
      promise.completeExceptionally(ce);
      // Mark that we detected the cancellation
      promiseCancelled.set(true);
    });

    // Cancel the parent task
    parentTask.cancel();

    // Verify the promise was completed with cancellation
    assertTrue(promiseCancelled.get(), "Promise should be cancelled when parent task is cancelled");
  }
}