package io.github.panghy.javaflow.rpc.stream;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;
import io.github.panghy.javaflow.rpc.serialization.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional tests for {@link StreamManager} to improve code coverage.
 */
public class StreamManagerAdditionalTest {

  private StreamManager streamManager;
  private StreamManager.StreamMessageSender mockSender;
  private EndpointId testEndpoint;

  @BeforeEach
  public void setUp() {
    streamManager = new StreamManager();
    mockSender = mock(StreamManager.StreamMessageSender.class);
    when(mockSender.sendMessage(any(), any(), any(), any()))
        .thenReturn(FlowFuture.completed(null));

    streamManager.setMessageSender(mockSender);
    testEndpoint = new EndpointId("test-endpoint");
  }

  @Test
  public void testCreateRemoteStreamWithTypeToken() {
    // Create a TypeToken for List<String>
    TypeToken<List<String>> typeToken = new TypeToken<List<String>>() { };
    
    RemotePromiseStream<List<String>> stream = 
        streamManager.createRemoteStream(testEndpoint, typeToken);
    
    assertNotNull(stream);
    assertEquals(testEndpoint, stream.getDestination());
    assertEquals(List.class, stream.getValueType());
    assertNotNull(stream.getStreamId());
    
    // Verify type info is stored
    Type storedType = streamManager.getStreamTypeInfo(stream.getStreamId());
    assertNotNull(storedType);
    assertInstanceOf(ParameterizedType.class, storedType);
    ParameterizedType paramType = (ParameterizedType) storedType;
    assertEquals(List.class, paramType.getRawType());
    assertEquals(String.class, paramType.getActualTypeArguments()[0]);
  }

  @Test
  public void testRegisterIncomingStreamWithTypeToken() {
    // Create a TypeToken for Map<String, Integer>
    TypeToken<Map<String, Integer>> typeToken = new TypeToken<Map<String, Integer>>() { };
    
    PromiseStream<Map<String, Integer>> stream = new PromiseStream<>();
    UUID streamId = UUID.randomUUID();
    
    streamManager.registerIncomingStream(streamId, stream, typeToken);
    
    // Verify type info is stored
    Type storedType = streamManager.getStreamTypeInfo(streamId);
    assertNotNull(storedType);
    assertInstanceOf(ParameterizedType.class, storedType);
    ParameterizedType paramType = (ParameterizedType) storedType;
    assertEquals(Map.class, paramType.getRawType());
    assertEquals(2, paramType.getActualTypeArguments().length);
    assertEquals(String.class, paramType.getActualTypeArguments()[0]);
    assertEquals(Integer.class, paramType.getActualTypeArguments()[1]);
  }

  @Test
  public void testGetStreamTypeInfo() {
    // Test with simple type
    RemotePromiseStream<String> stream1 = streamManager.createRemoteStream(testEndpoint, String.class);
    Type type1 = streamManager.getStreamTypeInfo(stream1.getStreamId());
    assertEquals(String.class, type1);
    
    // Test with generic type
    TypeToken<List<String>> typeToken = new TypeToken<List<String>>() { };
    RemotePromiseStream<List<String>> stream2 = streamManager.createRemoteStream(testEndpoint, typeToken);
    Type type2 = streamManager.getStreamTypeInfo(stream2.getStreamId());
    assertInstanceOf(ParameterizedType.class, type2);
    
    // Test with non-existent stream
    UUID nonExistentId = UUID.randomUUID();
    Type type3 = streamManager.getStreamTypeInfo(nonExistentId);
    assertNull(type3);
  }

  @Test
  public void testSendToStream() {
    streamManager.sendToStream(testEndpoint, UUID.randomUUID(), "test value");
    
    verify(mockSender).sendMessage(
        eq(testEndpoint),
        eq(RpcMessageHeader.MessageType.STREAM_DATA),
        any(UUID.class),
        eq("test value"));
  }

  @Test
  public void testSendToStreamWithoutMessageSender() {
    // Create new stream manager without message sender
    StreamManager managerWithoutSender = new StreamManager();
    
    // Should not throw exception
    managerWithoutSender.sendToStream(testEndpoint, UUID.randomUUID(), "test value");
    
    // No verification needed - just ensuring no exception
  }

