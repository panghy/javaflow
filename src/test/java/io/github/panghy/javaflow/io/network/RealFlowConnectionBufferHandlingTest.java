package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests the buffer handling in RealFlowConnection to ensure that buffer operations
 * are performed correctly without any buffer overflows or underflows.
 * Also tests the fixed receive(maxBytes) method to ensure it correctly respects maxBytes.
 */
public class RealFlowConnectionBufferHandlingTest extends AbstractFlowTest {

  private AsynchronousServerSocketChannel serverChannel;
  private int port;
  private AsynchronousSocketChannel clientChannel;
  private AsynchronousSocketChannel serverSideChannel;
  private RealFlowConnection clientConnection;
  private RealFlowConnection serverConnection;

  @BeforeEach
  void setUp() throws Exception {
    // Create a server socket
    serverChannel = AsynchronousServerSocketChannel.open()
        .bind(new InetSocketAddress("localhost", 0));

    // Get the ephemeral port
    port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

    // Set up connection acceptance
    CountDownLatch acceptLatch = new CountDownLatch(1);
    AtomicReference<AsynchronousSocketChannel> acceptedChannelRef = new AtomicReference<>();

    serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
      @Override
      public void completed(AsynchronousSocketChannel result, Void attachment) {
        acceptedChannelRef.set(result);
        acceptLatch.countDown();
      }

      @Override
      public void failed(Throwable exc, Void attachment) {
        fail("Accept failed: " + exc.getMessage());
      }
    });

    // Create client channel and connect
    clientChannel = AsynchronousSocketChannel.open();
    clientChannel.connect(new InetSocketAddress("localhost", port)).get(2, TimeUnit.SECONDS);

    // Wait for server to accept
    assertTrue(acceptLatch.await(2, TimeUnit.SECONDS), "Accept timed out");

    // Get the accepted channel
    serverSideChannel = acceptedChannelRef.get();
    assertNotNull(serverSideChannel, "Server side channel not created");

    // Create RealFlowConnections on both sides
    Endpoint clientLocalEndpoint = new Endpoint((InetSocketAddress) clientChannel.getLocalAddress());
    Endpoint serverRemoteEndpoint = new Endpoint((InetSocketAddress) serverSideChannel.getRemoteAddress());

    Endpoint serverLocalEndpoint = new Endpoint((InetSocketAddress) serverSideChannel.getLocalAddress());
    Endpoint clientRemoteEndpoint = new Endpoint((InetSocketAddress) clientChannel.getRemoteAddress());

    clientConnection = new RealFlowConnection(clientChannel, clientLocalEndpoint, clientRemoteEndpoint);
    serverConnection = new RealFlowConnection(serverSideChannel, serverLocalEndpoint, serverRemoteEndpoint);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (clientConnection != null && clientConnection.isOpen()) {
      clientConnection.close();
    }
    if (serverConnection != null && serverConnection.isOpen()) {
      serverConnection.close();
    }
    if (clientChannel != null && clientChannel.isOpen()) {
      clientChannel.close();
    }
    if (serverSideChannel != null && serverSideChannel.isOpen()) {
      serverSideChannel.close();
    }
    if (serverChannel != null && serverChannel.isOpen()) {
      serverChannel.close();
    }
  }

  /**
   * Test the buffer handling in the completed method of the read CompletionHandler in RealFlowConnection.
   * This tests the exact code that was causing BufferOverflowException in the NetworkEchoExample.
   * <p>
   * Emulates the core logic of the CompletionHandler.completed method in RealFlowConnection.
   */
  @Test
  void testBufferHandling() throws Exception {
    // This test directly verifies the logic in the read completion handler

    // Case 1: Normal case, buffer has exactly the bytes we need
    ByteBuffer testBuffer1 = ByteBuffer.allocate(1024);
    testBuffer1.put("Hello World".getBytes());
    testBuffer1.flip();

    int bytesRead1 = testBuffer1.remaining();

    // Create a byte array of the correct size
    byte[] tmp1 = new byte[bytesRead1];

    // Read using the for loop pattern to avoid buffer over/underflow
    for (int i = 0; i < bytesRead1 && testBuffer1.hasRemaining(); i++) {
      tmp1[i] = testBuffer1.get();
    }

    // Verify correct data was read
    assertEquals("Hello World", new String(tmp1));

    // Case 2: Edge case - large buffer with partial data
    ByteBuffer testBuffer2 = ByteBuffer.allocate(8192); // Large buffer
    testBuffer2.put("Small message".getBytes());
    testBuffer2.flip();

    int bytesRead2 = testBuffer2.remaining();

    // Create a byte array of the correct size
    byte[] tmp2 = new byte[bytesRead2];

    // Read using the for loop pattern
    for (int i = 0; i < bytesRead2 && testBuffer2.hasRemaining(); i++) {
      tmp2[i] = testBuffer2.get();
    }

    // Verify correct data was read
    assertEquals("Small message", new String(tmp2));

    // Case 3: Edge case - attempting to read more bytes than available
    ByteBuffer testBuffer3 = ByteBuffer.allocate(10);
    testBuffer3.put("12345".getBytes());
    testBuffer3.flip();

    // Pretend we're trying to read more bytes than actually available
    int bytesRead3 = 10; // This is wrong, buffer only has 5 bytes

    // Create a byte array of the requested size
    byte[] tmp3 = new byte[bytesRead3];

    // Using the for loop pattern should prevent buffer underflow
    for (int i = 0; i < bytesRead3 && testBuffer3.hasRemaining(); i++) {
      tmp3[i] = testBuffer3.get();
    }

    // Verify what we could read (should be just "12345" and the rest zeros)
    assertEquals("12345", new String(tmp3, 0, 5).trim());

    // Verify that the original buffer is now empty
    assertEquals(0, testBuffer3.remaining());

    // Case 4: Directly using ByteBuffer.get(byte[]) would cause buffer underflow
    ByteBuffer testBuffer4 = ByteBuffer.allocate(10);
    testBuffer4.put("12345".getBytes());
    testBuffer4.flip();

    byte[] largerArray = new byte[10];

    // This would throw BufferUnderflowException
    assertThrows(java.nio.BufferUnderflowException.class, () -> {
      testBuffer4.get(largerArray);
    });
  }

  /**
   * Test for buffer isolation between different read operations.
   * This simulates multiple sequential reads to verify data doesn't bleed between reads.
   */
  @Test
  void testBufferIsolation() throws Exception {
    // Simulate multiple read operations with different data
    // Create three buffers with different content
    ByteBuffer buffer1 = ByteBuffer.allocate(1024);
    buffer1.put("hello".getBytes());
    buffer1.flip();

    ByteBuffer buffer2 = ByteBuffer.allocate(1024);
    buffer2.put("wow".getBytes());
    buffer2.flip();

    ByteBuffer buffer3 = ByteBuffer.allocate(1024);
    buffer3.put("third message".getBytes());
    buffer3.flip();

    // Process the buffers one after another, simulating sequential reads

    // First read
    int bytesRead1 = buffer1.remaining();
    byte[] tmp1 = new byte[bytesRead1];
    for (int i = 0; i < bytesRead1 && buffer1.hasRemaining(); i++) {
      tmp1[i] = buffer1.get();
    }

    // Second read 
    int bytesRead2 = buffer2.remaining();
    byte[] tmp2 = new byte[bytesRead2];
    for (int i = 0; i < bytesRead2 && buffer2.hasRemaining(); i++) {
      tmp2[i] = buffer2.get();
    }

    // Third read
    int bytesRead3 = buffer3.remaining();
    byte[] tmp3 = new byte[bytesRead3];
    for (int i = 0; i < bytesRead3 && buffer3.hasRemaining(); i++) {
      tmp3[i] = buffer3.get();
    }

    // Verify all reads captured the correct data without interference
    assertEquals("hello", new String(tmp1));
    assertEquals("wow", new String(tmp2));
    assertEquals("third message", new String(tmp3));

    // Verify all buffers are fully consumed
    assertEquals(0, buffer1.remaining());
    assertEquals(0, buffer2.remaining());
    assertEquals(0, buffer3.remaining());
  }

  /**
   * Test that simulates our new implementation approach where we use a dedicated
   * CompletionHandler for each read operation.
   */
  @Test
  void testSeparateHandlers() throws Exception {
    // This test manually simulates what our new implementation does - creating separate
    // handlers and buffers for each read to avoid interference

    // Simulated base class to hold common functionality
    class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
      private final ByteBuffer targetBuffer;
      private final String expectedMessage;

      ReadHandler(ByteBuffer buffer, String message) {
        this.targetBuffer = buffer;
        this.expectedMessage = message;
      }

      @Override
      public void completed(Integer bytesRead, ByteBuffer attachment) {
        // When read completes, flip the buffer
        attachment.flip();

        // Create a properly sized result buffer for the data
        byte[] tmp = new byte[bytesRead];
        for (int i = 0; i < bytesRead && attachment.hasRemaining(); i++) {
          tmp[i] = attachment.get();
        }

        // Verify this handler received the expected message
        assertEquals(expectedMessage, new String(tmp));
      }

      @Override
      public void failed(Throwable exc, ByteBuffer attachment) {
        // Not relevant for test
      }

      public ByteBuffer getBuffer() {
        return targetBuffer;
      }
    }

    // Create separate handlers for each read operation
    ReadHandler firstHandler = new ReadHandler(
        ByteBuffer.allocate(1024), "hello");
    ReadHandler secondHandler = new ReadHandler(
        ByteBuffer.allocate(1024), "wow");

    // Simulate first read
    ByteBuffer firstBuffer = firstHandler.getBuffer();
    firstBuffer.put("hello".getBytes());
    firstHandler.completed(5, firstBuffer);

    // Simulate second read with different handler
    ByteBuffer secondBuffer = secondHandler.getBuffer();
    secondBuffer.put("wow".getBytes());
    secondHandler.completed(3, secondBuffer);

    // Our assertions are in the handler, the test passes if no assert fails
  }

  /**
   * Tests that receive() respects the maxBytes parameter and doesn't return more data than
   * requested, even when more is available.
   */
  @Test
  void testReceiveRespectsMaxBytes() throws Exception {
    // Send a 1000-byte message
    byte[] data = new byte[1000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }

    ByteBuffer sendBuffer = ByteBuffer.wrap(data);
    CompletableFuture<Void> sendFuture = clientConnection.send(sendBuffer);
    sendFuture.get(5, TimeUnit.SECONDS);

    // Receive with a smaller maxBytes (only 100 bytes)
    CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(100);
    ByteBuffer receiveBuffer = receiveFuture.get(5, TimeUnit.SECONDS);

    // Verify we got exactly 100 bytes, not more
    assertEquals(100, receiveBuffer.remaining());

    // Verify the content matches the first 100 bytes of the sent data
    byte[] receivedData = new byte[receiveBuffer.remaining()];
    receiveBuffer.get(receivedData);

    byte[] expectedFirstChunk = new byte[100];
    System.arraycopy(data, 0, expectedFirstChunk, 0, 100);
    assertArrayEquals(expectedFirstChunk, receivedData);

    // Now receive the remaining data in a second read with larger maxBytes
    CompletableFuture<ByteBuffer> receiveFuture2 = serverConnection.receive(2000);
    ByteBuffer receiveBuffer2 = receiveFuture2.get(5, TimeUnit.SECONDS);

    // Verify we got the remaining 900 bytes
    assertEquals(900, receiveBuffer2.remaining());

    // Verify the content matches the remaining bytes
    byte[] receivedData2 = new byte[receiveBuffer2.remaining()];
    receiveBuffer2.get(receivedData2);

    byte[] expectedSecondChunk = new byte[900];
    System.arraycopy(data, 100, expectedSecondChunk, 0, 900);
    assertArrayEquals(expectedSecondChunk, receivedData2);
  }

  /**
   * Tests receiving data with exact buffer size matches maxBytes.
   */
  @Test
  void testReceiveExactBufferSize() throws Exception {
    // Send a 500-byte message
    byte[] data = new byte[500];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }

    ByteBuffer sendBuffer = ByteBuffer.wrap(data);
    CompletableFuture<Void> sendFuture = clientConnection.send(sendBuffer);
    sendFuture.get(5, TimeUnit.SECONDS);

    // Receive with exactly matching maxBytes
    CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(500);
    ByteBuffer receiveBuffer = receiveFuture.get(5, TimeUnit.SECONDS);

    // Verify we got exactly 500 bytes
    assertEquals(500, receiveBuffer.remaining());

    // Verify the content matches exactly
    byte[] receivedData = new byte[receiveBuffer.remaining()];
    receiveBuffer.get(receivedData);
    assertArrayEquals(data, receivedData);
  }

  /**
   * Tests receiving data when maxBytes is larger than the available data.
   */
  @Test
  void testReceiveLargerThanAvailable() throws Exception {
    // Send a 300-byte message
    byte[] data = new byte[300];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }

    ByteBuffer sendBuffer = ByteBuffer.wrap(data);
    CompletableFuture<Void> sendFuture = clientConnection.send(sendBuffer);
    sendFuture.get(5, TimeUnit.SECONDS);

    // Receive with a larger maxBytes than actual data
    CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(1000);
    ByteBuffer receiveBuffer = receiveFuture.get(5, TimeUnit.SECONDS);

    // Verify we got exactly 300 bytes (all that was available)
    assertEquals(300, receiveBuffer.remaining());

    // Verify the content matches exactly
    byte[] receivedData = new byte[receiveBuffer.remaining()];
    receiveBuffer.get(receivedData);
    assertArrayEquals(data, receivedData);
  }

  /**
   * Tests receiving data in exact-sized chunks (receiving exactly maxBytes each time).
   */
  @ParameterizedTest
  @ValueSource(ints = {10, 50, 100, 250})
  void testReceiveInExactChunks(int chunkSize) throws Exception {
    // Create data buffer of 1000 bytes
    final int totalSize = 1000;
    byte[] data = new byte[totalSize];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }

    ByteBuffer sendBuffer = ByteBuffer.wrap(data);
    CompletableFuture<Void> sendFuture = clientConnection.send(sendBuffer);
    sendFuture.get(5, TimeUnit.SECONDS);

    // Receive the data in exact-sized chunks
    ByteBuffer combinedBuffer = ByteBuffer.allocate(totalSize);
    int bytesReceived = 0;

    // Calculate how many full chunks to receive
    int chunksToReceive = totalSize / chunkSize;
    int lastChunkSize = totalSize % chunkSize;

    for (int i = 0; i < chunksToReceive; i++) {
      CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(chunkSize);
      ByteBuffer chunk = receiveFuture.get(5, TimeUnit.SECONDS);

      assertEquals(chunkSize, chunk.remaining(), "Chunk size mismatch");
      combinedBuffer.put(chunk);
      bytesReceived += chunkSize;
    }

    // Get the last chunk if there's a remainder
    if (lastChunkSize > 0) {
      CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(lastChunkSize);
      ByteBuffer chunk = receiveFuture.get(5, TimeUnit.SECONDS);

      assertEquals(lastChunkSize, chunk.remaining(), "Last chunk size mismatch");
      combinedBuffer.put(chunk);
      bytesReceived += lastChunkSize;
    }

    assertEquals(totalSize, bytesReceived, "Total bytes received mismatch");

    // Verify the combined data matches exactly
    combinedBuffer.flip();
    byte[] receivedData = new byte[combinedBuffer.remaining()];
    combinedBuffer.get(receivedData);
    assertArrayEquals(data, receivedData);
  }

  /**
   * Tests receiving data with a very small maxBytes value to ensure buffer splitting works correctly
   * across many reads.
   */
  @Test
  void testReceiveWithVerySmallMaxBytes() throws Exception {
    // Send a buffer with 100 bytes
    byte[] data = new byte[100];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }

    ByteBuffer sendBuffer = ByteBuffer.wrap(data);
    CompletableFuture<Void> sendFuture = clientConnection.send(sendBuffer);
    sendFuture.get(5, TimeUnit.SECONDS);

    // Receive in many tiny chunks (5 bytes each)
    int chunkSize = 5;
    int expectedChunks = 100 / chunkSize;
    List<byte[]> receivedChunks = new ArrayList<>();

    for (int i = 0; i < expectedChunks; i++) {
      CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(chunkSize);
      ByteBuffer chunk = receiveFuture.get(5, TimeUnit.SECONDS);

      assertEquals(chunkSize, chunk.remaining());

      byte[] chunkBytes = new byte[chunk.remaining()];
      chunk.get(chunkBytes);
      receivedChunks.add(chunkBytes);

      // Verify this chunk matches the expected portion of the original data
      byte[] expectedChunk = new byte[chunkSize];
      System.arraycopy(data, i * chunkSize, expectedChunk, 0, chunkSize);
      assertArrayEquals(expectedChunk, chunkBytes, "Chunk " + i + " doesn't match expected data");
    }

    assertEquals(expectedChunks, receivedChunks.size());
  }

  /**
   * Tests that invalid maxBytes values (0 or negative) are properly rejected.
   */
  @Test
  void testInvalidMaxBytes() throws Exception {
    try {
      CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(0);
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception for maxBytes = 0");
    } catch (Exception e) {
      assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    try {
      CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(-10);
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception for maxBytes = -10");
    } catch (Exception e) {
      assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }
  }

  /**
   * Tests that multiple receive calls are handled correctly with a specific sequence of operations:
   * 1. Send a large buffer (1000 bytes)
   * 2. Receive a small chunk (100 bytes)
   * 3. Send another large buffer (500 bytes)
   * 4. Receive the rest of the first buffer (900 bytes)
   * 5. Receive the entire second buffer (500 bytes)
   * <p>
   * This tests that buffers are properly queued and processed in order.
   */
  @Test
  void testMultipleReceiveAndSendOperations() throws Exception {
    // Send first buffer (1000 bytes)
    byte[] firstData = new byte[1000];
    for (int i = 0; i < firstData.length; i++) {
      firstData[i] = (byte) (i % 256);
    }

    ByteBuffer firstSendBuffer = ByteBuffer.wrap(firstData);
    CompletableFuture<Void> firstSendFuture = clientConnection.send(firstSendBuffer);
    firstSendFuture.get(5, TimeUnit.SECONDS);

    // Receive first chunk (100 bytes)
    CompletableFuture<ByteBuffer> firstReceiveFuture = serverConnection.receive(100);
    ByteBuffer firstReceiveBuffer = firstReceiveFuture.get(5, TimeUnit.SECONDS);
    assertEquals(100, firstReceiveBuffer.remaining());

    byte[] firstReceivedChunk = new byte[firstReceiveBuffer.remaining()];
    firstReceiveBuffer.get(firstReceivedChunk);

    byte[] expectedFirstChunk = new byte[100];
    System.arraycopy(firstData, 0, expectedFirstChunk, 0, 100);
    assertArrayEquals(expectedFirstChunk, firstReceivedChunk);

    // Receive rest of first buffer (900 bytes)
    CompletableFuture<ByteBuffer> secondReceiveFuture = serverConnection.receive(1000); // More than needed
    ByteBuffer secondReceiveBuffer = secondReceiveFuture.get(5, TimeUnit.SECONDS);
    assertEquals(900, secondReceiveBuffer.remaining());

    // Send second buffer (500 bytes)
    byte[] secondData = new byte[500];
    for (int i = 0; i < secondData.length; i++) {
      secondData[i] = (byte) ((i + 128) % 256); // Different pattern
    }

    ByteBuffer secondSendBuffer = ByteBuffer.wrap(secondData);
    CompletableFuture<Void> secondSendFuture = clientConnection.send(secondSendBuffer);
    secondSendFuture.get(5, TimeUnit.SECONDS);

    byte[] secondReceivedChunk = new byte[secondReceiveBuffer.remaining()];
    secondReceiveBuffer.get(secondReceivedChunk);

    byte[] expectedSecondChunk = new byte[900];
    System.arraycopy(firstData, 100, expectedSecondChunk, 0, 900);
    assertArrayEquals(expectedSecondChunk, secondReceivedChunk);

    // Receive entire second buffer (500 bytes)
    CompletableFuture<ByteBuffer> thirdReceiveFuture = serverConnection.receive(1000); // More than needed
    ByteBuffer thirdReceiveBuffer = thirdReceiveFuture.get(5, TimeUnit.SECONDS);
    assertEquals(500, thirdReceiveBuffer.remaining());

    byte[] thirdReceivedChunk = new byte[thirdReceiveBuffer.remaining()];
    thirdReceiveBuffer.get(thirdReceivedChunk);

    assertArrayEquals(secondData, thirdReceivedChunk);
  }
}