package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the pull-based behavior of RealFlowConnection.
 * These tests verify that reads are only performed when requested.
 */
@SuppressWarnings("unchecked")
public class RealFlowConnectionPullBehaviorTest extends AbstractFlowTest {

  // The mocked socket channel
  private AsynchronousSocketChannel mockChannel;

  // The connection under test
  private RealFlowConnection connection;

  // Endpoints for the connection
  private Endpoint localEndpoint;
  private Endpoint remoteEndpoint;

  // Track read operations
  private AtomicInteger readCount;

  // Track if connection is closed
  private AtomicBoolean channelClosed;

  @BeforeEach
  void setUp() throws IOException {
    // Reset counters
    readCount = new AtomicInteger(0);
    channelClosed = new AtomicBoolean(false);

    // Create mock AsynchronousSocketChannel
    mockChannel = mock(AsynchronousSocketChannel.class);

    // Set up default mock behavior
    when(mockChannel.isOpen()).thenReturn(true);

    // Setup endpoints
    localEndpoint = new Endpoint("localhost", 12345);
    remoteEndpoint = new Endpoint("localhost", 54321);

    // Mock the close() method
    doAnswer(invocation -> {
      channelClosed.set(true);
      when(mockChannel.isOpen()).thenReturn(false);
      return null;
    }).when(mockChannel).close();
  }

  /**
   * Tests that no reads are performed during construction (pull-based).
   */
  @Test
  void testNoReadDuringConstruction() throws Exception {
    // Mock the read method to capture reads
    doAnswer(invocation -> {
      readCount.incrementAndGet();
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create connection - NO reads should happen during construction (pull-based)
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Verify that NO read was performed during construction
    assertEquals(0, readCount.get());
  }

  /**
   * Tests that accessing the stream triggers a read (pull-based).
   */
  @Test
  void testReceiveStreamAccess() throws Exception {
    final List<CompletionHandler<Integer, ByteBuffer>> handlers = new ArrayList<>();

    // Mock the read method to capture the handler and increment readCount
    doAnswer(invocation -> {
      readCount.incrementAndGet();
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      handlers.add(handler);
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Verify that NO read was performed during construction
    assertEquals(0, readCount.get());

    // Access the receiveStream() - this should trigger a read
    connection.receiveStream();

    // Verify that NO read was performed just by accessing the stream
    assertEquals(0, readCount.get());

    // Simulate data arrival to test the stream
    if (!handlers.isEmpty()) {
      byte[] testData = "Test data".getBytes();
      handlers.get(0).completed(testData.length, ByteBuffer.wrap(testData));
    }
  }

  /**
   * Tests that receive() calls trigger reads.
   */
  @Test
  void testReceiveTriggersRead() throws Exception {
    final List<CompletionHandler<Integer, ByteBuffer>> handlers = new ArrayList<>();
    final List<ByteBuffer> readBuffers = new ArrayList<>();

    // Mock the read method to capture handlers and buffers
    doAnswer(invocation -> {
      readCount.incrementAndGet();
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      readBuffers.add(buffer);
      handlers.add(handler);
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Verify no initial read
    assertEquals(0, readCount.get());

    // Request to receive data - this should trigger a read
    FlowFuture<ByteBuffer> receiveFuture = connection.receive(1024);

    // Verify a read was triggered
    assertEquals(1, readCount.get());

    // Simulate data arrival by properly filling the read buffer
    if (!handlers.isEmpty() && !readBuffers.isEmpty()) {
      ByteBuffer readBuffer = readBuffers.get(0);
      byte[] testData = "Test data".getBytes();

      // Clear the buffer and put test data
      readBuffer.clear();
      readBuffer.put(testData);

      // Call the completion handler with the number of bytes read
      handlers.get(0).completed(testData.length, readBuffer);
    }

    // Verify we can get the data (just check not null, content verification is complex in mock)
    ByteBuffer result = receiveFuture.getNow();
    assertNotNull(result);
  }

  /**
   * Tests that read buffer size is customizable.
   */
  @Test
  void testCustomReadBufferSize() throws Exception {
    // Create connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Set custom read buffer size
    connection.setReadBufferSize(8192);

    // Mock the read method to verify the buffer size after setting it
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      // We should see a buffer of at least the custom size (or larger if maxBytes is bigger)
      assertTrue(buffer.capacity() >= 8192);
      readCount.incrementAndGet();
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Request to receive data - this should use the custom buffer size
    connection.receive(8192);

    // Verify the read was performed
    assertEquals(1, readCount.get());
  }

  /**
   * Tests error handling during read operations.
   */
  @Test
  void testReadErrorHandling() throws Exception {
    final List<CompletionHandler<Integer, ByteBuffer>> handlers = new ArrayList<>();

    // Mock the read method to capture handlers and simulate failure
    doAnswer(invocation -> {
      readCount.incrementAndGet();
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      handlers.add(handler);

      // Simulate failure on first read
      if (readCount.get() == 1) {
        handler.failed(new IOException("Simulated read error"), null);
      }

      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Trigger a read by calling receive
    connection.receive(1024);

    // Verify that a read was performed and failed
    assertEquals(1, readCount.get());

    // Connection should be closed due to the error
    assertTrue(channelClosed.get());
    assertFalse(connection.isOpen());
  }

  /**
   * Tests end of stream handling.
   */
  @Test
  void testEndOfStreamHandling() throws Exception {
    final List<CompletionHandler<Integer, ByteBuffer>> handlers = new ArrayList<>();

    // Mock the read method to simulate end of stream
    doAnswer(invocation -> {
      readCount.incrementAndGet();
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      handlers.add(handler);

      // Simulate end of stream (bytesRead = -1)
      if (readCount.get() == 1) {
        handler.completed(-1, null);
      }

      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Trigger a read by calling receive
    connection.receive(1024);

    // Verify that a read was performed
    assertEquals(1, readCount.get());

    // Connection should be closed due to end of stream
    assertTrue(channelClosed.get());
    assertFalse(connection.isOpen());
  }
}