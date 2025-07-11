package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for RealFlowTransport using actual socket channels.
 * These tests focus on improving code coverage for the RealFlowTransport class.
 */
public class RealFlowTransportIntegrationTest extends AbstractFlowTest {

  private RealFlowTransport transport;
  private ConnectionListener connectionListener;

  @BeforeEach
  void setUp() throws Exception {
    transport = new RealFlowTransport();
  }

  @AfterEach
  void tearDown() {
    if (transport != null) {
      transport.close();
    }
  }

  /**
   * Tests the basic listen and connect functionality.
   */
  @Test
  void testListenAndConnect() throws Exception {
    // Start listening on an available port
    connectionListener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> connectionStream = connectionListener.getStream();
    LocalEndpoint serverEndpoint = connectionListener.getBoundEndpoint();

    // Get next connection future
    CompletableFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();

    // Connect a client
    CompletableFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", serverEndpoint.getPort()));
    connectFuture.get(5, TimeUnit.SECONDS);

    // Verify the connection future completes
    assertFalse(connectFuture.isCompletedExceptionally());
    FlowConnection clientConnection = connectFuture.get(5, TimeUnit.SECONDS);
    assertNotNull(clientConnection);

    FlowConnection serverConnection = acceptFuture.get(5, TimeUnit.SECONDS);
    assertNotNull(serverConnection);

    // Verify the connections are open
    assertTrue(clientConnection.isOpen());
    assertTrue(serverConnection.isOpen());

    // Verify the endpoints match
    assertEquals(serverEndpoint.getPort(), clientConnection.getRemoteEndpoint().getPort());
    int clientLocalPort = clientConnection.getLocalEndpoint().getPort();
    int serverRemotePort = serverConnection.getRemoteEndpoint().getPort();
    assertEquals(clientLocalPort, serverRemotePort);

    // Use the connections
    String message = "Test message";
    ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

    CompletableFuture<Void> sendFuture = clientConnection.send(buffer);
    sendFuture.get(5, TimeUnit.SECONDS);

    CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    receiveFuture.get(5, TimeUnit.SECONDS);

    ByteBuffer received = receiveFuture.get(5, TimeUnit.SECONDS);
    byte[] receivedBytes = new byte[received.remaining()];
    received.get(receivedBytes);

    assertEquals(message, new String(receivedBytes));

    // Close connections
    clientConnection.close();
    serverConnection.close();
  }

