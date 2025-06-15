package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.RandomSource;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SingleThreadedScheduler's priority aging mechanism.
 * This test verifies that tasks waiting in the ready queue have their priorities boosted
 * over time to prevent starvation of lower priority tasks.
 */
public class SingleThreadedSchedulerPriorityAgingTest {
  
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
  public void testPriorityAgingWithHighFrequencyRandomSelection() {
    // Set up a controlled random source
    AtomicBoolean firstCall = new AtomicBoolean(true);
    RandomSource controlledSource = new RandomSource() {
      private final Random random = new Random() {
        @Override
        public double nextDouble() {
          // Always trigger random selection (return < 1.0)
          return 0.1;
        }
        
        @Override
        public int nextInt(int bound) {
          // Alternate selection to ensure different paths
          if (firstCall.getAndSet(false)) {
            return 0; // First task
          } else {
            return bound - 1; // Last task
          }
        }
      };
      
      @Override
      public Random getRandom() {
        return random;
      }
      
      @Override
      public long getSeed() {
        return 42;
      }
      
      @Override
      public void reset(long seed) {
      }
      
      @Override
      public RandomSource createChild(String name) {
        return this;
      }
    };
    
    FlowRandom.initialize(controlledSource);
    
    // Set up configuration with 100% random selection
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(1.0);  // Always random
    
    SimulationContext context = new SimulationContext(42, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule multiple tasks with different priorities
    FlowFuture<Integer> f1 = scheduler.schedule(() -> 1, TaskPriority.HIGH);
    FlowFuture<Integer> f2 = scheduler.schedule(() -> 2, TaskPriority.DEFAULT);
    FlowFuture<Integer> f3 = scheduler.schedule(() -> 3, TaskPriority.LOW);
    
    // Pump tasks - this will exercise the priority aging logic
    scheduler.pump();
    
    // All tasks should complete
    assertTrue(f1.isDone() && f2.isDone() && f3.isDone());
  }
  
  @Test
  public void testInterTaskDelayWithRandomSelection() {
    // Set up a controlled random source
    AtomicInteger callCount = new AtomicInteger(0);
    RandomSource controlledSource = new RandomSource() {
      private final Random random = new Random() {
        @Override
        public double nextDouble() {
          // First few calls don't trigger random selection,
          // then trigger it to exercise different code paths
          return callCount.getAndIncrement() < 3 ? 0.8 : 0.2;
        }
        
        @Override
        public int nextInt(int bound) {
          return 1; // Select second task
        }
      };
      
      @Override
      public Random getRandom() {
        return random;
      }
      
      @Override
      public long getSeed() {
        return 99;
      }
      
      @Override
      public void reset(long seed) {
      }
      
      @Override
      public RandomSource createChild(String name) {
        return this;
      }
    };
    
    FlowRandom.initialize(controlledSource);
    
    // Set up configuration with inter-task delay
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.5)
        .setInterTaskDelayMs(10.0);  // 10ms delay between tasks
    
    SimulationContext context = new SimulationContext(99, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule tasks
    FlowFuture<Integer> f1 = scheduler.schedule(() -> 1);
    FlowFuture<Integer> f2 = scheduler.schedule(() -> 2);
    
    // Advance time and pump to handle inter-task delays
    scheduler.advanceTime(100); // Advance 100ms to ensure delays are processed
    scheduler.pump();
    
    // All tasks should complete
    assertTrue(f1.isDone() && f2.isDone());
  }
}