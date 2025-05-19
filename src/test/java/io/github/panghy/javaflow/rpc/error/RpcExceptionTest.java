package io.github.panghy.javaflow.rpc.error;

import io.github.panghy.javaflow.rpc.EndpointId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link RpcException} base class and its subclasses.
 */
public class RpcExceptionTest {

  @Test
  public void testRpcExceptionWithErrorCode() {
    RpcException exception = new RpcException(RpcException.ErrorCode.UNKNOWN);
    assertEquals(RpcException.ErrorCode.UNKNOWN, exception.getErrorCode());
    assertEquals(1000, exception.getErrorCodeValue());
    assertTrue(exception.getMessage().contains("UNKNOWN"));
  }

  @Test
  public void testRpcExceptionWithErrorCodeAndMessage() {
    String message = "Test error message";
    RpcException exception = new RpcException(RpcException.ErrorCode.METHOD_NOT_FOUND, message);
    assertEquals(RpcException.ErrorCode.METHOD_NOT_FOUND, exception.getErrorCode());
    assertEquals(1004, exception.getErrorCodeValue());
    assertEquals(message, exception.getMessage());
  }

  @Test
  public void testRpcExceptionWithErrorCodeAndCause() {
    Exception cause = new RuntimeException("Original cause");
    RpcException exception = new RpcException(RpcException.ErrorCode.INVOCATION_ERROR, cause);
    assertEquals(RpcException.ErrorCode.INVOCATION_ERROR, exception.getErrorCode());
    assertEquals(1005, exception.getErrorCodeValue());
    assertSame(cause, exception.getCause());
    assertTrue(exception.getMessage().contains("INVOCATION_ERROR"));
  }

