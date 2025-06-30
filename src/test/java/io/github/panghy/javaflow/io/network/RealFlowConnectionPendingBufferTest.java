package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for RealFlowConnection's pending buffer functionality.
 * Uses real socket connections to test buffer management scenarios.
 */
public class RealFlowConnectionPendingBufferTest extends AbstractFlowTest {

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

    // Get the actual port
    port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

    // Set up the server to accept one connection
    CountDownLatch serverReady = new CountDownLatch(1);
    serverChannel.accept(null, new java.nio.channels.CompletionHandler<AsynchronousSocketChannel, Void>() {
      @Override
      public void completed(AsynchronousSocketChannel result, Void attachment) {
        serverSideChannel = result;
        serverReady.countDown();
      }

      @Override
      public void failed(Throwable exc, Void attachment) {
        serverReady.countDown();
      }
    });

    // Connect the client
    clientChannel = AsynchronousSocketChannel.open();
    clientChannel.connect(new InetSocketAddress("localhost", port)).get(5, TimeUnit.SECONDS);

    // Wait for server to accept
    assertTrue(serverReady.await(5, TimeUnit.SECONDS));

    // Create endpoints
    Endpoint clientEndpoint = new Endpoint("localhost", clientChannel.getLocalAddress() != null ?
        ((InetSocketAddress) clientChannel.getLocalAddress()).getPort() : 0);
    Endpoint serverEndpoint = new Endpoint("localhost", port);

    // Create connections
    clientConnection = new RealFlowConnection(clientChannel, clientEndpoint, serverEndpoint);
    serverConnection = new RealFlowConnection(serverSideChannel, serverEndpoint, clientEndpoint);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (clientConnection != null) {
      clientConnection.close();
    }
    if (serverConnection != null) {
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
   * Tests that pending buffer correctly handles data when receive requests less than available.
   */
  @Test
  void testPendingBufferPartialRead() throws Exception {
    // Send 100 bytes from server
    byte[] data = new byte[100];
    for (int i = 0; i < 100; i++) {
      data[i] = (byte) i;
    }
    ByteBuffer sendBuffer = ByteBuffer.wrap(data);
    CompletableFuture<Void> sendFuture = serverConnection.send(sendBuffer);

    // Give some time for data to be sent
    Thread.sleep(100);

    // Client requests only 30 bytes
    CompletableFuture<ByteBuffer> receive1 = clientConnection.receive(30);
    ByteBuffer result1 = receive1.get(5, TimeUnit.SECONDS);
    assertEquals(30, result1.remaining());

    // Verify the first 30 bytes
    byte[] received1 = new byte[30];
    result1.get(received1);
    for (int i = 0; i < 30; i++) {
      assertEquals((byte) i, received1[i]);
    }

    // Client requests another 40 bytes - should come from pending buffer
    CompletableFuture<ByteBuffer> receive2 = clientConnection.receive(40);
    ByteBuffer result2 = receive2.get(5, TimeUnit.SECONDS);
    assertEquals(40, result2.remaining());

    // Verify the next 40 bytes
    byte[] received2 = new byte[40];
    result2.get(received2);
    for (int i = 0; i < 40; i++) {
      assertEquals((byte) (i + 30), received2[i]);
    }

    // Client requests the remaining 30 bytes
    CompletableFuture<ByteBuffer> receive3 = clientConnection.receive(30);
    ByteBuffer result3 = receive3.get(5, TimeUnit.SECONDS);
    assertEquals(30, result3.remaining());

    // Verify the last 30 bytes
    byte[] received3 = new byte[30];
    result3.get(received3);
    for (int i = 0; i < 30; i++) {
      assertEquals((byte) (i + 70), received3[i]);
    }
  }

  /**
   * Tests receive with exact buffer size match.
   */
  @Test
  void testReceiveExactMatch() throws Exception {
    // Send exactly 50 bytes
    byte[] data = new byte[50];
    for (int i = 0; i < 50; i++) {
      data[i] = (byte) (i + 100); // Different pattern
    }
    serverConnection.send(ByteBuffer.wrap(data)).get(5, TimeUnit.SECONDS);

    // Give time for data to arrive
    Thread.sleep(100);

    // Request exactly 50 bytes
    CompletableFuture<ByteBuffer> receiveFuture = clientConnection.receive(50);
    ByteBuffer result = receiveFuture.get(5, TimeUnit.SECONDS);

    assertEquals(50, result.remaining());
    byte[] received = new byte[50];
    result.get(received);
    assertArrayEquals(data, received);
  }

  /**
   * Tests multiple small receives that consume pending buffer completely.
   */
  @Test
  void testMultipleSmallReceives() throws Exception {
    // Send 20 bytes
    byte[] data = new byte[20];
    for (int i = 0; i < 20; i++) {
      data[i] = (byte) (i * 2);
    }
    serverConnection.send(ByteBuffer.wrap(data)).get(5, TimeUnit.SECONDS);

    Thread.sleep(100);

    // Receive in 5-byte chunks
    for (int chunk = 0; chunk < 4; chunk++) {
      CompletableFuture<ByteBuffer> future = clientConnection.receive(5);
      ByteBuffer result = future.get(5, TimeUnit.SECONDS);
      assertEquals(5, result.remaining());

      // Verify chunk data
      for (int i = 0; i < 5; i++) {
        assertEquals((byte) ((chunk * 5 + i) * 2), result.get());
      }
    }
  }

  /**
   * Tests custom read buffer size setting.
   */
  @Test
  void testCustomReadBufferSize() throws Exception {
    // Set a larger read buffer size
    clientConnection.setReadBufferSize(8192);

    // Send some data
    byte[] data = new byte[1000];
    for (int i = 0; i < 1000; i++) {
      data[i] = (byte) (i % 256);
    }
    serverConnection.send(ByteBuffer.wrap(data)).get(5, TimeUnit.SECONDS);

    Thread.sleep(100);

    // Receive the data
    CompletableFuture<ByteBuffer> future = clientConnection.receive(1000);
    ByteBuffer result = future.get(5, TimeUnit.SECONDS);

    assertNotNull(result);
    assertEquals(1000, result.remaining());
  }

  /**
   * Tests exception handling in pending buffer slicing.
   */
  @Test
  void testPendingBufferSliceException() throws Exception {
    // Send data that will be partially consumed
    byte[] data = new byte[100];
    serverConnection.send(ByteBuffer.wrap(data)).get(5, TimeUnit.SECONDS);

    Thread.sleep(100);

    // First receive to establish pending buffer
    clientConnection.receive(40).get(5, TimeUnit.SECONDS);

    // Try to receive with a very small buffer that tests edge cases
    CompletableFuture<ByteBuffer> future = clientConnection.receive(1);
    ByteBuffer result = future.get(5, TimeUnit.SECONDS);
    assertEquals(1, result.remaining());
  }
}