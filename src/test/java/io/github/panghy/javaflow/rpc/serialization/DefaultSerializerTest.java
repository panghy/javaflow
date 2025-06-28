package io.github.panghy.javaflow.rpc.serialization;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.error.RpcSerializationException;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive tests for {@link DefaultSerializer}.
 */
@SuppressWarnings("unchecked")
public class DefaultSerializerTest {

  @Test
  public void testSerializeNull() {
    DefaultSerializer<Object> serializer = new DefaultSerializer<>();
    ByteBuffer buffer = serializer.serialize(null);
    assertNotNull(buffer);
    assertEquals(1, buffer.remaining());
  }

  @Test
  public void testDeserializeNull() {
    DefaultSerializer<Object> serializer = new DefaultSerializer<>();
    Object result = serializer.deserialize(null, Object.class);
    assertNull(result);
  }

  @Test
  public void testDeserializeEmptyBuffer() {
    DefaultSerializer<Object> serializer = new DefaultSerializer<>();
    ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
    Object result = serializer.deserialize(emptyBuffer, Object.class);
    assertNull(result);
  }

  @Test
  public void testSerializeNonSerializable() {
    DefaultSerializer<NonSerializableClass> serializer = new DefaultSerializer<>();
    NonSerializableClass obj = new NonSerializableClass();
    RpcSerializationException exception = assertThrows(RpcSerializationException.class,
        () -> serializer.serialize(obj));
    assertTrue(exception.getMessage().contains("not Serializable"));
  }

  @Test
  public void testSerializeString() {
    DefaultSerializer<String> serializer = new DefaultSerializer<>();
    String testString = "Hello, World!";
    ByteBuffer buffer = serializer.serialize(testString);
    assertNotNull(buffer);
    assertTrue(buffer.remaining() > 0);

    // Deserialize and verify
    String result = serializer.deserialize(buffer, String.class);
    assertEquals(testString, result);
  }

  @Test
  public void testDeserializePrimitiveWrappersFromRawBytes() {
    // Test the special case handling in deserialize for primitive wrappers
    // These expect raw bytes, not Java serialized objects

    DefaultSerializer<Integer> intSerializer = new DefaultSerializer<>();
    ByteBuffer intBuffer = intSerializer.serialize(42);
    Integer intResult = intSerializer.deserialize(intBuffer, Integer.class);
    assertEquals(42, intResult);

    // Test with primitive type
    intBuffer.rewind();
    int intResult2 = intSerializer.deserialize(intBuffer, int.class);
    assertEquals(42, intResult2);

    DefaultSerializer<Long> longSerializer = new DefaultSerializer<>();
    ByteBuffer longBuffer = longSerializer.serialize(123456789L);
    Long longResult = longSerializer.deserialize(longBuffer, Long.class);
    assertEquals(123456789L, longResult);

    DefaultSerializer<Boolean> boolSerializer = new DefaultSerializer<>();
    ByteBuffer boolBuffer = boolSerializer.serialize(true);
    Boolean boolResult = boolSerializer.deserialize(boolBuffer, Boolean.class);
    assertTrue(boolResult);
    boolBuffer.rewind();
    boolean boolResult2 = boolSerializer.deserialize(boolBuffer, boolean.class);
    assertTrue(boolResult2);
    boolBuffer = boolSerializer.serialize(false);
    boolResult = boolSerializer.deserialize(boolBuffer, Boolean.class);
    assertFalse(boolResult);
    boolBuffer.rewind();
    boolean boolResult3 = boolSerializer.deserialize(boolBuffer, boolean.class);
    assertFalse(boolResult3);

    DefaultSerializer<Byte> byteSerializer = new DefaultSerializer<>();
    ByteBuffer byteBuffer = byteSerializer.serialize((byte) 123);
    Byte byteResult = byteSerializer.deserialize(byteBuffer, Byte.class);
    assertEquals((byte) 123, byteResult);
    byteBuffer.rewind();
    byte byteResult2 = byteSerializer.deserialize(byteBuffer, byte.class);
    assertEquals((byte) 123, byteResult2);

    DefaultSerializer<Short> shortSerializer = new DefaultSerializer<>();
    ByteBuffer shortBuffer = shortSerializer.serialize((short) 32000);
    Short shortResult = shortSerializer.deserialize(shortBuffer, Short.class);
    assertEquals((short) 32000, shortResult);
    shortBuffer.rewind();
    short shortResult2 = shortSerializer.deserialize(shortBuffer, short.class);
    assertEquals((short) 32000, shortResult2);

    DefaultSerializer<Float> floatSerializer = new DefaultSerializer<>();
    ByteBuffer floatBuffer = floatSerializer.serialize(3.14f);
    Float floatResult = floatSerializer.deserialize(floatBuffer, Float.class);
    assertEquals(3.14f, floatResult);
    floatBuffer.rewind();
    float floatResult2 = floatSerializer.deserialize(floatBuffer, float.class);
    assertEquals(3.14f, floatResult2);

    DefaultSerializer<Double> doubleSerializer = new DefaultSerializer<>();
    ByteBuffer doubleBuffer = doubleSerializer.serialize(3.14159265359);
    Double doubleResult = doubleSerializer.deserialize(doubleBuffer, Double.class);
    assertEquals(3.14159265359, doubleResult);
    doubleBuffer.rewind();
    double doubleResult2 = doubleSerializer.deserialize(doubleBuffer, double.class);
    assertEquals(3.14159265359, doubleResult2);

    DefaultSerializer<Character> charSerializer = new DefaultSerializer<>();
    ByteBuffer charBuffer = charSerializer.serialize('A');
    Character charResult = charSerializer.deserialize(charBuffer, Character.class);
    assertEquals('A', charResult);
    charBuffer.rewind();
    char charResult2 = charSerializer.deserialize(charBuffer, char.class);
    assertEquals('A', charResult2);
  }

