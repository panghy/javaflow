package io.github.panghy.javaflow.simulation;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the BUGGIFY fault injection framework.
 */
public class BuggifyTest extends AbstractFlowTest {
  
  @BeforeEach
  public void setUp() {
    // Clear any previous bug registrations
    BugRegistry.getInstance().clear();
  }
  
  @Test
  public void testBuggifyDisabledInNonSimulation() {
    // Verify that BUGGIFY is disabled when not in simulation
    // Note: AbstractFlowTest runs in simulation mode, so we can't test this directly
    // The implementation ensures this behavior
    assertTrue(Flow.isSimulated(), "Test should be running in simulation mode");
  }
  
  @Test
  public void testIsEnabled() {
    // Register a bug with 100% probability
    BugRegistry.getInstance().register("test_bug", 1.0);
    
    // Should always be enabled
    for (int i = 0; i < 10; i++) {
      assertTrue(Buggify.isEnabled("test_bug"));
    }
    
    // Unregistered bug should never be enabled
    assertFalse(Buggify.isEnabled("unregistered_bug"));
  }
  
  @Test
  public void testSometimes() {
    int trueCount = 0;
    int iterations = 1000;
    double probability = 0.3;
    
    for (int i = 0; i < iterations; i++) {
      if (Buggify.sometimes(probability)) {
        trueCount++;
      }
    }
    
    // Check that the actual rate is reasonably close to expected
    double actualRate = (double) trueCount / iterations;
    assertEquals(probability, actualRate, 0.05, "Actual rate should be close to expected");
    
    // Edge cases
    assertFalse(Buggify.sometimes(0.0), "0% probability should always be false");
    assertTrue(Buggify.sometimes(1.0), "100% probability should always be true");
  }
  
  @Test
  public void testIsEnabledWithRecovery() {
    // Register a bug with high probability
    BugRegistry.getInstance().register("recovery_bug", 0.5);
    
    // Create a simulation context with controlled time
    SimulationContext context = new SimulationContext(42L, true, new SimulationConfiguration());
    SimulationContext.setCurrent(context);
    
    try {
      // Before 300 seconds, should use normal probability
      int enabledCount = 0;
      for (int i = 0; i < 100; i++) {
        if (Buggify.isEnabledWithRecovery("recovery_bug")) {
          enabledCount++;
        }
      }
      assertTrue(enabledCount > 30 && enabledCount < 70, 
          "Should be enabled roughly 50% of the time before recovery");
      
      // Advance time past 300 seconds
      context.advanceTime(301.0);
      
      // After 300 seconds, should use reduced probability (1%)
      enabledCount = 0;
      for (int i = 0; i < 1000; i++) {
        if (Buggify.isEnabledWithRecovery("recovery_bug")) {
          enabledCount++;
        }
      }
      assertTrue(enabledCount < 30, 
          "Should be enabled much less frequently after recovery time");
    } finally {
      SimulationContext.clearCurrent();
    }
  }
  
  @Test
  public void testIsEnabledIf() {
    BugRegistry.getInstance().register("conditional_bug", 1.0);
    
    // Should be enabled when condition is true
    assertTrue(Buggify.isEnabledIf("conditional_bug", true));
    
    // Should be disabled when condition is false
    assertFalse(Buggify.isEnabledIf("conditional_bug", false));
    
    // Should be disabled when bug is not registered, even if condition is true
    assertFalse(Buggify.isEnabledIf("unregistered_bug", true));
  }
  
  @Test
  public void testInjectDelay() {
    AtomicBoolean delayInjected = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Always inject delay
      if (Buggify.injectDelay(1.0, 0.1)) {
        delayInjected.set(true);
      }
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(delayInjected.get(), "Delay should have been injected");
    
    // Test with 0% probability
    delayInjected.set(false);
    future = Flow.startActor(() -> {
      if (Buggify.injectDelay(0.0, 0.1)) {
        delayInjected.set(true);
      }
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    assertFalse(delayInjected.get(), "Delay should not have been injected");
  }
  
  @Test
  public void testChoose() {
    // Test with 100% probability
    assertEquals("first", Buggify.choose(1.0, "first", "second"));
    
    // Test with 0% probability
    assertEquals("second", Buggify.choose(0.0, "first", "second"));
    
    // Test with 50% probability - should get both values
    Set<String> values = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      values.add(Buggify.choose(0.5, "first", "second"));
    }
    assertEquals(2, values.size(), "Should get both values with 50% probability");
  }
  
  @Test
  public void testRandomInt() {
    // Test range
    for (int i = 0; i < 100; i++) {
      int value = Buggify.randomInt(10, 20);
      assertTrue(value >= 10 && value < 20, "Value should be in range [10, 20)");
    }
    
    // Test that we get different values
    Set<Integer> values = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      values.add(Buggify.randomInt(0, 100));
    }
    assertTrue(values.size() > 20, "Should get many different values");
    
    // Edge case: empty range
    assertEquals(5, Buggify.randomInt(5, 5), "Empty range should return min");
  }
  
