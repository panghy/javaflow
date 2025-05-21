package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.stream.RemotePromiseStream;
import io.github.panghy.javaflow.rpc.stream.StreamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for serialization of generic types in the JavaFlow RPC framework.
 * These tests verify that type information is properly preserved when
 * serializing and deserializing objects with generic types.
 */
public class GenericTypeSerializationTest {

  private StreamManager streamManager;
  private EndpointId destination;

  @BeforeEach
  void setUp() {
    streamManager = Mockito.mock(StreamManager.class);
    destination = new EndpointId(UUID.randomUUID().toString());

    // Mock streamManager to return a RemotePromiseStream for Class type
    Mockito.when(streamManager.createRemoteStream(
            Mockito.eq(destination), ArgumentMatchers.<Class<?>>any()))
        .thenAnswer(invocation -> {
          Class<?> valueType = invocation.getArgument(1);
          return new RemotePromiseStream<>(UUID.randomUUID(), destination, valueType, streamManager);
        });

    // Mock streamManager to return a RemotePromiseStream for TypeToken
    Mockito.when(streamManager.createRemoteStream(
            Mockito.eq(destination), ArgumentMatchers.<TypeToken<?>>any()))
        .thenAnswer(invocation -> {
          TypeToken<?> typeToken = invocation.getArgument(1);
          Class<?> rawType = (Class<?>) getRawType(typeToken.getType());
          RemotePromiseStream<?> stream = new RemotePromiseStream<>(
              UUID.randomUUID(), destination, rawType, streamManager);
          stream.setValueTypeInfo(typeToken.getType());
          return stream;
        });
  }