  @Test
  public void testSerializeDeserializeCharacter() {
    DefaultSerializer<Character> serializer = new DefaultSerializer<>();

    // Test serializing and deserializing various characters
    Character[] testChars = {'A', 'z', '0', ' ', '\n', '\u00E9', '\u4E2D', '\uFFFF'};

    for (Character ch : testChars) {
      ByteBuffer buffer = serializer.serialize(ch);
      Character result = serializer.deserialize(buffer, Character.class);
      assertEquals(ch, result, "Failed for character: " + ch);
    }

    // Test primitive char type
    ByteBuffer buffer = serializer.serialize('X');
    char primitiveResult = serializer.deserialize(buffer, char.class);
    assertEquals('X', primitiveResult);
  }

  @Test
  public void testDeserializeVoidClass() {
    DefaultSerializer<Void> serializer = new DefaultSerializer<>();
    ByteBuffer buffer = ByteBuffer.allocate(10);
    Void result = serializer.deserialize(buffer, Void.class);
    assertNull(result);
  }

  @Test
  public void testDeserializeVoidPrimitive() {
    DefaultSerializer<Object> serializer = new DefaultSerializer<>();
    ByteBuffer buffer = ByteBuffer.allocate(10);
    Object result = serializer.deserialize(buffer, void.class);
    assertNull(result);
  }

  @Test
  public void testSerializeCustomSerializableObject() {
    DefaultSerializer<SerializableClass> serializer = new DefaultSerializer<>();
    SerializableClass obj = new SerializableClass("test", 42);
    ByteBuffer buffer = serializer.serialize(obj);
    assertNotNull(buffer);
    assertTrue(buffer.remaining() > 0);

    SerializableClass result = serializer.deserialize(buffer, SerializableClass.class);
    assertEquals(obj.name, result.name);
    assertEquals(obj.value, result.value);
  }

  @Test
  public void testSerializeCollection() {
    DefaultSerializer<List<String>> serializer = new DefaultSerializer<>();
    List<String> list = new ArrayList<>();
    list.add("one");
    list.add("two");
    list.add("three");

    ByteBuffer buffer = serializer.serialize(list);
    assertNotNull(buffer);

    @SuppressWarnings("unchecked")
    List<String> result = serializer.deserialize(buffer, (Class<List<String>>) (Class<?>) List.class);
    assertEquals(list, result);
  }

  @Test
  public void testSerializeMap() {
    DefaultSerializer<Map<String, Integer>> serializer = new DefaultSerializer<>();
    Map<String, Integer> map = new HashMap<>();
    map.put("one", 1);
    map.put("two", 2);
    map.put("three", 3);

    ByteBuffer buffer = serializer.serialize(map);
    assertNotNull(buffer);

    @SuppressWarnings("unchecked")
    Map<String, Integer> result = serializer.deserialize(buffer,
        (Class<Map<String, Integer>>) (Class<?>) Map.class);
    assertEquals(map, result);
  }

  @Test
  public void testSerializeArray() {
    DefaultSerializer<int[]> serializer = new DefaultSerializer<>();
    int[] array = {1, 2, 3, 4, 5};

    ByteBuffer buffer = serializer.serialize(array);
    assertNotNull(buffer);

    int[] result = serializer.deserialize(buffer, int[].class);
    assertArrayEquals(array, result);
  }

