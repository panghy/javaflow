package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.error.RpcSerializationException;
import io.github.panghy.javaflow.rpc.stream.RemotePromiseStream;
import io.github.panghy.javaflow.rpc.stream.StreamManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Serializer for PromiseStream objects in the JavaFlow RPC framework.
 * This serializer doesn't actually serialize the stream itself, but rather
 * registers it with the StreamManager and serializes its ID along with type information.
 * 
 * <p>Streams in the RPC framework are not serialized directly. Instead,
 * they are registered with the StreamManager and a unique ID is generated
 * for each stream. This ID and the value type information are serialized and 
 * sent over the network. On the receiving side, a local or remote stream is 
 * created and mapped to the ID with the correct generic type information.</p>
 * 
 * <p>When values are sent to the stream, they are serialized and sent as
 * separate messages to the remote endpoint, where they are delivered to the
 * corresponding stream. This allows streams to be effectively used across
 * network boundaries with proper generic type information.</p>
 * 
 * <p>This serializer requires access to a StreamManager and the destination
 * endpoint ID, which are typically provided by the RPC transport layer when
 * serializing/deserializing messages.</p>
 * 
 * @param <T> The type of value flowing through the stream
 */
public class PromiseStreamSerializer<T> implements Serializer<PromiseStream<T>> {

  private final StreamManager streamManager;
  private final EndpointId destination;
  private final Type valueType;
  
  /**
   * Creates a new PromiseStreamSerializer.
   *
   * @param streamManager The StreamManager to register streams with
   * @param destination   The destination endpoint
   * @param valueType     The expected value type
   */
  public PromiseStreamSerializer(StreamManager streamManager, EndpointId destination, 
                                Class<T> valueType) {
    this(streamManager, destination, (Type) valueType);
  }
  
  /**
   * Creates a new PromiseStreamSerializer with complete type information.
   *
   * @param streamManager The StreamManager to register streams with
   * @param destination   The destination endpoint
   * @param valueType     The expected value type including generic information
   */
  public PromiseStreamSerializer(StreamManager streamManager, EndpointId destination, 
                                Type valueType) {
    this.streamManager = streamManager;
    this.destination = destination;
    this.valueType = valueType;
  }
  
  /**
   * Creates a new PromiseStreamSerializer using a TypeToken.
   *
   * @param <V>           The value type
   * @param streamManager The StreamManager to register streams with
   * @param destination   The destination endpoint
   * @param typeToken     The TypeToken containing the type information
   * @return A new PromiseStreamSerializer
   */
  public static <V> PromiseStreamSerializer<V> create(
      StreamManager streamManager, EndpointId destination, TypeToken<V> typeToken) {
    return new PromiseStreamSerializer<>(streamManager, destination, typeToken.getType());
  }
  
  @Override
  public ByteBuffer serialize(PromiseStream<T> stream) {
    if (stream == null) {
      return ByteBuffer.allocate(0);
    }
    
    UUID streamId;
    
    if (stream instanceof RemotePromiseStream) {
      // If this is already a RemotePromiseStream, just use its ID
      streamId = ((RemotePromiseStream<T>) stream).getStreamId();
    } else {
      // Otherwise, create a new RemotePromiseStream and register the original stream
      @SuppressWarnings("unchecked")
      RemotePromiseStream<T> remoteStream = streamManager.createRemoteStream(
          destination, (Class<T>) getRawType(valueType));
      streamId = remoteStream.getStreamId();
      
      // Register the original stream with the new ID
      streamManager.registerIncomingStream(streamId, stream);
    }
    
    try {
      // Create a TypeDescription to preserve generic type information
      TypeDescription typeDesc = TypeDescription.fromType(valueType);
      
      // Serialize the type description along with the stream ID
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      
      // Write the stream ID
      oos.writeLong(streamId.getMostSignificantBits());
      oos.writeLong(streamId.getLeastSignificantBits());
      
      // Write the type description
      oos.writeObject(typeDesc);
      
      oos.flush();
      return ByteBuffer.wrap(baos.toByteArray());
      
    } catch (IOException e) {
      throw new RpcSerializationException(PromiseStream.class, 
          "Failed to serialize stream with ID: " + streamId, e);
    }
  }
  
  @Override
  public PromiseStream<T> deserialize(ByteBuffer buffer, Class<? extends PromiseStream<T>> expectedType) {
    if (buffer == null || buffer.remaining() < 16) {
      throw new RpcSerializationException(PromiseStream.class, "Buffer too small to contain a stream ID");
    }
    
    try {
      java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(
          buffer.array(), buffer.position(), buffer.remaining());
      java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
      
      // Extract the stream ID
      long mostSigBits = ois.readLong();
      long leastSigBits = ois.readLong();
      UUID streamId = new UUID(mostSigBits, leastSigBits);
      
      // Extract the type description
      TypeDescription typeDesc = (TypeDescription) ois.readObject();
      Type actualType = typeDesc.toType();
      
      // Create a remote stream for the ID with the correct type
      @SuppressWarnings("unchecked")
      RemotePromiseStream<T> stream = streamManager.createRemoteStream(
          destination, (Class<T>) getRawType(actualType));
      
      // Store the complete type information in the stream
      stream.setValueTypeInfo(actualType);
      
      return stream;
      
    } catch (IOException | ClassNotFoundException e) {
      throw new RpcSerializationException(PromiseStream.class, 
          "Failed to deserialize stream", e);
    }
  }
  
  /**
   * Extracts the raw type from a Type object.
   *
   * @param type The type
   * @return The raw class
   */
  private static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      return (Class<?>) type;
    } else if (type instanceof java.lang.reflect.ParameterizedType) {
      return (Class<?>) ((java.lang.reflect.ParameterizedType) type).getRawType();
    } else {
      throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }
}