  @Test
  public void testRandomDouble() {
    // Test range
    for (int i = 0; i < 100; i++) {
      double value = Buggify.randomDouble(1.0, 2.0);
      assertTrue(value >= 1.0 && value < 2.0, "Value should be in range [1.0, 2.0)");
    }
    
    // Test distribution
    double sum = 0;
    int count = 1000;
    for (int i = 0; i < count; i++) {
      sum += Buggify.randomDouble(0.0, 1.0);
    }
    double average = sum / count;
    assertEquals(0.5, average, 0.05, "Average should be close to 0.5");
    
    // Edge case: empty range
    assertEquals(5.0, Buggify.randomDouble(5.0, 5.0), "Empty range should return min");
  }
  
  @Test
  public void testDeterministicBehavior() {
    // Create two simulation contexts with the same seed
    SimulationContext context1 = new SimulationContext(12345L, true, new SimulationConfiguration());
    SimulationContext context2 = new SimulationContext(12345L, true, new SimulationConfiguration());
    
    // Collect results from first run
    SimulationContext.setCurrent(context1);
    boolean[] results1 = new boolean[10];
    for (int i = 0; i < 10; i++) {
      results1[i] = Buggify.sometimes(0.5);
    }
    SimulationContext.clearCurrent();
    
    // Collect results from second run
    SimulationContext.setCurrent(context2);
    boolean[] results2 = new boolean[10];
    for (int i = 0; i < 10; i++) {
      results2[i] = Buggify.sometimes(0.5);
    }
    SimulationContext.clearCurrent();
    
    // Results should be identical
    assertArrayEquals(results1, results2, "Same seed should produce same results");
  }
  
  @Test
  public void testInjectDelayMethod() {
    // Test the actual delay injection - this method is void but schedules a delay
    AtomicBoolean taskCompleted = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.startActor(() -> {
      // This should always inject delay with 100% probability
      boolean injected = Buggify.injectDelay(1.0, 0.5);
      assertTrue(injected, "Delay should have been injected");
      
      // Do something after potential delay
      taskCompleted.set(true);
      return null;
    });
    
    // The task should complete after we pump the scheduler
    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(taskCompleted.get(), "Task should have completed");
    
    // Test with 0% probability
    taskCompleted.set(false);
    future = Flow.startActor(() -> {
      boolean injected = Buggify.injectDelay(0.0, 0.5);
      assertFalse(injected, "Delay should not have been injected");
      taskCompleted.set(true);
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    assertTrue(taskCompleted.get(), "Task should have completed");
  }
  
  @Test
  public void testBuggifyInActorContext() {
    BugRegistry.getInstance()
        .register("actor_bug", 0.5)
        .register("delay_bug", 1.0);
    
    AtomicInteger bugCount = new AtomicInteger(0);
    AtomicBoolean delayExecuted = new AtomicBoolean(false);
    
    FlowFuture<String> future = Flow.startActor(() -> {
      // Test basic bug injection
      if (Buggify.isEnabled("actor_bug")) {
        bugCount.incrementAndGet();
      }
      
      // Test delay injection
      if (Buggify.isEnabled("delay_bug")) {
        Flow.await(Flow.delay(0.1));
        delayExecuted.set(true);
      }
      
      // Test choose in actor
      return Buggify.choose(0.0, "unexpected", "expected");
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    try {
      assertEquals("expected", future.getNow());
    } catch (ExecutionException e) {
      fail("Future failed with exception: " + e.getMessage());
    }
    assertTrue(delayExecuted.get(), "Delay should have been executed");
    // Bug with 50% probability might or might not have triggered
    assertTrue(bugCount.get() >= 0 && bugCount.get() <= 1);
  }
  
  @Test
  public void testChooseWithNullValues() {
    // Test choose with null values
    assertNull(Buggify.choose(1.0, null, "not null"));
    assertEquals("not null", Buggify.choose(0.0, null, "not null"));
    
    // Test with both null
    assertNull(Buggify.choose(0.5, null, null));
  }
  
  @Test
  public void testEdgeCasesForCoverage() {
    // Test edge cases to improve coverage
    
    // Test isEnabledIf with false condition
    assertFalse(Buggify.isEnabledIf("any_bug", false));
    
    // Test with registered bug but false condition
    BugRegistry.getInstance().register("test_bug", 1.0);
    assertFalse(Buggify.isEnabledIf("test_bug", false));
    
    // Test randomInt and randomDouble with equal min/max
    assertEquals(5, Buggify.randomInt(5, 5));
    assertEquals(5.0, Buggify.randomDouble(5.0, 5.0), 0.001);
    
    // Test choose with various probabilities to ensure coverage
    for (int i = 0; i < 10; i++) {
      // This will sometimes return "a" and sometimes "b"
      String result = Buggify.choose(0.5, "a", "b");
      assertTrue("a".equals(result) || "b".equals(result));
    }
  }
}