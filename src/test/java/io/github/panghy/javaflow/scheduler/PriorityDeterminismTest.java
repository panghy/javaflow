package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests to verify deterministic behavior of the SingleThreadedScheduler's priority boosting.
 * This test ensures that given the same initial conditions, tasks will always be processed
 * in the same order, even with priority aging/boosting occurring.
 */
class PriorityDeterminismTest {

  private SimulatedClock clock;
  private SingleThreadedScheduler scheduler;
  private static final int NUM_TEST_ITERATIONS = 100;

  // We'll use these to track the exact order of task execution events
  private final List<String> executionLog = new ArrayList<>();
  private final AtomicInteger yieldCounter = new AtomicInteger(0);
  private final AtomicInteger resumeCounter = new AtomicInteger(0);
  private final AtomicInteger completeCounter = new AtomicInteger(0);
  
  @BeforeEach
  void setUp() {
    if (scheduler != null) {
      scheduler.close();
    }
    // Create a simulated clock to have precise control over time
    clock = new SimulatedClock();
    clock.setCurrentTime(0); // Start at time 0
    
    // Create a scheduler with the simulated clock and manual pumping
    scheduler = new SingleThreadedScheduler(false, clock);
    scheduler.start();
    
    // Clear all state between tests
    executionLog.clear();
    yieldCounter.set(0);
    resumeCounter.set(0);
    completeCounter.set(0);
  }
  
  @AfterEach
  void tearDown() {
    scheduler.close();
  }
  
  /**
   * Creates a test scenario with tasks of different priorities that yield and resume
   * in a deterministic pattern, and verifies the execution order is consistent.
   */
  @Test
  void testDeterministicPriorityBoosting() {
    // Run the same scenario multiple times to verify determinism
    List<String> firstRunLog = null;
    
    for (int iteration = 0; iteration < NUM_TEST_ITERATIONS; iteration++) {
      // Reset the test state for each iteration
      setUp();
      
      // Create the test scenario
      createTestScenario();
      
      // Capture the execution log from the first run
      if (iteration == 0) {
        firstRunLog = new ArrayList<>(executionLog);
      } else {
        // For subsequent runs, verify the execution log matches the first run
        assertEquals(firstRunLog, executionLog,
            "Execution order should be deterministic across multiple runs");
      }
    }
  }
  
  /**
   * Creates a test scenario with five tasks of different priorities that yield
   * and interact in a specific pattern.
   */
  private void createTestScenario() {
    // Create tasks with different priorities
    scheduleTestTask("HighPriTask", TaskPriority.HIGH);
    scheduleTestTask("DefaultPriTask1", TaskPriority.DEFAULT);
    scheduleTestTask("DefaultPriTask2", TaskPriority.DEFAULT);
    scheduleTestTask("LowPriTask1", TaskPriority.LOW);
    scheduleTestTask("LowPriTask2", TaskPriority.LOW);
    
    // Run tasks for several pump/time cycles to observe priority boosting effects
    for (int cycle = 0; cycle < 10; cycle++) {
      // Process all ready tasks
      int tasksProcessed = scheduler.pump();
      if (tasksProcessed > 0) {
        executionLog.add("Cycle " + cycle + ": Processed " + tasksProcessed + " tasks");
      }
      
      // Advance time to trigger priority aging
      // The scheduler is configured to update priorities every 100ms
      clock.advanceTime(100);
      
      // If all tasks completed, we're done
      if (completeCounter.get() == 5) {
        executionLog.add("All tasks completed after " + cycle + " cycles");
        break;
      }
    }
  }
  
  /**
   * Schedules a test task that will yield a specific number of times and log its execution.
   */
  private void scheduleTestTask(String taskName, int priority) {
    scheduler.schedule(() -> {
      // Log task start
      executionLog.add(taskName + " started (priority=" + priority + ")");
      
      // Run for a bit, then yield
      executionLog.add(taskName + " working (1/3)");
      int myYieldId = yieldCounter.incrementAndGet();
      executionLog.add(taskName + " yielding (yield #" + myYieldId + ")");
      
      try {
        scheduler.await(scheduler.yield());
        int myResumeId = resumeCounter.incrementAndGet();
        executionLog.add(taskName + " resumed (resume #" + myResumeId + ")");
        
        // Work some more, then yield again
        executionLog.add(taskName + " working (2/3)");
        myYieldId = yieldCounter.incrementAndGet();
        executionLog.add(taskName + " yielding again (yield #" + myYieldId + ")");
        
        scheduler.await(scheduler.yield());
        myResumeId = resumeCounter.incrementAndGet();
        executionLog.add(taskName + " resumed again (resume #" + myResumeId + ")");
        
        // Finish work
        executionLog.add(taskName + " working (3/3)");
        executionLog.add(taskName + " completing");
        completeCounter.incrementAndGet();
      } catch (Exception e) {
        executionLog.add(taskName + " FAILED: " + e.getMessage());
      }
      
      return taskName + " result";
    }, priority);
  }
  
