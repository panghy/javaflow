package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TestScheduler implementation.
 */
class TestSchedulerTest {

  private TestScheduler testScheduler;
  private FlowScheduler simulatedScheduler;

  @BeforeEach
  void setUp() {
    // Create a simulated scheduler
    simulatedScheduler = new FlowScheduler(false, FlowClock.createSimulatedClock());

    // Create the test scheduler
    testScheduler = new TestScheduler(simulatedScheduler);
  }

  @AfterEach
  void tearDown() {
    // Ensure we restore the original scheduler
    if (testScheduler.isSimulationActive()) {
      testScheduler.endSimulation();
    }
  }

  @Test
  void testStartSimulation() {
    // Check initial state
    assertFalse(testScheduler.isSimulationActive());

    // Start simulation
    testScheduler.startSimulation();

    // Verify simulation is active
    assertTrue(testScheduler.isSimulationActive());

    // Verify scheduler was changed
    assertSame(simulatedScheduler, Flow.scheduler());

    // Cannot start simulation twice
    assertThrows(IllegalStateException.class, () -> testScheduler.startSimulation());
  }

  @Test
  void testEndSimulation() {
    // Start simulation first
    testScheduler.startSimulation();
    assertTrue(testScheduler.isSimulationActive());

    // End simulation
    testScheduler.endSimulation();

    // Verify simulation is no longer active
    assertFalse(testScheduler.isSimulationActive());

    // Cannot end simulation twice
    assertThrows(IllegalStateException.class, () -> testScheduler.endSimulation());
  }

  @Test
  void testAdvanceTime() {
    // Start simulation
    testScheduler.startSimulation();

    // Initial time should be 0
    assertEquals(0, simulatedScheduler.getClock().currentTimeMillis());

    // Advance time by 1 second
    int tasksExecuted = testScheduler.advanceTime(1000);

    // Verify time advanced
    assertEquals(1000, simulatedScheduler.getClock().currentTimeMillis());

    // No tasks should have executed since none were scheduled
    assertEquals(0, tasksExecuted);

    // Cannot advance time when simulation is not active
    testScheduler.endSimulation();
    assertThrows(IllegalStateException.class, () -> testScheduler.advanceTime(1000));
  }

  @Test
  void testAdvanceTimeWithTasks() {
    // Start simulation
    testScheduler.startSimulation();

    // Schedule tasks with different delays
    AtomicInteger counter = new AtomicInteger(0);
    AtomicLong executionTime = new AtomicLong(0);
    CountDownLatch latch = new CountDownLatch(1);

    // Schedule a task with 500ms delay
    Flow.scheduler().schedule(() -> {
      try {
        FlowFuture<Void> delay = Flow.scheduler().scheduleDelay(0.5); // 500ms
        Flow.scheduler().await(delay);

        counter.incrementAndGet();
        executionTime.set(Flow.scheduler().getClock().currentTimeMillis());
        latch.countDown();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    // Advance time by 100ms - not enough to trigger the task
    testScheduler.advanceTime(100);
    assertEquals(0, counter.get());

    // Advance time by 500ms more - should trigger the task
    testScheduler.advanceTime(500);

    // Verify task executed
    try {
      assertTrue(latch.await(1, TimeUnit.SECONDS), "Task should have executed");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertEquals(1, counter.get());
    // Time can be either 500 or 600 depending on when the task executes
    assertTrue(executionTime.get() >= 500, "Time should be at least 500ms");
  }

  @Test
  void testAdvanceTimeSeconds() {
    // Start simulation
    testScheduler.startSimulation();

    // Initial time should be 0
    assertEquals(0, simulatedScheduler.getClock().currentTimeMillis());

    // Advance time by 1.5 seconds
    testScheduler.advanceTime(1.5);

    // Verify time advanced to 1500ms
    assertEquals(1500, simulatedScheduler.getClock().currentTimeMillis());
  }

  @Test
  void testPump() {
    // Start simulation
    testScheduler.startSimulation();

    // Schedule tasks that will be ready immediately
    AtomicBoolean task1Executed = new AtomicBoolean(false);
    AtomicBoolean task2Executed = new AtomicBoolean(false);

    Flow.scheduler().schedule(() -> {
      task1Executed.set(true);
      return null;
    });

    Flow.scheduler().schedule(() -> {
      task2Executed.set(true);
      return null;
    });

    // Pump to process the tasks
    int tasksProcessed = testScheduler.pump();

    // Verify both tasks executed
    assertTrue(tasksProcessed >= 2, "At least 2 tasks should have been processed");
    assertTrue(task1Executed.get(), "Task 1 should have executed");
    assertTrue(task2Executed.get(), "Task 2 should have executed");

    // Cannot pump when simulation is not active
    testScheduler.endSimulation();
    assertThrows(IllegalStateException.class, () -> testScheduler.pump());
  }

  @Test
  void testGetSimulatedScheduler() {
    // Verify we can access the underlying scheduler
    assertNotNull(testScheduler.getSimulatedScheduler());
    assertSame(simulatedScheduler, testScheduler.getSimulatedScheduler());
  }

  @Test
  void testCompleteFlowWithSimulation() {
    // Start simulation
    testScheduler.startSimulation();

    // Create a flow that executes tasks with delays
    AtomicInteger counter = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(3);

    // Schedule a flow that performs multiple delays
    FlowFuture<Integer> flowFuture = Flow.scheduler().schedule(() -> {
      try {
        // First delay
        Flow.scheduler().await(Flow.scheduler().scheduleDelay(0.1));
        counter.incrementAndGet();
        latch.countDown();

        // Second delay
        Flow.scheduler().await(Flow.scheduler().scheduleDelay(0.2));
        counter.incrementAndGet();
        latch.countDown();

        // Third delay
        Flow.scheduler().await(Flow.scheduler().scheduleDelay(0.3));
        counter.incrementAndGet();
        latch.countDown();

        return counter.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Advance time to trigger all delays
    testScheduler.advanceTime(0.1);  // First delay
    testScheduler.advanceTime(0.2);  // Second delay
    testScheduler.advanceTime(0.3);  // Third delay

    // Verify all stages completed
    try {
      assertTrue(latch.await(1, TimeUnit.SECONDS), "All stages should have completed");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    // Get the result
    try {
      assertEquals(3, flowFuture.getNow());
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }

    // Verify final time is the sum of all delays
    assertEquals(600, simulatedScheduler.getClock().currentTimeMillis());
  }
}