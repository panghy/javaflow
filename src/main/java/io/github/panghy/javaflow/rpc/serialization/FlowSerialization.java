package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.core.PromiseStream;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for serializers in the JavaFlow RPC framework.
 * This class manages serializers for different types and provides
 * methods for serializing and deserializing objects.
 * 
 * <p>FlowSerialization is the central point for serialization operations
 * in the RPC framework. It maintains a registry of serializers for different
 * types and provides methods for serializing and deserializing objects.</p>
 * 
 * <p>The registry supports:</p>
 * <ul>
 *   <li>Direct registration of serializers for specific types</li>
 *   <li>Registration of serializer factories for packages</li>
 *   <li>Special handling for system types (FlowPromise, PromiseStream, etc.)</li>
 * </ul>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Register a serializer for a specific type
 * FlowSerialization.registerSerializer(UserInfo.class, new JsonSerializer<>(UserInfo.class));
 * 
 * // Register a serializer factory for a package
 * FlowSerialization.registerSerializerFactory(
 *     "com.example.model", 
 *     new ProtobufSerializerFactory()
 * );
 * 
 * // Serialize an object
 * UserInfo user = new UserInfo("user123", "John Doe");
 * ByteBuffer buffer = FlowSerialization.serialize(user);
 * 
 * // Deserialize an object
 * UserInfo deserializedUser = FlowSerialization.deserialize(buffer, UserInfo.class);
 * }</pre>
 */
public class FlowSerialization {

  // Map of registered serializers by type
  private static final Map<Class<?>, Serializer<?>> serializers = new ConcurrentHashMap<>();
  
  // Map of serializer factories by package
  private static final Map<String, SerializerFactory> serializerFactories = new ConcurrentHashMap<>();
  
  // Default serializer for types without a specific serializer
  private static Serializer<Object> defaultSerializer = new DefaultSerializer<>();

  // Flag to control whether system types are initialized
  private static boolean systemTypesInitialized = false;
  
  // Private constructor to prevent instantiation
  private FlowSerialization() {
  }
  
  /**
   * Initializes serializers for system types.
   * This is called automatically on first use of the FlowSerialization class.
   */
  private static void initializeSystemTypes() {
    if (systemTypesInitialized) {
      return;
    }
    
    // Register serializers for primitive types and common Java types
    registerSerializer(String.class, new DefaultSerializer<>());
    registerSerializer(Integer.class, new DefaultSerializer<>());
    registerSerializer(int.class, new DefaultSerializer<>());
    registerSerializer(Long.class, new DefaultSerializer<>());
    registerSerializer(long.class, new DefaultSerializer<>());
    registerSerializer(Boolean.class, new DefaultSerializer<>());
    registerSerializer(boolean.class, new DefaultSerializer<>());
    registerSerializer(Byte.class, new DefaultSerializer<>());
    registerSerializer(byte.class, new DefaultSerializer<>());
    registerSerializer(Short.class, new DefaultSerializer<>());
    registerSerializer(short.class, new DefaultSerializer<>());
    registerSerializer(Float.class, new DefaultSerializer<>());
    registerSerializer(float.class, new DefaultSerializer<>());
    registerSerializer(Double.class, new DefaultSerializer<>());
    registerSerializer(double.class, new DefaultSerializer<>());
    registerSerializer(Character.class, new DefaultSerializer<>());
    registerSerializer(char.class, new DefaultSerializer<>());
    
    // Note: Special types like FlowPromise and PromiseStream are not registered directly
    // as they require contextual information (promiseTracker, streamManager, destination)
    // that is only available at the time of serialization/deserialization.
    // These types are handled by the RPC transport layer before serialization.
    
    systemTypesInitialized = true;
  }

  /**
   * Registers a serializer for a specific type.
   *
   * @param <T>        The type parameter
   * @param type       The class to register the serializer for
   * @param serializer The serializer instance
   */
  public static <T> void registerSerializer(Class<T> type, Serializer<T> serializer) {
    serializers.put(type, serializer);
  }

  /**
   * Registers a serializer factory for a package.
   * All types in the specified package without a specific serializer
   * will use the provided factory to create a serializer.
   *
   * @param packageName The package name (e.g., "com.example.model")
   * @param factory     The serializer factory
   */
  public static void registerSerializerFactory(String packageName, SerializerFactory factory) {
    serializerFactories.put(packageName, factory);
  }

