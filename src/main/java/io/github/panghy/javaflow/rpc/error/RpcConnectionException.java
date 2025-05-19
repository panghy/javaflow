package io.github.panghy.javaflow.rpc.error;

import io.github.panghy.javaflow.rpc.EndpointId;

/**
 * Exception thrown when there is an error connecting to a remote endpoint.
 * This includes network connectivity issues, endpoint not found, etc.
 */
public class RpcConnectionException extends RpcException {
  
  private final EndpointId endpointId;
  
  /**
   * Creates a new RPC connection exception.
   *
   * @param endpointId The endpoint that couldn't be connected to
   */
  public RpcConnectionException(EndpointId endpointId) {
    super(ErrorCode.CONNECTION_ERROR, "Failed to connect to endpoint: " + endpointId);
    this.endpointId = endpointId;
  }
  
  /**
   * Creates a new RPC connection exception with a custom message.
   *
   * @param endpointId The endpoint that couldn't be connected to
   * @param message    The error message
   */
  public RpcConnectionException(EndpointId endpointId, String message) {
    super(ErrorCode.CONNECTION_ERROR, message);
    this.endpointId = endpointId;
  }
  
  /**
   * Creates a new RPC connection exception with a custom message and cause.
   *
   * @param endpointId The endpoint that couldn't be connected to
   * @param message    The error message
   * @param cause      The underlying cause
   */
  public RpcConnectionException(EndpointId endpointId, String message, Throwable cause) {
    super(ErrorCode.CONNECTION_ERROR, message, cause);
    this.endpointId = endpointId;
  }
  
  /**
   * Creates a new RPC connection exception with a cause.
   *
   * @param endpointId The endpoint that couldn't be connected to
   * @param cause      The underlying cause
   */
  public RpcConnectionException(EndpointId endpointId, Throwable cause) {
    super(ErrorCode.CONNECTION_ERROR, "Failed to connect to endpoint: " + endpointId, cause);
    this.endpointId = endpointId;
  }
  
  /**
   * Gets the endpoint that couldn't be connected to.
   *
   * @return The endpoint ID
   */
  public EndpointId getEndpointId() {
    return endpointId;
  }
}