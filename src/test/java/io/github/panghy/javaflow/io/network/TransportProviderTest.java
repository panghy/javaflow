package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.scheduler.FlowClock;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.TestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the TransportProvider class.
 */
public class TransportProviderTest {

  private TestScheduler testScheduler;
  
  @BeforeEach
  void setup() {
    FlowScheduler simulatedScheduler = new FlowScheduler(false, FlowClock.createSimulatedClock());
    testScheduler = new TestScheduler(simulatedScheduler);
    testScheduler.startSimulation();
  }

  @AfterEach
  void cleanup() {
    // End simulation
    testScheduler.endSimulation();
    
    // Ensure we reset the default transport between tests
    TransportProvider.setDefaultTransport(null);
  }

  @Test
  void testGetDefaultTransport() {
    // Get the default transport
    FlowTransport transport = TransportProvider.getDefaultTransport();
    
    // Verify it's not null
    assertNotNull(transport);
    
    // Verify subsequent calls return the same instance
    FlowTransport secondCall = TransportProvider.getDefaultTransport();
    assertSame(transport, secondCall);
  }
  
  @Test
  void testSetDefaultTransport() {
    // Create a mock transport (we'll just use a simulated one)
    FlowTransport mockTransport = new SimulatedFlowTransport();
    
    // Set it as the default
    TransportProvider.setDefaultTransport(mockTransport);
    
    // Verify subsequent calls return our mock
    FlowTransport retrieved = TransportProvider.getDefaultTransport();
    assertSame(mockTransport, retrieved);
  }
  
  @Test
  void testSetToNullRecreatesToDefault() {
    // Set the default to null
    TransportProvider.setDefaultTransport(null);
    
    // Verify getting default transport creates a new one
    FlowTransport transport = TransportProvider.getDefaultTransport();
    assertNotNull(transport);
    
    // It should be either a real or simulated transport
    boolean isExpectedType = transport instanceof RealFlowTransport || 
                             transport instanceof SimulatedFlowTransport;
    assertTrue(isExpectedType, "Expected RealFlowTransport or SimulatedFlowTransport");
  }

  @Test
  void testGetDefaultTransportWithIsSimulatedTrue() {
    // Use mockito to mock the static method Flow.isSimulated()
    try (MockedStatic<Flow> mockedFlow = Mockito.mockStatic(Flow.class)) {
      // Make Flow.isSimulated() return true
      mockedFlow.when(Flow::isSimulated).thenReturn(true);
      
      // Reset to ensure we create a new transport
      TransportProvider.setDefaultTransport(null);
      
      // Get the default transport
      FlowTransport transport = TransportProvider.getDefaultTransport();
      
      // Verify it's a SimulatedFlowTransport
      assertTrue(transport instanceof SimulatedFlowTransport, 
          "Expected SimulatedFlowTransport in simulation mode");
    }
  }
  
  @Test
  void testGetDefaultTransportWithIsSimulatedFalse() {
    // Use mockito to mock the static method Flow.isSimulated()
    try (MockedStatic<Flow> mockedFlow = Mockito.mockStatic(Flow.class)) {
      // Make Flow.isSimulated() return false
      mockedFlow.when(Flow::isSimulated).thenReturn(false);
      
      // Reset to ensure we create a new transport
      TransportProvider.setDefaultTransport(null);
      
      // Get the default transport
      FlowTransport transport = TransportProvider.getDefaultTransport();
      
      // Verify it's a RealFlowTransport
      assertTrue(transport instanceof RealFlowTransport, 
          "Expected RealFlowTransport in real mode");
    }
  }
  
  @Test
  void testPrivateConstructor() throws Exception {
    // Test the private constructor for code coverage
    Constructor<TransportProvider> constructor = TransportProvider.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    
    // Create an instance (should not throw)
    TransportProvider instance = constructor.newInstance();
    assertNotNull(instance);
    
    // Verify class has a private constructor
    assertTrue(Modifier.isPrivate(constructor.getModifiers()));
  }
}