package io.github.panghy.javaflow.rpc.serialization;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

/**
 * A serializable description of a type, including generic type information.
 * This class is used to transmit type information over the network,
 * preserving generics that would otherwise be lost due to type erasure.
 * 
 * <p>TypeDescription can represent simple classes, as well as parameterized
 * types (generic classes with type arguments). It is designed to be
 * serializable so that it can be sent over the network as part of the
 * JavaFlow RPC framework.</p>
 */
public class TypeDescription implements Serializable {
  
  private final String className;
  private final TypeDescription[] typeArguments;
  
  /**
   * Creates a new TypeDescription for a simple class.
   *
   * @param clazz The class
   */
  public TypeDescription(Class<?> clazz) {
    this.className = clazz.getName();
    this.typeArguments = new TypeDescription[0];
  }
  
  /**
   * Creates a new TypeDescription.
   *
   * @param className     The class name
   * @param typeArguments The type arguments
   */
  public TypeDescription(String className, TypeDescription... typeArguments) {
    this.className = className;
    this.typeArguments = typeArguments;
  }
  
  /**
   * Creates a TypeDescription from a Type object.
   *
   * @param type The type to describe
   * @return A TypeDescription
   */
  public static TypeDescription fromType(Type type) {
    if (type instanceof Class<?>) {
      return new TypeDescription((Class<?>) type);
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Class<?> rawType = (Class<?>) parameterizedType.getRawType();
      
      Type[] typeArguments = parameterizedType.getActualTypeArguments();
      TypeDescription[] typeDescriptions = new TypeDescription[typeArguments.length];
      
      for (int i = 0; i < typeArguments.length; i++) {
        typeDescriptions[i] = fromType(typeArguments[i]);
      }
      
      return new TypeDescription(rawType.getName(), typeDescriptions);
    } else {
      throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }
  
  /**
   * Creates a Type object from this description.
   *
   * @return A Type object
   * @throws ClassNotFoundException if the class cannot be found
   */
  public Type toType() throws ClassNotFoundException {
    Class<?> rawType = Class.forName(className);
    
    if (typeArguments.length == 0) {
      return rawType;
    } else {
      // Convert TypeDescription[] to Type[]
      Type[] typeArgs = getTypeArgumentsAsTypes();
      return new ParameterizedTypeImpl(rawType, typeArgs);
    }
  }
  
  /**
   * Gets the class name.
   *
   * @return The class name
   */
  public String getClassName() {
    return className;
  }
  
  /**
   * Gets the type arguments.
   *
   * @return The type arguments
   */
  public TypeDescription[] getTypeArguments() {
    return typeArguments;
  }
  
  /**
   * Gets the type arguments as Type objects.
   *
   * @return The type arguments as Type objects
   * @throws ClassNotFoundException if a class cannot be found
   */
  public Type[] getTypeArgumentsAsTypes() throws ClassNotFoundException {
    Type[] types = new Type[typeArguments.length];
    for (int i = 0; i < typeArguments.length; i++) {
      types[i] = typeArguments[i].toType();
    }
    return types;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TypeDescription that = (TypeDescription) o;
    return Objects.equals(className, that.className)
        && Arrays.equals(typeArguments, that.typeArguments);
  }
  
  @Override
  public int hashCode() {
    int result = Objects.hash(className);
    result = 31 * result + Arrays.hashCode(typeArguments);
    return result;
  }
  
  @Override
  public String toString() {
    if (typeArguments.length == 0) {
      return className;
    }
    
    StringBuilder sb = new StringBuilder(className);
    sb.append('<');
    for (int i = 0; i < typeArguments.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(typeArguments[i]);
    }
    sb.append('>');
    
    return sb.toString();
  }
  
  /**
   * Implementation of ParameterizedType for creating type objects.
   */
  private static class ParameterizedTypeImpl implements ParameterizedType, Serializable {
    private final Class<?> rawType;
    private final Type[] actualTypeArguments;
    
    ParameterizedTypeImpl(Class<?> rawType, Type[] actualTypeArguments) {
      this.rawType = rawType;
      this.actualTypeArguments = actualTypeArguments;
    }
    
    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }
    
    @Override
    public Type getRawType() {
      return rawType;
    }
    
    @Override
    public Type getOwnerType() {
      return null;
    }
    
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || !(o instanceof ParameterizedType)) {
        return false;
      }
      
      ParameterizedType that = (ParameterizedType) o;
      return Objects.equals(rawType, that.getRawType())
          && Arrays.equals(actualTypeArguments, that.getActualTypeArguments())
          && Objects.equals(null, that.getOwnerType());
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(rawType) ^ Arrays.hashCode(actualTypeArguments);
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(rawType.getName());
      if (actualTypeArguments.length > 0) {
        sb.append('<');
        for (int i = 0; i < actualTypeArguments.length; i++) {
          if (i > 0) {
            sb.append(", ");
          }
          sb.append(actualTypeArguments[i].getTypeName());
        }
        sb.append('>');
      }
      return sb.toString();
    }
  }
}