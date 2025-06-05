package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.io.network.Endpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link FlowRpcProvider} class.
 */
public class FlowRpcProviderTest {

  // Store initial values to restore after tests
  private FlowRpcTransport originalTransport;
  
  @BeforeEach
  public void setUp() {
    // Save original values
    originalTransport = null;
    
    try {
      // Try to get the default transport before tests
      originalTransport = FlowRpcProvider.getDefaultTransport();
    } catch (Exception e) {
      // Ignore if default transport is not initialized or has issues
    }
    
    // Reset to a clean state for each test
    FlowRpcProvider.setDefaultTransport(null);
  }
  
  @AfterEach
  public void tearDown() {
    // Restore original values
    FlowRpcProvider.setDefaultTransport(originalTransport);
  }
  
  
  @Test
  public void testGetDefaultTransport() {
    // First call should create a new transport
    FlowRpcTransport transport1 = FlowRpcProvider.getDefaultTransport();
    assertNotNull(transport1);
    assertInstanceOf(FlowRpcTransportImpl.class, transport1);
    
    // Second call should return the same instance
    FlowRpcTransport transport2 = FlowRpcProvider.getDefaultTransport();
    assertSame(transport1, transport2);
  }
  
  @Test
  public void testSetDefaultTransport() {
    // Create a mock transport
    FlowRpcTransport mockTransport = new MockFlowRpcTransport();
    
    // Set it as the default
    FlowRpcProvider.setDefaultTransport(mockTransport);
    
    // Get should return our mock
    FlowRpcTransport retrieved = FlowRpcProvider.getDefaultTransport();
    assertSame(mockTransport, retrieved);
  }
  
  
  @Test
  public void testConcurrentAccess() throws InterruptedException {
    // Test that multiple threads can safely access the provider
    final int threadCount = 10;
    Thread[] threads = new Thread[threadCount];
    FlowRpcTransport[] transports = new FlowRpcTransport[threadCount];
    
    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      threads[i] = new Thread(() -> transports[index] = FlowRpcProvider.getDefaultTransport());
      threads[i].start();
    }
    
    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join();
    }
    
    // All threads should have gotten the same transport instance
    FlowRpcTransport expectedTransport = transports[0];
    assertNotNull(expectedTransport);
    for (int i = 1; i < threadCount; i++) {
      assertSame(expectedTransport, transports[i]);
    }
  }
  
  @Test
  public void testTransportLifecycle() {
    // Get a transport
    FlowRpcTransport transport = FlowRpcProvider.getDefaultTransport();
    assertNotNull(transport);
    
    // Close it
    FlowFuture<Void> closeFuture = transport.close();
    assertNotNull(closeFuture);
    
    // Future should complete immediately for connection manager close
    assertTrue(closeFuture.isDone());
  }
  
  @Test
  public void testGetInstance() {
    // Test the static getInstance method on FlowRpcTransport
    FlowRpcTransport transport = FlowRpcTransport.getInstance();
    assertNotNull(transport);
    
    // Should be the same as getting from provider
    FlowRpcTransport providerTransport = FlowRpcProvider.getDefaultTransport();
    assertSame(transport, providerTransport);
  }
  
  /**
   * Mock implementation of FlowRpcTransport for testing.
   */
  private static class MockFlowRpcTransport implements FlowRpcTransport {
    private final EndpointResolver resolver = new DefaultEndpointResolver();
    
    @Override
    public EndpointResolver getEndpointResolver() {
      return resolver;
    }
    
    @Override
    public <T> T getRpcStub(EndpointId id, Class<T> interfaceClass) {
      throw new UnsupportedOperationException("Mock implementation");
    }
    
    @Override
    public <T> T getRpcStub(Endpoint endpoint, Class<T> interfaceClass) {
      throw new UnsupportedOperationException("Mock implementation");
    }
    
    @Override
    public <T> T getLocalStub(EndpointId id, Class<T> interfaceClass) {
      throw new UnsupportedOperationException("Mock implementation");
    }
    
    @Override
    public FlowFuture<Void> close() {
      return FlowFuture.completed(null);
    }
  }
}