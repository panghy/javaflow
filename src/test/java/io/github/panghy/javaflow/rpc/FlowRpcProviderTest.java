package io.github.panghy.javaflow.rpc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link FlowRpcProvider} class.
 */
public class FlowRpcProviderTest {

  // Store initial values to restore after tests
  private boolean originalSimulationMode;
  private FlowRpcTransport originalTransport;
  
  @BeforeEach
  public void setUp() {
    // Save original values
    originalSimulationMode = FlowRpcProvider.isSimulationMode();
    originalTransport = null;
    
    try {
      // Try to get the default transport before tests
      originalTransport = FlowRpcProvider.getDefaultTransport();
    } catch (Exception e) {
      // Ignore if default transport is not initialized or has issues
    }
    
    // Reset to a clean state for each test
    FlowRpcProvider.setSimulationMode(false);
    FlowRpcProvider.setDefaultTransport(null);
  }
  
  @AfterEach
  public void tearDown() {
    // Restore original values
    FlowRpcProvider.setSimulationMode(originalSimulationMode);
    FlowRpcProvider.setDefaultTransport(originalTransport);
  }
  
  @Test
  public void testSimulationMode() {
    // Default should be false
    assertFalse(FlowRpcProvider.isSimulationMode());
    
    // Set to true
    FlowRpcProvider.setSimulationMode(true);
    assertTrue(FlowRpcProvider.isSimulationMode());
    
    // Set back to false
    FlowRpcProvider.setSimulationMode(false);
    assertFalse(FlowRpcProvider.isSimulationMode());
  }
}