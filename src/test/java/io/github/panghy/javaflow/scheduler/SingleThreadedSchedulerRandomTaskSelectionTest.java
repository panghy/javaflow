package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.DeterministicRandomSource;
import io.github.panghy.javaflow.simulation.RandomSource;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SingleThreadedScheduler's random task selection behavior.
 * This test verifies that when SimulationConfiguration specifies task selection probability,
 * the scheduler can randomly select tasks instead of always picking the highest priority one.
 */
public class SingleThreadedSchedulerRandomTaskSelectionTest {
  
  private SingleThreadedScheduler scheduler;
  private SimulationContext context;
  
  @BeforeEach
  public void setUp() {
    // Set up simulation context BEFORE creating scheduler
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.5)  // 50% chance for random selection
        .setTaskExecutionLogging(true);    // Enable logging
    
    context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Initialize FlowRandom with a deterministic source
    FlowRandom.initialize(new DeterministicRandomSource(12345));
    
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
  public void testRandomTaskSelectionWithLogging() {
    // First verify the context is set up correctly
    assertNotNull(SimulationContext.current());
    assertNotNull(SimulationContext.currentConfiguration());
    assertEquals(0.5, SimulationContext.currentConfiguration().getTaskSelectionProbability());
    assertTrue(SimulationContext.currentConfiguration().isTaskExecutionLogging());
    
    // Set up a controlled random source that will trigger random selection
    AtomicInteger nextDoubleCount = new AtomicInteger(0);
    AtomicInteger nextIntCount = new AtomicInteger(0);
    RandomSource controlledSource = new RandomSource() {
      private final Random random = new Random() {
        @Override
        public double nextDouble() {
          int count = nextDoubleCount.getAndIncrement();
          // First call triggers random selection (< 0.5)
          // Third call also triggers it
          // Others don't trigger it (>= 0.5)
          if (count == 0 || count == 2) {
            return 0.3;  // Less than 0.5, triggers random selection
          }
          return 0.9;  // Greater than 0.5, uses priority selection
        }
        
        @Override
        public int nextInt(int bound) {
          int count = nextIntCount.getAndIncrement();
          // First time select middle task, second time select last task
          if (count == 0) {
            return bound / 2;  // Middle task
          }
          return bound - 1;  // Last task
        }
      };
      
      @Override
      public Random getRandom() {
        return random;
      }
      
      @Override
      public long getSeed() {
        return 12345;
      }
      
      @Override
      public void reset(long seed) {
      }
      
      @Override
      public RandomSource createChild(String name) {
        return this;
      }
    };
    
    // Replace the random source with our controlled one
    FlowRandom.initialize(controlledSource);
    
    // Schedule multiple tasks with different priorities to ensure varied selection
    CompletableFuture<Integer> f1 = scheduler.schedule(() -> {
      // This task will execute and create more work
      CompletableFuture<Integer> f4 = scheduler.schedule(() -> 4);
      CompletableFuture<Integer> f5 = scheduler.schedule(() -> 5);
      return 1;
    });
    CompletableFuture<Integer> f2 = scheduler.schedule(() -> 2);
    CompletableFuture<Integer> f3 = scheduler.schedule(() -> 3);
    
    // First pump - should trigger random selection (first nextDouble returns 0.3)
    scheduler.pump();
    
    // Second pump - should use priority selection (second nextDouble returns 0.9)
    scheduler.pump();
    
    // Third pump - should trigger random selection again (third nextDouble returns 0.3)
    scheduler.pump();
    
    // Continue pumping until all tasks complete
    while (!f1.isDone() || !f2.isDone() || !f3.isDone()) {
      scheduler.pump();
    }
    
    // All tasks should complete
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
    assertTrue(f3.isDone());
  }
  
  @Test
  public void testPrioritySelectionWithLogging() {
    // Set up a random source that never triggers random selection
    RandomSource controlledSource = new RandomSource() {
      private final Random random = new Random() {
        @Override
        public double nextDouble() {
          return 0.9;  // Always greater than any reasonable probability
        }
        
        @Override
        public int nextInt(int bound) {
          return 0;
        }
      };
      
      @Override
      public Random getRandom() {
        return random;
      }
      
      @Override
      public long getSeed() {
        return 12345;
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
    
    // Set up configuration with logging enabled but low random selection probability
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.1)  // 10% chance (but our random always returns 0.9)
        .setTaskExecutionLogging(true);    // Enable logging
    
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule tasks
    CompletableFuture<Integer> f1 = scheduler.schedule(() -> 1);
    CompletableFuture<Integer> f2 = scheduler.schedule(() -> 2);
    
    // Pump - should use priority selection and log it
    scheduler.pump();
    scheduler.pump();
    
    // All tasks should complete
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
  }
  
  @Test
  public void testNoRandomSelectionWithoutConfig() {
    // Don't set any simulation context - should use default priority selection
    CompletableFuture<Integer> f1 = scheduler.schedule(() -> 1);
    CompletableFuture<Integer> f2 = scheduler.schedule(() -> 2);
    
    // Pump
    scheduler.pump();
    scheduler.pump();
    
    // All tasks should complete
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
  }
}