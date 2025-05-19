package io.github.panghy.javaflow.rpc.error;

import io.github.panghy.javaflow.rpc.EndpointId;

/**
 * Exception thrown when there is an error in the RPC transport layer.
 * This indicates a lower-level network issue such as a connection being lost
 * during an operation, or a message being corrupted in transit.
 */
public class RpcTransportException extends RpcException {
  
  private final EndpointId endpointId;
  
  /**
   * Creates a new RPC transport exception.
   *
   * @param endpointId The endpoint involved in the failed transport operation
   */
  public RpcTransportException(EndpointId endpointId) {
    super(ErrorCode.TRANSPORT_ERROR, "Transport error communicating with endpoint: " + endpointId);
    this.endpointId = endpointId;
  }
  
  /**
   * Creates a new RPC transport exception with a custom message.
   *
   * @param endpointId The endpoint involved in the failed transport operation
   * @param message    The error message
   */
  public RpcTransportException(EndpointId endpointId, String message) {
    super(ErrorCode.TRANSPORT_ERROR, message);
    this.endpointId = endpointId;
  }
  
  /**
   * Creates a new RPC transport exception with a custom message and cause.
   *
   * @param endpointId The endpoint involved in the failed transport operation
   * @param message    The error message
   * @param cause      The underlying cause
   */
  public RpcTransportException(EndpointId endpointId, String message, Throwable cause) {
    super(ErrorCode.TRANSPORT_ERROR, message, cause);
    this.endpointId = endpointId;
  }
  
  /**
   * Creates a new RPC transport exception with a cause.
   *
   * @param endpointId The endpoint involved in the failed transport operation
   * @param cause      The underlying cause
   */
  public RpcTransportException(EndpointId endpointId, Throwable cause) {
    super(ErrorCode.TRANSPORT_ERROR, "Transport error communicating with endpoint: " + endpointId, cause);
    this.endpointId = endpointId;
  }
  
  /**
   * Gets the endpoint involved in the failed transport operation.
   *
   * @return The endpoint ID
   */
  public EndpointId getEndpointId() {
    return endpointId;
  }
}