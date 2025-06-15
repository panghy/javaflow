package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.simulation.DeterministicRandomSource;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the selectNextTask method to ensure branch coverage.
 */
public class SingleThreadedSchedulerSelectNextTaskTest {
  
  private SingleThreadedScheduler scheduler;
  
  @BeforeEach
  public void setUp() {
    scheduler = new SingleThreadedScheduler(false, new SimulatedClock());
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
  public void testSelectNextTaskWithEmptyQueue() throws Exception {
    // Use reflection to test the private selectNextTask method
    Method selectNextTask = SingleThreadedScheduler.class.getDeclaredMethod("selectNextTask");
    selectNextTask.setAccessible(true);
    
    // Should return null when queue is empty
    Object result = selectNextTask.invoke(scheduler);
    assertNull(result);
  }
  
  @Test
  public void testSelectNextTaskWithRandomSelectionEnabled() {
    // Set up simulation context with random selection
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.8)  // 80% chance
        .setTaskExecutionLogging(true);    // Enable logging
    
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Set up random source that triggers random selection
    AtomicInteger doubleCallCount = new AtomicInteger(0);
    FlowRandom.initialize(new DeterministicRandomSource(12345) {
      @Override
      public Random getRandom() {
        return new Random() {
          @Override
          public double nextDouble() {
            int count = doubleCallCount.getAndIncrement();
            // First few calls trigger random selection (< 0.8)
            if (count < 3) {
              return 0.5; // Less than 0.8
            }
            return 0.9; // Greater than 0.8
          }
          
          @Override
          public int nextInt(int bound) {
            // Select middle task
            return bound / 2;
          }
        };
      }
    });
    
    // Schedule multiple tasks
    List<FlowFuture<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final int taskId = i;
      futures.add(scheduler.schedule(() -> taskId));
    }
    
    // Pump several times to trigger different selection paths
    for (int i = 0; i < 8; i++) {
      scheduler.pump();
    }
    
    // Verify all tasks completed
    assertTrue(futures.stream().allMatch(FlowFuture::isDone));
  }
  
  @Test
  public void testSelectNextTaskWithNoRandomSelection() {
    // Set up simulation context without random selection (null config)
    SimulationContext context = new SimulationContext(12345, true, null);
    SimulationContext.setCurrent(context);
    
    // Schedule tasks
    FlowFuture<Integer> f1 = scheduler.schedule(() -> 1);
    FlowFuture<Integer> f2 = scheduler.schedule(() -> 2);
    
    // Pump
    scheduler.pump();
    scheduler.pump();
    
    // Both should complete
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
  }
  
  @Test
  public void testSelectNextTaskWithLoggingOnlyNonRandom() {
    // Set up config with logging but 0% random selection
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.0)  // 0% chance - never random
        .setTaskExecutionLogging(true);    // Enable logging
    
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Set up random that would trigger if threshold was > 0
    FlowRandom.initialize(new DeterministicRandomSource(12345) {
      @Override
      public Random getRandom() {
        return new Random() {
          @Override
          public double nextDouble() {
            return 0.1; // Would trigger if threshold > 0.1
          }
        };
      }
    });
    
    // Schedule tasks
    FlowFuture<String> f1 = scheduler.schedule(() -> "task1");
    FlowFuture<String> f2 = scheduler.schedule(() -> "task2");
    
    // Pump
    scheduler.pump();
    scheduler.pump();
    
    // Both should complete
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
  }
  
  // Inter-task delay is covered by the carrier thread tests, skip for pump-based tests
  
  @Test 
  public void testNegativeDelayValidation() {
    assertThrows(IllegalArgumentException.class, () -> {
      scheduler.scheduleDelay(-1.0);
    });
  }
  
  @Test
  public void testUpdateEffectivePriorities() throws Exception {
    // Use reflection to access the updateEffectivePriorities method
    Method updateMethod = SingleThreadedScheduler.class.getDeclaredMethod("updateEffectivePriorities");
    updateMethod.setAccessible(true);
    
    // Schedule some tasks
    scheduler.schedule(() -> "task1", 50);
    scheduler.schedule(() -> "task2", 100);
    
    // Advance time to trigger priority aging
    if (scheduler.getClock().isSimulated()) {
      ((SimulatedClock) scheduler.getClock()).advanceTime(200);
    }
    
    // Call update method
    updateMethod.invoke(scheduler);
    
    // Pump to execute
    scheduler.pump();
    scheduler.pump();
  }
}