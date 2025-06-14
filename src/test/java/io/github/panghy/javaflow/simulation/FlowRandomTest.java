package io.github.panghy.javaflow.simulation;

import io.github.panghy.javaflow.test.AbstractFlowSimulationTest;
import io.github.panghy.javaflow.test.FixedSeed;
import io.github.panghy.javaflow.test.RandomSeed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

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
}