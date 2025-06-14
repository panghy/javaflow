package io.github.panghy.javaflow.simulation;

import io.github.panghy.javaflow.test.AbstractFlowSimulationTest;
import io.github.panghy.javaflow.test.FixedSeed;
import io.github.panghy.javaflow.test.RandomSeed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the FlowRandom infrastructure.
 */
public class FlowRandomTest extends AbstractFlowSimulationTest {
  
  @Test
  @FixedSeed(12345)
  public void testDeterministicBehavior() {
    // Generate some random numbers
    List<Integer> firstRun = generateRandomNumbers();
    
    // Reset with same seed
    FlowRandom.initialize(new DeterministicRandomSource(12345));
    
    // Generate again - should be identical
    List<Integer> secondRun = generateRandomNumbers();
    
    assertEquals(firstRun, secondRun, "Same seed should produce same sequence");
  }
  
  @Test
  @RandomSeed
  public void testRandomSeed() {
    // This test runs with a random seed
    long seed = FlowRandom.getCurrentSeed();
    assertTrue(seed != 0, "Seed should be non-zero with @RandomSeed");
    
    // Generate some values to ensure it works
    Random random = FlowRandom.current();
    assertNotNull(random, "Random should not be null");
    
    // Generate a few values
    for (int i = 0; i < 10; i++) {
      random.nextInt();
      random.nextDouble();
      random.nextBoolean();
    }
  }
  
  @Test
  public void testSystemRandomFallback() {
    // Clear any existing random source
    FlowRandom.clear();
    
    // Should fall back to system random
    Random random = FlowRandom.current();
    assertNotNull(random, "Should create system random as fallback");
    assertFalse(FlowRandom.isDeterministic(), "Should not be deterministic");
  }
  
  @Test
  @FixedSeed(100)
  public void testChildRandomSources() {
    // Create child sources
    RandomSource child1 = FlowRandom.createChild("child1");
    RandomSource child2 = FlowRandom.createChild("child2");
    RandomSource child1Again = FlowRandom.createChild("child1");
    
    // Children with same name should produce same sequence
    List<Integer> child1Values = generateRandomNumbers(child1.getRandom(), 5);
    List<Integer> child1AgainValues = generateRandomNumbers(child1Again.getRandom(), 5);
    assertEquals(child1Values, child1AgainValues, "Same child name should produce same sequence");
    
    // Different children should produce different sequences
    List<Integer> child2Values = generateRandomNumbers(child2.getRandom(), 5);
    assertNotEquals(child1Values, child2Values, "Different children should produce different sequences");
  }
  
  @Test
  @FixedSeed(42)
  public void testThreadLocalIsolation() throws InterruptedException {
    // Get values from main thread
    List<Integer> mainThreadValues = generateRandomNumbers();
    
    // Get values from another thread
    List<Integer> otherThreadValues = new ArrayList<>();
    Thread otherThread = new Thread(() -> {
      // Should get system random in new thread (no initialization)
      otherThreadValues.addAll(generateRandomNumbers());
    });
    otherThread.start();
    otherThread.join();
    
    // Values should be different (other thread uses system random)
    assertNotEquals(mainThreadValues, otherThreadValues, 
        "Different threads should have different random sources");
  }
  
  private List<Integer> generateRandomNumbers() {
    return generateRandomNumbers(FlowRandom.current(), 10);
  }
  
