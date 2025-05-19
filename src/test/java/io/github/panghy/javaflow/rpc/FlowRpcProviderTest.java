package io.github.panghy.javaflow.rpc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
  
  @Test
  public void testSetDefaultTransport() {
    // Create a mock transport
    FlowRpcTransport mockTransport = mock(FlowRpcTransport.class);
    
    // Set it as the default
    FlowRpcProvider.setDefaultTransport(mockTransport);
    
    // Verify it's returned as the default
    assertEquals(mockTransport, FlowRpcProvider.getDefaultTransport());
    
    // Set to null
    FlowRpcProvider.setDefaultTransport(null);
    
    // Since createDefaultTransport returns null, getDefaultTransport should return null
    assertNull(FlowRpcProvider.getDefaultTransport());
  }
  
  @Test
  public void testSimulationModeResetsDefaultTransport() {
    // Create a mock transport
    FlowRpcTransport mockTransport = mock(FlowRpcTransport.class);
    
    // Set it as the default
    FlowRpcProvider.setDefaultTransport(mockTransport);
    
    // Verify it's returned as the default
    assertEquals(mockTransport, FlowRpcProvider.getDefaultTransport());
    
    // Change simulation mode - this should reset the default transport
    FlowRpcProvider.setSimulationMode(true);
    
    // Since createDefaultTransport returns null, getDefaultTransport should return null
    assertNull(FlowRpcProvider.getDefaultTransport());
  }
}