  @Test
  public void testCloseStream() {
    UUID streamId = UUID.randomUUID();
    streamManager.closeStream(testEndpoint, streamId);
    
    verify(mockSender).sendMessage(
        eq(testEndpoint),
        eq(RpcMessageHeader.MessageType.STREAM_CLOSE),
        eq(streamId),
        eq(null));
  }

  @Test
  public void testCloseStreamRemovesFromMaps() {
    // Create a remote stream
    RemotePromiseStream<String> stream = streamManager.createRemoteStream(testEndpoint, String.class);
    UUID streamId = stream.getStreamId();
    
    // Verify it's tracked
    assertNotNull(streamManager.getStreamTypeInfo(streamId));
    
    // Close the stream
    streamManager.closeStream(testEndpoint, streamId);
    
    // Verify it's removed
    assertNull(streamManager.getStreamTypeInfo(streamId));
  }

  @Test
  public void testCloseStreamByIdWithException() {
    // Register an incoming stream
    PromiseStream<String> stream = new PromiseStream<>();
    UUID streamId = UUID.randomUUID();
    streamManager.registerIncomingStream(streamId, stream);
    
    // Close with exception
    Exception exception = new RuntimeException("Test exception");
    streamManager.closeStream(streamId, exception);
    
    // Verify stream is closed
    assertTrue(stream.getFutureStream().isClosed());
    
    // Verify it's removed from tracking
    assertNull(streamManager.getStreamTypeInfo(streamId));
  }

  @Test
  public void testCloseStreamByIdWithoutException() {
    // Register an incoming stream
    PromiseStream<String> stream = new PromiseStream<>();
    UUID streamId = UUID.randomUUID();
    streamManager.registerIncomingStream(streamId, stream);
    
    // Close normally
    streamManager.closeStream(streamId, null);
    
    // Verify stream is closed
    assertTrue(stream.getFutureStream().isClosed());
    
    // Verify it's removed from tracking
    assertNull(streamManager.getStreamTypeInfo(streamId));
  }

  @Test
  public void testCloseNonExistentStreamById() {
    UUID nonExistentId = UUID.randomUUID();
    
    // Should not throw exception
    streamManager.closeStream(nonExistentId, null);
    streamManager.closeStream(nonExistentId, new RuntimeException("Test"));
  }

  @Test
  public void testCloseStreamExceptionally() {
    UUID streamId = UUID.randomUUID();
    Exception exception = new RuntimeException("Test exception");
    
    streamManager.closeStreamExceptionally(testEndpoint, streamId, exception);
    
    verify(mockSender).sendMessage(
        eq(testEndpoint),
        eq(RpcMessageHeader.MessageType.STREAM_CLOSE),
        eq(streamId),
        eq(exception));
  }

  @Test
  public void testCloseStreamExceptionallyWithoutMessageSender() {
    // Create new stream manager without message sender
    StreamManager managerWithoutSender = new StreamManager();
    
    // Should not throw exception
    managerWithoutSender.closeStreamExceptionally(
        testEndpoint, UUID.randomUUID(), new RuntimeException("Test"));
  }

  @Test
  public void testReceiveDataWithTypeMismatch() {
    // Register a stream expecting String
    PromiseStream<String> stream = new PromiseStream<>();
    UUID streamId = UUID.randomUUID();
    streamManager.registerIncomingStream(streamId, stream);
    
    // Try to send an Integer (type mismatch)
    // This will trigger the ClassCastException catch block
    AtomicReference<Throwable> capturedError = new AtomicReference<>();
    stream.getFutureStream().onClose().whenComplete((v, ex) -> {
      capturedError.set(ex);
    });
    
    // Send wrong type - in real usage this would cause ClassCastException
    // For testing, we need to simulate this more carefully
    boolean result = streamManager.receiveData(streamId, 123); // Integer instead of String
    
    // The current implementation will actually succeed because of type erasure
    // But we can test the removal logic by closing the stream
    assertTrue(result); // It will actually succeed due to type erasure at runtime
  }

