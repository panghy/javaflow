package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.error.RpcSerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Default serializer implementation using Java's built-in serialization.
 * This serializer can handle any object that implements {@link Serializable},
 * but will throw exceptions for non-serializable objects.
 * 
 * <p>This is provided as a simple default implementation, but for production use,
 * it's recommended to implement a custom serializer with better performance and
 * smaller serialized size using frameworks like Protocol Buffers, JSON, etc.</p>
 * 
 * <p>The default serializer does not handle system types like {@link FlowPromise} and
 * {@link PromiseStream} directly, as these are transformed into references/IDs before
 * serialization by the RPC layer.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Register as the default serializer
 * FlowSerialization.setDefaultSerializer(new DefaultSerializer());
 * 
 * // Use it to serialize an object
 * User user = new User("John", 30);
 * ByteBuffer buffer = new DefaultSerializer().serialize(user);
 * 
 * // And to deserialize
 * User deserializedUser = new DefaultSerializer().deserialize(buffer, User.class);
 * }</pre>
 * 
 * @param <T> The type of object this serializer handles
 */
public class DefaultSerializer<T> implements Serializer<T> {

  @Override
  public ByteBuffer serialize(T obj) {
    if (obj == null) {
      return ByteBuffer.allocate(0);
    }
    
    if (!(obj instanceof Serializable)) {
      throw new RpcSerializationException(
          obj.getClass(), "Object is not Serializable");
    }
    
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      
      oos.writeObject(obj);
      oos.flush();
      
      byte[] bytes = baos.toByteArray();
      return ByteBuffer.wrap(bytes);
      
    } catch (IOException e) {
      throw new RpcSerializationException(obj.getClass(), "Failed to serialize object", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T deserialize(ByteBuffer buffer, Class<? extends T> expectedType) {
    if (buffer == null || !buffer.hasRemaining()) {
      return null;
    }
    
    // Special handling for primitive wrappers and common types
    if (expectedType == String.class) {
      return (T) deserializeString(buffer);
    } else if (expectedType == Integer.class) {
      return (T) Integer.valueOf(buffer.getInt());
    } else if (expectedType == Long.class) {
      return (T) Long.valueOf(buffer.getLong());
    } else if (expectedType == Boolean.class) {
      return (T) Boolean.valueOf(buffer.get() != 0);
    } else if (expectedType == Byte.class) {
      return (T) Byte.valueOf(buffer.get());
    } else if (expectedType == Short.class) {
      return (T) Short.valueOf(buffer.getShort());
    } else if (expectedType == Float.class) {
      return (T) Float.valueOf(buffer.getFloat());
    } else if (expectedType == Double.class) {
      return (T) Double.valueOf(buffer.getDouble());
    } else if (expectedType == Character.class) {
      return (T) Character.valueOf(buffer.getChar());
    } else if (expectedType == Void.class || expectedType == void.class) {
      return null;
    } else if (FlowFuture.class.isAssignableFrom(expectedType)) {
      // FlowFuture instances are handled specially by the RPC layer
      // and should not be directly serialized/deserialized
      throw new RpcSerializationException(
          FlowFuture.class, "Cannot be directly serialized/deserialized");
    } else if (FlowPromise.class.isAssignableFrom(expectedType)) {
      // FlowPromise instances are handled specially by the RPC layer
      // and should not be directly serialized/deserialized
      throw new RpcSerializationException(
          FlowPromise.class, "Cannot be directly serialized/deserialized");
    } else if (PromiseStream.class.isAssignableFrom(expectedType)) {
      // PromiseStream instances are handled specially by the RPC layer
      // and should not be directly serialized/deserialized
      throw new RpcSerializationException(
          PromiseStream.class, "Cannot be directly serialized/deserialized");
    }
    
    // Default handling using Java serialization
    try {
      buffer = buffer.duplicate();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      
      try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
           ObjectInputStream ois = new ObjectInputStream(bais)) {
        
        Object obj = ois.readObject();
        
        if (expectedType.isInstance(obj)) {
          return (T) obj;
        } else {
          throw new RpcSerializationException(
              expectedType, "Deserialized object type " + obj.getClass().getName() + 
              " does not match expected type");
        }
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new RpcSerializationException(expectedType, "Failed to deserialize object", e);
    }
  }
  
  /**
   * Deserializes a String from a ByteBuffer.
   *
   * @param buffer The buffer containing the serialized string
   * @return The deserialized string
   */
  private String deserializeString(ByteBuffer buffer) {
    // For strings, we'll use standard Java serialization
    try {
      buffer = buffer.duplicate();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      
      try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
           ObjectInputStream ois = new ObjectInputStream(bais)) {
        
        return (String) ois.readObject();
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new RpcSerializationException(String.class, "Failed to deserialize string", e);
    }
  }
}