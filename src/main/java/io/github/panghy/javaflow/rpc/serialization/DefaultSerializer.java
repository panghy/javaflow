package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.error.RpcSerializationException;
import io.github.panghy.javaflow.util.Tuple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

import static io.github.panghy.javaflow.util.Tuple.packItems;

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

  /**
   * Serialized form of null.
   */
  private static final byte[] NULL_PACKED = packItems(new Object[]{null});

  @Override
  public ByteBuffer serialize(T obj) {
    if (obj == null) {
      return ByteBuffer.wrap(NULL_PACKED);
    }

    if (!(obj instanceof Serializable)) {
      throw new RpcSerializationException(
          obj.getClass(), "Object is not Serializable");
    }

    switch (obj) {
      case String s -> {
        return ByteBuffer.wrap(packItems(s));
      }
      case Integer num -> {
        return ByteBuffer.wrap(packItems(num));
      }
      case Long num -> {
        return ByteBuffer.wrap(packItems(num));
      }
      case Boolean bool -> {
        return ByteBuffer.wrap(packItems(bool));
      }
      case Byte b -> {
        return ByteBuffer.wrap(packItems(b));
      }
      case Short num -> {
        return ByteBuffer.wrap(packItems(num));
      }
      case Float num -> {
        return ByteBuffer.wrap(packItems(num));
      }
      case Double num -> {
        return ByteBuffer.wrap(packItems(num));
      }
      case Character c -> {
        return ByteBuffer.wrap(packItems(c));
      }
      default -> {
        // Fall through to Java serialization
      }
    }
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // For other Serializable types, use Java serialization
      try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {

        oos.writeObject(obj);
        oos.flush();

        byte[] bytes = baos.toByteArray();
        return ByteBuffer.wrap(packItems(new Object[]{bytes}));
      }
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
    if (FlowFuture.class.isAssignableFrom(expectedType)) {
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
      if (expectedType == String.class) {
        return (T) Tuple.readString(buffer);
      } else if (expectedType == Integer.class || expectedType == int.class) {
        return (T) Integer.valueOf(Tuple.readInt(buffer));
      } else if (expectedType == Long.class || expectedType == long.class) {
        return (T) Long.valueOf(Tuple.readLong(buffer));
      } else if (expectedType == Boolean.class || expectedType == boolean.class) {
        return (T) Boolean.valueOf(Tuple.readBoolean(buffer));
      } else if (expectedType == Byte.class || expectedType == byte.class) {
        return (T) Byte.valueOf(Tuple.readByte(buffer));
      } else if (expectedType == Short.class || expectedType == short.class) {
        return (T) Short.valueOf(Tuple.readShort(buffer));
      } else if (expectedType == Float.class || expectedType == float.class) {
        return (T) Float.valueOf(Tuple.readFloat(buffer));
      } else if (expectedType == Double.class || expectedType == double.class) {
        return (T) Double.valueOf(Tuple.readDouble(buffer));
      } else if (expectedType == Character.class || expectedType == char.class) {
        return (T) Character.valueOf(Tuple.readChar(buffer));
      } else if (expectedType == Void.class || expectedType == void.class) {
        return null;
      }

      Object onlyItem = Tuple.readSingleItem(buffer);
      if (onlyItem == null) {
        return null;
      } else if (!(onlyItem instanceof byte[])) {
        // Not a byte array, so it's not Java serialized
        
        // Special handling for numeric conversions since Tuple stores all integers as Long
        if (onlyItem instanceof Long && (expectedType == Integer.class || expectedType == int.class)) {
          Long longValue = (Long) onlyItem;
          // Check if the value fits in an Integer
          if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
            return (T) Integer.valueOf(longValue.intValue());
          } else {
            throw new RpcSerializationException(
                expectedType, "Long value " + longValue + " cannot be converted to Integer");
          }
        } else if (onlyItem instanceof Long && (expectedType == Short.class || expectedType == short.class)) {
          Long longValue = (Long) onlyItem;
          // Check if the value fits in a Short
          if (longValue >= Short.MIN_VALUE && longValue <= Short.MAX_VALUE) {
            return (T) Short.valueOf(longValue.shortValue());
          } else {
            throw new RpcSerializationException(
                expectedType, "Long value " + longValue + " cannot be converted to Short");
          }
        } else if (onlyItem instanceof Long && (expectedType == Byte.class || expectedType == byte.class)) {
          Long longValue = (Long) onlyItem;
          // Check if the value fits in a Byte
          if (longValue >= Byte.MIN_VALUE && longValue <= Byte.MAX_VALUE) {
            return (T) Byte.valueOf(longValue.byteValue());
          } else {
            throw new RpcSerializationException(
                expectedType, "Long value " + longValue + " cannot be converted to Byte");
          }
        }
        
        if (!expectedType.isAssignableFrom(onlyItem.getClass())) {
          throw new RpcSerializationException(
              expectedType, "Deserialized object type " + onlyItem.getClass().getName() +
                            " does not match expected type");
        }
        return (T) onlyItem;
      } else {
        // Java serialization
        try (ByteArrayInputStream bais = new ByteArrayInputStream((byte[]) onlyItem)) {
          // For other Serializable types, use Java deserialization
          try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            if (expectedType.isAssignableFrom(obj.getClass())) {
              return (T) obj;
            } else {
              throw new RpcSerializationException(
                  expectedType, "Deserialized object type " + obj.getClass().getName() +
                                " does not match expected type");
            }
          }
        }
      }
    } catch (IllegalArgumentException | IOException | ClassNotFoundException e) {
      throw new RpcSerializationException(expectedType, "Failed to deserialize object", e);
    }
  }
}