package io.github.panghy.javaflow.rpc.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a complete RPC message in the JavaFlow RPC framework.
 * An RPC message consists of a header, method information, promise IDs,
 * and a serialized payload.
 * 
 * <p>The RPC message is the fundamental unit of communication in the RPC framework.
 * It encapsulates all the information needed to invoke a method on a remote endpoint
 * or to deliver results back to the caller.</p>
 * 
 * <p>Message structure:</p>
 * <pre>
 * +----------------+------------------+---------------+----------------+
 * | Message Header | Interface Method | Promise IDs   | Payload        |
 * +----------------+------------------+---------------+----------------+
 * </pre>
 * 
 * <p>This class provides methods for serializing and deserializing complete
 * RPC messages to and from ByteBuffers for network transmission.</p>
 */
public class RpcMessage {

  private final RpcMessageHeader header;
  private final List<UUID> promiseIds;
  private final ByteBuffer payload;
  
  /**
   * Creates a new RPC message.
   *
   * @param header     The message header
   * @param promiseIds The list of promise IDs (can be empty)
   * @param payload    The serialized payload
   */
  public RpcMessage(RpcMessageHeader header, List<UUID> promiseIds, ByteBuffer payload) {
    this.header = header;
    this.promiseIds = promiseIds != null ? promiseIds : new ArrayList<>();
    this.payload = payload;
  }
  
  /**
   * Creates a new RPC message.
   *
   * @param type       The message type
   * @param messageId  The unique message ID
   * @param methodId   The method identifier
   * @param promiseIds The list of promise IDs (can be empty)
   * @param payload    The serialized payload
   */
  public RpcMessage(RpcMessageHeader.MessageType type, UUID messageId, String methodId, 
                   List<UUID> promiseIds, ByteBuffer payload) {
    this(new RpcMessageHeader(type, messageId, methodId, 
         payload != null ? payload.remaining() : 0), 
         promiseIds, payload);
  }
  
  /**
   * Gets the message header.
   *
   * @return The message header
   */
  public RpcMessageHeader getHeader() {
    return header;
  }
  
  /**
   * Gets the list of promise IDs.
   *
   * @return The promise IDs
   */
  public List<UUID> getPromiseIds() {
    return promiseIds;
  }
  
  /**
   * Gets the serialized payload.
   *
   * @return The payload
   */
  public ByteBuffer getPayload() {
    return payload != null ? payload.duplicate() : null;
  }
  
  /**
   * Serializes this message to a ByteBuffer.
   *
   * @return A ByteBuffer containing the serialized message
   */
  public ByteBuffer serialize() {
    // Calculate total size
    int headerSize = header.serialize().remaining();
    int promiseIdsSize = 2 + (promiseIds.size() * 16); // 2 bytes for count + 16 bytes per UUID
    int payloadSize = payload != null ? payload.remaining() : 0;
    int totalSize = headerSize + promiseIdsSize + payloadSize;
    
    ByteBuffer buffer = ByteBuffer.allocate(totalSize);
    
    // Write header
    buffer.put(header.serialize());
    
    // Write promise IDs count
    buffer.putShort((short) promiseIds.size());
    
    // Write promise IDs
    for (UUID id : promiseIds) {
      buffer.putLong(id.getMostSignificantBits());
      buffer.putLong(id.getLeastSignificantBits());
    }
    
    // Write payload
    if (payload != null && payload.hasRemaining()) {
      buffer.put(payload.duplicate());
    }
    
    // Reset position for reading
    buffer.flip();
    return buffer;
  }
  
  /**
   * Deserializes a message from a ByteBuffer.
   *
   * @param buffer The buffer containing the serialized message
   * @return The deserialized message
   */
  public static RpcMessage deserialize(ByteBuffer buffer) {
    // Read header
    RpcMessageHeader header = RpcMessageHeader.deserialize(buffer);
    
    // Read promise IDs
    short promiseIdCount = buffer.getShort();
    List<UUID> promiseIds = new ArrayList<>(promiseIdCount);
    for (int i = 0; i < promiseIdCount; i++) {
      long mostSigBits = buffer.getLong();
      long leastSigBits = buffer.getLong();
      promiseIds.add(new UUID(mostSigBits, leastSigBits));
    }
    
    // Read payload
    ByteBuffer payload = null;
    if (header.getPayloadLength() > 0) {
      payload = ByteBuffer.allocate(header.getPayloadLength());
      // Copy the right number of bytes into the payload buffer
      byte[] bytes = new byte[header.getPayloadLength()];
      buffer.get(bytes);
      payload.put(bytes);
      payload.flip();
    }
    
    return new RpcMessage(header, promiseIds, payload);
  }
  
  @Override
  public String toString() {
    return "RpcMessage{" +
        "header=" + header +
        ", promiseIds=" + promiseIds +
        ", payloadSize=" + (payload != null ? payload.remaining() : 0) +
        '}';
  }
}