package io.github.panghy.javaflow.rpc.error;

/**
 * Exception thrown when there is an error serializing or deserializing a message.
 * This can happen if the serializer doesn't know how to handle a specific type,
 * or if the serialized data is corrupted or invalid.
 */
public class RpcSerializationException extends RpcException {
  
  private final Class<?> type;
  
  /**
   * Creates a new RPC serialization exception.
   *
   * @param type The type that couldn't be serialized or deserialized
   */
  public RpcSerializationException(Class<?> type) {
    super(ErrorCode.SERIALIZATION_ERROR, "Failed to serialize/deserialize type: " + type.getName());
    this.type = type;
  }
  
  /**
   * Creates a new RPC serialization exception with a custom message.
   *
   * @param type    The type that couldn't be serialized or deserialized
   * @param message The error message
   */
  public RpcSerializationException(Class<?> type, String message) {
    super(ErrorCode.SERIALIZATION_ERROR, message);
    this.type = type;
  }
  
  /**
   * Creates a new RPC serialization exception with a custom message and cause.
   *
   * @param type    The type that couldn't be serialized or deserialized
   * @param message The error message
   * @param cause   The underlying cause
   */
  public RpcSerializationException(Class<?> type, String message, Throwable cause) {
    super(ErrorCode.SERIALIZATION_ERROR, message, cause);
    this.type = type;
  }
  
  /**
   * Creates a new RPC serialization exception with a cause.
   *
   * @param type  The type that couldn't be serialized or deserialized
   * @param cause The underlying cause
   */
  public RpcSerializationException(Class<?> type, Throwable cause) {
    super(ErrorCode.SERIALIZATION_ERROR, "Failed to serialize/deserialize type: " + type.getName(), cause);
    this.type = type;
  }
  
  /**
   * Gets the type that couldn't be serialized or deserialized.
   *
   * @return The type
   */
  public Class<?> getType() {
    return type;
  }
}