package io.github.panghy.javaflow.rpc.error;

import io.github.panghy.javaflow.rpc.EndpointId;

/**
 * Exception thrown when an RPC operation times out.
 *
 * <p>This exception is thrown in the following scenarios:</p>
 * <ul>
 *   <li>When a unary RPC call exceeds the configured timeout</li>
 *   <li>When a stream has no activity for longer than the inactivity timeout</li>
 *   <li>When establishing a connection takes longer than the connection timeout</li>
 * </ul>
 */
public class RpcTimeoutException extends RpcException {

  /**
   * The type of timeout that occurred.
   */
  public enum TimeoutType {
    /** Timeout occurred during a unary RPC call. */
    UNARY_RPC,
    /** Timeout occurred due to stream inactivity. */
    STREAM_INACTIVITY,
    /** Timeout occurred while establishing a connection. */
    CONNECTION
  }

  private final EndpointId endpointId;
  private final String methodName;
  private final long timeoutMs;
  private final TimeoutType timeoutType;

  /**
   * Creates a new RpcTimeoutException for an RPC method call.
   * This constructor is for backward compatibility.
   *
   * @param endpointId The endpoint that timed out
   * @param methodName The method that timed out
   * @param timeoutMs The timeout duration in milliseconds
   */
  public RpcTimeoutException(EndpointId endpointId, String methodName, long timeoutMs) {
    super(ErrorCode.TIMEOUT, 
        String.format("RPC call to %s.%s timed out after %dms", endpointId, methodName, timeoutMs));
    this.endpointId = endpointId;
    this.methodName = methodName;
    this.timeoutMs = timeoutMs;
    this.timeoutType = TimeoutType.UNARY_RPC;
  }

  /**
   * Creates a new RpcTimeoutException for an RPC method call with a custom message.
   * This constructor is for backward compatibility.
   *
   * @param endpointId The endpoint that timed out
   * @param methodName The method that timed out
   * @param timeoutMs The timeout duration in milliseconds
   * @param message The error message
   */
  public RpcTimeoutException(EndpointId endpointId, String methodName, long timeoutMs, String message) {
    super(ErrorCode.TIMEOUT, message);
    this.endpointId = endpointId;
    this.methodName = methodName;
    this.timeoutMs = timeoutMs;
    this.timeoutType = TimeoutType.UNARY_RPC;
  }

  /**
   * Creates a new RpcTimeoutException with specific timeout type.
   *
   * @param timeoutType The type of timeout that occurred
   * @param timeoutMs The timeout duration in milliseconds
   * @param message The error message
   */
  public RpcTimeoutException(TimeoutType timeoutType, long timeoutMs, String message) {
    super(ErrorCode.TIMEOUT, message);
    this.endpointId = null;
    this.methodName = null;
    this.timeoutMs = timeoutMs;
    this.timeoutType = timeoutType;
  }

  /**
   * Creates a new RpcTimeoutException with specific timeout type and cause.
   *
   * @param timeoutType The type of timeout that occurred
   * @param timeoutMs The timeout duration in milliseconds
   * @param message The error message
   * @param cause The underlying cause
   */
  public RpcTimeoutException(TimeoutType timeoutType, long timeoutMs, String message, Throwable cause) {
    super(ErrorCode.TIMEOUT, message, cause);
    this.endpointId = null;
    this.methodName = null;
    this.timeoutMs = timeoutMs;
    this.timeoutType = timeoutType;
  }

  /**
   * Gets the endpoint ID that timed out.
   *
   * @return The endpoint ID, or null if not applicable
   */
  public EndpointId getEndpointId() {
    return endpointId;
  }

  /**
   * Gets the method name that timed out.
   *
   * @return The method name, or null if not applicable
   */
  public String getMethodName() {
    return methodName;
  }

  /**
   * Gets the type of timeout that occurred.
   *
   * @return The timeout type
   */
  public TimeoutType getTimeoutType() {
    return timeoutType;
  }

  /**
   * Gets the timeout duration in milliseconds.
   *
   * @return The timeout duration
   */
  public long getTimeoutMs() {
    return timeoutMs;
  }

  @Override
  public String toString() {
    if (endpointId != null && methodName != null) {
      return String.format("RpcTimeoutException{type=%s, endpoint=%s, method=%s, timeoutMs=%d, message='%s'}",
          timeoutType, endpointId, methodName, timeoutMs, getMessage());
    } else {
      return String.format("RpcTimeoutException{type=%s, timeoutMs=%d, message='%s'}",
          timeoutType, timeoutMs, getMessage());
    }
  }
}