  /**
   * Sets the default serializer used for types that don't have
   * a specific serializer registered.
   *
   * @param serializer The default serializer
   */
  public static void setDefaultSerializer(Serializer<Object> serializer) {
    defaultSerializer = serializer;
  }

  /**
   * Gets a serializer for the specified type.
   * This will first check for a directly registered serializer,
   * then check package-based serializer factories, and finally
   * fall back to the default serializer.
   *
   * @param <T>  The type parameter
   * @param type The class to get a serializer for
   * @return A serializer that can handle the specified type
   * @throws IllegalStateException if no suitable serializer is found
   */
  @SuppressWarnings("unchecked")
  public static <T> Serializer<T> getSerializer(Class<T> type) {
    // Initialize system types if needed
    if (!systemTypesInitialized) {
      initializeSystemTypes();
    }
    
    // Check for direct registration
    Serializer<?> serializer = serializers.get(type);
    if (serializer != null) {
      return (Serializer<T>) serializer;
    }
    
    // Special handling for system types
    if (CompletableFuture.class.isAssignableFrom(type) || 
        PromiseStream.class.isAssignableFrom(type)) {
      // System types need special handling by the RPC transport layer
      // If we're here, it means we're trying to serialize a system type directly,
      // which is not supported as they require contextual information
      throw new IllegalStateException(
          "No serializer found for system type " + type.getName() + 
          ". System types require contextual information and must be handled " +
          "by the RPC transport layer before serialization.");
    }

    // Check for package-based factories
    String packageName = type.getPackage() != null ? type.getPackage().getName() : "";
    while (!packageName.isEmpty()) {
      SerializerFactory factory = serializerFactories.get(packageName);
      if (factory != null) {
        serializer = factory.createSerializer(type);
        if (serializer != null) {
          // Cache the result for future use
          serializers.put(type, serializer);
          return (Serializer<T>) serializer;
        }
      }

      // Move up to parent package
      int lastDot = packageName.lastIndexOf('.');
      if (lastDot > 0) {
        packageName = packageName.substring(0, lastDot);
      } else {
        packageName = "";
      }
    }

    // Fall back to default serializer
    if (defaultSerializer != null) {
      return (Serializer<T>) defaultSerializer;
    }

    throw new IllegalStateException("No serializer found for type " + type.getName());
  }

  /**
   * Serializes an object using the appropriate serializer.
   *
   * @param <T> The type of the object
   * @param obj The object to serialize
   * @return A ByteBuffer containing the serialized object
   */
  public static <T> ByteBuffer serialize(T obj) {
    if (obj == null) {
      return ByteBuffer.allocate(0);
    }

    @SuppressWarnings("unchecked")
    Class<T> type = (Class<T>) obj.getClass();
    Serializer<T> serializer = getSerializer(type);
    return serializer.serialize(obj);
  }

  /**
   * Deserializes an object from a ByteBuffer.
   *
   * @param <T>  The type of the object
   * @param buffer The buffer containing the serialized object
   * @param type The expected type of the object
   * @return The deserialized object
   */
  public static <T> T deserialize(ByteBuffer buffer, Class<T> type) {
    if (buffer == null || buffer.remaining() == 0) {
      return null;
    }

    Serializer<T> serializer = getSerializer(type);
    return serializer.deserialize(buffer, type);
  }
  
  /**
   * Deserializes an object from a ByteBuffer, using an appropriate serializer
   * based on the object's class information in the buffer.
   *
   * @param buffer The buffer containing the serialized object
   * @return The deserialized object
   */
  @SuppressWarnings("unchecked")
  public static Object deserialize(ByteBuffer buffer) {
    if (buffer == null || buffer.remaining() == 0) {
      return null;
    }
    
    // For simplicity, assume the first bytes represent a string class name
    // This is a placeholder implementation - a real implementation would use
    // something like the type code from the serialized data
    
    // For now, just use the default serializer with Object.class
    return deserialize(buffer, Object.class);
  }
  
  /**
   * Clears all registered serializers and factories.
   * This is primarily used for testing to reset the state.
   */
  public static void clear() {
    serializers.clear();
    serializerFactories.clear();
    defaultSerializer = null;
  }
}