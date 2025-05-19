package io.github.panghy.javaflow.rpc.message;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Header information for RPC messages in the JavaFlow RPC framework.
 * The header contains metadata about the message such as its type,
 * length, and identifiers for method and promises.
 * 
 * <p>The RPC message format consists of a header followed by payload data.
 * The header contains all the necessary information to route and process
 * the message appropriately.</p>
 * 
 * <p>Message structure:</p>
 * <pre>
 * +----------------+------------------+---------------+----------------+
 * | Message Header | Interface Method | Promise IDs   | Payload        |
 * +----------------+------------------+---------------+----------------+
 * </pre>
 * 
 * <p>The header fields include:</p>
 * <ul>
 *   <li>Message type: request, response, or error</li>
 *   <li>Message ID: a unique identifier for correlation</li>
 *   <li>Method ID: identifies which interface method to invoke</li>
 *   <li>Payload length: size of the serialized payload</li>
 * </ul>
 */
public class RpcMessageHeader {

  /**
   * Enumeration of possible RPC message types.
   */
  public enum MessageType {
    /**
     * A request to invoke a method on a remote endpoint.
     */
    REQUEST(1),
    
    /**
     * A successful response to a request.
     */
    RESPONSE(2),
    
    /**
     * An error response to a request.
     */
    ERROR(3),
    
    /**
     * A promise completion notification.
     */
    PROMISE_COMPLETE(4),
    
    /**
     * A stream value notification.
     */
    STREAM_VALUE(5),
    
    /**
     * A stream close notification.
     */
    STREAM_CLOSE(6);
    
    private final int code;
    
    MessageType(int code) {
      this.code = code;
    }
    
    public int getCode() {
      return code;
    }
    
    public static MessageType fromCode(int code) {
      for (MessageType type : values()) {
        if (type.code == code) {
          return type;
        }
      }
      throw new IllegalArgumentException("Unknown message type code: " + code);
    }
  }
  
  private final MessageType type;
  private final UUID messageId;
  private final String methodId;
  private final int payloadLength;
  
  /**
   * Creates a new RPC message header.
   *
   * @param type          The message type
   * @param messageId     The unique message ID
   * @param methodId      The method identifier
   * @param payloadLength The length of the payload in bytes
   */
  public RpcMessageHeader(MessageType type, UUID messageId, String methodId, int payloadLength) {
    this.type = type;
    this.messageId = messageId;
    this.methodId = methodId;
    this.payloadLength = payloadLength;
  }
  
  /**
   * Gets the message type.
   *
   * @return The message type
   */
  public MessageType getType() {
    return type;
  }
  
  /**
   * Gets the unique message ID.
   *
   * @return The message ID
   */
  public UUID getMessageId() {
    return messageId;
  }
  
  /**
   * Gets the method identifier.
   *
   * @return The method ID
   */
  public String getMethodId() {
    return methodId;
  }
  
  /**
   * Gets the payload length in bytes.
   *
   * @return The payload length
   */
  public int getPayloadLength() {
    return payloadLength;
  }
  
  /**
   * Serializes this header to a ByteBuffer.
   *
   * @return A ByteBuffer containing the serialized header
   */
  public ByteBuffer serialize() {
    // Format:
    // - Message type (1 byte)
    // - Message ID (16 bytes - UUID)
    // - Method ID length (2 bytes)
    // - Method ID (variable)
    // - Payload length (4 bytes)
    
    // Calculate total size
    int methodIdSize = methodId != null ? methodId.getBytes().length : 0;
    int totalSize = 1 + 16 + 2 + methodIdSize + 4;
    
    ByteBuffer buffer = ByteBuffer.allocate(totalSize);
    
    // Write message type
    buffer.put((byte) type.getCode());
    
    // Write message ID
    if (messageId != null) {
      buffer.putLong(messageId.getMostSignificantBits());
      buffer.putLong(messageId.getLeastSignificantBits());
    } else {
      buffer.putLong(0);
      buffer.putLong(0);
    }
    
    // Write method ID
    if (methodId != null) {
      byte[] methodIdBytes = methodId.getBytes();
      buffer.putShort((short) methodIdBytes.length);
      buffer.put(methodIdBytes);
    } else {
      buffer.putShort((short) 0);
    }
    
    // Write payload length
    buffer.putInt(payloadLength);
    
    // Reset position for reading
    buffer.flip();
    return buffer;
  }
  
  /**
   * Deserializes a header from a ByteBuffer.
   *
   * @param buffer The buffer containing the serialized header
   * @return The deserialized header
   */
  public static RpcMessageHeader deserialize(ByteBuffer buffer) {
    // Read message type
    MessageType type = MessageType.fromCode(buffer.get());
    
    // Read message ID
    long mostSigBits = buffer.getLong();
    long leastSigBits = buffer.getLong();
    UUID messageId = (mostSigBits != 0 || leastSigBits != 0) 
        ? new UUID(mostSigBits, leastSigBits) 
        : null;
    
    // Read method ID
    short methodIdLength = buffer.getShort();
    String methodId = null;
    if (methodIdLength > 0) {
      byte[] methodIdBytes = new byte[methodIdLength];
      buffer.get(methodIdBytes);
      methodId = new String(methodIdBytes);
    }
    
    // Read payload length
    int payloadLength = buffer.getInt();
    
    return new RpcMessageHeader(type, messageId, methodId, payloadLength);
  }
  
  @Override
  public String toString() {
    return "RpcMessageHeader{" +
        "type=" + type +
        ", messageId=" + messageId +
        ", methodId='" + methodId + '\'' +
        ", payloadLength=" + payloadLength +
        '}';
  }
}