package io.github.panghy.javaflow.rpc.serialization;

import java.nio.ByteBuffer;

/**
 * Interface for serializing and deserializing objects for RPC communication.
 * Implementations of this interface handle the conversion between Java objects
 * and byte buffers for network transmission.
 * 
 * <p>The Serializer interface is the core of JavaFlow's serialization system.
 * It provides a simple contract for converting objects to and from byte sequences
 * for network transmission. Each RPC call payload is serialized using a Serializer
 * before transmission and deserialized upon reception.</p>
 * 
 * <p>The framework allows for pluggable serializer implementations to support
 * different serialization formats (JSON, Protocol Buffers, custom binary, etc.).
 * Users can register custom serializers for specific types or packages.</p>
 * 
 * <p>Built-in special handlers are provided for system types like {@link java.util.concurrent.CompletableFuture}
 * and {@link io.github.panghy.javaflow.core.PromiseStream} to enable them to be
 * serialized as references that can cross the network.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Get a serializer for a specific type
 * Serializer<UserInfo> userSerializer = FlowSerialization.getSerializer(UserInfo.class);
 * 
 * // Serialize an object
 * UserInfo user = new UserInfo("user123", "John Doe");
 * ByteBuffer buffer = userSerializer.serialize(user);
 * 
 * // Deserialize an object
 * UserInfo deserializedUser = userSerializer.deserialize(buffer, UserInfo.class);
 * }</pre>
 * 
 * @param <T> The type of object this serializer can handle
 */
public interface Serializer<T> {

  /**
   * Serializes an object to a ByteBuffer.
   *
   * @param obj The object to serialize
   * @return A ByteBuffer containing the serialized object
   */
  ByteBuffer serialize(T obj);

  /**
   * Deserializes an object from a ByteBuffer.
   *
   * @param buffer The buffer containing the serialized object
   * @param expectedType The expected class of the deserialized object
   * @return The deserialized object
   */
  T deserialize(ByteBuffer buffer, Class<? extends T> expectedType);
}