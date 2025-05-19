package io.github.panghy.javaflow.rpc.error;

/**
 * Base exception class for RPC-related errors in the JavaFlow RPC framework.
 * This exception is used for errors that occur during RPC operations.
 * 
 * <p>RpcException serves as the base class for a hierarchy of exceptions
 * that can occur in the RPC framework. It includes an error code to allow
 * for programmatic handling of specific error conditions.</p>
 * 
 * <p>Specific subclasses may be defined for common error types like connection
 * errors, timeouts, serialization errors, etc.</p>
 */
public class RpcException extends RuntimeException {

  /**
   * Enumeration of error codes for RPC exceptions.
   */
  public enum ErrorCode {
    /**
     * Unknown or unspecified error.
     */
    UNKNOWN(1000),
    
    /**
     * Connection error (couldn't connect to endpoint).
     */
    CONNECTION_ERROR(1001),
    
    /**
     * Timeout error (operation took too long).
     */
    TIMEOUT(1002),
    
    /**
     * Serialization error (couldn't serialize or deserialize a message).
     */
    SERIALIZATION_ERROR(1003),
    
    /**
     * Method not found on the remote endpoint.
     */
    METHOD_NOT_FOUND(1004),
    
    /**
     * Error invoking a method on the remote endpoint.
     */
    INVOCATION_ERROR(1005),
    
    /**
     * Network transport error (e.g., connection lost during operation).
     */
    TRANSPORT_ERROR(1006),
    
    /**
     * Authentication error (not authorized to access the endpoint).
     */
    AUTHENTICATION_ERROR(1007),
    
    /**
     * Authorization error (not authorized to perform the operation).
     */
    AUTHORIZATION_ERROR(1008),
    
    /**
     * Resource exhausted (e.g., out of memory, too many connections).
     */
    RESOURCE_EXHAUSTED(1009),
    
    /**
     * Invalid argument (e.g., null where not allowed).
     */
    INVALID_ARGUMENT(1010),
    
    /**
     * Service unavailable (e.g., shutting down, overloaded).
     */
    SERVICE_UNAVAILABLE(1011);
    
    private final int code;
    
    ErrorCode(int code) {
      this.code = code;
    }
    
    /**
     * Gets the numeric code for this error.
     *
     * @return The error code
     */
    public int getCode() {
      return code;
    }
    
    /**
     * Gets an ErrorCode from its numeric value.
     *
     * @param code The numeric error code
     * @return The corresponding ErrorCode, or UNKNOWN if not found
     */
    public static ErrorCode fromCode(int code) {
      for (ErrorCode errorCode : values()) {
        if (errorCode.code == code) {
          return errorCode;
        }
      }
      return UNKNOWN;
    }
  }
  
  private final ErrorCode errorCode;
  
  /**
   * Creates a new RPC exception with the specified error code.
   *
   * @param errorCode The error code
   */
  public RpcException(ErrorCode errorCode) {
    super("RPC error: " + errorCode);
    this.errorCode = errorCode;
  }
  
  /**
   * Creates a new RPC exception with the specified error code and message.
   *
   * @param errorCode The error code
   * @param message   The error message
   */
  public RpcException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
  
  /**
   * Creates a new RPC exception with the specified error code, message, and cause.
   *
   * @param errorCode The error code
   * @param message   The error message
   * @param cause     The underlying cause
   */
  public RpcException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }
  
  /**
   * Creates a new RPC exception with the specified error code and cause.
   *
   * @param errorCode The error code
   * @param cause     The underlying cause
   */
  public RpcException(ErrorCode errorCode, Throwable cause) {
    super("RPC error: " + errorCode, cause);
    this.errorCode = errorCode;
  }
  
  /**
   * Gets the error code for this exception.
   *
   * @return The error code
   */
  public ErrorCode getErrorCode() {
    return errorCode;
  }
  
  /**
   * Gets the numeric value of the error code.
   *
   * @return The numeric error code
   */
  public int getErrorCodeValue() {
    return errorCode.getCode();
  }
  
  @Override
  public String toString() {
    return "RpcException{" +
        "errorCode=" + errorCode +
        ", message='" + getMessage() + '\'' +
        '}';
  }
}