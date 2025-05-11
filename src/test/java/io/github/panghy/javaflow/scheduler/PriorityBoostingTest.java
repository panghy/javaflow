package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify that priority boosting works correctly, including boosting to MUST_RUN.
 */
public class PriorityBoostingTest {

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
   * Tests that a task can be boosted to MUST_RUN and then resets to its original priority.
   */
  @Test
  public void testBoostingToMustRun() throws Exception {
    // Get access to the special MUST_RUN priority value
    Field mustRunField = TaskPriority.class.getDeclaredField("MUST_RUN");
    mustRunField.setAccessible(true);
    int mustRunPriority = (int) mustRunField.get(null);
    
    // Create a low-priority task that never completes so we can inspect it
    final AtomicInteger executionCount = new AtomicInteger(0);
    final AtomicInteger currentPriority = new AtomicInteger(0);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Create a task that will never complete by itself
      while (true) {
        executionCount.incrementAndGet();
        currentPriority.set(Flow.scheduler().getCurrentTaskForFuture(null).getPriority());
        Flow.await(Flow.yieldF()); // Yield and come back with same priority
      }
    }, TaskPriority.IDLE);  // Start with lowest priority
    
    // Get the task object
    Task task = scheduler.getActiveTasks().stream()
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No active task found"));
    
    // Verify the task was created with IDLE priority
    assertEquals(TaskPriority.IDLE, task.getPriority(), "Task should be created with IDLE priority");
    
    // Run the task once
    scheduler.pump();
    assertEquals(1, executionCount.get(), "Task should have executed once");
    assertEquals(TaskPriority.IDLE, currentPriority.get(), "Task should run with IDLE priority");
    
    // Directly access the task and set its priority boost time way back in time
    // This will trigger priority aging on the next call to aging
    long oldBoostTime = clock.currentTimeMillis() - 10000; // 10 seconds ago
    task.setEffectivePriority(task.getPriority(), oldBoostTime);
    
    // Directly boost the task to MUST_RUN to test the reset behavior
    System.out.println("Task initial priority: " + task.getPriority());
    System.out.println("Forcing task to MUST_RUN priority: " + mustRunPriority);
    task.setEffectivePriority(mustRunPriority, clock.currentTimeMillis());

    // Verify the task has been set to MUST_RUN
    assertEquals(mustRunPriority, task.getPriority(),
        "Task should have been set to MUST_RUN");
    
    // Now run the task again - it should execute and have its priority reset
    // Run multiple pump cycles to ensure task execution
    for (int i = 0; i < 5; i++) {
      scheduler.pump();
    }

    // Verify the task got executed again
    assertTrue(executionCount.get() > 1, "Task should have executed again");

    // Check the current state of the task
    System.out.println("After execution, task priority: " + task.getPriority());
    System.out.println("Original task priority: " + task.getOriginalPriority());
    System.out.println("Execution count: " + executionCount.get());

    // The priority should have been reset to IDLE after execution
    assertEquals(TaskPriority.IDLE, task.getPriority(),
        "Task priority should have been reset to IDLE after execution");
  }
}