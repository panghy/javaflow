package io.github.panghy.javaflow.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the ChaosScenarios class.
 */
public class ChaosScenariosTest {
  
  @BeforeEach
  public void setUp() {
    // Clear registry before each test
    BugRegistry.getInstance().clear();
  }
  
  @Test
  public void testNetworkChaos() {
    ChaosScenarios.networkChaos();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Verify network bugs are registered with correct probabilities
    assertEquals(0.05, registry.getProbability(BugIds.PACKET_LOSS));
    assertEquals(0.03, registry.getProbability(BugIds.PACKET_REORDER));
    assertEquals(0.02, registry.getProbability(BugIds.CONNECTION_TIMEOUT));
    assertEquals(0.01, registry.getProbability(BugIds.NETWORK_PARTITION));
    assertEquals(0.01, registry.getProbability(BugIds.CONNECTION_RESET));
  }
  
  @Test
  public void testStorageChaos() {
    ChaosScenarios.storageChaos();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Verify storage bugs are registered with correct probabilities
    assertEquals(0.10, registry.getProbability(BugIds.DISK_SLOW));
    assertEquals(0.02, registry.getProbability(BugIds.WRITE_FAILURE));
    assertEquals(0.01, registry.getProbability(BugIds.READ_FAILURE));
    assertEquals(0.005, registry.getProbability(BugIds.DISK_FULL));
    assertEquals(0.001, registry.getProbability(BugIds.DATA_CORRUPTION));
  }
  
  @Test
  public void testProcessChaos() {
    ChaosScenarios.processChaos();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Verify process bugs are registered with correct probabilities
    assertEquals(0.05, registry.getProbability(BugIds.MEMORY_PRESSURE));
    assertEquals(0.03, registry.getProbability(BugIds.CPU_STARVATION));
    assertEquals(0.01, registry.getProbability(BugIds.PROCESS_CRASH));
    assertEquals(0.005, registry.getProbability(BugIds.PROCESS_HANG));
    assertEquals(0.02, registry.getProbability(BugIds.CLOCK_SKEW));
  }
  
  @Test
  public void testSchedulingChaos() {
    ChaosScenarios.schedulingChaos();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Verify scheduling bugs are registered with correct probabilities
    assertEquals(0.20, registry.getProbability(BugIds.TASK_DELAY));
    assertEquals(0.05, registry.getProbability(BugIds.PRIORITY_INVERSION));
    assertEquals(0.03, registry.getProbability(BugIds.UNFAIR_SCHEDULING));
    assertEquals(0.01, registry.getProbability(BugIds.TASK_STARVATION));
  }
  
  @Test
  public void testFullChaos() {
    ChaosScenarios.fullChaos();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Verify all categories are registered
    // Network
    assertNotNull(registry.getProbability(BugIds.PACKET_LOSS));
    // Storage
    assertNotNull(registry.getProbability(BugIds.DISK_SLOW));
    // Process
    assertNotNull(registry.getProbability(BugIds.MEMORY_PRESSURE));
    // Scheduling
    assertNotNull(registry.getProbability(BugIds.TASK_DELAY));
    
    // Should have many bugs registered
    assertTrue(registry.size() >= 15, "Full chaos should register many bugs");
  }
  
  @Test
  public void testSplitBrainScenario() {
    ChaosScenarios.splitBrainScenario();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // High probability of network partition
    assertEquals(0.50, registry.getProbability(BugIds.NETWORK_PARTITION));
    // Significant clock skew
    assertEquals(0.30, registry.getProbability(BugIds.CLOCK_SKEW));
    // Some connection timeouts
    assertEquals(0.10, registry.getProbability(BugIds.CONNECTION_TIMEOUT));
  }
  
  @Test
  public void testCascadingFailureScenario() {
    ChaosScenarios.cascadingFailureScenario();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Very high memory pressure
    assertEquals(0.70, registry.getProbability(BugIds.MEMORY_PRESSURE));
    // High connection failures
    assertEquals(0.50, registry.getProbability(BugIds.CONNECTION_TIMEOUT));
    // Significant process crashes
    assertEquals(0.30, registry.getProbability(BugIds.PROCESS_CRASH));
    // CPU issues
    assertEquals(0.20, registry.getProbability(BugIds.CPU_STARVATION));
  }
  
  @Test
  public void testDataCorruptionScenario() {
    ChaosScenarios.dataCorruptionScenario();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Focus on data integrity issues
    assertEquals(0.10, registry.getProbability(BugIds.DATA_CORRUPTION));
    assertEquals(0.05, registry.getProbability(BugIds.PARTIAL_WRITE));
    assertEquals(0.05, registry.getProbability(BugIds.WRITE_FAILURE));
    assertEquals(0.02, registry.getProbability(BugIds.DISK_FULL));
  }
  
  @Test
  public void testHighLatencyScenario() {
    ChaosScenarios.highLatencyScenario();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Focus on latency-inducing issues
    assertEquals(0.30, registry.getProbability(BugIds.NETWORK_SLOW));
    assertEquals(0.20, registry.getProbability(BugIds.DISK_SLOW));
    assertEquals(0.40, registry.getProbability(BugIds.TASK_DELAY));
    assertEquals(0.10, registry.getProbability(BugIds.PACKET_REORDER));
  }
  
  @Test
  public void testResourceConstraintScenario() {
    ChaosScenarios.resourceConstraintScenario();
    
    BugRegistry registry = BugRegistry.getInstance();
    
    // Focus on resource limitations
    assertEquals(0.80, registry.getProbability(BugIds.LOW_RESOURCES));
    assertEquals(0.50, registry.getProbability(BugIds.MEMORY_PRESSURE));
    assertEquals(0.30, registry.getProbability(BugIds.THREAD_EXHAUSTION));
    assertEquals(0.20, registry.getProbability(BugIds.DISK_FULL));
  }
  
  @Test
  public void testClearAll() {
    // Set up some scenarios
    ChaosScenarios.fullChaos();
    
    BugRegistry registry = BugRegistry.getInstance();
    assertTrue(registry.size() > 0, "Should have bugs registered");
    
    // Clear all
    ChaosScenarios.clearAll();
    
    assertEquals(0, registry.size(), "All bugs should be cleared");
  }
  
  @Test
  public void testScenarioOverwrite() {
    // Set up one scenario
    ChaosScenarios.networkChaos();
    
    BugRegistry registry = BugRegistry.getInstance();
    int initialSize = registry.size();
    
    // Set up another scenario that might have overlapping bugs
    ChaosScenarios.storageChaos();
    
    // Size should increase (no overlapping bugs in these scenarios)
    assertTrue(registry.size() > initialSize, 
        "Adding another scenario should register more bugs");
  }
}