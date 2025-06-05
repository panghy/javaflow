package io.github.panghy.javaflow.rpc.serialization;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.io.Serializable;
import java.util.Objects;

/**
 * A class to capture and preserve generic type information.
 * This class helps to work around Java's type erasure by capturing
 * and preserving the generic type information at compile time and
 * making it available at runtime.
 * 
 * <p>TypeToken is particularly useful in the JavaFlow RPC framework
 * for maintaining generic type information when serializing and
 * deserializing objects that include generic types, such as
 * PromiseStream&lt;T&gt; instances.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create a TypeToken for a specific generic type
 * TypeToken<List<String>> token = new TypeToken<List<String>>() {};
 * 
 * // Get the actual type
 * Type type = token.getType();
 * 
 * // Use it to create a properly typed serializer
 * Serializer<List<String>> serializer = createSerializer(type);
 * }</pre>
 * 
 * @param <T> The type parameter
 */
public abstract class TypeToken<T> implements Serializable {
  
  private final Type type;
  private final Class<? super T> rawType;
  
  /**
   * Creates a new TypeToken.
   * This constructor captures the generic type information from the subclass.
   */
  @SuppressWarnings("unchecked")
  protected TypeToken() {
    Type superclass = getClass().getGenericSuperclass();
    if (!(superclass instanceof ParameterizedType)) {
      throw new IllegalArgumentException("TypeToken must be created with a type parameter");
    }
    
    this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    this.rawType = (Class<? super T>) getRawType(this.type);
  }
  
  /**
   * Creates a new TypeToken with the specified type.
   * This constructor is used internally by the factory methods.
   */
  @SuppressWarnings("unchecked")
  protected TypeToken(Type type) {
    this.type = type;
    this.rawType = (Class<? super T>) getRawType(type);
  }
  
  /**
   * Creates a TypeToken from a Class object.
   *
   * @param <T> The type parameter
   * @param clazz The class object
   * @return A TypeToken for the specified class
   */
  @SuppressWarnings("unchecked")
  public static <T> TypeToken<T> of(Class<T> clazz) {
    return new SimpleTypeToken<>(clazz);
  }
  
  /**
   * Creates a TypeToken from a ParameterizedType.
   *
   * @param <T> The type parameter
   * @param type The parameterized type
   * @return A TypeToken for the specified type
   */
  @SuppressWarnings("unchecked")
  public static <T> TypeToken<T> of(ParameterizedType type) {
    return new ParameterizedTypeToken<>(type);
  }
  
  /**
   * Gets the captured type.
   *
   * @return The captured type
   */
  public Type getType() {
    return type;
  }
  
  /**
   * Gets the raw type.
   *
   * @return The raw type (erased class)
   */
  public Class<? super T> getRawType() {
    return rawType;
  }
  
  /**
   * Extracts the raw type from a Type object.
   *
   * @param type The type to extract from
   * @return The raw class type
   */
  private static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      return (Class<?>) type;
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      return (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TypeToken<?> typeToken = (TypeToken<?>) o;
    return Objects.equals(type, typeToken.type);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(type);
  }
  
  @Override
  public String toString() {
    return "TypeToken{" + type + '}';
  }
  
  /**
   * Simple implementation for non-parameterized types.
   *
   * @param <T> The type parameter
   */
  private static class SimpleTypeToken<T> extends TypeToken<T> {
    
    SimpleTypeToken(Class<T> type) {
      super(type);
    }
  }
  
  /**
   * Implementation for parameterized types.
   *
   * @param <T> The type parameter
   */
  private static class ParameterizedTypeToken<T> extends TypeToken<T> {
    
    ParameterizedTypeToken(ParameterizedType type) {
      super(type);
    }
  }
}