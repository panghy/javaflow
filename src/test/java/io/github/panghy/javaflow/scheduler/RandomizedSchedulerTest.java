package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for randomized task scheduling in simulation mode.
 */
@Timeout(30)
@org.junit.jupiter.api.Disabled("TODO: Fix scheduler tests to work with test environment")
public class RandomizedSchedulerTest extends AbstractFlowTest {
  
  @BeforeEach
  public void setupSimulation() {
    // Initialize random source for tests
    FlowRandom.initialize(new DeterministicRandomSource(12345));
  }
  
  @AfterEach
  public void tearDownSimulation() {
    // Clear simulation context
    SimulationContext.clear();
    FlowRandom.clear();
  }
  
  @Test
  public void testDeterministicScheduling() {
    // Create deterministic configuration
    SimulationConfiguration config = SimulationConfiguration.deterministic();
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    List<Integer> executionOrder = new ArrayList<>();
    
    // Create tasks with different priorities
    // Note: In Flow, higher numeric priority = higher actual priority
    FlowFuture<Void> task1 = Flow.startActor(() -> {
      executionOrder.add(1);
      return null;
    }, 100); // Lower priority
    
    FlowFuture<Void> task2 = Flow.startActor(() -> {
      executionOrder.add(2);
      return null;
    }, 200); // Higher priority
    
    FlowFuture<Void> task3 = Flow.startActor(() -> {
      executionOrder.add(3);
      return null;
    }, 150); // Medium priority
    
    // Let all tasks get scheduled before pumping
    // This ensures they're all in the ready queue
    pump();
    
    // Now pump remaining work
    pumpAndAdvanceTimeUntilDone(task1, task2, task3);
    
    // In deterministic mode, tasks should execute in priority order: 2, 3, 1
    assertEquals(List.of(2, 3, 1), executionOrder);
  }
  
  @Test
  public void testRandomizedScheduling() {
    // Create configuration with high randomization
    SimulationConfiguration config = SimulationConfiguration.chaos()
        .setTaskSelectionProbability(0.8); // 80% chance of random selection
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    AtomicInteger randomSelections = new AtomicInteger(0);
    List<Integer> executionOrder = new ArrayList<>();
    
    // Create many tasks to observe randomization
    List<FlowFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      final int taskId = i;
      futures.add(Flow.startActor(() -> {
        executionOrder.add(taskId);
        return null;
      }, 100)); // All same priority
    }
    
    // Pump all tasks
    pumpAndAdvanceTimeUntilDone(futures.toArray(new FlowFuture[0]));
    
    // With randomization, execution order should not be strictly sequential
    boolean isRandomized = false;
    for (int i = 0; i < executionOrder.size() - 1; i++) {
      if (executionOrder.get(i) > executionOrder.get(i + 1)) {
        isRandomized = true;
        break;
      }
    }
    
    assertTrue(isRandomized, "Execution order should show randomization: " + executionOrder);
  }
  
  @Test
  public void testTaskSelectionDeterminism() {
    // Run the same test twice with the same seed to verify determinism
    List<Integer> firstRun = runSchedulingTest(99999);
    List<Integer> secondRun = runSchedulingTest(99999);
    
    assertEquals(firstRun, secondRun, "Same seed should produce same execution order");
  }
  
  @Test
  public void testDifferentSeedsProduceDifferentOrders() {
    // Run with different seeds
    List<Integer> run1 = runSchedulingTest(11111);
    List<Integer> run2 = runSchedulingTest(22222);
    List<Integer> run3 = runSchedulingTest(33333);
    
    // Collect unique execution orders
    Set<List<Integer>> uniqueOrders = new HashSet<>();
    uniqueOrders.add(run1);
    uniqueOrders.add(run2);
    uniqueOrders.add(run3);
    
    // With different seeds, we should see at least 2 different execution orders
    assertTrue(uniqueOrders.size() >= 2, 
        "Different seeds should produce different execution orders");
  }
  
  @Test
  public void testMixedPriorityWithRandomization() {
    // Create configuration with moderate randomization
    SimulationConfiguration config = SimulationConfiguration.chaos()
        .setTaskSelectionProbability(0.5); // 50% chance of random selection
    SimulationContext context = new SimulationContext(77777, true, config);
    SimulationContext.setCurrent(context);
    
    List<String> executionOrder = new ArrayList<>();
    
    // Create tasks with very different priorities
    FlowFuture<Void> highPriority = Flow.startActor(() -> {
      executionOrder.add("HIGH");
      return null;
    }, 1000); // Very high priority
    
    FlowFuture<Void> lowPriority = Flow.startActor(() -> {
      executionOrder.add("LOW");
      return null;
    }, 10); // Very low priority
    
    // Create several medium priority tasks
    List<FlowFuture<Void>> mediumTasks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final int taskId = i;
      mediumTasks.add(Flow.startActor(() -> {
        executionOrder.add("MEDIUM-" + taskId);
        return null;
      }, 500)); // Medium priority
    }
    
    // Collect all futures
    List<FlowFuture<Void>> allFutures = new ArrayList<>();
    allFutures.add(highPriority);
    allFutures.add(lowPriority);
    allFutures.addAll(mediumTasks);
    
    // Pump all tasks
    pumpAndAdvanceTimeUntilDone(allFutures.toArray(new FlowFuture[0]));
    
    // Even with randomization, very high priority task often executes early
    int highIndex = executionOrder.indexOf("HIGH");
    int lowIndex = executionOrder.indexOf("LOW");
    
    // High priority task should generally come before low priority
    // (though randomization can occasionally override this)
    assertTrue(highIndex < executionOrder.size() / 2, 
        "High priority task should generally execute in first half");
  }
  
  @Test
  public void testInterTaskDelay() {
    // Create configuration with inter-task delays
    SimulationConfiguration config = new SimulationConfiguration()
        .setInterTaskDelayMs(10); // 10ms delay between tasks
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Test that configuration is properly set
    assertEquals(10, SimulationContext.currentConfiguration().getInterTaskDelayMs());
    
    // Actual delay testing would require real-time execution
    // which is not suitable for unit tests
  }
  
  /**
   * Helper method to run a scheduling test with a specific seed.
   */
  private List<Integer> runSchedulingTest(long seed) {
    // Clear any existing context
    SimulationContext.clear();
    FlowRandom.clear();
    
    // Set up new context with randomization
    FlowRandom.initialize(new DeterministicRandomSource(seed));
    SimulationConfiguration config = SimulationConfiguration.chaos()
        .setTaskSelectionProbability(0.7);
    SimulationContext context = new SimulationContext(seed, true, config);
    SimulationContext.setCurrent(context);
    
    List<Integer> executionOrder = new ArrayList<>();
    
    // Create tasks
    List<FlowFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      final int taskId = i;
      futures.add(Flow.startActor(() -> {
        executionOrder.add(taskId);
        return null;
      }, 100)); // All same priority
    }
    
    // Pump all tasks
    pumpAndAdvanceTimeUntilDone(futures.toArray(new FlowFuture[0]));
    
    return executionOrder;
  }
}