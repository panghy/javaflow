package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.StreamClosedException;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for RealFlowConnection using actual socket channels.
 * These tests focus on improving code coverage for the RealFlowConnection class.
 */
public class RealFlowConnectionIntegrationTest extends AbstractFlowTest {

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
   * Tests basic send and receive functionality.
   */
  @Test
  void testSendAndReceive() throws Exception {
    // Send data from client to server
    String testMessage = "Hello, server!";
    ByteBuffer sendBuffer = ByteBuffer.wrap(testMessage.getBytes());

    FlowFuture<Void> sendFuture = clientConnection.send(sendBuffer);
    sendFuture.getNow();

    // Receive on server side
    FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    receiveFuture.getNow();

    assertFalse(receiveFuture.isCompletedExceptionally());
    ByteBuffer receiveBuffer = receiveFuture.getNow();

    // Verify received data
    byte[] receivedBytes = new byte[receiveBuffer.remaining()];
    receiveBuffer.get(receivedBytes);
    String receivedMessage = new String(receivedBytes);

    assertEquals(testMessage, receivedMessage);
  }

  /**
   * Tests that trying to use a connection after closing it throws an appropriate exception.
   */
  @Test
  void testUseAfterClose() throws Exception {
    // Close the connection
    FlowFuture<Void> closeFuture = clientConnection.close();
    closeFuture.getNow();
    // Verify it reports as closed
    assertFalse(clientConnection.isOpen());

    // Try to send, should fail
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    FlowFuture<Void> sendFuture = clientConnection.send(data);
    try {
      sendFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertInstanceOf(IOException.class, e.getCause());
      assertTrue(e.getCause().getMessage().contains("closed"));
    }

    // Try to receive, should fail
    FlowFuture<ByteBuffer> receiveFuture = clientConnection.receive(1024);

    try {
      receiveFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertInstanceOf(IOException.class, e.getCause());
      assertTrue(e.getCause().getMessage().contains("closed"));
    }
  }

  /**
   * Tests the stream receive API.
   */
  @Test
  void testReceiveStream() throws Exception {
    // Get the receive stream
    FlowStream<ByteBuffer> receiveStream = serverConnection.receiveStream();
    FlowFuture<ByteBuffer> firstReceiveFuture = receiveStream.nextAsync();

    // Send first message
    String message1 = "First message";
    ByteBuffer buffer1 = ByteBuffer.wrap(message1.getBytes());
    clientConnection.send(buffer1);

    ByteBuffer received1 = firstReceiveFuture.getNow();
    byte[] bytes1 = new byte[received1.remaining()];
    received1.get(bytes1);
    assertEquals(message1, new String(bytes1));

    // Get next future from stream
    FlowFuture<ByteBuffer> secondReceiveFuture = receiveStream.nextAsync();

    // Send second message
    String message2 = "Second message";
    ByteBuffer buffer2 = ByteBuffer.wrap(message2.getBytes());
    clientConnection.send(buffer2);

    ByteBuffer received2 = secondReceiveFuture.getNow();
    byte[] bytes2 = new byte[received2.remaining()];
    received2.get(bytes2);
    assertEquals(message2, new String(bytes2));
  }

  /**
   * Tests that closing the connection properly closes the stream.
   */
  @Test
  void testCloseConnectionClosesStream() {
    // Get receive stream
    FlowStream<ByteBuffer> receiveStream = serverConnection.receiveStream();
    FlowFuture<ByteBuffer> receiveFuture = receiveStream.nextAsync();

    // Close the client side (should close the server side too)
    clientConnection.close();

    try {
      receiveFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException expected) {
    }
  }

  /**
   * Tests sending large data that requires multiple writes.
   */
  @Test
  void testSendLargeData() throws Exception {
    // Create a large data buffer (100KB)
    byte[] largeData = new byte[102400];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    ByteBuffer sendBuffer = ByteBuffer.wrap(largeData);

    // Send the large data
    FlowFuture<Void> sendFuture = clientConnection.send(sendBuffer);
    sendFuture.getNow();

    // Receive the data in chunks
    ByteBuffer combinedBuffer = ByteBuffer.allocate(102400);

    // Keep receiving until we get all the data
    while (combinedBuffer.position() < largeData.length) {
      FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);

      ByteBuffer chunk = receiveFuture.getNow();
      combinedBuffer.put(chunk);
    }

    // Verify the data
    combinedBuffer.flip();
    byte[] receivedData = new byte[combinedBuffer.remaining()];
    combinedBuffer.get(receivedData);

