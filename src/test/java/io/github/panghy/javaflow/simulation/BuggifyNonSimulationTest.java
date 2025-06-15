package io.github.panghy.javaflow.simulation;

import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Buggify behavior outside of simulation mode.
 * This test class does NOT extend AbstractFlowTest to ensure we're testing
 * the non-simulation code paths.
 */
public class BuggifyNonSimulationTest {
  
  @Test
  public void testBuggifyInNonSimulationMode() {
    // Ensure we're not in simulation mode
    assertFalse(Flow.isSimulated(), "This test must run outside simulation mode");
    
    // All Buggify methods should return false/default values
    assertFalse(Buggify.isEnabled("any_bug"));
    assertFalse(Buggify.sometimes(0.5));
    assertFalse(Buggify.sometimes(1.0)); // Even 100% probability returns false
    assertFalse(Buggify.isEnabledWithRecovery("any_bug"));
    assertFalse(Buggify.isEnabledIf("any_bug", true));
    
    // Choose should return the "false" option
    assertEquals("default", Buggify.choose(0.5, "option", "default"));
    assertEquals("default", Buggify.choose(1.0, "option", "default"));
    
    // Random methods should return minimum values
    assertEquals(10, Buggify.randomInt(10, 20));
    assertEquals(10, Buggify.randomInt(10, 10)); // Equal min/max
    assertEquals(1.0, Buggify.randomDouble(1.0, 2.0), 0.001);
    assertEquals(1.0, Buggify.randomDouble(1.0, 1.0), 0.001); // Equal min/max
    
    // maybeDelay should return null in non-simulation mode
    assertNull(Buggify.maybeDelay(1.0, 1.0));
  }
  
  @Test
  public void testBugRegistryInNonSimulation() {
    // Registry works normally, but shouldInject always returns false
    BugRegistry registry = BugRegistry.getInstance();
    registry.clear();
    
    // Register a bug with 100% probability
    registry.register("always_bug", 1.0);
    assertTrue(registry.isRegistered("always_bug"));
    assertEquals(1.0, registry.getProbability("always_bug"));
    
    // But Buggify still returns false in non-simulation
    assertFalse(Buggify.isEnabled("always_bug"));
  }
}