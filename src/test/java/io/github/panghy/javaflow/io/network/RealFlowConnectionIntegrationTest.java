package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;

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
        Assertions.fail("Accept failed: " + exc.getMessage());
      }
    });
    
    // Create client channel and connect
    clientChannel = AsynchronousSocketChannel.open();
    clientChannel.connect(new InetSocketAddress("localhost", port)).get(2, TimeUnit.SECONDS);
    
    // Wait for server to accept
    Assertions.assertTrue(acceptLatch.await(2, TimeUnit.SECONDS), "Accept timed out");
    
    // Get the accepted channel
    serverSideChannel = acceptedChannelRef.get();
    Assertions.assertNotNull(serverSideChannel, "Server side channel not created");
    
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
    pumpUntilDone(sendFuture);
    
    // Receive on server side
    FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    pumpUntilDone(receiveFuture);
    
    Assertions.assertFalse(receiveFuture.isCompletedExceptionally());
    ByteBuffer receiveBuffer = receiveFuture.getNow();
    
    // Verify received data
    byte[] receivedBytes = new byte[receiveBuffer.remaining()];
    receiveBuffer.get(receivedBytes);
    String receivedMessage = new String(receivedBytes);
    
    Assertions.assertEquals(testMessage, receivedMessage);
  }

  /**
   * Tests that trying to use a connection after closing it throws an appropriate exception.
   */
  @Test
  void testUseAfterClose() throws Exception {
    // Close the connection
    FlowFuture<Void> closeFuture = clientConnection.close();
    pumpUntilDone(closeFuture);
    
    // Verify it reports as closed
    Assertions.assertFalse(clientConnection.isOpen());
    
    // Try to send, should fail
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    FlowFuture<Void> sendFuture = clientConnection.send(data);
    pumpUntilDone(sendFuture);
    
    Assertions.assertTrue(sendFuture.isCompletedExceptionally());
    try {
      sendFuture.getNow();
      Assertions.fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      Assertions.assertTrue(e.getCause() instanceof IOException);
      Assertions.assertTrue(e.getCause().getMessage().contains("closed"));
    }
    
    // Try to receive, should fail
    FlowFuture<ByteBuffer> receiveFuture = clientConnection.receive(1024);
    pumpUntilDone(receiveFuture);
    
    Assertions.assertTrue(receiveFuture.isCompletedExceptionally());
    try {
      receiveFuture.getNow();
      Assertions.fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      Assertions.assertTrue(e.getCause() instanceof IOException);
      Assertions.assertTrue(e.getCause().getMessage().contains("closed"));
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
    
    // Wait for first message to be received
    pumpUntilDone(firstReceiveFuture);
    
    ByteBuffer received1 = firstReceiveFuture.getNow();
    byte[] bytes1 = new byte[received1.remaining()];
    received1.get(bytes1);
    Assertions.assertEquals(message1, new String(bytes1));
    
    // Get next future from stream
    FlowFuture<ByteBuffer> secondReceiveFuture = receiveStream.nextAsync();
    
    // Send second message
    String message2 = "Second message";
    ByteBuffer buffer2 = ByteBuffer.wrap(message2.getBytes());
    clientConnection.send(buffer2);
    
    // Wait for second message to be received
    pumpUntilDone(secondReceiveFuture);
    
    ByteBuffer received2 = secondReceiveFuture.getNow();
    byte[] bytes2 = new byte[received2.remaining()];
    received2.get(bytes2);
    Assertions.assertEquals(message2, new String(bytes2));
  }
  
  /**
   * Tests that closing the connection properly closes the stream.
   */
  @Test
  void testCloseConnectionClosesStream() throws Exception {
    // Get receive stream
    FlowStream<ByteBuffer> receiveStream = serverConnection.receiveStream();
    FlowFuture<ByteBuffer> receiveFuture = receiveStream.nextAsync();
    
    // Close the client side (should close the server side too)
    FlowFuture<Void> closeFuture = clientConnection.close();
    pumpUntilDone(closeFuture);
    
    // Wait for server to detect close
    Thread.sleep(100);
    pumpUntilDone();
    
    // Now the receive future should complete exceptionally
    Assertions.assertTrue(receiveFuture.isCompletedExceptionally());
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
    pumpUntilDone(sendFuture);
    
    // Receive the data in chunks
    ByteBuffer combinedBuffer = ByteBuffer.allocate(102400);
    
    // Keep receiving until we get all the data
    while (combinedBuffer.position() < largeData.length) {
      FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
      pumpUntilDone(receiveFuture);
      
      ByteBuffer chunk = receiveFuture.getNow();
      combinedBuffer.put(chunk);
    }
    
    // Verify the data
    combinedBuffer.flip();
    byte[] receivedData = new byte[combinedBuffer.remaining()];
    combinedBuffer.get(receivedData);
    
    Assertions.assertArrayEquals(largeData, receivedData);
  }
  
  /**
   * Tests multiple close calls (should be idempotent).
   */
  @Test
  void testMultipleClose() throws Exception {
    // Close the connection
    FlowFuture<Void> firstCloseFuture = clientConnection.close();
    pumpUntilDone(firstCloseFuture);
    
    // Close again
    FlowFuture<Void> secondCloseFuture = clientConnection.close();
    pumpUntilDone(secondCloseFuture);
    
    // Both should complete normally
    Assertions.assertFalse(firstCloseFuture.isCompletedExceptionally());
    Assertions.assertFalse(secondCloseFuture.isCompletedExceptionally());
    
    // Should be the same future instance
    Assertions.assertSame(firstCloseFuture, secondCloseFuture);
  }
  
  /**
   * Tests the closeFuture method.
   */
  @Test
  void testCloseFuture() throws Exception {
    // Get the close future
    FlowFuture<Void> closeFuture = clientConnection.closeFuture();
    
    // It should not be completed yet
    Assertions.assertFalse(closeFuture.isDone());
    
    // Close the connection
    clientConnection.close();
    pumpUntilDone();
    
    // Now the future should be completed
    Assertions.assertTrue(closeFuture.isDone());
    Assertions.assertFalse(closeFuture.isCompletedExceptionally());
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
    
    pumpUntilDone(sendFuture);
    
    // Verify the future completed exceptionally
    Assertions.assertTrue(sendFuture.isCompletedExceptionally());
    try {
      sendFuture.getNow();
      Assertions.fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      // Expected exception - will vary by platform
    }
    
    // Connection should be closed
    Assertions.assertFalse(clientConnection.isOpen());
  }
  
  /**
   * Tests partial writes for large buffers.
   */
  @Test
  void testPartialWrites() throws Exception {
    // Create a very large buffer to ensure multiple writes
    byte[] largeArray = new byte[1024 * 1024]; // 1MB
    for (int i = 0; i < largeArray.length; i++) {
      largeArray[i] = (byte) (i % 256);
    }
    
    ByteBuffer largeBuffer = ByteBuffer.wrap(largeArray);
    
    // Send the large buffer
    FlowFuture<Void> sendFuture = clientConnection.send(largeBuffer);
    
    // This will take multiple pump cycles
    for (int i = 0; i < 100 && !sendFuture.isDone(); i++) {
      pumpUntilDone();
      if (!sendFuture.isDone()) {
        Thread.sleep(10);
      }
    }
    
    // Verify the send completed successfully
    Assertions.assertTrue(sendFuture.isDone());
    Assertions.assertFalse(sendFuture.isCompletedExceptionally());
    
    // Receive and verify the first chunk (don't try to read it all)
    FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    pumpUntilDone(receiveFuture);
    
    ByteBuffer receivedBuffer = receiveFuture.getNow();
    byte[] receivedData = new byte[receivedBuffer.remaining()];
    receivedBuffer.get(receivedData);
    
    // Verify the first chunk matches
    for (int i = 0; i < receivedData.length; i++) {
      Assertions.assertEquals((byte) (i % 256), receivedData[i]);
    }
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
    
    // The server should eventually detect this and fail the read
    for (int i = 0; i < 100 && !receiveFuture.isDone(); i++) {
      pumpUntilDone();
      if (!receiveFuture.isDone()) {
        Thread.sleep(10);
      }
    }
    
    // Verify the receive completed exceptionally
    Assertions.assertTrue(receiveFuture.isCompletedExceptionally());
    try {
      receiveFuture.getNow();
      Assertions.fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      // Expected exception
    }
    
    // Server connection should be closed
    Assertions.assertFalse(serverConnection.isOpen());
  }
  
  /**
   * Tests the getLocalEndpoint and getRemoteEndpoint methods.
   */
  @Test
  void testEndpoints() throws Exception {
    // Get the endpoints
    Endpoint localEndpoint = clientConnection.getLocalEndpoint();
    Endpoint remoteEndpoint = clientConnection.getRemoteEndpoint();
    
    // Verify they're not null
    Assertions.assertNotNull(localEndpoint);
    Assertions.assertNotNull(remoteEndpoint);
    
    // Verify they have the correct port
    Assertions.assertEquals(port, remoteEndpoint.getPort());
  }
  
  /**
   * Helper method to create a client-server connection pair.
   *
   * @param port The port to connect to
   * @return Array containing [clientConnection, serverConnection]
   */
  private FlowConnection[] createClientServerConnectionPair(int port) throws Exception {
    // Create a transport
    RealFlowTransport transport = new RealFlowTransport();
    
    // Set up server
    LocalEndpoint localEndpoint = LocalEndpoint.localhost(port);
    FlowStream<FlowConnection> stream = transport.listen(localEndpoint);
    
    // Connect client
    FlowFuture<FlowConnection> connectFuture = transport.connect(new Endpoint("localhost", port));
    pumpUntilDone(connectFuture);
    FlowConnection clientConnection = connectFuture.getNow();
    
    // Accept server connection
    FlowFuture<FlowConnection> acceptFuture = stream.nextAsync();
    pumpUntilDone(acceptFuture);
    FlowConnection serverConnection = acceptFuture.getNow();
    
    return new FlowConnection[]{clientConnection, serverConnection};
  }
  
  /**
   * Creates a test specifically targeting the read completion handler error path.
   * This tests the inner class in RealFlowConnection that handles read operations.
   */
  @Test
  void testReadCompletionHandlerFailurePath() throws Exception {
    // Create a temporary server endpoint to use for this test
    AsynchronousServerSocketChannel tempChannel = AsynchronousServerSocketChannel.open();
    tempChannel.bind(new InetSocketAddress("localhost", 0));
    int tempPort = ((InetSocketAddress) tempChannel.getLocalAddress()).getPort();
    tempChannel.close();
    
    // Get a client connection 
    FlowConnection[] connections = createClientServerConnectionPair(tempPort);
    FlowConnection clientConnection = connections[0];
    FlowConnection serverConnection = connections[1];
    
    // First, send some data to establish that the connection works
    String testMessage = "Test message";
    ByteBuffer buffer = ByteBuffer.wrap(testMessage.getBytes());
    FlowFuture<Void> sendFuture = clientConnection.send(buffer);
    pumpUntilDone(sendFuture);
    
    // Read from the server side to verify connection
    FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    pumpUntilDone(receiveFuture);
    
    ByteBuffer receiveBuffer = receiveFuture.getNow();
    byte[] bytes = new byte[receiveBuffer.remaining()];
    receiveBuffer.get(bytes);
    String receivedMessage = new String(bytes);
    
    Assertions.assertEquals(testMessage, receivedMessage);
    
    // Create a special test that forces the completion handler's failed method to be called
    // Extract the RealFlowConnection channel (requires reflection)
    AsynchronousSocketChannel channel = null;
    try {
      java.lang.reflect.Field fieldChannel = RealFlowConnection.class.getDeclaredField("channel");
      fieldChannel.setAccessible(true);
      channel = (AsynchronousSocketChannel) fieldChannel.get(serverConnection);
    } catch (Exception e) {
      Assertions.fail("Could not access channel field: " + e.getMessage());
    }
    
    // Force close the underlying channel
    if (channel != null) {
      channel.close();
    }
    
    // Give a short delay for the channel close to take effect
    Thread.sleep(100);
    
    // Now try to read from the closed channel - this should trigger the failed path
    FlowFuture<ByteBuffer> errorReceiveFuture = serverConnection.receive(1024);
    pumpUntilDone(errorReceiveFuture);
    
    // Verify that the future failed with the expected exception
    boolean isCompleted = errorReceiveFuture.isDone();
    boolean isExceptional = errorReceiveFuture.isCompletedExceptionally();
    
    System.out.println("Future completed: " + isCompleted);
    System.out.println("Future exceptionally: " + isExceptional);
    
    if (!isExceptional && isCompleted) {
      try {
        ByteBuffer buf = errorReceiveFuture.getNow();
        System.out.println("Received buffer size: " + (buf != null ? buf.remaining() : "null"));
      } catch (Exception e) {
        System.out.println("Exception when getting future value: " + e);
      }
    }
    
    Assertions.assertTrue(errorReceiveFuture.isCompletedExceptionally(), 
        "Future should have completed exceptionally");
    
    try {
      ByteBuffer buf = errorReceiveFuture.getNow();
      Assertions.fail("Expected ExecutionException but got result: " + 
          (buf != null ? "Buffer with " + buf.remaining() + " bytes" : "null"));
    } catch (ExecutionException e) {
      System.out.println("Got expected exception: " + e);
      System.out.println("Cause: " + e.getCause());
      Assertions.assertTrue(e.getCause() instanceof IOException, 
          "Expected IOException but got: " + e.getCause().getClass().getName());
      
      String message = e.getCause().getMessage();
      System.out.println("Message: " + message);
      Assertions.assertTrue(message != null && message.contains("closed"), 
          "Expected message to contain 'closed' but was: " + message);
    }
  }
}