  /**
   * Tests connecting to a server that doesn't exist.
   */
  @Test
  void testConnectToNonExistentServer() throws Exception {
    // Connect to a port where there's no server
    CompletableFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", 12345));
    try {
      connectFuture.get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (Exception e) {
      // This is expected - could be various IO exceptions depending on OS
    }
  }

  /**
   * Tests closing the transport while connections are active.
   */
  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  void testCloseWithActiveConnections() throws Exception {
    // Start listening on an available port
    connectionListener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> connectionStream = connectionListener.getStream();
    LocalEndpoint serverEndpoint = connectionListener.getBoundEndpoint();

    // Connect a client
    CompletableFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", serverEndpoint.getPort()));

    FlowConnection clientConnection = connectFuture.get(5, TimeUnit.SECONDS);

    // Accept the connection
    CompletableFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();

    FlowConnection serverConnection = acceptFuture.get(5, TimeUnit.SECONDS);

    // Verify connections are open
    assertTrue(clientConnection.isOpen());
    assertTrue(serverConnection.isOpen());

    // Close the transport (this should close the server stream)
    CompletableFuture<Void> closeFuture = transport.close();
    closeFuture.get(5, TimeUnit.SECONDS);

    // Try to get next connection - should fail
    CompletableFuture<FlowConnection> nextAcceptFuture = connectionStream.nextAsync();

    try {
      nextAcceptFuture.get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (Exception ignored) {
    }

    // Try to connect after transport is closed
    CompletableFuture<FlowConnection> connectAfterCloseFuture = transport.connect(
        new Endpoint("localhost", serverEndpoint.getPort()));
    try {
      connectAfterCloseFuture.get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (Exception e) {
      assertInstanceOf(IOException.class, e.getCause());
      assertTrue(e.getCause().getMessage().contains("closed"));
    }
  }

  /**
   * Tests listening on the same endpoint twice.
   */
  @Test
  void testListenOnSameEndpointTwice() throws Exception {
    // Listen on an available port
    connectionListener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> connectionStream = connectionListener.getStream();
    LocalEndpoint serverEndpoint = connectionListener.getBoundEndpoint();

    // Try to listen again - should return the same stream
    FlowStream<FlowConnection> secondStream = transport.listen(serverEndpoint);

    // Should be the same stream instance
    assertSame(connectionStream, secondStream);
  }

  /**
   * Tests listening on multiple endpoints.
   */
  @Test
  @SuppressWarnings("unchecked")
  void testListenOnMultipleEndpoints() throws Exception {
    // Create multiple listeners on available ports
    int numEndpoints = 3;
    ConnectionListener[] listeners = new ConnectionListener[numEndpoints];
    FlowStream<FlowConnection>[] streams = new FlowStream[numEndpoints];
    LocalEndpoint[] endpoints = new LocalEndpoint[numEndpoints];

    for (int i = 0; i < numEndpoints; i++) {
      listeners[i] = transport.listenOnAvailablePort();
      streams[i] = listeners[i].getStream();
      endpoints[i] = listeners[i].getBoundEndpoint();
    }

    // Connect to each endpoint
    for (int i = 0; i < endpoints.length; i++) {
      // Get the next connection future
      CompletableFuture<FlowConnection> acceptFuture = streams[i].nextAsync();

      // Connect
      CompletableFuture<FlowConnection> connectFuture = transport.connect(
          new Endpoint("localhost", endpoints[i].getPort()));

      FlowConnection clientConnection = connectFuture.get(5, TimeUnit.SECONDS);

      FlowConnection serverConnection = acceptFuture.get(5, TimeUnit.SECONDS);

      // Verify the connections
      assertEquals(endpoints[i].getPort(), clientConnection.getRemoteEndpoint().getPort());

      // Close the connections
      clientConnection.close();
      serverConnection.close();
    }
  }

  /**
   * Tests closing the transport multiple times (should be idempotent).
   */
  @Test
  void testMultipleClose() throws Exception {
    // First close
    CompletableFuture<Void> firstCloseFuture = transport.close();
    firstCloseFuture.get(5, TimeUnit.SECONDS);

    // Second close
    CompletableFuture<Void> secondCloseFuture = transport.close();
    secondCloseFuture.get(5, TimeUnit.SECONDS);

    // Should be the same future
    assertSame(firstCloseFuture, secondCloseFuture);
  }

  /**
   * Tests error handling during accept.
   */
  @Test
  void testAcceptErrorHandling() throws Exception {
    // This test requires OS manipulation, which isn't practical in a unit test.
    // The best we can do is to verify that the code doesn't crash if accept fails.
    // This is mostly just to hit the code paths and improve coverage.

    // Start listening on an available port
    connectionListener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> connectionStream = connectionListener.getStream();
    LocalEndpoint serverEndpoint = connectionListener.getBoundEndpoint();

    // Get the next connection future
    CompletableFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();

    // Connect and immediately close many clients rapidly to try to trigger an accept error
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < 10; i++) {
      try {
        AsynchronousSocketChannel clientChannel = AsynchronousSocketChannel.open();
        clientChannel.connect(new InetSocketAddress("localhost", serverEndpoint.getPort()))
            .get(100, TimeUnit.MILLISECONDS);
        clientChannel.close();
        successCount.incrementAndGet();
      } catch (Exception e) {
        // Ignore connect errors
      }
    }
  }

  /**
   * Tests handling transport close during accept.
   */
  @Test
  void testCloseTransportDuringAccept() throws Exception {
    // Start listening on an available port
    connectionListener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> localStream = connectionListener.getStream();

    // Get the next connection future
    CompletableFuture<FlowConnection> acceptFuture = localStream.nextAsync();

    // No client has connected yet, so acceptFuture should not be done
    assertFalse(acceptFuture.isDone());

    // Close the transport
    transport.close();

    try {
      acceptFuture.get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (Exception expected) {
    }
  }

  /**
   * Tests the behavior when a server socket is already bound to the port.
   */
  @Test
  void testPortAlreadyBound() throws Exception {
    // Create a server socket on a specific port
    AsynchronousServerSocketChannel existingServer = AsynchronousServerSocketChannel.open()
        .bind(new InetSocketAddress("localhost", 0));

    int boundPort = ((InetSocketAddress) existingServer.getLocalAddress()).getPort();

    try {
      // Try to listen on the same port
      LocalEndpoint endpoint = LocalEndpoint.localhost(boundPort);

      FlowStream<FlowConnection> localStream = transport.listen(endpoint);

      // Try to get a connection
      CompletableFuture<FlowConnection> acceptFuture = localStream.nextAsync();
      try {
        acceptFuture.get(5, TimeUnit.SECONDS);
        fail("Expected ExecutionException");
      } catch (Exception expected) {
      }
      transport.close();
    } finally {
      existingServer.close();
    }
  }

  /**
   * Tests the failure path of connect when the local address can't be determined.
   */
  @Test
  void testConnectLocalAddressError() throws Exception {
    // Open a random port first.
    // Grab that port and then close it.
    AtomicInteger port = new AtomicInteger(0);
    try (AsynchronousServerSocketChannel server =
             AsynchronousServerSocketChannel.open().bind(new InetSocketAddress("localhost", 0))) {
      port.set(((InetSocketAddress) server.getLocalAddress()).getPort());
      server.close();
    } catch (IOException e) {
      fail("Failed to open server socket", e);
    }

    // Connect to a non-existent server
    CompletableFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", port.get()));

    try {
      connectFuture.get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (Exception expected) {
    }
  }

  /**
   * Tests handling of server socket closure in the accept logic.
   */
  @Test
  void testServerSocketClosedDuringAccept() throws Exception {
    // Create a custom transport that we can manipulate
    RealFlowTransport customTransport = new RealFlowTransport();

    try {
      // Start listening on an available port
      ConnectionListener listener = customTransport.listenOnAvailablePort();
      FlowStream<FlowConnection> stream = listener.getStream();

      // Get the next connection future
      CompletableFuture<FlowConnection> acceptFuture = stream.nextAsync();

      // Close the transport, which should close the server socket
      customTransport.close();

      try {
        acceptFuture.get(5, TimeUnit.SECONDS);
        fail("Expected ExecutionException");
      } catch (Exception expected) {
      }
    } finally {
      customTransport.close();
    }
  }

  /**
   * Tests the transport with a large number of connections to improve code coverage.
   */
  @Test
  void testManyConnections() throws Exception {
    // Create a new transport for this test
    RealFlowTransport customTransport = new RealFlowTransport();

    try {
      // Start listening on an available port
      ConnectionListener listener = customTransport.listenOnAvailablePort();
      FlowStream<FlowConnection> stream = listener.getStream();
      LocalEndpoint customEndpoint = listener.getBoundEndpoint();
      int port = customEndpoint.getPort();

      // Create several connections, using a smaller number to reduce resource usage
      final int numConnections = 5;
      FlowConnection[] clients = new FlowConnection[numConnections];
      FlowConnection[] servers = new FlowConnection[numConnections];

      for (int i = 0; i < numConnections; i++) {
        // Connect a client
        CompletableFuture<FlowConnection> connectFuture = customTransport.connect(
            new Endpoint("localhost", port));

        clients[i] = connectFuture.get(5, TimeUnit.SECONDS);

        // Accept the connection
        CompletableFuture<FlowConnection> acceptFuture = stream.nextAsync();

        servers[i] = acceptFuture.get(5, TimeUnit.SECONDS);
      }

      // Send some data on each connection
      for (int i = 0; i < numConnections; i++) {
        String message = "Message " + i;
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

        CompletableFuture<Void> sendFuture = clients[i].send(buffer);
        sendFuture.get(5, TimeUnit.SECONDS);

        CompletableFuture<ByteBuffer> receiveFuture = servers[i].receive(1024);
        receiveFuture.get(5, TimeUnit.SECONDS);

        ByteBuffer received = receiveFuture.get(5, TimeUnit.SECONDS);
        byte[] bytes = new byte[received.remaining()];
        received.get(bytes);
        assertEquals(message, new String(bytes));
      }

      // Close all connections with proper waiting
      for (int i = 0; i < numConnections; i++) {
        CompletableFuture<Void> clientCloseFuture = clients[i].close();
        CompletableFuture<Void> serverCloseFuture = servers[i].close();

        // Wait for connections to close properly
        clientCloseFuture.get(5, TimeUnit.SECONDS);
        serverCloseFuture.get(5, TimeUnit.SECONDS);
      }
    } finally {
      CompletableFuture<Void> closeFuture = customTransport.close();
      closeFuture.get(5, TimeUnit.SECONDS);
    }
  }
}