package io.github.panghy.javaflow.rpc.serialization;

/**
 * Factory interface for creating serializers for specific types.
 * This interface is used to generate serializers for types that don't have
 * a directly registered serializer.
 * 
 * <p>SerializerFactory is used by the FlowSerialization system to dynamically create
 * serializers for types based on packages or other criteria. This allows for
 * more flexible serialization configurations where entire packages of types
 * can be handled by a specific serialization strategy.</p>
 * 
 * <p>For example, a ProtobufSerializerFactory might be registered for the
 * "com.example.proto" package, which would automatically create appropriate
 * serializers for all Protocol Buffer message types in that package.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create a factory for JSON serialization
 * SerializerFactory jsonFactory = new SerializerFactory() {
 *   @Override
 *   public <T> Serializer<T> createSerializer(Class<T> type) {
 *     return new JsonSerializer<>(type);
 *   }
 * };
 * 
 * // Register it for a package
 * FlowSerialization.registerSerializerFactory("com.example.model", jsonFactory);
 * }</pre>
 * 
 * @see FlowSerialization
 * @see Serializer
 */
public interface SerializerFactory {

  /**
   * Creates a serializer for the specified type.
   *
   * @param <T>  The type parameter
   * @param type The class to create a serializer for
   * @return A serializer that can handle the specified type, or null if this
   *         factory cannot create a serializer for the given type
   */
  <T> Serializer<T> createSerializer(Class<T> type);
}