  private List<Integer> generateRandomNumbers(Random random, int count) {
    List<Integer> values = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      values.add(random.nextInt(1000));
    }
    return values;
  }
  
  @Test
  @FixedSeed(777)
  public void testFlowRandomInitializeWithNull() {
    // Test initializing with null (should throw NPE)
    try {
      FlowRandom.initialize(null);
      // Should not reach here
      throw new AssertionError("Should throw NullPointerException");
    } catch (NullPointerException e) {
      // Expected
    }
  }
  
  @Test
  @FixedSeed(888)
  public void testFlowRandomGetCurrentSeed() {
    long seed = FlowRandom.getCurrentSeed();
    assertEquals(888, seed, "Should return current seed");
  }
  
  @Test
  @FixedSeed(999)
  public void testDeterministicRandomSourceReset() {
    DeterministicRandomSource source = new DeterministicRandomSource(999);
    
    // Generate some values
    List<Integer> firstRun = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      firstRun.add(source.getRandom().nextInt());
    }
    
    // Reset to same seed
    source.reset(999);
    
    // Generate again - should be identical
    List<Integer> secondRun = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      secondRun.add(source.getRandom().nextInt());
    }
    
    assertEquals(firstRun, secondRun, "Reset should produce same sequence");
  }
  
  @Test
  public void testSystemRandomSourceMethods() {
    SystemRandomSource source = new SystemRandomSource();
    
    // Test getSeed returns creation time (non-zero)
    assertTrue(source.getSeed() > 0, "System random should return non-zero seed (creation time)");
    
    // Test reset does nothing (no exceptions)
    source.reset(12345); // Should not throw
    
    // Test createChild returns new system random
    RandomSource child = source.createChild("test");
    assertNotNull(child, "Child should not be null");
    assertTrue(child instanceof SystemRandomSource, "Child should be SystemRandomSource");
    assertNotEquals(source, child, "Child should be different instance");
  }
  
  @Test
  @FixedSeed(1111)
  public void testSimulationContextGettersSetters() {
    SimulationContext context = new SimulationContext(1111, true);
    
    // Test getters
    assertEquals(1111, context.getSeed(), "Seed should match");
    assertTrue(context.isSimulatedContext(), "Should be simulated");
    
    // Test setCurrent and current
    SimulationContext.setCurrent(context);
    assertEquals(context, SimulationContext.current(), "Current should match");
    
    // Test static isSimulated method
    assertTrue(SimulationContext.isSimulated(), "Static isSimulated should be true");
    
    // Test clear
    SimulationContext.clear();
    assertNotEquals(context, SimulationContext.current(), "Should be cleared");
  }
  
  @Test
  public void testSimulationContextWithNonSimulated() {
    SimulationContext context = new SimulationContext(2222, false);
    assertFalse(context.isSimulatedContext(), "Should not be simulated");
    
    // Test that non-simulated context uses SystemRandomSource
    assertTrue(context.getRandomSource() instanceof SystemRandomSource, 
        "Non-simulated should use SystemRandomSource");
  }
  
  @Test
  @FixedSeed(3333)
  public void testFlowRandomCreateChildWithNull() {
    try {
      FlowRandom.createChild(null);
      // Should not reach here
      throw new AssertionError("Should throw NullPointerException");
    } catch (NullPointerException e) {
      // Expected
    }
  }
  
  @Test
  public void testSimulationContextStaticIsSimulatedWithNoContext() {
    // Clear any existing context
    SimulationContext.clear();
    
    // Test static isSimulated returns false when no context
    assertFalse(SimulationContext.isSimulated(), "Should be false when no context");
  }
  
  @Test
  public void testSimulationContextCurrentReturnsNull() {
    // Clear any existing context
    SimulationContext.clear();
    
    // Test current() returns null when no context set
    assertNull(SimulationContext.current(), "Should return null when no context");
  }
  
  @Test
  @FixedSeed(4444)
  public void testFlowRandomEdgeCases() {
    // Test getCurrentSeed when source is null
    FlowRandom.clear();
    assertEquals(0, FlowRandom.getCurrentSeed(), "Should return 0 when no source");
    
    // Test isDeterministic with system random
    FlowRandom.clear();
    FlowRandom.current(); // Forces creation of SystemRandomSource
    assertFalse(FlowRandom.isDeterministic(), "System random should not be deterministic");
  }
  
  @Test
  public void testDeterministicRandomSourceToString() {
    DeterministicRandomSource source = new DeterministicRandomSource(5555);
    String str = source.toString();
    assertTrue(str.contains("5555"), "toString should contain seed");
    assertTrue(str.contains("DeterministicRandomSource"), "toString should contain class name");
  }
}