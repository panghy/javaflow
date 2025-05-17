package io.github.panghy.javaflow.io.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the FlowTransport interface's default methods.
 */
public class FlowTransportTest {

  @AfterEach
  void cleanup() {
    // Reset the default transport
    TransportProvider.setDefaultTransport(null);
  }

  @Test
  void testGetDefault() {
    // Get a transport via the interface's static method
    FlowTransport transport = FlowTransport.getDefault();
    
    // Verify it's not null
    assertNotNull(transport);
    
    // Verify it's the same as what we get from the provider
    FlowTransport fromProvider = TransportProvider.getDefaultTransport();
    assertSame(transport, fromProvider);
  }
  
  @Test
  void testGetDefaultReturnsSameInstance() {
    // Get a transport via the interface's static method
    FlowTransport first = FlowTransport.getDefault();
    FlowTransport second = FlowTransport.getDefault();
    
    // Verify they're the same instance
    assertSame(first, second);
  }
}