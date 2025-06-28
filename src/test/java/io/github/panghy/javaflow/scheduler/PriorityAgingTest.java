package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

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
    CompletableFuture<Void> lowPriorityTask = Flow.startActor(() -> {
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
}