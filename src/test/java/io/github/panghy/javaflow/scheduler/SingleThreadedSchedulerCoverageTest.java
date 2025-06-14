package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.simulation.DeterministicRandomSource;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage tests for SingleThreadedScheduler simulation features.
 * This test class focuses on covering edge cases and specific branches.
 */
public class SingleThreadedSchedulerCoverageTest {
  
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
  public void testSelectNextTaskWithNullConfiguration() {
    // Clear any existing context to ensure config is null
    SimulationContext.clear();
    
    // Schedule a task
    FlowFuture<String> future = scheduler.schedule(() -> "test");
    
    // Pump to execute - should use default selection
    while (!future.isDone()) {
      scheduler.pump();
    }
    
    assertTrue(future.isDone());
  }
  
  @Test
  public void testRandomSelectionWithLoggingDisabled() {
    // Set up config with random selection but no logging
    FlowRandom.initialize(new DeterministicRandomSource(1234));
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(1.0) // Always random
        .setTaskExecutionLogging(false); // No logging
    SimulationContext context = new SimulationContext(1234, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule multiple tasks
    FlowFuture<Void> f1 = scheduler.schedule(() -> null);
    FlowFuture<Void> f2 = scheduler.schedule(() -> null);
    
    // Pump to execute all
    while (!f1.isDone() || !f2.isDone()) {
      scheduler.pump();
    }
    
    assertTrue(f1.isDone());
    assertTrue(f2.isDone());
  }
  
  @Test
  public void testDefaultSelectionWithLogging() {
    // Set up config with no random selection but with logging
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.0) // Never random
        .setTaskExecutionLogging(true); // Enable logging
    SimulationContext context = new SimulationContext(5678, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule a task
    FlowFuture<Void> future = scheduler.schedule(() -> null);
    
    // Pump to execute
    while (!future.isDone()) {
      scheduler.pump();
    }
    
    assertTrue(future.isDone());
  }
  
  @Test
  public void testInterTaskDelayWithInterruption() throws InterruptedException {
    // Set up config with inter-task delay
    SimulationConfiguration config = new SimulationConfiguration()
        .setInterTaskDelayMs(50); // 50ms delay
    SimulationContext context = new SimulationContext(9999, true, config);
    SimulationContext.setCurrent(context);
    
    AtomicBoolean taskStarted = new AtomicBoolean(false);
    AtomicBoolean interruptHandled = new AtomicBoolean(false);
    CountDownLatch latch = new CountDownLatch(1);
    
    // Create a separate thread to run the scheduler
    Thread schedulerThread = new Thread(() -> {
      try {
        // Schedule a task
        scheduler.schedule(() -> {
          taskStarted.set(true);
          return null;
        });
        
        // Pump - this should trigger inter-task delay
        scheduler.pump();
        
        // If we get here without interruption, pump succeeded
        latch.countDown();
      } catch (Exception e) {
        // Unexpected exception
        e.printStackTrace();
      }
    });
    
    schedulerThread.start();
    
    // Give thread time to start and hit the delay
    Thread.sleep(10);
    
    // Interrupt the thread during delay
    schedulerThread.interrupt();
    
    // Wait for thread to finish
    schedulerThread.join(1000);
    
    // The task might or might not have started depending on timing
    // But the thread should have handled the interrupt properly
    assertTrue(!schedulerThread.isAlive(), "Thread should have terminated");
  }
  
  @Test
  public void testRandomSelectionWithSpecificSeed() {
    // Use a seed that will generate a value less than 0.5
    FlowRandom.initialize(new DeterministicRandomSource(42));
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.5) // 50% chance
        .setTaskExecutionLogging(true); // Enable all logging
    SimulationContext context = new SimulationContext(42, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule multiple tasks to increase chance of hitting random branch
    for (int i = 0; i < 5; i++) {
      scheduler.schedule(() -> null);
    }
    
    // Pump multiple times
    int pumps = 0;
    while (pumps < 10 && scheduler.pump() > 0) {
      pumps++;
    }
    
    // At least some pumps should have executed
    assertTrue(pumps > 0);
  }
  
  @Test
  public void testEmptyReadyTasks() {
    // This tests the early return in selectNextTask when readyTasks is empty
    // Just pump an empty scheduler
    int pumped = scheduler.pump();
    assertTrue(pumped == 0, "Should pump 0 tasks from empty scheduler");
  }
}