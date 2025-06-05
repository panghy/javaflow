package io.github.panghy.javaflow.rpc.stream;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the RemotePromiseStream class.
 * These tests verify that values and close events are properly forwarded to the StreamManager.
 */
public class RemotePromiseStreamTest extends AbstractFlowTest {

  // A testable StreamManager that tracks calls to its methods
  private static class TestableStreamManager extends StreamManager {
    final AtomicReference<Object> lastSentValue = new AtomicReference<>();
    final AtomicReference<UUID> lastClosedStreamId = new AtomicReference<>();
    final AtomicReference<Throwable> lastCloseException = new AtomicReference<>();
    
    @Override
    public <T> void sendToStream(EndpointId destination, UUID streamId, T value) {
      lastSentValue.set(value);
      super.sendToStream(destination, streamId, value);
    }
    
    @Override
    public void closeStream(EndpointId destination, UUID streamId) {
      lastClosedStreamId.set(streamId);
      super.closeStream(destination, streamId);
    }
    
    @Override
    public void closeStreamExceptionally(EndpointId destination, UUID streamId, Throwable exception) {
      lastClosedStreamId.set(streamId);
      lastCloseException.set(exception);
      super.closeStreamExceptionally(destination, streamId, exception);
    }
  }
  
  private TestableStreamManager streamManager;
  private EndpointId destination;
  private UUID streamId;
  
  @BeforeEach
  void setUp() {
    streamManager = new TestableStreamManager();
    
    // Set up a message sender that does nothing
    streamManager.setMessageSender(new StreamManager.StreamMessageSender() {
      @Override
      public FlowFuture<Void> sendMessage(EndpointId destination, 
                                        RpcMessageHeader.MessageType type,
                                        UUID streamId, Object payload) {
        return new FlowFuture<>();
      }
    });
    
    destination = new EndpointId("test-endpoint");
    streamId = UUID.randomUUID();
  }
  
  @Test
  void testConstructor() {
    // Create a remote promise stream
    RemotePromiseStream<String> stream = 
        new RemotePromiseStream<>(streamId, destination, String.class, streamManager);
    
    // Verify getters return correct values
    assertEquals(streamId, stream.getStreamId());
    assertEquals(destination, stream.getDestination());
    assertEquals(String.class, stream.getValueType());
  }
  
  @Test
  void testSendValue() {
    // Create a remote promise stream
    RemotePromiseStream<String> stream = 
        new RemotePromiseStream<>(streamId, destination, String.class, streamManager);
    
    // Send a value
    boolean result = stream.send("test value");
    
    // Verify the result
    assertTrue(result);
    
    // Verify the value was forwarded to the stream manager
    assertEquals("test value", streamManager.lastSentValue.get());
  }
  
  @Test
  void testClose() {
    // Create a remote promise stream
    RemotePromiseStream<String> stream = 
        new RemotePromiseStream<>(streamId, destination, String.class, streamManager);
    
    // Close the stream
    boolean result = stream.close();
    
    // Verify the result
    assertTrue(result);
    
    // Verify the close was forwarded to the stream manager
    assertEquals(streamId, streamManager.lastClosedStreamId.get());
    
    // Verify the stream is closed locally
    assertTrue(stream.getFutureStream().isClosed());
  }
  
  @Test
  void testCloseExceptionally() {
    // Create a remote promise stream
    RemotePromiseStream<String> stream = 
        new RemotePromiseStream<>(streamId, destination, String.class, streamManager);
    
    // Close with an exception
    Exception testException = new RuntimeException("test error");
    boolean result = stream.closeExceptionally(testException);
    
    // Verify the result
    assertTrue(result);
    
    // Verify the close was forwarded to the stream manager
    assertEquals(streamId, streamManager.lastClosedStreamId.get());
    assertEquals(testException, streamManager.lastCloseException.get());
    
    // Verify the stream is closed locally
    assertTrue(stream.getFutureStream().isClosed());
  }
  
  @Test
  void testFutureStreamAccess() {
    // Create a remote promise stream
    RemotePromiseStream<String> stream = 
        new RemotePromiseStream<>(streamId, destination, String.class, streamManager);
    
    // Get the future stream
    FutureStream<String> futureStream = stream.getFutureStream();
    
    // Verify the future stream is not null
    assertNotNull(futureStream);
    
    // Verify the future stream is not closed
    assertFalse(futureStream.isClosed());
    
    // Send a value and verify it can be received
    stream.send("test value");
    
    // Verify the stream state
    assertFalse(futureStream.isClosed());
    
    // Close the stream
    stream.close();
    
    // Verify the stream state
    assertTrue(futureStream.isClosed());
  }
  
  @Test
  void testCreateStreamInManager() {
    // Test the createRemoteStream method in StreamManager
    RemotePromiseStream<String> stream = 
        streamManager.createRemoteStream(destination, String.class);
    
    // Verify the stream was created correctly
    assertNotNull(stream);
    assertEquals(destination, stream.getDestination());
    assertEquals(String.class, stream.getValueType());
    
    // Verify the stream is tracked in the manager
    // This is not directly testable without modifying the StreamManager, 
    // but we can test indirectly by closing the stream and checking that
    // the close message is sent
    stream.close();
    assertNotNull(streamManager.lastClosedStreamId.get());
  }
}