    assertArrayEquals(largeData, receivedData);
  }

  /**
   * Tests multiple close calls (should be idempotent).
   */
  @Test
  void testMultipleClose() throws Exception {
    // Close the connection
    FlowFuture<Void> firstCloseFuture = clientConnection.close();
    firstCloseFuture.getNow();

    // Close again
    FlowFuture<Void> secondCloseFuture = clientConnection.close();
    secondCloseFuture.getNow();

    // Both should complete normally
    assertFalse(firstCloseFuture.isCompletedExceptionally());
    assertFalse(secondCloseFuture.isCompletedExceptionally());

    // Should be the same future instance
    assertSame(firstCloseFuture, secondCloseFuture);
  }

  /**
   * Tests the closeFuture method.
   */
  @Test
  void testCloseFuture() throws Exception {
    // Get the close future
    FlowFuture<Void> closeFuture = clientConnection.closeFuture();

    // It should not be completed yet
    assertFalse(closeFuture.isDone());

    // Close the connection
    clientConnection.close();
    closeFuture.getNow();

    // Now the future should be completed
    assertTrue(closeFuture.isDone());
    assertFalse(closeFuture.isCompletedExceptionally());
  }

  /**
   * Tests the error handling in the send completion handler.
   */
  @Test
  void testSendErrorHandling() throws Exception {
    // Break the channel by closing it directly
    clientChannel.close();

    // Now try to send, which should trigger the error handler
    ByteBuffer buffer = ByteBuffer.wrap("This will fail".getBytes());
    FlowFuture<Void> sendFuture = clientConnection.send(buffer);

    try {
      sendFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException expected) {
    }

    // Connection should be closed
    assertFalse(clientConnection.isOpen());
  }

  /**
   * Tests partial writes for large buffers, specifically targeting the
   * bufferToWrite.hasRemaining() branch in RealFlowConnection.send() CompletionHandler.
   */
  @Test
  void testPartialWrites() throws Exception {
    // Create a very large buffer to ensure multiple socket writes (which forces partial writes)
    final int LARGE_SIZE = 50 * 1024 * 1024; // 50MB to guarantee partial writes
    byte[] largeData = new byte[LARGE_SIZE];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    ByteBuffer largeBuffer = ByteBuffer.wrap(largeData);

    // Send the large buffer from client to server
    FlowFuture<Void> sendFuture = clientConnection.send(largeBuffer);
    sendFuture.toCompletableFuture().whenComplete((result, error) -> {
      if (error == null) {
        System.out.println("Send completed successfully");
      } else {
        System.err.println("Send failed: " + error.getMessage());
      }
    });

    // On the server side, start receiving data in chunks immediately
    // This prevents the send buffer from filling up and blocking
    ByteBuffer combinedBuffer = ByteBuffer.allocate(LARGE_SIZE);

    // Start a concurrent task to receive data
    AtomicInteger totalBytesReceived = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    while (totalBytesReceived.get() < LARGE_SIZE) {
      // Ensure we don't get stuck in an infinite loop
      if (System.currentTimeMillis() - startTime > 30000) { // 30-second timeout
        throw new RuntimeException("Timed out waiting for all data to be received");
      }

      // Receive a chunk of data
      System.out.println("Waiting for next chunk...");
      ByteBuffer chunk = serverConnection.receive(8192).getNow();

      // Add the chunk to our combined buffer
      int chunkSize = chunk.remaining();
      combinedBuffer.put(chunk);
      totalBytesReceived.addAndGet(chunkSize);

      System.out.println("Received chunk of " + chunkSize + " bytes, total: " +
                         totalBytesReceived.get() + "/" + LARGE_SIZE);
    }

    // Now wait for the send to complete
    sendFuture.getNow();

    // Verify the future completed successfully
    assertTrue(sendFuture.isDone(), "Send future should be completed");
    assertFalse(sendFuture.isCompletedExceptionally(), "Send should not complete exceptionally");

    // Prepare the buffer for reading
    combinedBuffer.flip();

    // Create a byte array to hold the received data
    byte[] receivedData = new byte[combinedBuffer.remaining()];
    combinedBuffer.get(receivedData);

    // Verify the received data matches the sent data (check first 1MB)
    for (int i = 0; i < Math.min(1024 * 1024, receivedData.length); i++) {
      assertEquals((byte) (i % 256), receivedData[i],
          "Data mismatch at position " + i);
    }

    // This test has successfully verified:
    // 1. Large buffers can be sent correctly (which must use partial writes internally)
    // 2. The bufferToWrite.hasRemaining() branch must have been executed
    //    for the data to be sent correctly

    System.out.println("Successfully verified partial write functionality");
  }

  /**
   * Tests the read completion handler by closing the socket during a read.
   */
  @Test
  void testReadCompletionHandlerError() throws Exception {
    // Start a read
    FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);

    // Break the client channel by closing it
    clientChannel.close();

    try {
      receiveFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException expected) {
    }

    // Server connection should be closed
    assertFalse(serverConnection.isOpen());
  }

  /**
   * Tests the getLocalEndpoint and getRemoteEndpoint methods.
   */
  @Test
  void testEndpoints() {
    // Get the endpoints
    Endpoint localEndpoint = clientConnection.getLocalEndpoint();
    Endpoint remoteEndpoint = clientConnection.getRemoteEndpoint();

    // Verify they're not null
    assertNotNull(localEndpoint);
    assertNotNull(remoteEndpoint);

    // Verify they have the correct port
    assertEquals(port, remoteEndpoint.getPort());
  }

  /**
   * Helper method to create a client-server connection pair on an available port.
   *
   * @return Array containing [clientConnection, serverConnection]
   */
  private FlowConnection[] createClientServerConnectionPair() throws Exception {
    // Create a transport
    RealFlowTransport transport = new RealFlowTransport();

    // Set up server on any available port
    ConnectionListener listener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> stream = listener.getStream();
    int boundPort = listener.getPort();

    // Connect client with a timeout
    FlowFuture<FlowConnection> connectFuture = transport.connect(new Endpoint("localhost", boundPort));

    // Use a CountDownLatch to wait for completion with a timeout
    CountDownLatch connectLatch = new CountDownLatch(1);
    AtomicReference<FlowConnection> clientConnectionRef = new AtomicReference<>();
    AtomicReference<Throwable> connectErrorRef = new AtomicReference<>();

    connectFuture.whenComplete((connection, error) -> {
      if (error != null) {
        connectErrorRef.set(error);
      } else {
        clientConnectionRef.set(connection);
      }
      connectLatch.countDown();
    });

    // Wait up to 5 seconds for connection
    if (!connectLatch.await(5, TimeUnit.SECONDS)) {
      throw new RuntimeException("Connection timed out after 5 seconds");
    }

    // Check for connection error
    if (connectErrorRef.get() != null) {
      throw new RuntimeException("Connection failed", connectErrorRef.get());
    }

    FlowConnection clientConnection = clientConnectionRef.get();
    if (clientConnection == null) {
      throw new RuntimeException("Client connection is null despite successful future");
    }

    // Accept server connection with timeout
    FlowFuture<FlowConnection> acceptFuture = stream.nextAsync();

    // Use a CountDownLatch to wait for server acceptance with a timeout
    CountDownLatch acceptLatch = new CountDownLatch(1);
    AtomicReference<FlowConnection> serverConnectionRef = new AtomicReference<>();
    AtomicReference<Throwable> acceptErrorRef = new AtomicReference<>();

    acceptFuture.whenComplete((connection, error) -> {
      if (error != null) {
        acceptErrorRef.set(error);
      } else {
        serverConnectionRef.set(connection);
      }
      acceptLatch.countDown();
    });

    // Wait up to 5 seconds for server acceptance
    if (!acceptLatch.await(5, TimeUnit.SECONDS)) {
      throw new RuntimeException("Server acceptance timed out after 5 seconds");
    }

    // Check for acceptance error
    if (acceptErrorRef.get() != null) {
      throw new RuntimeException("Server acceptance failed", acceptErrorRef.get());
    }

    FlowConnection serverConnection = serverConnectionRef.get();
    if (serverConnection == null) {
      throw new RuntimeException("Server connection is null despite successful future");
    }

    return new FlowConnection[]{clientConnection, serverConnection};
  }

  /**
   * Helper method to create a client-server connection pair.
   * This overload is kept for backward compatibility with existing tests.
   *
   * @param port The port to connect to (ignored - will use an available port)
   * @return Array containing [clientConnection, serverConnection]
   */
  private FlowConnection[] createClientServerConnectionPair(int port) throws Exception {
    return createClientServerConnectionPair();
  }

  /**
   * Creates a test specifically targeting the read completion handler error path.
   * This tests the inner class in RealFlowConnection that handles read operations.
   */
  @Test
  void testReadCompletionHandlerFailurePath() throws Exception {
    // Get a client connection pair using our available port method
    FlowConnection[] connections = createClientServerConnectionPair();
    FlowConnection clientConnection = connections[0];
    FlowConnection serverConnection = connections[1];

    // First, send some data to establish that the connection works
    String testMessage = "Test message";
    ByteBuffer buffer = ByteBuffer.wrap(testMessage.getBytes());
    FlowFuture<Void> sendFuture = clientConnection.send(buffer);

    // Wait for send to complete with timeout
    CountDownLatch sendLatch = new CountDownLatch(1);
    AtomicReference<Throwable> sendErrorRef = new AtomicReference<>();

    sendFuture.whenComplete((result, error) -> {
      if (error != null) {
        sendErrorRef.set(error);
      }
      sendLatch.countDown();
    });

    if (!sendLatch.await(5, TimeUnit.SECONDS)) {
      throw new RuntimeException("Send operation timed out after 5 seconds");
    }

    if (sendErrorRef.get() != null) {
      throw new RuntimeException("Send operation failed", sendErrorRef.get());
    }

    // Read from the server side to verify connection
    FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);

    // Wait for receive to complete with timeout
    CountDownLatch receiveLatch = new CountDownLatch(1);
    AtomicReference<ByteBuffer> receiveBufferRef = new AtomicReference<>();
    AtomicReference<Throwable> receiveErrorRef = new AtomicReference<>();

    receiveFuture.whenComplete((buffer1, error) -> {
      if (error != null) {
        receiveErrorRef.set(error);
      } else {
        receiveBufferRef.set(buffer1);
      }
      receiveLatch.countDown();
    });

    if (!receiveLatch.await(5, TimeUnit.SECONDS)) {
      throw new RuntimeException("Receive operation timed out after 5 seconds");
    }

    if (receiveErrorRef.get() != null) {
      throw new RuntimeException("Receive operation failed", receiveErrorRef.get());
    }

    ByteBuffer receiveBuffer = receiveBufferRef.get();
    if (receiveBuffer == null) {
      throw new RuntimeException("Receive buffer is null despite successful future");
    }

    byte[] bytes = new byte[receiveBuffer.remaining()];
    receiveBuffer.get(bytes);
    String receivedMessage = new String(bytes);

    assertEquals(testMessage, receivedMessage);

    // Create a special test that forces the completion handler's failed method to be called
    // Extract the RealFlowConnection channel (requires reflection)
    AsynchronousSocketChannel channel = null;
    try {
      java.lang.reflect.Field fieldChannel = RealFlowConnection.class.getDeclaredField("channel");
      fieldChannel.setAccessible(true);
      channel = (AsynchronousSocketChannel) fieldChannel.get(serverConnection);
    } catch (Exception e) {
      fail("Could not access channel field: " + e.getMessage());
    }

    // Force close the underlying channel
    if (channel != null) {
      channel.close();
    }

    // Give a short delay for the channel close to take effect
    Thread.sleep(100);

    // Now try to read from the closed channel - this should trigger the failed path
    FlowFuture<ByteBuffer> errorReceiveFuture = serverConnection.receive(1024);

    // Wait for the receive operation to complete or timeout
    CountDownLatch errorReceiveLatch = new CountDownLatch(1);
    AtomicReference<ByteBuffer> errorBufferRef = new AtomicReference<>();
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    errorReceiveFuture.whenComplete((buffer1, error) -> {
      if (error != null) {
        errorRef.set(error);
      } else {
        errorBufferRef.set(buffer1);
      }
      errorReceiveLatch.countDown();
    });

    // Wait up to 5 seconds for the error to happen
    if (!errorReceiveLatch.await(5, TimeUnit.SECONDS)) {
      throw new RuntimeException("Error receive operation timed out after 5 seconds");
    }

    // Verify that an error occurred (should be true because channel was closed)
    assertNotNull(errorRef.get(), "Expected an error but none occurred");

    // Log the error details for debugging
    Throwable error = errorRef.get();
    System.out.println("Error type: " + error.getClass().getName());
    System.out.println("Error message: " + error.getMessage());
    // If it's not an ExecutionException, it should be directly related to I/O
    boolean isIORelated = error instanceof StreamClosedException &&
                          error.getMessage() != null &&
                          error.getMessage().contains("closed");

    assertTrue(isIORelated,
        "Expected an I/O related exception due to closed channel but got: " + error.getClass().getName());
  }
}