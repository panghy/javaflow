package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.simulation.DeterministicRandomSource;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SingleThreadedScheduler simulation features.
 * These tests use a real scheduler instead of the test scheduler
 * to properly test the randomization logic.
 */
public class SingleThreadedSchedulerSimulationTest {
  
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
  public void testSelectNextTaskWithNoConfiguration() {
    // No simulation context - should use default behavior
    List<Integer> executionOrder = new ArrayList<>();
    
    // Schedule tasks
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      final int taskId = i;
      futures.add(scheduler.schedule(() -> {
        executionOrder.add(taskId);
        return null;
      }, i * 10));
    }
    
    // Pump until all complete
    while (futures.stream().anyMatch(f -> !f.isDone())) {
      scheduler.pump();
    }
    
    // All tasks should execute
    assertEquals(3, executionOrder.size());
    assertTrue(executionOrder.contains(1));
    assertTrue(executionOrder.contains(2));
    assertTrue(executionOrder.contains(3));
  }
  
  @Test
  public void testSelectNextTaskWithDeterministicConfiguration() {
    // Set up deterministic configuration
    SimulationConfiguration config = SimulationConfiguration.deterministic();
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    FlowRandom.initialize(new DeterministicRandomSource(12345));
    
    List<Integer> executionOrder = new ArrayList<>();
    
    // Schedule tasks
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final int taskId = i;
      futures.add(scheduler.schedule(() -> {
        executionOrder.add(taskId);
        return null;
      }, 50)); // All same priority
    }
    
    // Pump until all complete
    while (futures.stream().anyMatch(f -> !f.isDone())) {
      scheduler.pump();
    }
    
    // With deterministic config and same priority, should be in order
    assertEquals(List.of(0, 1, 2, 3, 4), executionOrder);
  }
  
  @Test
  public void testSelectNextTaskWithRandomization() {
    // Set up configuration with some randomization
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.5);
    SimulationContext context = new SimulationContext(99999, true, config);
    SimulationContext.setCurrent(context);
    FlowRandom.initialize(new DeterministicRandomSource(99999));
    
    // This just tests that the code path is covered
    // Actual randomization behavior is hard to test deterministically
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      futures.add(scheduler.schedule(() -> null, 50));
    }
    
    // Pump until all complete
    while (futures.stream().anyMatch(f -> !f.isDone())) {
      scheduler.pump();
    }
    
    // All should complete
    assertTrue(futures.stream().allMatch(CompletableFuture::isDone));
  }
  
  @Test
  public void testSelectNextTaskWithEmptyQueue() {
    // Test edge case of empty queue
    assertEquals(0, scheduler.pump());
  }
  
  @Test
  public void testApplyInterTaskDelay() {
    // Set up configuration with logging but no delay
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskExecutionLogging(true)
        .setInterTaskDelayMs(0); // No delay
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule a simple task
    CompletableFuture<String> future = scheduler.schedule(() -> "test", 50);
    
    // Pump to execute
    while (!future.isDone()) {
      scheduler.pump();
    }
    
    try {
      assertEquals("test", future.getNow(null));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  @Test
  public void testCurrentConfigurationNotNull() {
    // Test that currentConfiguration returns non-null when set
    SimulationConfiguration config = SimulationConfiguration.chaos();
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    assertNotNull(SimulationContext.currentConfiguration());
    assertEquals(config, SimulationContext.currentConfiguration());
  }
  
  @Test
  public void testInterTaskDelayWithInterrupt() {
    // Set up configuration with delay
    SimulationConfiguration config = new SimulationConfiguration()
        .setInterTaskDelayMs(100); // 100ms delay
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule a task on a separate thread that will be interrupted
    Thread testThread = new Thread(() -> {
      try {
        CompletableFuture<Void> future = scheduler.schedule(() -> null);
        // Interrupt during pump
        Thread.currentThread().interrupt();
        scheduler.pump();
      } catch (Exception e) {
        // Expected
      }
    });
    
    testThread.start();
    try {
      testThread.join(1000); // Wait up to 1 second
    } catch (InterruptedException e) {
      // Ignore
    }
  }
  
  @Test
  public void testTaskLoggingEnabled() {
    // Test with task execution logging enabled
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskExecutionLogging(true)
        .setTaskSelectionProbability(0.0); // No randomization
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    FlowRandom.initialize(new DeterministicRandomSource(12345));
    
    // Schedule tasks with different priorities to trigger logging
    CompletableFuture<Void> high = scheduler.schedule(() -> null, 100);
    CompletableFuture<Void> low = scheduler.schedule(() -> null, 10);
    
    // Pump until complete
    while (!high.isDone() || !low.isDone()) {
      scheduler.pump();
    }
    
    assertTrue(high.isDone());
    assertTrue(low.isDone());
  }
  
  @Test
  public void testRandomTaskSelectionTriggered() {
    // Set up configuration with very high randomization probability
    // Use a specific seed that will trigger random selection
    FlowRandom.initialize(new DeterministicRandomSource(7777));
    SimulationConfiguration config = new SimulationConfiguration()
        .setTaskSelectionProbability(0.99) // 99% chance of random selection
        .setTaskExecutionLogging(true);
    SimulationContext context = new SimulationContext(7777, true, config);
    SimulationContext.setCurrent(context);
    
    // Schedule multiple tasks to ensure randomization path is taken
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      futures.add(scheduler.schedule(() -> null, 50));
    }
    
    // Add a task, pump once to move it to ready queue
    scheduler.pump();
    
    // Now pump to execute - this should trigger random selection
    while (futures.stream().anyMatch(f -> !f.isDone())) {
      scheduler.pump();
    }
    
    // All tasks should complete
    assertTrue(futures.stream().allMatch(CompletableFuture::isDone));
  }
}