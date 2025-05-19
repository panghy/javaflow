package io.github.panghy.javaflow.rpc.error;

import io.github.panghy.javaflow.rpc.EndpointId;

/**
 * Exception thrown when an RPC operation times out.
 * This indicates that the remote endpoint didn't respond within the specified time.
 */
public class RpcTimeoutException extends RpcException {
  
  private final EndpointId endpointId;
  private final String methodName;
  private final long timeoutMs;
  
  /**
   * Creates a new RPC timeout exception.
   *
   * @param endpointId The endpoint that timed out
   * @param methodName The method that was called
   * @param timeoutMs  The timeout in milliseconds
   */
  public RpcTimeoutException(EndpointId endpointId, String methodName, long timeoutMs) {
    super(ErrorCode.TIMEOUT, "RPC call to " + endpointId + "." + methodName 
        + " timed out after " + timeoutMs + "ms");
    this.endpointId = endpointId;
    this.methodName = methodName;
    this.timeoutMs = timeoutMs;
  }
  
  /**
   * Creates a new RPC timeout exception with a custom message.
   *
   * @param endpointId The endpoint that timed out
   * @param methodName The method that was called
   * @param timeoutMs  The timeout in milliseconds
   * @param message    The error message
   */
  public RpcTimeoutException(EndpointId endpointId, String methodName, long timeoutMs, String message) {
    super(ErrorCode.TIMEOUT, message);
    this.endpointId = endpointId;
    this.methodName = methodName;
    this.timeoutMs = timeoutMs;
  }
  
  /**
   * Gets the endpoint that timed out.
   *
   * @return The endpoint ID
   */
  public EndpointId getEndpointId() {
    return endpointId;
  }
  
  /**
   * Gets the method that was called.
   *
   * @return The method name
   */
  public String getMethodName() {
    return methodName;
  }
  
  /**
   * Gets the timeout that was exceeded.
   *
   * @return The timeout in milliseconds
   */
  public long getTimeoutMs() {
    return timeoutMs;
  }
}