  @Test
  public void testDeserializeFlowFuture() {
    DefaultSerializer<CompletableFuture<String>> serializer = new DefaultSerializer<>();
    ByteBuffer buffer = ByteBuffer.allocate(10);
    RpcSerializationException exception = assertThrows(RpcSerializationException.class,
        () -> serializer.deserialize(buffer, (Class<CompletableFuture<String>>) (Class<?>) FlowFuture.class));
    assertTrue(exception.getMessage().contains("Cannot be directly serialized/deserialized"));
  }

  @Test
  public void testDeserializeFlowPromise() {
    DefaultSerializer<FlowPromise<String>> serializer = new DefaultSerializer<>();
    ByteBuffer buffer = ByteBuffer.allocate(10);
    RpcSerializationException exception = assertThrows(RpcSerializationException.class,
        () -> serializer.deserialize(buffer, (Class<FlowPromise<String>>) (Class<?>) FlowPromise.class));
    assertTrue(exception.getMessage().contains("Cannot be directly serialized/deserialized"));
  }

  @Test
  public void testDeserializePromiseStream() {
    DefaultSerializer<PromiseStream<String>> serializer = new DefaultSerializer<>();
    ByteBuffer buffer = ByteBuffer.allocate(10);
    RpcSerializationException exception = assertThrows(RpcSerializationException.class,
        () -> serializer.deserialize(buffer, (Class<PromiseStream<String>>) (Class<?>) PromiseStream.class));
    assertTrue(exception.getMessage().contains("Cannot be directly serialized/deserialized"));
  }

  @Test
  public void testDeserializeFlowSubclasses() {
    // Test subclasses of flow types
    ByteBuffer buffer = ByteBuffer.allocate(10);

    // Custom FlowFuture subclass
    DefaultSerializer<CustomCompletableFuture<String>> futureSerializer = new DefaultSerializer<>();
    RpcSerializationException futureEx = assertThrows(RpcSerializationException.class,
        () -> futureSerializer.deserialize(buffer,
            (Class<CustomCompletableFuture<String>>) (Class<?>) CustomFlowFuture.class));
    assertTrue(futureEx.getMessage().contains("Cannot be directly serialized/deserialized"));

    // Custom FlowPromise subclass
    DefaultSerializer<FlowPromise<String>> promiseSerializer = new DefaultSerializer<>();
    RpcSerializationException promiseEx = assertThrows(RpcSerializationException.class,
        () -> promiseSerializer.deserialize(buffer, (Class<FlowPromise<String>>) (Class<?>) FlowPromise.class));
    assertTrue(promiseEx.getMessage().contains("Cannot be directly serialized/deserialized"));

    // Custom PromiseStream subclass
    DefaultSerializer<CustomPromiseStream<String>> streamSerializer = new DefaultSerializer<>();
    RpcSerializationException streamEx = assertThrows(RpcSerializationException.class,
        () -> streamSerializer.deserialize(buffer,
            (Class<CustomPromiseStream<String>>) (Class<?>) CustomPromiseStream.class));
    assertTrue(streamEx.getMessage().contains("Cannot be directly serialized/deserialized"));
  }

  @Test
  public void testDeserializeWrongType() {
    // For primitive wrappers, DefaultSerializer doesn't check type compatibility
    // when deserializing from raw bytes, so we need to test with objects
    DefaultSerializer<SerializableClass> serializer1 = new DefaultSerializer<>();
    SerializableClass obj = new SerializableClass("test", 42);
    ByteBuffer buffer = serializer1.serialize(obj);

    // Try to deserialize as different type
    DefaultSerializer<String> serializer2 = new DefaultSerializer<>();
    RpcSerializationException exception = assertThrows(RpcSerializationException.class,
        () -> serializer2.deserialize(buffer, String.class));
    assertThat(exception.getMessage()).contains("Failed to deserialize");
  }

  @Test
  public void testDeserializeCorruptedData() {
    // Create corrupted data
    DefaultSerializer<String> serializer = new DefaultSerializer<>();
    ByteBuffer buffer = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

    RpcSerializationException exception = assertThrows(RpcSerializationException.class,
        () -> serializer.deserialize(buffer, String.class));
    assertTrue(exception.getMessage().contains("Failed to deserialize"));
  }

