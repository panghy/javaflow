package io.github.panghy.javaflow.scheduler;

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
 * Tests for SingleThreadedScheduler that require carrier thread to achieve branch coverage.
 */
public class SingleThreadedSchedulerCarrierThreadCoverageTest {

  private SingleThreadedScheduler scheduler;

  @BeforeEach
  public void setUp() {
    scheduler = new SingleThreadedScheduler(true, new SimulatedClock());
    scheduler.start();
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
  public void testRandomTaskSelectionWithCarrierThread() throws Exception {
    // Set up simulation context with random selection BEFORE creating scheduler
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.8)  // 80% chance of random selection
        .setTaskExecutionLogging(true);    // Enable logging for coverage

    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Set up deterministic random that will trigger random selection
    AtomicInteger doubleCallCount = new AtomicInteger(0);
    AtomicInteger intCallCount = new AtomicInteger(0);
    
    FlowRandom.initialize(new DeterministicRandomSource(12345) {
      @Override
      public Random getRandom() {
        return new Random() {
          @Override
          public double nextDouble() {
            int count = doubleCallCount.getAndIncrement();
            // First few calls trigger random selection (< 0.8)
            if (count < 3) {
              return 0.5; // Less than 0.8, triggers random selection
            }
            return 0.9; // Greater than 0.8, uses priority
          }

          @Override
          public int nextInt(int bound) {
            int count = intCallCount.getAndIncrement();
            // Select different tasks each time
            return count % bound;
          }
        };
      }
    });
    
    // Use a latch to coordinate test completion
    CountDownLatch completionLatch = new CountDownLatch(5);
    List<Integer> executionOrder = new ArrayList<>();

    // Schedule multiple tasks with different priorities
    for (int i = 0; i < 5; i++) {
      final int taskId = i;
      scheduler.schedule(() -> {
        synchronized (executionOrder) {
          executionOrder.add(taskId);
        }
        completionLatch.countDown();
        return taskId;
      }, (i % 3) * 50); // Vary priorities: 0, 50, 100, 0, 50
    }

    // Wait for all tasks to complete
    assertTrue(completionLatch.await(2, TimeUnit.SECONDS), "Tasks should complete");

    // Verify all tasks executed
    assertEquals(5, executionOrder.size());
  }

  @Test 
  @Timeout(5)
  public void testRandomTaskSelectionWithZeroProbability() throws Exception {
    // Set up config with 0% random selection but logging enabled
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.0)  // 0% chance - never random
        .setTaskExecutionLogging(true);    // Enable logging

    SimulationContext context = new SimulationContext(12345, true, config);
    
    CountDownLatch completionLatch = new CountDownLatch(3);
    List<Integer> executionOrder = new ArrayList<>();

    // Schedule tasks with different priorities
    scheduler.schedule(() -> {
      SimulationContext.setCurrent(context);
      synchronized (executionOrder) {
        executionOrder.add(1);
      }
      completionLatch.countDown();
      return "low";
    }, 100); // Low priority

    scheduler.schedule(() -> {
      SimulationContext.setCurrent(context);
      synchronized (executionOrder) {
        executionOrder.add(2);
      }
      completionLatch.countDown();
      return "high";
    }, 10); // High priority

    scheduler.schedule(() -> {
      SimulationContext.setCurrent(context);
      synchronized (executionOrder) {
        executionOrder.add(3);
      }
      completionLatch.countDown();
      return "medium";
    }, 50); // Medium priority

    // Wait for completion
    assertTrue(completionLatch.await(2, TimeUnit.SECONDS), "Tasks should complete");

    // Verify all tasks completed
    assertEquals(3, executionOrder.size());
  }
}