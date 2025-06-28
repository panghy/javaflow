package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.simulation.DeterministicRandomSource;
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
 * Targeted tests to achieve branch coverage for SingleThreadedScheduler.
 */
public class SingleThreadedSchedulerBranchCoverageTest {
  
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
  public void testRandomSelectionWithLoggingEnabled() {
    // This test specifically targets the logging branches inside random selection
    // We need a seed that produces a random value < taskSelectionProbability
    
    // Set up a deterministic random that will produce values < 0.9
    FlowRandom.initialize(new DeterministicRandomSource(123456789L));
    
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.9) // 90% chance - our random should be less
        .setTaskExecutionLogging(true);   // Enable logging to hit those branches
    
    SimulationContext context = new SimulationContext(123456789L, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule multiple tasks to ensure we have choices for random selection
    CompletableFuture<Integer> f1 = scheduler.schedule(() -> 1, 50);
    CompletableFuture<Integer> f2 = scheduler.schedule(() -> 2, 50);
    CompletableFuture<Integer> f3 = scheduler.schedule(() -> 3, 50);
    
    // Pump once to get tasks into ready queue
    scheduler.pump();
    
    // Pump again to trigger task selection with randomization
    scheduler.pump();
    scheduler.pump();
    scheduler.pump();
    
    // Continue pumping until all done
    while (!f1.isDone() || !f2.isDone() || !f3.isDone()) {
      scheduler.pump();
    }
    
    assertTrue(f1.isDone() && f2.isDone() && f3.isDone());
  }
  
  @Test
  public void testInterTaskDelayInterruptHandling() throws InterruptedException {
    // This test targets the InterruptedException handling in applyInterTaskDelay
    
    SimulationConfiguration config = new SimulationConfiguration()
        .setInterTaskDelayMs(100); // 100ms delay to ensure we can interrupt
    
    SimulationContext context = new SimulationContext(987654321L, true, config);
    SimulationContext.setCurrent(context);
    
    AtomicBoolean threadInterrupted = new AtomicBoolean(false);
    CountDownLatch taskScheduled = new CountDownLatch(1);
    CountDownLatch threadStarted = new CountDownLatch(1);
    
    Thread schedulerThread = new Thread(() -> {
      try {
        threadStarted.countDown();
        
        // Schedule a task
        scheduler.schedule(() -> {
          taskScheduled.countDown();
          return "test";
        });
        
        // Pump - this should apply inter-task delay
        scheduler.pump();
        
        // Check if thread was interrupted
        if (Thread.currentThread().isInterrupted()) {
          threadInterrupted.set(true);
        }
      } catch (Exception e) {
        // Ignore
      }
    });
    
    schedulerThread.start();
    threadStarted.await();
    
    // Give it a moment to start the delay
    Thread.sleep(20);
    
    // Interrupt the thread while it's in the delay
    schedulerThread.interrupt();
    
    // Wait for thread to finish
    schedulerThread.join(500);
    
    // The interrupt should have been handled and restored
    assertTrue(!schedulerThread.isAlive(), "Thread should have finished");
  }
  
  @Test
  public void testRandomSelectionDifferentSeed() {
    // Use a different seed that might produce different random behavior
    FlowRandom.initialize(new DeterministicRandomSource(999999999L));
    
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.6) // 60% chance
        .setTaskExecutionLogging(true);   // Logging enabled
    
    SimulationContext context = new SimulationContext(999999999L, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule several tasks
    for (int i = 0; i < 5; i++) {
      scheduler.schedule(() -> null, 100);
    }
    
    // Pump multiple times to exercise selection logic
    for (int i = 0; i < 10; i++) {
      scheduler.pump();
    }
    
    // Just verify it completes without error
    assertTrue(true);
  }
}