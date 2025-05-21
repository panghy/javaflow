package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.RemotePromiseTracker;
import io.github.panghy.javaflow.rpc.stream.StreamManager;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Factory for creating serializers for JavaFlow system types.
 * This factory handles special serialization for system types like
 * {@link FlowPromise} and {@link PromiseStream}, preserving generic
 * type information across the network.
 *
 * <p>System types require special handling because they can't be simply
 * serialized and deserialized like regular objects. Instead, they need to
 * be tracked and managed by the RPC system to provide transparent network
 * semantics.</p>
 *
 * <p>For example, when a promise crosses the network boundary, the RPC system
 * tracks it and ensures that the result is properly propagated back to the
 * original endpoint when the promise is fulfilled, with the correct type
 * information preserved.</p>
 *
 * <p>This factory is typically used internally by the RPC transport layer
 * and not directly by application code.</p>
 */
public class SystemTypeSerializerFactory implements SerializerFactory {

  private final RemotePromiseTracker promiseTracker;
  private final StreamManager streamManager;
  private final EndpointId destination;

  /**
   * Creates a new SystemTypeSerializerFactory.
   *
   * @param promiseTracker The RemotePromiseTracker to use
   * @param streamManager  The StreamManager to use
   * @param destination    The destination endpoint
   */
  public SystemTypeSerializerFactory(RemotePromiseTracker promiseTracker,
                                   StreamManager streamManager,
                                   EndpointId destination) {
    this.promiseTracker = promiseTracker;
    this.streamManager = streamManager;
    this.destination = destination;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public <T> Serializer<T> createSerializer(Class<T> type) {
    // This method creates serializers based on the raw type
    // For full generic type support, use the createSerializerForType method
    
    if (FlowPromise.class.isAssignableFrom(type)) {
      // For FlowPromise types, create a PromiseSerializer with Object as fallback
      return (Serializer<T>) new PromiseSerializer(promiseTracker, destination, Object.class);
    } else if (PromiseStream.class.isAssignableFrom(type)) {
      // For PromiseStream types, create a PromiseStreamSerializer with Object as fallback
      return (Serializer<T>) new PromiseStreamSerializer(streamManager, destination, Object.class);
    } else if (FlowFuture.class.isAssignableFrom(type)) {
      // FlowFuture is typically not serialized directly
      // Instead, we'd handle it similarly to FlowPromise
      return null;
    }

    // For other types, return null to let the next factory handle it
    return null;
  }
  
  /**
   * Creates a serializer for a type with full generic type information.
   *
   * @param <T>  The type parameter
   * @param type The full type including generic information
   * @return A serializer for the specified type
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> Serializer<T> createSerializerForType(Type type) {
    // Get the raw type
    Class<?> rawType = getRawType(type);
    
    if (FlowPromise.class.isAssignableFrom(rawType)) {
      // For FlowPromise, extract the value type from the generic parameters
      Type valueType = extractGenericValueType(type, FlowPromise.class);
      
      if (valueType != null) {
        // Use the extracted value type
        return (Serializer<T>) new PromiseSerializer(promiseTracker, destination, getRawType(valueType));
      } else {
        // Fallback to Object
        return (Serializer<T>) new PromiseSerializer(promiseTracker, destination, Object.class);
      }
    } else if (PromiseStream.class.isAssignableFrom(rawType)) {
      // For PromiseStream, extract the value type from the generic parameters
      Type valueType = extractGenericValueType(type, PromiseStream.class);
      
      if (valueType != null) {
        // Use the extracted value type with full generic information
        return (Serializer<T>) new PromiseStreamSerializer(streamManager, destination, valueType);
      } else {
        // Fallback to Object
        return (Serializer<T>) new PromiseStreamSerializer(streamManager, destination, Object.class);
      }
    } else if (FlowFuture.class.isAssignableFrom(rawType)) {
      // FlowFuture is typically not serialized directly
      return null;
    }
    
    // For other types, return null to let the next factory handle it
    return null;
  }
  
  /**
   * Extracts the raw class from a Type.
   *
   * @param type The type
   * @return The raw class
   */
  private static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      return (Class<?>) type;
    } else if (type instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) type).getRawType();
    } else {
      throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }
  
  /**
   * Extracts the generic value type from a parameterized type.
   *
   * @param type      The type to extract from
   * @param baseClass The base class to match against
   * @return The value type, or null if not found
   */
  private static Type extractGenericValueType(Type type, Class<?> baseClass) {
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Class<?> rawType = (Class<?>) parameterizedType.getRawType();
      
      if (baseClass.isAssignableFrom(rawType)) {
        // Get the first type parameter (T in PromiseStream<T> or FlowPromise<T>)
        Type[] typeArgs = parameterizedType.getActualTypeArguments();
        if (typeArgs.length > 0) {
          return typeArgs[0];
        }
      }
    } else if (type instanceof Class<?>) {
      // Check superclass and interfaces to find the generic type
      Class<?> clazz = (Class<?>) type;
      
      // Check superclass
      Type superclass = clazz.getGenericSuperclass();
      if (superclass != null && superclass != Object.class) {
        Type valueType = extractGenericValueType(superclass, baseClass);
        if (valueType != null) {
          return valueType;
        }
      }
      
      // Check interfaces
      for (Type iface : clazz.getGenericInterfaces()) {
        Type valueType = extractGenericValueType(iface, baseClass);
        if (valueType != null) {
          return valueType;
        }
      }
    }
    
    return null;
  }
}