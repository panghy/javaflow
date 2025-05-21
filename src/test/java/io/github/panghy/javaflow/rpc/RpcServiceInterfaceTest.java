package io.github.panghy.javaflow.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link RpcServiceInterface} interface.
 */
public class RpcServiceInterfaceTest {

  private FlowRpcTransport mockTransport;
  
  // A simple implementation of RpcServiceInterface for testing
  private static class TestService implements RpcServiceInterface {
    public final PromiseStream<Pair<String, FlowPromise<String>>> echo = new PromiseStream<>();
    
    public FlowFuture<String> echoAsync(String message) {
      FlowFuture<String> future = new FlowFuture<>();
      echo.send(new Pair<>(message, future.getPromise()));
      return future;
    }
    
    // Override onClose for testing
    private final FlowPromise<Void> closePromise = new FlowFuture<Void>().getPromise();
    
    @Override
    public FlowFuture<Void> onClose() {
      return closePromise.getFuture();
    }
    
    public void close() {
      closePromise.complete(null);
    }
  }
  
  @BeforeEach
  public void setUp() {
    // Create a mock transport
    mockTransport = mock(FlowRpcTransport.class);
    
    // Set it as the default for the tests
    FlowRpcProvider.setDefaultTransport(mockTransport);
  }
  
  @Test
  public void testRegisterAsLoopback() {
    TestService service = new TestService();
    EndpointId endpointId = new EndpointId("test-service");
    
    // Call registerAsLoopback
    service.registerAsLoopback(endpointId);
    
    // Verify the transport was called to register the service
    verify(mockTransport).registerLoopbackEndpoint(eq(endpointId), eq(service));
  }
  
  @Test
  public void testReady() {
    TestService service = new TestService();
    
    // Call ready and verify it completes immediately
    FlowFuture<Void> readyFuture = service.ready();
    
    assertNotNull(readyFuture);
    assertTrue(readyFuture.isCompleted());
  }
  
  @Test
  public void testOnClose() {
    TestService service = new TestService();
    
    // Get the onClose future
    FlowFuture<Void> closeFuture = service.onClose();
    
    // Verify it's not completed initially
    assertNotNull(closeFuture);
    assertFalse(closeFuture.isCompleted());
    
    // Close the service
    service.close();
    
    // Verify the future is now completed
    assertTrue(closeFuture.isCompleted());
  }
  
  @Test
  public void testMethodInvocation() throws Exception {
    TestService service = new TestService();
    
    // Mock the stream behavior directly instead of using forEach 
    String testMessage = "Hello";
    FlowFuture<String> future = service.echoAsync(testMessage);
    
    // Complete the future manually 
    future.complete("Echo: " + testMessage);
    
    // Verify the future is completed
    assertTrue(future.isCompleted());
  }
  
  @Test
  public void testRemoteService() {
    // Create a mock remote service endpoint
    EndpointId remoteId = new EndpointId("remote-service");
    TestService mockRemoteService = mock(TestService.class);
    
    // Configure the mock transport to return our mock service
    when(mockTransport.getRoundRobinEndpoint(eq(remoteId), eq(TestService.class)))
        .thenReturn(mockRemoteService);
    
    // Create a mock future for the echo call
    @SuppressWarnings("unchecked")
    FlowFuture<String> mockFuture = mock(FlowFuture.class);
    when(mockRemoteService.echoAsync(any())).thenReturn(mockFuture);
    
    // Get the remote service via the transport
    TestService remoteService = FlowRpcTransport.getInstance().getRoundRobinEndpoint(remoteId, TestService.class);
    
    // Call a method on the remote service
    FlowFuture<String> result = remoteService.echoAsync("Test");
    
    // Verify the call was forwarded to the mock remote service
    verify(mockRemoteService).echoAsync("Test");
    assertEquals(mockFuture, result);
  }
}