  @Test
  public void testStreamCleanupOnClose() {
    // Test outgoing stream cleanup
    RemotePromiseStream<String> outgoingStream = streamManager.createRemoteStream(testEndpoint, String.class);
    UUID outgoingId = outgoingStream.getStreamId();
    
    // Verify it's tracked
    assertNotNull(streamManager.getStreamTypeInfo(outgoingId));
    
    // Close the stream directly (simulating the cleanup callback)
    outgoingStream.close();
    
    // Verify it's cleaned up
    assertNull(streamManager.getStreamTypeInfo(outgoingId));
    
    // Test incoming stream cleanup
    PromiseStream<String> incomingStream = new PromiseStream<>();
    UUID incomingId = UUID.randomUUID();
    streamManager.registerIncomingStream(incomingId, incomingStream);
    
    // Verify it's tracked
    assertTrue(streamManager.receiveData(incomingId, "test"));
    
    // Close the stream
    incomingStream.close();
    
    // Verify it's cleaned up
    assertFalse(streamManager.receiveData(incomingId, "test2"));
  }

  @Test
  public void testGetRawTypeWithUnsupportedType() {
    // Test with a custom Type that's not Class or ParameterizedType
    Type customType = new Type() {
      @Override
      public String getTypeName() {
        return "CustomType";
      }
    };
    
    // Create a custom TypeToken that returns this unsupported type
    TypeToken<String> brokenToken = new TypeToken<String>() {
      @Override
      public Type getType() {
        return customType;
      }
    };
    
    // This should throw IllegalArgumentException
    assertThrows(IllegalArgumentException.class, () -> {
      streamManager.createRemoteStream(testEndpoint, brokenToken);
    });
  }

  @Test
  public void testClearWithStreamsPresent() {
    // Create multiple streams
    RemotePromiseStream<String> remote1 = streamManager.createRemoteStream(testEndpoint, String.class);
    RemotePromiseStream<Integer> remote2 = streamManager.createRemoteStream(testEndpoint, Integer.class);
    
    PromiseStream<String> incoming1 = new PromiseStream<>();
    PromiseStream<Integer> incoming2 = new PromiseStream<>();
    UUID incomingId1 = UUID.randomUUID();
    UUID incomingId2 = UUID.randomUUID();
    
    streamManager.registerIncomingStream(incomingId1, incoming1);
    streamManager.registerIncomingStream(incomingId2, incoming2);
    
    // Store IDs for verification
    UUID remoteId1 = remote1.getStreamId();
    UUID remoteId2 = remote2.getStreamId();
    
    // Clear all
    streamManager.clear();
    
    // Verify all streams are closed
    assertTrue(incoming1.getFutureStream().isClosed());
    assertTrue(incoming2.getFutureStream().isClosed());
    
    // Verify all type info is cleared
    assertNull(streamManager.getStreamTypeInfo(remoteId1));
    assertNull(streamManager.getStreamTypeInfo(remoteId2));
    assertNull(streamManager.getStreamTypeInfo(incomingId1));
    assertNull(streamManager.getStreamTypeInfo(incomingId2));
    
    // Verify incoming streams can't receive data
    assertFalse(streamManager.receiveData(incomingId1, "test"));
    assertFalse(streamManager.receiveData(incomingId2, 123));
  }

  @Test
  public void testMultipleSendsToSameStream() {
    RemotePromiseStream<String> stream = streamManager.createRemoteStream(testEndpoint, String.class);
    UUID streamId = stream.getStreamId();
    
    // Send multiple values
    stream.send("value1");
    stream.send("value2");
    stream.send("value3");
    
    // Verify all sends
    verify(mockSender, times(3)).sendMessage(
        eq(testEndpoint),
        eq(RpcMessageHeader.MessageType.STREAM_DATA),
        eq(streamId),
        any(String.class));
  }

  @Test
  public void testStreamTypeInfoCleanupAfterException() {
    // Test that type info is cleaned up when stream is closed with exception
    PromiseStream<String> stream = new PromiseStream<>();
    UUID streamId = UUID.randomUUID();
    TypeToken<String> typeToken = new TypeToken<String>() { };
    
    streamManager.registerIncomingStream(streamId, stream, typeToken);
    
    // Verify type info exists
    assertNotNull(streamManager.getStreamTypeInfo(streamId));
    
    // Close with exception
    boolean result = streamManager.handleStreamClose(streamId, new RuntimeException("Test"));
    
    assertTrue(result);
    assertNull(streamManager.getStreamTypeInfo(streamId));
  }
}