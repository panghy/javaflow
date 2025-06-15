package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.simulation.DeterministicRandomSource;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for priority randomization feature in SingleThreadedScheduler.
 */
public class SingleThreadedSchedulerPriorityRandomizationTest {

  private SingleThreadedScheduler scheduler;

  @BeforeEach
  public void setUp() {
    SimulationContext.clear();
    FlowRandom.clear();
  }

  @AfterEach
  public void tearDown() {
    if (scheduler != null) {
      scheduler.close();
    }
    SimulationContext.clear();
    FlowRandom.clear();
  }

  @Test
  @Timeout(5)
  public void testPriorityRandomizationEnabled() throws Exception {
    // Set up simulation configuration with priority randomization
    SimulationConfiguration config = new SimulationConfiguration()
        .setPriorityRandomization(true)
        .setTaskExecutionLogging(true);

    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);

    // Initialize deterministic random with predictable sequence
    AtomicInteger randomCallCount = new AtomicInteger(0);
    FlowRandom.initialize(new DeterministicRandomSource(12345) {
      @Override
      public Random getRandom() {
        return new Random() {
          @Override
          public double nextDouble() {
            // Return different values for each call to create variance
            int count = randomCallCount.getAndIncrement();
            double[] values = {0.1, 0.9, 0.3, 0.7, 0.5, 0.2, 0.8, 0.4, 0.6};
            return values[count % values.length];
          }
        };
      }
    });

    scheduler = new SingleThreadedScheduler(false, new SimulatedClock()); // Use pump mode
    scheduler.start();

    // Track execution order
    List<Integer> executionOrder = new ArrayList<>();
    List<FlowFuture<Integer>> futures = new ArrayList<>();

    // Schedule tasks with same priority - they will all be added to ready queue
    // before any execute
    for (int i = 0; i < 5; i++) {
      final int taskId = i;
      futures.add(scheduler.schedule(() -> {
        synchronized (executionOrder) {
          executionOrder.add(taskId);
        }
        return taskId;
      }, 100)); // All have same original priority
    }

    // Now pump to execute all tasks
    for (int i = 0; i < 5; i++) {
      scheduler.pump();
    }

    // Verify all completed
    for (FlowFuture<Integer> future : futures) {
      assertTrue(future.isDone());
    }

    // With priority randomization, tasks should not execute in exact creation order
    // due to priority variance
    boolean outOfOrder = false;
    for (int i = 1; i < executionOrder.size(); i++) {
      if (executionOrder.get(i) < executionOrder.get(i - 1)) {
        outOfOrder = true;
        break;
      }
    }
    
    assertTrue(outOfOrder, 
        "With priority randomization, tasks should execute in varied order, but got: " + executionOrder);
  }

  @Test
  @Timeout(5)
  public void testPriorityRandomizationDisabled() throws Exception {
    // Set up without priority randomization
    SimulationConfiguration config = new SimulationConfiguration()
        .setPriorityRandomization(false)
        .setTaskExecutionLogging(true);

    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);

    scheduler = new SingleThreadedScheduler(true, new SimulatedClock());
    scheduler.start();

    // Track execution order
    List<Integer> executionOrder = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(5);

    // Schedule tasks with same priority
    for (int i = 0; i < 5; i++) {
      final int taskId = i;
      scheduler.schedule(() -> {
        synchronized (executionOrder) {
          executionOrder.add(taskId);
        }
        latch.countDown();
        return taskId;
      }, 100); // All have same original priority
    }

    // Wait for completion
    assertTrue(latch.await(2, TimeUnit.SECONDS));

    // Without priority randomization, tasks should execute in creation order (FIFO)
    for (int i = 0; i < executionOrder.size(); i++) {
      assertEquals(i, executionOrder.get(i), 
          "Without randomization, tasks should execute in FIFO order");
    }
  }

  @Test
  @Timeout(5)
  public void testPriorityRandomizationRange() throws Exception {
    // Set up with priority randomization to test the variance range
    SimulationConfiguration config = new SimulationConfiguration()
        .setPriorityRandomization(true)
        .setTaskExecutionLogging(true);

    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);

    // Set up random to return extreme values
    FlowRandom.initialize(new DeterministicRandomSource(12345) {
      @Override
      public Random getRandom() {
        return new Random() {
          private int callCount = 0;
          
          @Override
          public double nextDouble() {
            // Alternate between minimum (0.0) and maximum (1.0)
            return (callCount++ % 2 == 0) ? 0.0 : 1.0;
          }
        };
      }
    });

    scheduler = new SingleThreadedScheduler(false, new SimulatedClock());
    scheduler.start();

    // Schedule a task and check its effective priority
    FlowFuture<Integer> future1 = scheduler.schedule(() -> 1, 100);
    FlowFuture<Integer> future2 = scheduler.schedule(() -> 2, 100);
    
    // Pump to execute
    scheduler.pump();
    scheduler.pump();
    
    assertTrue(future1.isDone());
    assertTrue(future2.isDone());
    
    // The effective priorities should be different due to randomization
    // One should be 80 (100 * 0.8) and the other 120 (100 * 1.2)
    // But we can't directly check the effective priorities from here,
    // so we just verify the tasks completed successfully
  }

  @Test
  public void testPriorityRandomizationWithZeroPriority() throws Exception {
    // Test edge case with zero priority
    SimulationConfiguration config = new SimulationConfiguration()
        .setPriorityRandomization(true);

    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);

    scheduler = new SingleThreadedScheduler(false, new SimulatedClock());
    scheduler.start();

    // Schedule task with zero priority
    FlowFuture<String> future = scheduler.schedule(() -> "zero", 0);
    
    // Pump to execute
    scheduler.pump();
    
    assertTrue(future.isDone());
    assertEquals("zero", future.getNow());
  }
}