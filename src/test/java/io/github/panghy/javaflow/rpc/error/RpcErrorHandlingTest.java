package io.github.panghy.javaflow.rpc.error;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.util.RpcTimeoutUtil;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for RPC error handling and propagation.
 * These tests verify that RPC errors and timeouts are handled correctly.
 */
public class RpcErrorHandlingTest extends AbstractFlowTest {

  /**
   * Tests that an RPC timeout is handled correctly.
   */
  @Test
  public void testRpcTimeout() throws Exception {
    // Create a task to run in an actor context
    CompletableFuture<Void> taskFuture = Flow.startActor(() -> {
      // Create a future that never completes
      CompletableFuture<String> future = new CompletableFuture<>();
      
      // Add a timeout
      EndpointId endpoint = new EndpointId("test-endpoint");
      String methodName = "testMethod";
      long timeoutMs = 100;
      CompletableFuture<String> timeoutFuture = RpcTimeoutUtil.withTimeout(
          future, endpoint, methodName, timeoutMs);
      
      // Advance time past the timeout
      advanceTime(timeoutMs / 1000.0 + 0.001);
      pump();
      
      // Verify that the timeout occurred
      assertTrue(timeoutFuture.isDone());
      assertTrue(timeoutFuture.isCompletedExceptionally());
      
      try {
        timeoutFuture.getNow(null);
        fail("Expected a timeout exception");
      } catch (Exception e) {
        // Verify that the exception is a RpcTimeoutException
        assertTrue(e.getCause() instanceof RpcTimeoutException);
        RpcTimeoutException timeoutException = (RpcTimeoutException) e.getCause();
        
        // Verify the exception details
        assertEquals(endpoint, timeoutException.getEndpointId());
        assertEquals(methodName, timeoutException.getMethodName());
        assertEquals(timeoutMs, timeoutException.getTimeoutMs());
        assertEquals(RpcException.ErrorCode.TIMEOUT, timeoutException.getErrorCode());
      }
      return null;
    });
    
    // Run the actor to completion
    pumpAndAdvanceTimeUntilDone(taskFuture);
  }
  
  /**
   * Tests that an RPC connection error is handled correctly.
   */
  @Test
  public void testRpcConnectionError() throws Exception {
    // Create a future and complete it with an RpcConnectionException
    CompletableFuture<String> future = new CompletableFuture<>();
    EndpointId endpoint = new EndpointId("test-endpoint");
    future.completeExceptionally(new RpcConnectionException(endpoint));
    
    // Verify that the error is propagated
    assertTrue(future.isDone());
    assertTrue(future.isCompletedExceptionally());
    
    try {
      future.getNow(null);
      fail("Expected a connection exception");
    } catch (Exception e) {
      // Verify that the exception is a RpcConnectionException
      assertTrue(e.getCause() instanceof RpcConnectionException);
      RpcConnectionException connectionException = (RpcConnectionException) e.getCause();
      
      // Verify the exception details
      assertEquals(endpoint, connectionException.getEndpointId());
      assertEquals(RpcException.ErrorCode.CONNECTION_ERROR, connectionException.getErrorCode());
    }
  }
  
  /**
   * Tests that an error in the RPC transport is handled correctly.
   */
  @Test
  public void testRpcTransportError() throws Exception {
    // Create a future and complete it with an RpcTransportException
    CompletableFuture<String> future = new CompletableFuture<>();
    EndpointId endpoint = new EndpointId("test-endpoint");
    Throwable cause = new RuntimeException("Network error");
    future.completeExceptionally(
        new RpcTransportException(endpoint, "Transport failed", cause));
    
    // Verify that the error is propagated
    assertTrue(future.isDone());
    assertTrue(future.isCompletedExceptionally());
    
    try {
      future.getNow(null);
      fail("Expected a transport exception");
    } catch (Exception e) {
      // Verify that the exception is a RpcTransportException
      assertTrue(e.getCause() instanceof RpcTransportException);
      RpcTransportException transportException = (RpcTransportException) e.getCause();
      
      // Verify the exception details
      assertEquals(endpoint, transportException.getEndpointId());
      assertEquals(RpcException.ErrorCode.TRANSPORT_ERROR, transportException.getErrorCode());
      assertEquals("Transport failed", transportException.getMessage());
      assertEquals(cause, transportException.getCause());
    }
  }
  
  /**
   * Tests that an error in serialization is handled correctly.
   */
  @Test
  public void testRpcSerializationError() throws Exception {
    // Create a future and complete it with an RpcSerializationException
    CompletableFuture<String> future = new CompletableFuture<>();
    Class<?> type = String.class;
    Throwable cause = new RuntimeException("Invalid format");
    future.completeExceptionally(
        new RpcSerializationException(type, "Failed to deserialize", cause));
    
    // Verify that the error is propagated
    assertTrue(future.isDone());
    assertTrue(future.isCompletedExceptionally());
    
    try {
      future.getNow(null);
      fail("Expected a serialization exception");
    } catch (Exception e) {
      // Verify that the exception is a RpcSerializationException
      assertTrue(e.getCause() instanceof RpcSerializationException);
      RpcSerializationException serializationException = (RpcSerializationException) e.getCause();
      
      // Verify the exception details
      assertEquals(type, serializationException.getType());
      assertEquals(RpcException.ErrorCode.SERIALIZATION_ERROR, serializationException.getErrorCode());
      assertEquals("Failed to deserialize", serializationException.getMessage());
      assertEquals(cause, serializationException.getCause());
    }
  }
  
  /**
   * Tests that a timeout can be cancelled by normal completion.
   */
  @Test
  public void testTimeoutCancelledByCompletion() throws Exception {
    // Create a task to run in an actor context
    CompletableFuture<Void> taskFuture = Flow.startActor(() -> {
      // Create a future and add a timeout
      CompletableFuture<String> future = new CompletableFuture<>();
      EndpointId endpoint = new EndpointId("test-endpoint");
      String methodName = "testMethod";
      long timeoutMs = 100;
      CompletableFuture<String> timeoutFuture = RpcTimeoutUtil.withTimeout(
          future, endpoint, methodName, timeoutMs);
      
      // Complete the future before the timeout
      future.complete("success");
      pump();
      
      // Verify that the completion was propagated
      assertTrue(timeoutFuture.isDone());
      assertFalse(timeoutFuture.isCompletedExceptionally());
      
      try {
        String result = timeoutFuture.getNow(null);
        assertEquals("success", result);
      } catch (Exception e) {
        fail("Did not expect an exception: " + e);
      }
      
      // Advance time past the timeout
      advanceTime(timeoutMs / 1000.0 + 0.001);
      pump();
      
      // Verify that the result is still the same
      try {
        String result = timeoutFuture.getNow(null);
        assertEquals("success", result);
      } catch (Exception e) {
        fail("Did not expect an exception: " + e);
      }
      return null;
    });
    
    // Run the actor to completion
    pumpAndAdvanceTimeUntilDone(taskFuture);
  }
}