  /**
   * Tests deterministic behavior with yield(changed_priority) and delay() operations.
   * This test verifies that:
   * 1. Yielding with an explicitly changed priority works deterministically
   * 2. Delay operations are executed in a deterministic order
   */
  @Test
  void testDeterministicYieldWithChangedPriorityAndDelay() {
    // Run the same scenario multiple times to verify determinism
    List<String> firstRunLog = null;
    
    for (int iteration = 0; iteration < NUM_TEST_ITERATIONS; iteration++) {
      // Reset the test state for each iteration
      setUp();
      
      // Create the test scenario with yield(changed_priority) and delay()
      createPriorityChangeAndDelayScenario();
      
      // Capture the execution log from the first run
      if (iteration == 0) {
        firstRunLog = new ArrayList<>(executionLog);
      } else {
        // For subsequent runs, verify the execution log matches the first run
        assertEquals(firstRunLog, executionLog,
            "Execution order should be deterministic across multiple runs with priority changes and delays");
      }
    }
  }
  
  /**
   * Creates a test scenario with tasks that explicitly change their priority when yielding
   * and use delay operations with different durations.
   */
  private void createPriorityChangeAndDelayScenario() {
    // Schedule tasks that will use yield with priority changes
    schedulePriorityChangeTask("LowToHighTask", TaskPriority.LOW, TaskPriority.HIGH);
    schedulePriorityChangeTask("HighToLowTask", TaskPriority.HIGH, TaskPriority.LOW);
    schedulePriorityChangeTask("DefaultTask", TaskPriority.DEFAULT, TaskPriority.DEFAULT);
    
    // Schedule tasks that will use delay operations
    scheduleDelayTask("ShortDelayTask", 0.1); // 100ms delay
    scheduleDelayTask("MediumDelayTask", 0.25); // 250ms delay
    scheduleDelayTask("LongDelayTask", 0.5); // 500ms delay
    
    // Run a simulation for a fixed duration to observe the interleaving of tasks
    for (int cycle = 0; cycle < 15; cycle++) {
      // Process all ready tasks
      int tasksProcessed = scheduler.pump();
      if (tasksProcessed > 0) {
        executionLog.add("Cycle " + cycle + ": Processed " + tasksProcessed + " tasks");
      }
      
      // Advance time to trigger both priority aging and to complete delays
      // This will automatically process timer tasks as part of the advanceTime method
      scheduler.advanceTime(100);
      
      // If all tasks completed, we're done
      if (completeCounter.get() == 6) {
        executionLog.add("All tasks completed after " + cycle + " cycles");
        break;
      }
    }
    
    // Verify that all tasks have completed (test is reliable)
    assertEquals(6, completeCounter.get(), "All tasks should complete during the test");
  }
  
  /**
   * Schedules a task that changes its priority when it yields.
   */
  private void schedulePriorityChangeTask(String taskName, int initialPriority, int newPriority) {
    scheduler.schedule(() -> {
      // Log task start
      executionLog.add(taskName + " started (priority=" + initialPriority + ")");
      
      // Run for a bit, then yield with new priority
      executionLog.add(taskName + " working (1/2)");
      int myYieldId = yieldCounter.incrementAndGet();
      String priorityChangeMsg = initialPriority == newPriority ? 
          "maintaining priority" : "changing priority to " + newPriority;
      executionLog.add(taskName + " yielding with " + priorityChangeMsg + " (yield #" + myYieldId + ")");
      
      try {
        // Use yield with explicit priority change
        scheduler.await(scheduler.yield(newPriority));
        int myResumeId = resumeCounter.incrementAndGet();
        executionLog.add(taskName + " resumed with new priority=" + newPriority + " (resume #" + myResumeId + ")");
        
        // Finish work
        executionLog.add(taskName + " working (2/2)");
        executionLog.add(taskName + " completing");
        completeCounter.incrementAndGet();
      } catch (Exception e) {
        executionLog.add(taskName + " FAILED: " + e.getMessage());
      }
      
      return taskName + " result";
    }, initialPriority);
  }
  
  /**
   * Schedules a task that uses delay() to wait for a specified time.
   */
  private void scheduleDelayTask(String taskName, double delaySeconds) {
    scheduler.schedule(() -> {
      // Log task start
      executionLog.add(taskName + " started");
      
      // Run for a bit, then delay
      executionLog.add(taskName + " working (1/2)");
      executionLog.add(taskName + " delaying for " + delaySeconds + " seconds");
      
      try {
        // Use scheduler.delay() to pause execution
        scheduler.await(scheduler.scheduleDelay(delaySeconds));
        executionLog.add(taskName + " delay completed after " + delaySeconds + " seconds");
        
        // Finish work
        executionLog.add(taskName + " working (2/2)");
        executionLog.add(taskName + " completing");
        completeCounter.incrementAndGet();
      } catch (Exception e) {
        executionLog.add(taskName + " FAILED: " + e.getMessage());
      }
      
      return taskName + " result";
    }, TaskPriority.DEFAULT);
  }
}