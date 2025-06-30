package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pump method in SingleThreadedScheduler.
 * This focuses on the behavior of the pump method in different scenarios.
 */
class PumpMethodTest {

  private SingleThreadedScheduler scheduler;
  
  @BeforeEach
  void setUp() {
    // Create a scheduler with the carrier thread disabled
    scheduler = new SingleThreadedScheduler(false);
    scheduler.start();
  }
  
  @AfterEach
  void tearDown() {
    if (scheduler != null) {
      scheduler.close();
    }
  }
  
  @Test
  void testPumpWithEmptyQueue() {
    // Pump on an empty queue should return 0
    int processed = scheduler.pump();
    assertEquals(0, processed, "Pump on empty queue should return 0");
  }
  
  @Test
  void testPumpWithSingleTask() throws Exception {
    // Create a task counter
    AtomicInteger counter = new AtomicInteger(0);
    
    // Schedule a task
    scheduler.schedule(() -> {
      counter.incrementAndGet();
      return null;
    });
    
    // Pump the scheduler
    int processed = scheduler.pump();
    assertEquals(1, processed, "Pump should process exactly 1 task");
    assertEquals(1, counter.get(), "Task should have executed");
  }
  
  @Test
  void testPumpProcessesAllTasksInBatch() {
    // Create a list to track execution order
    List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
    
    // Schedule multiple tasks with different priorities
    scheduler.schedule(() -> {
      executionOrder.add("low");
      return null;
    }, TaskPriority.LOW);
    
    scheduler.schedule(() -> {
      executionOrder.add("medium");
      return null;
    }, TaskPriority.DEFAULT);
    
    scheduler.schedule(() -> {
      executionOrder.add("high");
      return null;
    }, TaskPriority.HIGH);
    
    // Pump the scheduler
    int processed = scheduler.pump();
    assertEquals(3, processed, "Pump should process exactly 3 tasks");
    
    // Verify tasks executed in priority order
    assertEquals(3, executionOrder.size(), "All tasks should have executed");
    assertEquals("high", executionOrder.get(0), "High priority task should execute first");
    assertEquals("medium", executionOrder.get(1), "Medium priority task should execute second");
    assertEquals("low", executionOrder.get(2), "Low priority task should execute last");
  }
  
  @Test
  void testPumpHandlesTasksAddedDuringPumping() throws Exception {
    // Create a counter
    AtomicInteger counter = new AtomicInteger(0);
    
    // Create a task that schedules another task
    scheduler.schedule(() -> {
      counter.incrementAndGet();
      
      // Schedule another task while this one is running
      scheduler.schedule(() -> {
        counter.incrementAndGet();
        return null;
      });
      
      return null;
    });
    
    // Pump the scheduler
    int processed = scheduler.pump();
    
    // Only the first task should be processed in this pump
    assertEquals(1, processed, "Pump should process exactly 1 task");
    assertEquals(1, counter.get(), "Only the first task should have executed");
    
    // The second task remains in the queue for the next pump
    processed = scheduler.pump();
    assertEquals(1, processed, "Second pump should process the newly added task");
    assertEquals(2, counter.get(), "Both tasks should have executed after two pumps");
  }
  
  @Test
  void testPumpWithYieldingTasks() throws Exception {
    // Create a list to track execution steps
    List<String> executionSteps = Collections.synchronizedList(new ArrayList<>());
    
    // Create a latch to signal completion
    CountDownLatch completionLatch = new CountDownLatch(1);
    
    // Schedule a task that yields
    scheduler.schedule(() -> {
      executionSteps.add("start");
      
      try {
        // First yield
        CompletableFuture<Void> yieldFuture = scheduler.yield();
        
        // Record that we're yielded and signal to pump again
        executionSteps.add("yielded");
        
        // Wait to be resumed
        scheduler.await(yieldFuture);
        
        // Record that we're resumed
        executionSteps.add("resumed");
        
        // Second yield
        CompletableFuture<Void> secondYieldFuture = scheduler.yield();
        
        // Record that we're yielded again
        executionSteps.add("yielded-again");
        
        // Wait to be resumed again
        scheduler.await(secondYieldFuture);
        
        // Record completion
        executionSteps.add("completed");
        completionLatch.countDown();
      } catch (Exception e) {
        executionSteps.add("error: " + e.getMessage());
      }
      
      return null;
    });
    
    // First pump will run the task until it yields
    int processed = scheduler.pump();
    assertTrue(processed > 0, "First pump should process at least 1 task");
    assertTrue(executionSteps.contains("start"), "Task should have started");
    assertTrue(executionSteps.contains("yielded"), "Task should have yielded");
    
    // Make sure the task doesn't proceed without pumping
    Thread.sleep(100);
    assertFalse(executionSteps.contains("resumed"), "Task should not have resumed yet");
    
    // Second pump will resume the task
    processed = scheduler.pump();
    assertTrue(processed > 0, "Second pump should process at least 1 task");
    
    // Wait for a bit
    Thread.sleep(100);
    
    // Keep pumping until the task completes
    int maxPumps = 5;
    for (int i = 0; i < maxPumps && !completionLatch.await(100, TimeUnit.MILLISECONDS); i++) {
      scheduler.pump();
    }
    
    // Verify final state
    assertTrue(completionLatch.await(0, TimeUnit.MILLISECONDS), "Task should have completed");
    assertTrue(executionSteps.contains("completed"), "Task should have recorded completion");
    
    // Check the sequence (order matters)
    int startIndex = executionSteps.indexOf("start");
    int yieldedIndex = executionSteps.indexOf("yielded");
    int resumedIndex = executionSteps.indexOf("resumed");
    int completedIndex = executionSteps.indexOf("completed");
    
    assertTrue(startIndex < yieldedIndex, "Should start before yielding");
    assertTrue(yieldedIndex < resumedIndex, "Should yield before resuming");
    assertTrue(resumedIndex < completedIndex, "Should resume before completing");
  }
  
