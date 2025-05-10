package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the priority aging mechanism in the JavaFlow scheduler.
 */
public class PriorityAgingTest {

  private FlowScheduler originalScheduler;
  private SimulatedClock clock;
  private FlowScheduler testScheduler;

  @BeforeEach
  public void setUp() {
    // Save the original scheduler
    originalScheduler = Flow.scheduler();

    // Create a simulated clock for deterministic testing
    clock = new SimulatedClock();
    clock.setCurrentTime(0); // Start at time 0

    // Create a test scheduler with standard priority aging
    testScheduler = new FlowScheduler(false, clock);

    // Set the test scheduler as the global scheduler
    Flow.setScheduler(testScheduler);
  }

  @AfterEach
  public void tearDown() {
    // Restore the original scheduler
    Flow.setScheduler(originalScheduler);
  }

  /**
   * Tests that priority aging allows lower priority tasks to eventually execute
   * even when higher priority tasks are continuously added.
   */
  @Test
  public void testPriorityAging() {
    // Track executions of different priority tasks
    AtomicInteger highPriorityExecutions = new AtomicInteger(0);
    AtomicInteger lowPriorityExecutions = new AtomicInteger(0);

    // Create a low priority task - without priority aging, this would be starved
    FlowFuture<Void> lowPriorityTask = Flow.startActor(() -> {
      for (int i = 0; i < 10; i++) {
        lowPriorityExecutions.incrementAndGet();
        Flow.await(Flow.yieldF());
      }
      return null;
    }, TaskPriority.LOW);

    // Run several iterations, adding high priority tasks each time
    for (int i = 0; i < 10; i++) {
      // Add a new high priority task each iteration
      Flow.startActor(() -> {
        highPriorityExecutions.incrementAndGet();
        return null;
      }, TaskPriority.HIGH);

      // Process all ready tasks
      testScheduler.pump();

      // Advance time to trigger priority aging
      clock.advanceTime(500);
      testScheduler.pump();
    }

    // Verify that both high and low priority tasks have executed
    assertTrue(highPriorityExecutions.get() > 0, "High priority tasks should have executed");
    assertTrue(lowPriorityExecutions.get() > 0,
        "Low priority tasks should have executed due to priority aging");
  }

  /**
   * Tests that tasks receive the correct priority boost based on waiting time.
   */
  @Test
  public void testPriorityBoostAmount() {
    // Create tasks with different priorities
    List<Task> tasks = new ArrayList<>();

    // Add tasks to the scheduler
    for (int i = 0; i < 5; i++) {
      final int taskNumber = i;
      FlowFuture<Void> future = Flow.startActor(() -> {
        System.out.println("Executing task " + taskNumber);
        return null;
      }, TaskPriority.LOW + (i * 2)); // Varying priorities

      // Get the task from the scheduler
      Task task = testScheduler.getCurrentTaskForFuture(future);
      if (task != null) {
        tasks.add(task);
      }
    }

    // Don't execute tasks yet - we want to test priority aging

    // Advance time to trigger first aging interval
    clock.advanceTime(600); // Just over the 500ms aging interval

    // Examine task priorities - they should have been boosted once by 5 levels
    for (Task task : tasks) {
      int originalPriority = task.getOriginalPriority();
      int effectivePriority = task.getPriority();

      // Check that priority was boosted by 5 (or to the minimum priority)
      int expectedPriority = Math.max(TaskPriority.CRITICAL, originalPriority - 5);
      assertEquals(expectedPriority, effectivePriority,
          "Task priority should have been boosted by 5");
    }

    // Advance time again to trigger second aging interval
    clock.advanceTime(600);

    // Examine task priorities - they should have been boosted again
    for (Task task : tasks) {
      int originalPriority = task.getOriginalPriority();
      int effectivePriority = task.getPriority();

      // Check that priority was boosted by 10 (or to the minimum priority)
      int expectedPriority = Math.max(TaskPriority.CRITICAL, originalPriority - 10);
      assertEquals(expectedPriority, effectivePriority,
          "Task priority should have been boosted by 10 total");
    }

    // Advance time several more times to exceed the maximum boost
    for (int i = 0; i < 5; i++) {
      clock.advanceTime(600);
    }

    // Examine task priorities - they should be capped at original - maxBoost
    for (Task task : tasks) {
      int originalPriority = task.getOriginalPriority();
      int effectivePriority = task.getPriority();

      // Check that priority was boosted by at most 15 (the configured max)
      int expectedPriority = Math.max(TaskPriority.CRITICAL, originalPriority - 15);
      assertEquals(expectedPriority, effectivePriority,
          "Task priority should be capped at original - 15");
    }
  }
}