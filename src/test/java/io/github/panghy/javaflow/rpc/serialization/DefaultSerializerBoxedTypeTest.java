package io.github.panghy.javaflow.rpc.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for DefaultSerializer handling of boxed types and arrays.
 * 
 * Note: DefaultSerializer has an asymmetric design where primitive wrapper types
 * are deserialized from raw bytes for efficiency, but serialized using Java serialization.
 * This test focuses on the type compatibility checking that happens when using
 * Java serialization for complex objects.
 */
public class DefaultSerializerBoxedTypeTest {

  private DefaultSerializer<TestObject> objectSerializer;

  @BeforeEach
  void setUp() {
    objectSerializer = new DefaultSerializer<>();
  }

  @Test
  void testPrimitiveTypeCompatibilityInDeserialization() {
    // Test that when deserializing with primitive types, they are treated as their boxed equivalents
    DefaultSerializer<Integer> intSerializer = new DefaultSerializer<>();

    // Create raw bytes for Integer deserialization
    ByteBuffer buffer = intSerializer.serialize(42);

    // Deserialize with primitive type should work (treated as Integer)
    Integer result = intSerializer.deserialize(buffer, int.class);
    assertEquals(42, result);
    
    // And of course with Integer.class
    buffer.rewind();
    Integer result2 = intSerializer.deserialize(buffer, Integer.class);
    assertEquals(42, result2);
  }

  @Test
  void testAllPrimitiveTypesRawBytes() {
    // Test all primitive/boxed pairs with raw bytes
    testPrimitiveWithRawBytes();
  }

  private void testPrimitiveWithRawBytes() {
    // Boolean
    DefaultSerializer<Boolean> boolSerializer = new DefaultSerializer<>();
    ByteBuffer boolBuffer = boolSerializer.serialize(true);
    Boolean boolResult = boolSerializer.deserialize(boolBuffer, boolean.class);
    assertEquals(true, boolResult);
    
    // Long
    DefaultSerializer<Long> longSerializer = new DefaultSerializer<>();
    ByteBuffer longBuffer = longSerializer.serialize(10000L);
    Long longResult = longSerializer.deserialize(longBuffer, long.class);
    assertEquals(10000L, longResult);
    
    // Double
    DefaultSerializer<Double> doubleSerializer = new DefaultSerializer<>();
    ByteBuffer doubleBuffer = doubleSerializer.serialize(3.14159);
    Double doubleResult = doubleSerializer.deserialize(doubleBuffer, double.class);
    assertEquals(3.14159, doubleResult);
  }

  @Test
  void testComplexObjectWithMixedTypes() {
    // Test a complex object containing both primitives and boxed types
    // This tests the Java serialization path where type compatibility matters
    TestObject obj = new TestObject();
    obj.primitiveInt = 42;
    obj.boxedInt = 24;
    obj.primitiveArray = new int[]{1, 2, 3};
    obj.boxedArray = new Integer[]{4, 5, 6};
    
    ByteBuffer buffer = objectSerializer.serialize(obj);
    TestObject result = objectSerializer.deserialize(buffer, TestObject.class);
    
    assertEquals(obj.primitiveInt, result.primitiveInt);
    assertEquals(obj.boxedInt, result.boxedInt);
    assertArrayEquals(obj.primitiveArray, result.primitiveArray);
    assertArrayEquals(obj.boxedArray, result.boxedArray);
  }

  @Test
  void testSerializedObjectTypeCompatibility() {
    // Test type compatibility when deserializing Java-serialized objects
    // This demonstrates the isTypeCompatible method in action
    
    // Create a custom wrapper class to force Java serialization path
    SerializableWrapper wrapper = new SerializableWrapper();
    wrapper.value = 42;
    
    DefaultSerializer<SerializableWrapper> wrapperSerializer = new DefaultSerializer<>();
    ByteBuffer buffer = wrapperSerializer.serialize(wrapper);
    
    // Should deserialize successfully with exact type
    SerializableWrapper result = wrapperSerializer.deserialize(buffer, SerializableWrapper.class);
    assertEquals(wrapper.value, result.value);
    
    // Test with Object.class - should work due to type compatibility
    buffer.rewind();
    DefaultSerializer<Object> objSerializer = new DefaultSerializer<>();
    Object objResult = objSerializer.deserialize(buffer, Object.class);
    assertNotNull(objResult);
    assertEquals(SerializableWrapper.class, objResult.getClass());
  }
  
  // Helper class to test Java serialization path
  static class SerializableWrapper implements Serializable {
    Integer value;
  }

  @Test
  void testArraySerialization() {
    // Test array serialization/deserialization
    DefaultSerializer<int[]> primitiveArraySerializer = new DefaultSerializer<>();
    int[] primitiveArray = {1, 2, 3, 4, 5};
    
    ByteBuffer buffer = primitiveArraySerializer.serialize(primitiveArray);
    int[] result = primitiveArraySerializer.deserialize(buffer, int[].class);
    assertArrayEquals(primitiveArray, result);
    
    // Test Integer array
    DefaultSerializer<Integer[]> boxedArraySerializer = new DefaultSerializer<>();
    Integer[] boxedArray = {1, 2, 3, 4, 5};
    
    ByteBuffer buffer2 = boxedArraySerializer.serialize(boxedArray);
    Integer[] result2 = boxedArraySerializer.deserialize(buffer2, Integer[].class);
    assertArrayEquals(boxedArray, result2);
  }

  @Test
  void testMultiDimensionalArrays() {
    // Test 2D arrays
    DefaultSerializer<int[][]> array2DSerializer = new DefaultSerializer<>();
    int[][] primitive2D = {{1, 2}, {3, 4}};
    ByteBuffer buffer = array2DSerializer.serialize(primitive2D);
    
    int[][] result = array2DSerializer.deserialize(buffer, int[][].class);
    assertNotNull(result);
    assertEquals(2, result.length);
    assertArrayEquals(primitive2D[0], result[0]);
    assertArrayEquals(primitive2D[1], result[1]);
  }

  // Test class with mixed primitive and boxed fields
  static class TestObject implements Serializable {
    int primitiveInt;
    Integer boxedInt;
    int[] primitiveArray;
    Integer[] boxedArray;
  }
}