  @Test
  void testPumpWithCancelledTask() throws Exception {
    // Create flags to track task execution
    AtomicBoolean yielded = new AtomicBoolean(false);
    AtomicBoolean resumed = new AtomicBoolean(false);
    AtomicBoolean cancelled = new AtomicBoolean(false);
    
    // Create a latch to signal completion of yield
    CountDownLatch yieldLatch = new CountDownLatch(1);
    CountDownLatch cancelLatch = new CountDownLatch(1);
    
    // Schedule a task that yields and then can be cancelled
    CompletableFuture<Void> future = scheduler.schedule(() -> {
      try {
        // Yield and mark that we yielded
        CompletableFuture<Void> yieldFuture = scheduler.yield();
        yielded.set(true);
        yieldLatch.countDown();
        
        // Wait to be resumed (this will throw if cancelled)
        try {
          scheduler.await(yieldFuture);
          resumed.set(true);
        } catch (Exception e) {
          if (e instanceof java.util.concurrent.CancellationException) {
            cancelled.set(true);
            cancelLatch.countDown();
          }
          throw e;
        }
      } catch (Exception e) {
        // Expected exception on cancellation
        if (e instanceof java.util.concurrent.CancellationException) {
          cancelled.set(true);
          cancelLatch.countDown();
        }
      }
      return null;
    });
    
    // First pump will run the task until it yields
    int processed = scheduler.pump();
    assertTrue(processed > 0, "First pump should process at least 1 task");
    
    // Wait for the task to yield
    assertTrue(yieldLatch.await(1, TimeUnit.SECONDS), "Task should have yielded");
    assertTrue(yielded.get(), "Task should have reached the yield point");
    
    // Cancel the task
    future.cancel(true);
    
    // Pump multiple times to ensure the task has a chance to process the cancellation
    for (int i = 0; i < 3 && !cancelLatch.await(100, TimeUnit.MILLISECONDS); i++) {
      scheduler.pump();
    }
    
    // Verify the cancellation was detected
    assertTrue(future.isCancelled(), "Future should be cancelled");
    
    // Tasks will not process post-cancellation code past await(yield)
    assertFalse(resumed.get(), "Cancelled task should not have executed post-yield code");
  }
  
  @Test
  void testPumpWithExceptionalTasks() {
    // Create a task that will throw an exception
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    
    // Schedule a task that throws an exception
    CompletableFuture<Void> future = scheduler.schedule(() -> {
      exceptionThrown.set(true);
      throw new RuntimeException("Test exception");
    });
    
    // Pump the scheduler
    int processed = scheduler.pump();
    assertEquals(1, processed, "Pump should process the task despite exception");
    assertTrue(exceptionThrown.get(), "Task should have executed and thrown exception");
    
    // The future should be completed exceptionally
    assertTrue(future.isCompletedExceptionally(), "Future should be completed exceptionally");
  }
  
  @Test
  void testPumpWithTasksThatAddMoreTasks() {
    // Counter for total tasks executed
    AtomicInteger tasksExecuted = new AtomicInteger(0);
    
    // Schedule an initial task that adds more tasks
    scheduler.schedule(() -> {
      tasksExecuted.incrementAndGet();
      
      // Add 3 more tasks
      for (int i = 0; i < 3; i++) {
        final int taskIndex = i;
        scheduler.schedule(() -> {
          tasksExecuted.incrementAndGet();
          return "Task " + taskIndex;
        });
      }
      
      return "Initial task";
    });
    
    // First pump will execute the initial task but not the new tasks
    int firstPumpCount = scheduler.pump();
    assertEquals(1, firstPumpCount, "First pump should process only the initial task");
    assertEquals(1, tasksExecuted.get(), "Only initial task should have executed");
    
    // Second pump will execute the 3 added tasks
    int secondPumpCount = scheduler.pump();
    assertEquals(3, secondPumpCount, "Second pump should process 3 added tasks");
    assertEquals(4, tasksExecuted.get(), "All 4 tasks should have executed");
    
    // Third pump should find no tasks
    int thirdPumpCount = scheduler.pump();
    assertEquals(0, thirdPumpCount, "Third pump should find no tasks");
    assertEquals(4, tasksExecuted.get(), "No additional tasks should have executed");
  }
}