  @Test
  public void testRpcExceptionWithErrorCodeMessageAndCause() {
    String message = "Test error message with cause";
    Exception cause = new RuntimeException("Original cause");
    RpcException exception = new RpcException(
        RpcException.ErrorCode.AUTHENTICATION_ERROR, message, cause);
    assertEquals(RpcException.ErrorCode.AUTHENTICATION_ERROR, exception.getErrorCode());
    assertEquals(1007, exception.getErrorCodeValue());
    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  public void testRpcExceptionToString() {
    RpcException exception = new RpcException(
        RpcException.ErrorCode.RESOURCE_EXHAUSTED, "Out of memory");
    String toString = exception.toString();
    assertTrue(toString.contains("RESOURCE_EXHAUSTED"));
    assertTrue(toString.contains("Out of memory"));
  }

  @Test
  public void testErrorCodeFromCode() {
    assertEquals(RpcException.ErrorCode.CONNECTION_ERROR, 
        RpcException.ErrorCode.fromCode(1001));
    assertEquals(RpcException.ErrorCode.TIMEOUT, 
        RpcException.ErrorCode.fromCode(1002));
    assertEquals(RpcException.ErrorCode.UNKNOWN, 
        RpcException.ErrorCode.fromCode(9999)); // Invalid code returns UNKNOWN
  }

  @Test
  public void testErrorCodeGetCode() {
    assertEquals(1000, RpcException.ErrorCode.UNKNOWN.getCode());
    assertEquals(1001, RpcException.ErrorCode.CONNECTION_ERROR.getCode());
    assertEquals(1002, RpcException.ErrorCode.TIMEOUT.getCode());
    assertEquals(1003, RpcException.ErrorCode.SERIALIZATION_ERROR.getCode());
    assertEquals(1004, RpcException.ErrorCode.METHOD_NOT_FOUND.getCode());
    assertEquals(1005, RpcException.ErrorCode.INVOCATION_ERROR.getCode());
    assertEquals(1006, RpcException.ErrorCode.TRANSPORT_ERROR.getCode());
    assertEquals(1007, RpcException.ErrorCode.AUTHENTICATION_ERROR.getCode());
    assertEquals(1008, RpcException.ErrorCode.AUTHORIZATION_ERROR.getCode());
    assertEquals(1009, RpcException.ErrorCode.RESOURCE_EXHAUSTED.getCode());
    assertEquals(1010, RpcException.ErrorCode.INVALID_ARGUMENT.getCode());
    assertEquals(1011, RpcException.ErrorCode.SERVICE_UNAVAILABLE.getCode());
  }
  
  @Test
  public void testRpcSerializationException() {
    Class<?> type = String.class;
    
    // Test constructor with type only
    RpcSerializationException exception1 = new RpcSerializationException(type);
    assertEquals(RpcException.ErrorCode.SERIALIZATION_ERROR, exception1.getErrorCode());
    assertEquals(type, exception1.getType());
    assertTrue(exception1.getMessage().contains("String"));
    
    // Test constructor with type and message
    String message = "Failed to serialize string";
    RpcSerializationException exception2 = new RpcSerializationException(type, message);
    assertEquals(RpcException.ErrorCode.SERIALIZATION_ERROR, exception2.getErrorCode());
    assertEquals(type, exception2.getType());
    assertEquals(message, exception2.getMessage());
    
    // Test constructor with type and cause
    Exception cause = new RuntimeException("Original cause");
    RpcSerializationException exception3 = new RpcSerializationException(type, cause);
    assertEquals(RpcException.ErrorCode.SERIALIZATION_ERROR, exception3.getErrorCode());
    assertEquals(type, exception3.getType());
    assertSame(cause, exception3.getCause());
    assertTrue(exception3.getMessage().contains("String"));
    
    // Test constructor with type, message, and cause
    RpcSerializationException exception4 = new RpcSerializationException(
        type, message, cause);
    assertEquals(RpcException.ErrorCode.SERIALIZATION_ERROR, exception4.getErrorCode());
    assertEquals(type, exception4.getType());
    assertEquals(message, exception4.getMessage());
    assertSame(cause, exception4.getCause());
  }
  
  @Test
  public void testRpcConnectionException() {
    EndpointId endpointId = new EndpointId("test-endpoint");
    
    // Test constructor with endpointId only
    RpcConnectionException exception1 = new RpcConnectionException(endpointId);
    assertEquals(RpcException.ErrorCode.CONNECTION_ERROR, exception1.getErrorCode());
    assertEquals(endpointId, exception1.getEndpointId());
    assertTrue(exception1.getMessage().contains("test-endpoint"));
    
    // Test constructor with endpointId and message
    String message = "Connection refused";
    RpcConnectionException exception2 = new RpcConnectionException(endpointId, message);
    assertEquals(RpcException.ErrorCode.CONNECTION_ERROR, exception2.getErrorCode());
    assertEquals(endpointId, exception2.getEndpointId());
    assertEquals(message, exception2.getMessage());
    
    // Test constructor with endpointId and cause
    Exception cause = new RuntimeException("Original cause");
    RpcConnectionException exception3 = new RpcConnectionException(endpointId, cause);
    assertEquals(RpcException.ErrorCode.CONNECTION_ERROR, exception3.getErrorCode());
    assertEquals(endpointId, exception3.getEndpointId());
    assertSame(cause, exception3.getCause());
    assertTrue(exception3.getMessage().contains("test-endpoint"));
    
    // Test constructor with endpointId, message, and cause
    RpcConnectionException exception4 = new RpcConnectionException(
        endpointId, message, cause);
    assertEquals(RpcException.ErrorCode.CONNECTION_ERROR, exception4.getErrorCode());
    assertEquals(endpointId, exception4.getEndpointId());
    assertEquals(message, exception4.getMessage());
    assertSame(cause, exception4.getCause());
  }
  
  @Test
  public void testRpcTimeoutException() {
    EndpointId endpointId = new EndpointId("test-endpoint");
    String methodName = "testMethod";
    long timeoutMs = 5000;
    
    // Test constructor with endpointId, methodName, and timeoutMs
    RpcTimeoutException exception1 = new RpcTimeoutException(endpointId, methodName, timeoutMs);
    assertEquals(RpcException.ErrorCode.TIMEOUT, exception1.getErrorCode());
    assertEquals(endpointId, exception1.getEndpointId());
    assertEquals(methodName, exception1.getMethodName());
    assertEquals(timeoutMs, exception1.getTimeoutMs());
    assertTrue(exception1.getMessage().contains("test-endpoint"));
    assertTrue(exception1.getMessage().contains("testMethod"));
    assertTrue(exception1.getMessage().contains("5000"));
    
    // Test constructor with endpointId, methodName, timeoutMs, and message
    String message = "Operation timed out";
    RpcTimeoutException exception2 = new RpcTimeoutException(
        endpointId, methodName, timeoutMs, message);
    assertEquals(RpcException.ErrorCode.TIMEOUT, exception2.getErrorCode());
    assertEquals(endpointId, exception2.getEndpointId());
    assertEquals(methodName, exception2.getMethodName());
    assertEquals(timeoutMs, exception2.getTimeoutMs());
    assertEquals(message, exception2.getMessage());
  }
  
  @Test
  public void testRpcTransportException() {
    EndpointId endpointId = new EndpointId("test-endpoint");
    
    // Test constructor with endpointId only
    RpcTransportException exception1 = new RpcTransportException(endpointId);
    assertEquals(RpcException.ErrorCode.TRANSPORT_ERROR, exception1.getErrorCode());
    assertEquals(endpointId, exception1.getEndpointId());
    assertTrue(exception1.getMessage().contains("test-endpoint"));
    
    // Test constructor with endpointId and message
    String message = "Connection lost";
    RpcTransportException exception2 = new RpcTransportException(endpointId, message);
    assertEquals(RpcException.ErrorCode.TRANSPORT_ERROR, exception2.getErrorCode());
    assertEquals(endpointId, exception2.getEndpointId());
    assertEquals(message, exception2.getMessage());
    
    // Test constructor with endpointId and cause
    Exception cause = new RuntimeException("Original cause");
    RpcTransportException exception3 = new RpcTransportException(endpointId, cause);
    assertEquals(RpcException.ErrorCode.TRANSPORT_ERROR, exception3.getErrorCode());
    assertEquals(endpointId, exception3.getEndpointId());
    assertSame(cause, exception3.getCause());
    assertTrue(exception3.getMessage().contains("test-endpoint"));
    
    // Test constructor with endpointId, message, and cause
    RpcTransportException exception4 = new RpcTransportException(
        endpointId, message, cause);
    assertEquals(RpcException.ErrorCode.TRANSPORT_ERROR, exception4.getErrorCode());
    assertEquals(endpointId, exception4.getEndpointId());
    assertEquals(message, exception4.getMessage());
    assertSame(cause, exception4.getCause());
  }
}