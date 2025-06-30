package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for RealFlowConnection that mock AsynchronousSocketChannel to test
 * specific error conditions and edge cases.
 */
@SuppressWarnings("unchecked")
public class RealFlowConnectionTest extends AbstractFlowTest {

  // The mocked socket channel
  private AsynchronousSocketChannel mockChannel;

  // The connection under test
  private RealFlowConnection connection;

  // Endpoints for the connection
  private Endpoint localEndpoint;
  private Endpoint remoteEndpoint;

  // Flag to track if close was called on the channel
  private AtomicBoolean channelClosed = new AtomicBoolean(false);

  @BeforeEach
  void setUp() throws IOException {
    // Create mock AsynchronousSocketChannel
    mockChannel = mock(AsynchronousSocketChannel.class);

    // Set up default mock behavior
    when(mockChannel.isOpen()).thenReturn(true);

    // Setup endpoints
    localEndpoint = new Endpoint("localhost", 12345);
    remoteEndpoint = new Endpoint("localhost", 54321);

    // Setup the close method to update the channelClosed flag
    doAnswer(invocation -> {
      channelClosed.set(true);
      when(mockChannel.isOpen()).thenReturn(false);
      return null;
    }).when(mockChannel).close();
  }

  /**
   * Tests the failed() method in the write CompletionHandler with a simulated IOException.
   */
  @Test
  void testWriteCompletionHandlerFailure() throws Exception {
    // Mock the write() method to trigger the failed() method with an IOException
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      Void attachment = invocation.getArgument(1);
      CompletionHandler<Integer, Void> handler = invocation.getArgument(2);

      // Trigger the failed method with an IOException
      handler.failed(new IOException("Simulated write error"), attachment);

      // Return null as Future<Integer> to satisfy the method signature
      return mock(Future.class);
    }).when(mockChannel).write(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection with our mock
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Send data to trigger the write error
    ByteBuffer testData = ByteBuffer.wrap("Test data".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(testData);
    try {
      sendFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof IOException);
      Assertions.assertEquals("Simulated write error", e.getCause().getMessage());
    }

    // Verify the connection was closed due to the error
    assertTrue(channelClosed.get());
    assertFalse(connection.isOpen());
  }

  /**
   * Tests throwing an exception in channel.close() during connection.close().
   */
  @Test
  void testCloseWithChannelCloseException() throws Exception {
    // Mock the close() method to throw an IOException
    doAnswer(invocation -> {
      channelClosed.set(true);
      when(mockChannel.isOpen()).thenReturn(false);
      throw new IOException("Simulated close error");
    }).when(mockChannel).close();

    // Set up the read to avoid errors during initialization
    doAnswer(invocation -> {
      // Just do nothing for reads
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Call close on the connection
    CompletableFuture<Void> closeFuture = connection.close();
    try {
      closeFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof IOException);
      Assertions.assertEquals("Simulated close error", e.getCause().getMessage());
    }

    // Even with the exception, the closed state should be set
    assertFalse(connection.isOpen());
  }

  /**
   * Tests handling when channel.read() returns -1 (EOF).
   */
  @Test
  void testReadEOF() throws Exception {
    // Mock the read() method to return EOF (-1)
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      ByteBuffer attachment = invocation.getArgument(1);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);

      // Signal end of stream with -1
      handler.completed(-1, attachment);

      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection with our mock
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Request to receive data
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);

    try {
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      // Expected exception
    }

    // Connection should be closed at this point due to EOF
    assertTrue(channelClosed.get());
    assertFalse(connection.isOpen());
  }

  /**
   * Tests that operations are properly denied when connection is already closed.
   * This test replaces the zero-bytes test which was causing stack overflow issues.
   */
  @Test
  void testOperationsAfterClose() throws Exception {
    // Set up a minimal read handler that does nothing
    doAnswer(invocation -> mock(Future.class))
        .when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection with our mock
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Close the connection manually
    CompletableFuture<Void> closeFuture = connection.close();
    closeFuture.get(5, TimeUnit.SECONDS);

    // Verify connection is closed
    assertFalse(connection.isOpen());

    // Try to send data on closed connection
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(data);

    // Should immediately complete exceptionally, no need to pump
    assertTrue(sendFuture.isDone());
    assertTrue(sendFuture.isCompletedExceptionally());

    // Try to receive data on closed connection
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);

    // Should immediately complete exceptionally, no need to pump
    assertTrue(receiveFuture.isDone());
    assertTrue(receiveFuture.isCompletedExceptionally());

    // The exception should be an IOException mentioning "closed"
    try {
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof IOException);
      assertTrue(e.getCause().getMessage().contains("closed"));
    }
  }

  /**
   * Tests handling when channel.read() fails with an exception.
   */
  @Test
  void testReadCompletionHandlerFailure() throws Exception {
    // Mock the read() method to trigger failed() with an IOException
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      ByteBuffer attachment = invocation.getArgument(1);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);

      // Trigger the failed method with an IOException
      handler.failed(new IOException("Simulated read error"), attachment);

      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection with our mock
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Request to receive data
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);

    try {
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      // Expected exception
    }

    // Connection should be closed due to the error
    assertTrue(channelClosed.get());
    assertFalse(connection.isOpen());

    // ReceiveFuture should be completed exceptionally
    assertTrue(receiveFuture.isCompletedExceptionally());
    try {
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      // The error will be StreamClosedException since the stream is closed
      // rather than the direct IOException from the read
    }
  }

  /**
   * Tests the "zero bytes read" branch in the read completion handler.
   * This specifically targets the code that reuses the same buffer when zero bytes are read.
   */
  @Test
  void testReadZeroBytes() throws Exception {
    // We need to track the number of read attempts to avoid stack overflow
    final int[] readCount = new int[1];

    // Mock the read() method to first return 0 bytes, then return some actual data
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      ByteBuffer attachment = invocation.getArgument(1);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);

      readCount[0]++;

      if (readCount[0] == 1) {
        // First call - return 0 bytes (triggers the zero bytes branch)
        handler.completed(0, attachment);
      } else if (readCount[0] == 2) {
        // Second call - verify it's the same buffer and return some data
        Assertions.assertSame(buffer, attachment, "Buffer should be reused for zero-byte reads");

        // Simulate data being read into the buffer
        byte[] testData = "Test data after zero bytes".getBytes();
        buffer.put(testData);
        handler.completed(testData.length, attachment);
      } else {
        // Any subsequent reads (should only be one for the next continuous read cycle)
        // Just don't call the handler to avoid recursion stack overflow
      }

      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection with our mock
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Request to receive data
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    receiveFuture.get(5, TimeUnit.SECONDS);

    // Verify the read count indicates that we went through the zero bytes branch
    assertTrue(readCount[0] >= 2, "Should have performed at least 2 reads");

    // Verify the future completed with data (not exceptionally)
    assertFalse(receiveFuture.isCompletedExceptionally());
    ByteBuffer result = receiveFuture.get(5, TimeUnit.SECONDS);

    // Verify the received data
    byte[] resultBytes = new byte[result.remaining()];
    result.get(resultBytes);
    Assertions.assertEquals("Test data after zero bytes", new String(resultBytes));

    // Connection should still be open
    assertFalse(channelClosed.get());
    assertTrue(connection.isOpen());
  }

  /**
   * Tests multiple calls to close() which should be idempotent.
   */
  @Test
  void testMultipleCloseCalls() throws Exception {
    // Set up the read to avoid errors during initialization
    doAnswer(invocation -> {
      // Just do nothing for reads
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Call close on the connection multiple times
    CompletableFuture<Void> firstCloseFuture = connection.close();
    CompletableFuture<Void> secondCloseFuture = connection.close();

    firstCloseFuture.get(5, TimeUnit.SECONDS);
    secondCloseFuture.get(5, TimeUnit.SECONDS);

    // Verify the close futures completed normally
    assertFalse(firstCloseFuture.isCompletedExceptionally());
    assertFalse(secondCloseFuture.isCompletedExceptionally());

    // Verify close was called only once on the channel
    verify(mockChannel, times(1)).close();
  }

  /**
   * Tests using send() when the connection is already closed.
   */
  @Test
  void testSendWhenAlreadyClosed() throws Exception {
    // Set up the read to avoid errors during initialization
    doAnswer(invocation -> {
      // Just do nothing for reads
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Close the connection
    CompletableFuture<Void> closeFuture = connection.close();

    closeFuture.get(5, TimeUnit.SECONDS);

    // Try to send data after closing
    ByteBuffer testData = ByteBuffer.wrap("Test data".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(testData);

    // The future should complete immediately with an exception
    assertTrue(sendFuture.isDone());
    assertTrue(sendFuture.isCompletedExceptionally());

    try {
      sendFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof IOException);
      assertTrue(e.getCause().getMessage().contains("closed"));
    }

    // Verify write was never called on the channel
    verify(mockChannel, never()).write(any(ByteBuffer.class), any(), any(CompletionHandler.class));
  }

  /**
   * Tests using receive() when the connection is already closed.
   */
  @Test
  void testReceiveWhenAlreadyClosed() throws Exception {
    // Set up the read to avoid errors during initialization
    doAnswer(invocation -> {
      // Just do nothing for reads
      return mock(Future.class);
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any(CompletionHandler.class));

    // Create the connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Close the connection
    CompletableFuture<Void> closeFuture = connection.close();
    closeFuture.get(5, TimeUnit.SECONDS);

    // Try to receive data after closing
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);

    // The future should complete immediately with an exception
    assertTrue(receiveFuture.isDone());
    assertTrue(receiveFuture.isCompletedExceptionally());

    try {
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof IOException);
      assertTrue(e.getCause().getMessage().contains("closed"));
    }
  }
}
