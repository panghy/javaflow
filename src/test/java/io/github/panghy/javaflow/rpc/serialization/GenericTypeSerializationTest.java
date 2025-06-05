package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.stream.RemotePromiseStream;
import io.github.panghy.javaflow.rpc.stream.StreamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
}