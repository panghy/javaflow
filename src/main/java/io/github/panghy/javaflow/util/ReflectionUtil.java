package io.github.panghy.javaflow.util;

import java.lang.reflect.Field;

/**
 * Utility methods for reflection-based operations.
 */
public final class ReflectionUtil {

  private ReflectionUtil() {
    // Utility class should not be instantiated
  }

  /**
   * Gets a field value from an object using reflection.
   *
   * @param object The object to get the field from
   * @param fieldName The name of the field
   * @param <T> The expected type of the field value
   * @return The value of the field
   * @throws RuntimeException if field access fails
   */
  @SuppressWarnings("unchecked")
  public static <T> T getField(Object object, String fieldName) {
    try {
      Field field = findField(object.getClass(), fieldName);
      field.setAccessible(true);
      return (T) field.get(object);
    } catch (Exception e) {
      throw new RuntimeException("Failed to access field: " + fieldName, e);
    }
  }

  /**
   * Sets a field value on an object using reflection.
   *
   * @param object The object to set the field on
   * @param fieldName The name of the field
   * @param value The value to set
   * @throws RuntimeException if field access fails
   */
  public static void setField(Object object, String fieldName, Object value) {
    try {
      Field field = findField(object.getClass(), fieldName);
      field.setAccessible(true);
      field.set(object, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
  }

  /**
   * Finds a field in a class or its superclasses.
   *
   * @param clazz The class to search for the field
   * @param fieldName The name of the field
   * @return The field
   * @throws NoSuchFieldException if the field cannot be found
   */
  private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = clazz.getSuperclass();
      if (superClass == null) {
        throw e;
      }
      return findField(superClass, fieldName);
    }
  }
}