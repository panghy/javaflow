package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests to verify that priority boosting is properly reset when a task is executed.
 */
public class PriorityResetTest {

  private FlowScheduler scheduler;
  private SimulatedClock clock;

  @BeforeEach
  public void setUp() {
    clock = new SimulatedClock();
    scheduler = new FlowScheduler(false, clock);
    Flow.setScheduler(scheduler);
  }

  @AfterEach
  public void tearDown() {
    scheduler.close();
  }

  /**
   * Tests priority is reset when a task is resumed after it yields.
   */
  @Test
  public void testPriorityResetAfterYield() throws Exception {
    // Create a yielding task that will track priorities across multiple yields
    final boolean[] isDone = new boolean[1];
    final int[] priorities = new int[3]; // Track priority at different stages

    // Create a task with a yield
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Track first execution priority
      Task task1 = Flow.scheduler().getCurrentTaskForFuture(null);
      priorities[0] = task1.getPriority();

      // Yield and continue
      Flow.await(Flow.yieldF());

      // Track second execution priority
      Task task2 = Flow.scheduler().getCurrentTaskForFuture(null);
      priorities[1] = task2.getPriority();

      // Yield again
      Flow.await(Flow.yieldF());

      // Track third execution priority
      Task task3 = Flow.scheduler().getCurrentTaskForFuture(null);
      priorities[2] = task3.getPriority();

      // Mark as done
      isDone[0] = true;
      return null;
    }, TaskPriority.LOW);

    // Execute task and boost priority between executions

    // Run the first part
    scheduler.pump();

    // Create another task that will run during second execution to verify
    // we don't modify priorities of running tasks
    Task activeTask = scheduler.getActiveTasks().stream()
        .findFirst()
        .orElse(null);

    if (activeTask != null) {
      // Boost the priority
      activeTask.setEffectivePriority(TaskPriority.HIGH, clock.currentTimeMillis());
    }

    // Run second part and boost again
    scheduler.pump();

    activeTask = scheduler.getActiveTasks().stream()
        .findFirst()
        .orElse(null);

    if (activeTask != null) {
      // Boost again
      activeTask.setEffectivePriority(TaskPriority.CRITICAL, clock.currentTimeMillis());
    }

    // Run the third part
    scheduler.pump();

    // Run one more time to ensure all tasks complete
    scheduler.pump();

    // Wait until the task is done or timeout
    int maxAttempts = 10;
    while (!isDone[0] && maxAttempts > 0) {
      maxAttempts--;
      scheduler.pump();
    }

    // Verify the task has completed
    assertEquals(true, isDone[0], "Task should have completed");

    // Verify all priorities were LOW, meaning priority was reset on each execution
    assertEquals(TaskPriority.LOW, priorities[0], "First execution priority should be LOW");
    assertEquals(TaskPriority.LOW, priorities[1], "Second execution priority should be LOW after being boosted");
    assertEquals(TaskPriority.LOW, priorities[2], "Third execution priority should be LOW after being boosted");
  }
  
  /**
   * Test that we can successfully boost and reset task priorities.
   */
  @Test
  public void testPriorityBoostAndReset() throws Exception {
    // Create an atomic to track the priority when the task executes
    final AtomicInteger executedPriority = new AtomicInteger(0);
    
    // Create a counter to verify task execution
    final AtomicInteger executionCount = new AtomicInteger(0);
    
    // Create a task that will yield, allowing us to boost its priority before execution
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Record the priority when we execute
      executedPriority.set(Flow.scheduler().getCurrentTaskForFuture(null).getPriority());
      executionCount.incrementAndGet();
      return null;
    }, TaskPriority.LOW);
    
    // Make sure the task has not executed yet
    assertEquals(0, executionCount.get(), "Task should not have executed yet");
    
    // Get the task and manually set its priority to a boosted value to simulate aging
    Task task = scheduler.getActiveTasks().stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("No active task found"));
        
    // Manually boost the priority to simulate aging
    task.setEffectivePriority(TaskPriority.HIGH, clock.currentTimeMillis());
    
    // Verify the boost worked
    assertEquals(TaskPriority.HIGH, task.getPriority(),
        "Task priority should have been boosted");
    
    // Now execute the task
    scheduler.pump();
    
    // Verify the task executed
    assertEquals(1, executionCount.get(), "Task should have executed once");
    
    // Verify the priority was reset to LOW when the task executed
    assertEquals(TaskPriority.LOW, executedPriority.get(),
        "Priority should have been reset to LOW when task executed");
  }
}