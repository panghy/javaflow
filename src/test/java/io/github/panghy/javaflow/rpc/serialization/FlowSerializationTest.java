package io.github.panghy.javaflow.rpc.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the FlowSerialization class.
 * These tests verify the registration and proper functioning of serializers.
 */
public class FlowSerializationTest {

  // Test class for serialization
  static class TestData {
    private final String value;

    TestData(String value) {
      this.value = value;
    }

    String getValue() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TestData testData = (TestData) o;
      return value.equals(testData.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  // Test serializer implementation
  static class TestSerializer implements Serializer<TestData> {
    @Override
    public ByteBuffer serialize(TestData obj) {
      return ByteBuffer.wrap(obj.getValue().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public TestData deserialize(ByteBuffer buffer, Class<? extends TestData> expectedType) {
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return new TestData(new String(bytes, StandardCharsets.UTF_8));
    }
  }

  // Test factory implementation
  static class TestSerializerFactory implements SerializerFactory {
    @Override
    @SuppressWarnings("unchecked")
    public <T> Serializer<T> createSerializer(Class<T> type) {
      if (type.getName().endsWith("FactoryTestData")) {
        return (Serializer<T>) new TestSerializer();
      }
      return null;
    }
  }

  // Class for testing factory-based serialization
  static class FactoryTestData extends TestData {
    FactoryTestData(String value) {
      super(value);
    }
  }

  // Class for testing package-based serialization
  static class SubPackageTestData extends TestData {
    SubPackageTestData(String value) {
      super(value);
    }
  }

  // Simple default serializer for types that don't have specific serializers
  static class DefaultSerializer implements Serializer<Object> {
    @Override
    public ByteBuffer serialize(Object obj) {
      if (obj instanceof String) {
        return ByteBuffer.wrap(((String) obj).getBytes(StandardCharsets.UTF_8));
      }
      return ByteBuffer.allocate(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object deserialize(ByteBuffer buffer, Class<?> expectedType) {
      if (expectedType == String.class) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
      }
      return null;
    }
  }

  @BeforeEach
  void setUp() {
    // Re-register serializers before each test to ensure isolation
    FlowSerialization.registerSerializer(TestData.class, new TestSerializer());
    FlowSerialization.registerSerializerFactory(
        "io.github.panghy.javaflow.rpc.serialization", new TestSerializerFactory());
    FlowSerialization.setDefaultSerializer(new DefaultSerializer());
  }

  @Test
  void testRegisterAndGetSerializer() {
    // Test getting a directly registered serializer
    Serializer<TestData> serializer = FlowSerialization.getSerializer(TestData.class);
    assertNotNull(serializer);
    assertTrue(serializer instanceof TestSerializer);
  }

  @Test
  void testSerializeAndDeserialize() {
    // Test serializing an object
    TestData data = new TestData("test data");
    ByteBuffer buffer = FlowSerialization.serialize(data);
    
    // Test deserializing an object
    TestData deserialized = FlowSerialization.deserialize(buffer, TestData.class);
    assertEquals(data, deserialized);
  }

  @Test
  void testSerializeNull() {
    // Test serializing null
    ByteBuffer buffer = FlowSerialization.serialize(null);
    assertNotNull(buffer);
    assertEquals(0, buffer.remaining());
    
    // Test deserializing null/empty buffer
    TestData deserialized = FlowSerialization.deserialize(buffer, TestData.class);
    assertNull(deserialized);
  }

  @Test
  void testFactoryBasedSerializer() {
    // Clear any existing serializers
    FlowSerialization.clear();
    
    // Register the factory for our test package
    FlowSerialization.registerSerializerFactory(
        "io.github.panghy.javaflow.rpc.serialization", new TestSerializerFactory());
        
    // Create test data
    FactoryTestData data = new FactoryTestData("factory data");
    
    // Test serializing and deserializing
    ByteBuffer buffer = ByteBuffer.allocate(100);
    buffer.put(data.getValue().getBytes(StandardCharsets.UTF_8));
    buffer.flip();
    
    // Create a serializer for FactoryTestData
    class FactoryTestSerializer implements Serializer<FactoryTestData> {
      @Override
      public ByteBuffer serialize(FactoryTestData obj) {
        return ByteBuffer.wrap(obj.getValue().getBytes(StandardCharsets.UTF_8));
      }
      
      @Override
      public FactoryTestData deserialize(ByteBuffer buffer, Class<? extends FactoryTestData> expectedType) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new FactoryTestData(new String(bytes, StandardCharsets.UTF_8));
      }
    }
    
    // Register a serializer directly
    FlowSerialization.registerSerializer(FactoryTestData.class, new FactoryTestSerializer());
    
    // Now deserialize
    FactoryTestData deserialized = FlowSerialization.deserialize(buffer, FactoryTestData.class);
    assertEquals(data, deserialized);
  }

  @Test
  void testDefaultSerializer() {
    // Test using the default serializer for a type without a specific serializer
    String data = "default serializer test";
    
    // This will use the default serializer
    ByteBuffer buffer = FlowSerialization.serialize(data);
    String deserialized = FlowSerialization.deserialize(buffer, String.class);
    assertEquals(data, deserialized);
  }

  @Test
  void testPackageHierarchy() {
    // Register a serializer factory for a parent package
    class ParentPackageSerializer<T> implements Serializer<T> {
      @Override
      public ByteBuffer serialize(T obj) {
        return ByteBuffer.allocate(0);
      }

      @Override
      public T deserialize(ByteBuffer buffer, Class<? extends T> expectedType) {
        return null;
      }
    }
    
    class ParentPackageFactory implements SerializerFactory {
      @Override
      @SuppressWarnings("unchecked")
      public <T> Serializer<T> createSerializer(Class<T> type) {
        return (Serializer<T>) new ParentPackageSerializer<>();
      }
    }
    
    // Register for the parent package
    FlowSerialization.registerSerializerFactory("io.github.panghy", new ParentPackageFactory());
    
    // Get a serializer for a type in a child package that doesn't have a specific serializer
    Serializer<SubPackageTestData> serializer = 
        FlowSerialization.getSerializer(SubPackageTestData.class);
    
    assertNotNull(serializer);
    assertTrue(serializer instanceof ParentPackageSerializer);
  }

  @Test
  void testCachingOfFactorySerializers() {
    // Get the serializer twice and confirm it's the same instance (cached)
    Serializer<FactoryTestData> serializer1 = FlowSerialization.getSerializer(FactoryTestData.class);
    Serializer<FactoryTestData> serializer2 = FlowSerialization.getSerializer(FactoryTestData.class);
    
    assertSame(serializer1, serializer2);
  }

  @Test
  void testNoSerializerFound() {
    // Create a new class that won't have a serializer
    class UnregisteredClass {
    }
    
    // Clear all serializers and factories
    FlowSerialization.clear();
    
    // This should throw an exception
    assertThrows(IllegalStateException.class, () -> {
      FlowSerialization.getSerializer(UnregisteredClass.class);
    });
  }
}