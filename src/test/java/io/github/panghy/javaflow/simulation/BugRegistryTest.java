package io.github.panghy.javaflow.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the BugRegistry class.
 */
public class BugRegistryTest {
  
  private BugRegistry registry;
  
  @BeforeEach
  public void setUp() {
    registry = BugRegistry.getInstance();
    registry.clear();
  }
  
  @Test
  public void testSingleton() {
    assertSame(BugRegistry.getInstance(), BugRegistry.getInstance(),
        "getInstance should always return the same instance");
  }
  
  @Test
  public void testRegisterAndGetProbability() {
    // Register a bug
    registry.register("test_bug", 0.5);
    
    // Verify it's registered
    assertTrue(registry.isRegistered("test_bug"));
    assertEquals(0.5, registry.getProbability("test_bug"));
    
    // Unregistered bug should return null
    assertNull(registry.getProbability("unregistered_bug"));
    assertFalse(registry.isRegistered("unregistered_bug"));
  }
  
  @Test
  public void testRegisterWithInvalidProbability() {
    // Test negative probability
    assertThrows(IllegalArgumentException.class, 
        () -> registry.register("bug", -0.1),
        "Should reject negative probability");
    
    // Test probability > 1.0
    assertThrows(IllegalArgumentException.class, 
        () -> registry.register("bug", 1.1),
        "Should reject probability > 1.0");
    
    // Edge cases should work
    assertDoesNotThrow(() -> registry.register("bug1", 0.0));
    assertDoesNotThrow(() -> registry.register("bug2", 1.0));
  }
  
  @Test
  public void testMethodChaining() {
    BugRegistry result = registry
        .register("bug1", 0.1)
        .register("bug2", 0.2)
        .register("bug3", 0.3);
    
    assertSame(registry, result, "Methods should return the same instance");
    assertEquals(3, registry.size());
  }
  
  @Test
  public void testUnregister() {
    // Register some bugs
    registry.register("bug1", 0.1)
           .register("bug2", 0.2)
           .register("bug3", 0.3);
    
    assertEquals(3, registry.size());
    
    // Unregister one
    registry.unregister("bug2");
    
    assertEquals(2, registry.size());
    assertTrue(registry.isRegistered("bug1"));
    assertFalse(registry.isRegistered("bug2"));
    assertTrue(registry.isRegistered("bug3"));
    
    // Unregistering non-existent bug should not throw
    assertDoesNotThrow(() -> registry.unregister("non_existent"));
  }
  
  @Test
  public void testClear() {
    // Register some bugs
    registry.register("bug1", 0.1)
           .register("bug2", 0.2)
           .register("bug3", 0.3);
    
    assertEquals(3, registry.size());
    
    // Clear all
    registry.clear();
    
    assertEquals(0, registry.size());
    assertFalse(registry.isRegistered("bug1"));
    assertFalse(registry.isRegistered("bug2"));
    assertFalse(registry.isRegistered("bug3"));
  }
  
  @Test
  public void testShouldInject() {
    // Test with unregistered bug
    assertFalse(registry.shouldInject("unregistered"),
        "Unregistered bug should never inject");
    
    // Test with 0% probability
    registry.register("never_bug", 0.0);
    for (int i = 0; i < 100; i++) {
      assertFalse(registry.shouldInject("never_bug"),
          "0% probability should never inject");
    }
    
    // Test with 100% probability
    registry.register("always_bug", 1.0);
    for (int i = 0; i < 100; i++) {
      assertTrue(registry.shouldInject("always_bug"),
          "100% probability should always inject");
    }
    
    // Test with 50% probability - should get both true and false
    registry.register("sometimes_bug", 0.5);
    int trueCount = 0;
    int iterations = 1000;
    
    for (int i = 0; i < iterations; i++) {
      if (registry.shouldInject("sometimes_bug")) {
        trueCount++;
      }
    }
    
    // Should be roughly 50%
    double ratio = (double) trueCount / iterations;
    assertTrue(ratio > 0.4 && ratio < 0.6,
        "50% probability should inject roughly half the time");
  }
  
  @Test
  public void testUpdateProbability() {
    // Register initial probability
    registry.register("bug", 0.3);
    assertEquals(0.3, registry.getProbability("bug"));
    
    // Update to new probability
    registry.register("bug", 0.7);
    assertEquals(0.7, registry.getProbability("bug"));
  }
  
  @Test
  public void testSize() {
    assertEquals(0, registry.size(), "Empty registry should have size 0");
    
    registry.register("bug1", 0.1);
    assertEquals(1, registry.size());
    
    registry.register("bug2", 0.2);
    assertEquals(2, registry.size());
    
    // Re-registering same bug shouldn't increase size
    registry.register("bug1", 0.5);
    assertEquals(2, registry.size());
    
    registry.unregister("bug1");
    assertEquals(1, registry.size());
    
    registry.clear();
    assertEquals(0, registry.size());
  }
}