  /**
   * Extracts the raw type from a Type object.
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

  @Test
  void testTypeToken() {
    // Create a TypeToken for a simple type
    TypeToken<String> stringToken = new TypeToken<>() {
    };
    assertEquals(String.class, stringToken.getRawType());
    assertEquals(String.class, stringToken.getType());

    // Create a TypeToken for a parameterized type
    TypeToken<List<String>> listToken = new TypeToken<>() {
    };
    assertEquals(List.class, listToken.getRawType());
    assertInstanceOf(ParameterizedType.class, listToken.getType());

    // Extract the generic parameter
    ParameterizedType parameterizedType = (ParameterizedType) listToken.getType();
    assertEquals(1, parameterizedType.getActualTypeArguments().length);
    assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);
  }

  @Test
  void testTypeDescription() throws ClassNotFoundException {
    // Create a simple TypeDescription
    TypeDescription stringDesc = new TypeDescription(String.class);
    assertEquals("java.lang.String", stringDesc.getClassName());
    assertEquals(0, stringDesc.getTypeArguments().length);

    // Convert to Type and back
    Type stringType = stringDesc.toType();
    assertEquals(String.class, stringType);

    // Create a parameterized TypeDescription
    TypeDescription listDesc = new TypeDescription(
        List.class.getName(),
        new TypeDescription(String.class)
    );

    // Convert to Type
    Type listType = listDesc.toType();
    assertInstanceOf(ParameterizedType.class, listType);

    // Extract the generic parameter
    ParameterizedType parameterizedType = (ParameterizedType) listType;
    assertEquals(List.class, parameterizedType.getRawType());
    assertEquals(1, parameterizedType.getActualTypeArguments().length);
    assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);

    // Create a more complex nested type
    TypeDescription mapDesc = new TypeDescription(
        Map.class.getName(),
        new TypeDescription(String.class),
        new TypeDescription(
            List.class.getName(),
            new TypeDescription(Integer.class)
        )
    );

    // Convert to Type
    Type mapType = mapDesc.toType();
    assertInstanceOf(ParameterizedType.class, mapType);

    // Extract the generic parameters
    ParameterizedType mapParamType = (ParameterizedType) mapType;
    assertEquals(Map.class, mapParamType.getRawType());
    assertEquals(2, mapParamType.getActualTypeArguments().length);
    assertEquals(String.class, mapParamType.getActualTypeArguments()[0]);
    assertInstanceOf(ParameterizedType.class, mapParamType.getActualTypeArguments()[1]);

    // Verify the nested type
    ParameterizedType nestedListType = (ParameterizedType) mapParamType.getActualTypeArguments()[1];
    assertEquals(List.class, nestedListType.getRawType());
    assertEquals(1, nestedListType.getActualTypeArguments().length);
    assertEquals(Integer.class, nestedListType.getActualTypeArguments()[0]);

    // Verify round-trip conversion
    TypeDescription roundTripDesc = TypeDescription.fromType(mapType);
    assertEquals(mapDesc.getClassName(), roundTripDesc.getClassName());
    assertEquals(mapDesc.getTypeArguments().length, roundTripDesc.getTypeArguments().length);
  }

  @Test
  void testPromiseStreamSerializationWithGenericType() {
    // Create a TypeToken for List<String>
    TypeToken<List<String>> listToken = new TypeToken<>() {
    };

    // Create a PromiseStream with this type
    PromiseStream<List<String>> stream = new PromiseStream<>();

    // Create a serializer with the TypeToken
    PromiseStreamSerializer<List<String>> serializer = PromiseStreamSerializer.create(
        streamManager, destination, listToken);

    // Serialize the stream
    ByteBuffer buffer = serializer.serialize(stream);
    assertNotNull(buffer);
    assertTrue(buffer.hasRemaining());

    // Deserialize the stream
    @SuppressWarnings("unchecked")
    RemotePromiseStream<List<String>> deserializedStream =
        (RemotePromiseStream<List<String>>) serializer.deserialize(buffer, (Class) PromiseStream.class);

    // Verify the type information was preserved
    assertNotNull(deserializedStream);
    assertNotNull(deserializedStream.getValueTypeInfo());
    assertInstanceOf(ParameterizedType.class, deserializedStream.getValueTypeInfo());

    // Extract and verify the type parameters
    ParameterizedType parameterizedType = (ParameterizedType) deserializedStream.getValueTypeInfo();
    assertEquals(List.class, parameterizedType.getRawType());
    assertEquals(1, parameterizedType.getActualTypeArguments().length);
    assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);
  }

  @Test
  void testPromiseStreamSerializationWithNestedGenericType() {
    // Create a TypeToken for Map<String, List<Integer>>
    TypeToken<Map<String, List<Integer>>> mapToken = new TypeToken<>() {
    };

    // Create a PromiseStream with this type
    PromiseStream<Map<String, List<Integer>>> stream = new PromiseStream<>();

    // Create a serializer with the TypeToken
    PromiseStreamSerializer<Map<String, List<Integer>>> serializer = PromiseStreamSerializer.create(
        streamManager, destination, mapToken);

    // Serialize the stream
    ByteBuffer buffer = serializer.serialize(stream);
    assertNotNull(buffer);
    assertTrue(buffer.hasRemaining());

    // Deserialize the stream
    @SuppressWarnings("unchecked")
    RemotePromiseStream<Map<String, List<Integer>>> deserializedStream =
        (RemotePromiseStream<Map<String, List<Integer>>>) serializer.deserialize(
            buffer, (Class) PromiseStream.class);

    // Verify the type information was preserved
    assertNotNull(deserializedStream);
    assertNotNull(deserializedStream.getValueTypeInfo());
    assertInstanceOf(ParameterizedType.class, deserializedStream.getValueTypeInfo());

    // Extract and verify the type parameters
    ParameterizedType mapType = (ParameterizedType) deserializedStream.getValueTypeInfo();
    assertEquals(Map.class, mapType.getRawType());
    assertEquals(2, mapType.getActualTypeArguments().length);
    assertEquals(String.class, mapType.getActualTypeArguments()[0]);
    assertInstanceOf(ParameterizedType.class, mapType.getActualTypeArguments()[1]);

    // Verify the nested type
    ParameterizedType listType = (ParameterizedType) mapType.getActualTypeArguments()[1];
    assertEquals(List.class, listType.getRawType());
    assertEquals(1, listType.getActualTypeArguments().length);
    assertEquals(Integer.class, listType.getActualTypeArguments()[0]);
  }

  @Test
  void testSystemTypeSerializerFactoryWithGenericType() {
    // Create a TypeToken for List<String>
    TypeToken<List<String>> listToken = new TypeToken<>() {
    };

    // Create the factory
    SystemTypeSerializerFactory factory = new SystemTypeSerializerFactory(
        null, streamManager, destination);

    // Create a serializer for PromiseStream<List<String>>
    Serializer<PromiseStream<List<String>>> serializer =
        factory.createSerializerForType(
            new ParameterizedTypeImpl(PromiseStream.class, listToken.getType()));

    // Verify the serializer was created
    assertNotNull(serializer);
    assertInstanceOf(PromiseStreamSerializer.class, serializer);

    // Create a stream and test serialization
    PromiseStream<List<String>> stream = new PromiseStream<>();
    ByteBuffer buffer = serializer.serialize(stream);

    // Deserialize the stream
    @SuppressWarnings("unchecked")
    RemotePromiseStream<List<String>> deserializedStream =
        (RemotePromiseStream<List<String>>) serializer.deserialize(buffer, (Class) PromiseStream.class);

    // Verify type information
    assertNotNull(deserializedStream);
    assertNotNull(deserializedStream.getValueTypeInfo());
    assertInstanceOf(ParameterizedType.class, deserializedStream.getValueTypeInfo());

    // Verify the type parameters
    ParameterizedType parameterizedType = (ParameterizedType) deserializedStream.getValueTypeInfo();
    assertEquals(List.class, parameterizedType.getRawType());
    assertEquals(1, parameterizedType.getActualTypeArguments().length);
    assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);
  }

  /**
   * A simple implementation of ParameterizedType for testing.
   */
  private static class ParameterizedTypeImpl implements ParameterizedType {
    private final Class<?> rawType;
    private final Type[] actualTypeArguments;

    ParameterizedTypeImpl(Class<?> rawType, Type... actualTypeArguments) {
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
  }

  /**
   * Simple test to verify that a stream can be used with generic types.
   */
  @Test
  void testActualStreamUsageWithGenericTypes() {
    // Create a stream for lists of strings
    PromiseStream<List<String>> stream = new PromiseStream<>();

    // Create a TypeToken for the type
    TypeToken<List<String>> listToken = new TypeToken<>() {
    };

    // Create a serializer
    PromiseStreamSerializer<List<String>> serializer = PromiseStreamSerializer.create(
        streamManager, destination, listToken);

    // Serialize and deserialize
    ByteBuffer buffer = serializer.serialize(stream);
    @SuppressWarnings("unchecked")
    RemotePromiseStream<List<String>> remoteStream =
        (RemotePromiseStream<List<String>>) serializer.deserialize(buffer, (Class) PromiseStream.class);

    // Verify we can send a list of strings through the stream
    List<String> testData = new ArrayList<>();
    testData.add("test1");
    testData.add("test2");

    // This would not compile if the type information was lost
    assertTrue(remoteStream.send(testData));

    // Verify that the value type matches our expectations
    assertEquals(List.class, remoteStream.getValueType());
  }
}