  @Test
  public void testSerializeComplexNestedObject() {
    DefaultSerializer<ComplexObject> serializer = new DefaultSerializer<>();
    ComplexObject obj = new ComplexObject();
    obj.name = "Complex";
    obj.nested = new SerializableClass("Nested", 100);
    obj.list = new ArrayList<>();
    obj.list.add("item1");
    obj.list.add("item2");
    obj.map = new HashMap<>();
    obj.map.put("key1", 1);
    obj.map.put("key2", 2);

    ByteBuffer buffer = serializer.serialize(obj);
    assertNotNull(buffer);

    ComplexObject result = serializer.deserialize(buffer, ComplexObject.class);
    assertEquals(obj.name, result.name);
    assertEquals(obj.nested.name, result.nested.name);
    assertEquals(obj.nested.value, result.nested.value);
    assertEquals(obj.list, result.list);
    assertEquals(obj.map, result.map);
  }

  @Test
  public void testSerializeEmptyString() {
    DefaultSerializer<String> serializer = new DefaultSerializer<>();
    String emptyString = "";
    ByteBuffer buffer = serializer.serialize(emptyString);
    assertNotNull(buffer);

    String result = serializer.deserialize(buffer, String.class);
    assertEquals(emptyString, result);
  }

  @Test
  public void testSerializeLargeObject() {
    // Create a large list
    DefaultSerializer<List<String>> serializer = new DefaultSerializer<>();
    List<String> largeList = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      largeList.add("Item " + i);
    }

    ByteBuffer buffer = serializer.serialize(largeList);
    assertNotNull(buffer);
    assertTrue(buffer.remaining() > 1000); // Should be quite large

    @SuppressWarnings("unchecked")
    List<String> result = serializer.deserialize(buffer, (Class<List<String>>) (Class<?>) List.class);
    assertEquals(largeList, result);
  }

  @Test
  public void testSerializeNullInCollection() {
    DefaultSerializer<List<String>> serializer = new DefaultSerializer<>();
    List<String> list = new ArrayList<>();
    list.add("one");
    list.add(null);
    list.add("three");

    ByteBuffer buffer = serializer.serialize(list);
    assertNotNull(buffer);

    @SuppressWarnings("unchecked")
    List<String> result = serializer.deserialize(buffer, (Class<List<String>>) (Class<?>) List.class);
    assertEquals(list, result);
    assertNull(result.get(1));
  }

  @Test
  public void testSerializeDeserializePrimitiveWrappersAsObjects() {
    // Test serializing primitive wrappers as regular objects
    // This tests the full Java serialization path

    DefaultSerializer<Object> serializer = new DefaultSerializer<>();

    // Test various wrapper types as regular objects
    Integer intObj = 42;
    ByteBuffer intBuffer = serializer.serialize(intObj);
    Object intResult = serializer.deserialize(intBuffer, Object.class);
    assertEquals(intObj, ((Long) intResult).intValue());

    Long longObj = 123456789L;
    ByteBuffer longBuffer = serializer.serialize(longObj);
    Object longResult = serializer.deserialize(longBuffer, Object.class);
    assertEquals(longObj, longResult);

    Boolean boolObj = true;
    ByteBuffer boolBuffer = serializer.serialize(boolObj);
    Object boolResult = serializer.deserialize(boolBuffer, Object.class);
    assertEquals(boolObj, boolResult);
  }

  @Test
  public void testDeserializeTypeMismatchForObject() {
    // Serialize an Integer object
    DefaultSerializer<Integer> intSerializer = new DefaultSerializer<>();
    ByteBuffer buffer = intSerializer.serialize(42);

    // Try to deserialize as ComplexObject
    DefaultSerializer<ComplexObject> objSerializer = new DefaultSerializer<>();
    ByteBuffer finalBuffer1 = buffer;
    RpcSerializationException exception = assertThrows(RpcSerializationException.class,
        () -> objSerializer.deserialize(finalBuffer1, ComplexObject.class));
    assertTrue(exception.getMessage().contains("does not match expected type"));

    DefaultSerializer<SerializableClass> anotherSerializer = new DefaultSerializer<>();
    buffer = anotherSerializer.serialize(new SerializableClass("hello", 123));
    ByteBuffer finalBuffer = buffer;
    exception = assertThrows(RpcSerializationException.class,
        () -> objSerializer.deserialize(finalBuffer, ComplexObject.class));
    assertThat(exception.getMessage()).contains("does not match expected type");
  }

  // Test helper classes

  private static class NonSerializableClass {
    // This class does not implement Serializable
  }

  private static class SerializableClass implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
    int value;

    SerializableClass(String name, int value) {
      this.name = name;
      this.value = value;
    }
  }

  private static class ComplexObject implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
    SerializableClass nested;
    List<String> list;
    Map<String, Integer> map;
  }

  // Custom flow type subclasses for testing
  private static class CustomCompletableFuture<T> extends CompletableFuture<T> {
  }

  private static class CustomPromiseStream<T> extends PromiseStream<T> {
  }
}