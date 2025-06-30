package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests to validate proper drain and cancellation of tasks during scheduler shutdown.
 */
class SchedulerShutdownTest {

  private SingleThreadedScheduler scheduler;

  @BeforeEach
  void setUp() {
    // Create a scheduler with autoStart=false so we can control execution with pump()
    scheduler = new SingleThreadedScheduler(false);
    scheduler.start();
  }

  @AfterEach
  void tearDown() {
    // Make sure we clean up resources even if a test fails
    if (scheduler != null) {
      scheduler.close();
    }
  }

  /**
   * Test that tasks that are yielding receive cancellation when the scheduler starts draining,
   * but are still allowed to execute cleanup code before full shutdown.
   */
  @Test
  void testYieldCancellationOnDrain() throws Exception {
    // Use flags to track the task's execution stages
    AtomicBoolean yieldReached = new AtomicBoolean(false);
    AtomicBoolean exceptionReceived = new AtomicBoolean(false);
    AtomicBoolean cleanupExecuted = new AtomicBoolean(false);
    CountDownLatch taskDone = new CountDownLatch(1);
    AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
    
    // Create a task that will yield and then perform cleanup when cancelled
    scheduler.schedule(() -> {
      try {
        // First indicate that we've reached the yield point
        yieldReached.set(true);
        
        // Now yield and wait for completion or cancellation
        CompletableFuture<Void> yieldFuture = scheduler.yield();
        try {
          scheduler.await(yieldFuture);
          // If we get here, the yield succeeded (shouldn't happen)
        } catch (Throwable t) {
          // Save the exception for verification
          exceptionRef.set(t);
          exceptionReceived.set(true);
          
          // Perform cleanup work that should still execute
          cleanupExecuted.set(true);
        }
      } finally {
        taskDone.countDown();
      }
      return null;
    });
    
    // Run the scheduler to let the task reach its yield point
    int processed = scheduler.pump();
    assertTrue(processed > 0, "Scheduler should have processed at least one task");
    
    // Check that the task reached the yield point
    assertTrue(yieldReached.get(), "Task should have reached the yield point");
    
    // Drain the scheduler, which should cancel the yielding task but allow cleanup
    scheduler.drain(); // 1 second timeout
    
    // Wait for the task to complete with a cancellation
    assertTrue(taskDone.await(1, TimeUnit.SECONDS), "Task should have completed after drain");
    
    // Verify we got the expected cancellation exception
    assertTrue(exceptionReceived.get(), "Task should have received an exception");
    assertNotNull(exceptionRef.get(), "Exception should not be null");
    assertTrue(exceptionRef.get() instanceof CancellationException, 
        "Exception should be a CancellationException");
    
    // Verify that cleanup work was executed
    assertTrue(cleanupExecuted.get(), "Cleanup work should have been executed");
  }

  /**
   * Test that timer tasks are cancelled when the scheduler is drained,
   * but are still allowed to execute cleanup code before full shutdown.
   */
  @Test
  void testTimerCancellationOnDrain() throws Exception {
    // Tracking the timer task
    AtomicBoolean timerWaitStarted = new AtomicBoolean(false);
    AtomicBoolean exceptionReceived = new AtomicBoolean(false);
    AtomicBoolean cleanupExecuted = new AtomicBoolean(false);
    CountDownLatch taskDone = new CountDownLatch(1);
    AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
    
    // Create a task that will schedule a timer and then perform cleanup when cancelled
    scheduler.schedule(() -> {
      try {
        // Schedule a delay of 10 seconds
        CompletableFuture<Void> delayFuture = scheduler.scheduleDelay(10000);
        
        // Signal that we're waiting on the timer
        timerWaitStarted.set(true);
        
        try {
          // Await the timer (should be cancelled when scheduler drains)
          scheduler.await(delayFuture);
          // If we get here, the timer completed (shouldn't happen)
        } catch (Throwable t) {
          // Save the exception for verification
          exceptionRef.set(t);
          exceptionReceived.set(true);
          
          // Perform cleanup work that should still execute
          cleanupExecuted.set(true);
        }
      } finally {
        taskDone.countDown();
      }
      return null;
    });
    
    // Run the scheduler to let the task start and schedule its timer
    int processed = scheduler.pump();
    assertTrue(processed > 0, "Scheduler should have processed at least one task");
    
    // Check that the task started waiting on the timer
    assertTrue(timerWaitStarted.get(), "Task should have started waiting on the timer");
    
    // Drain the scheduler, which should cancel the timer but allow cleanup
    scheduler.drain();
    
    // Wait for the task to complete with a cancellation
    assertTrue(taskDone.await(1, TimeUnit.SECONDS), "Task should have completed after drain");
    
    // Verify we got the expected cancellation exception
    assertTrue(exceptionReceived.get(), "Task should have received an exception");
    assertNotNull(exceptionRef.get(), "Exception should not be null");
    assertTrue(exceptionRef.get() instanceof CancellationException, 
        "Exception should be a CancellationException");
    
    // Verify that cleanup work was executed
    assertTrue(cleanupExecuted.get(), "Cleanup work should have been executed");
  }

  /**
   * Test that new tasks cannot be scheduled when the scheduler is in drain mode.
   */
  @Test
  void testCannotScheduleWhileDraining() throws Exception {
    // Set up a task that puts the scheduler in drain mode
    CountDownLatch drainLatch = new CountDownLatch(1);
    
    // Schedule one task to ensure the scheduler is running
    scheduler.schedule(() -> {
      // Signal that we're going to put the scheduler in drain mode
      drainLatch.countDown();
      return null;
    });
    
    // Run the task
    scheduler.pump();
    
    // Wait for the task to indicate it's about to drain
    assertTrue(drainLatch.await(1, TimeUnit.SECONDS), "Task should have run");
    
    // Put the scheduler in drain mode
    scheduler.drain();
    
    // Now try to schedule a new task
    CompletableFuture<String> future = scheduler.schedule(() -> "test");
    
    // The future should complete exceptionally
    assertTrue(future.isCompletedExceptionally(), "Future should be completed exceptionally");
    
    // Verify the exception is an IllegalStateException
    try {
      future.toCompletableFuture().get();
      fail("Should have thrown an exception");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof IllegalStateException, 
          "Exception should be an IllegalStateException");
      assertTrue(e.getMessage().contains("shutting down"), 
          "Exception message should mention shutting down");
    }
  }
}