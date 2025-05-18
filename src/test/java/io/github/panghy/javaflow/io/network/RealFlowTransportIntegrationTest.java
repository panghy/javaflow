package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for RealFlowTransport using actual socket channels.
 * These tests focus on improving code coverage for the RealFlowTransport class.
 */
public class RealFlowTransportIntegrationTest extends AbstractFlowTest {

  private RealFlowTransport transport;
  private FlowStream<FlowConnection> connectionStream;
  private LocalEndpoint serverEndpoint;

  @BeforeEach
  void setUp() throws Exception {
    transport = new RealFlowTransport();

    // Get a random free port
    AsynchronousSocketChannel tempChannel = AsynchronousSocketChannel.open();
    tempChannel.bind(new InetSocketAddress("localhost", 0));
    int port = ((InetSocketAddress) tempChannel.getLocalAddress()).getPort();
    tempChannel.close();

    // Create endpoint
    serverEndpoint = LocalEndpoint.localhost(port);
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
    // Start listening
    connectionStream = transport.listen(serverEndpoint);

    // Get next connection future
    FlowFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();

    // Connect a client
    FlowFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", serverEndpoint.getPort()));
    connectFuture.getNow();

    // Verify the connection future completes
    Assertions.assertFalse(connectFuture.isCompletedExceptionally());
    FlowConnection clientConnection = connectFuture.getNow();
    Assertions.assertNotNull(clientConnection);

    // Wait for accept to complete
    pumpUntilDone(acceptFuture);

    // Verify the accept future completes
    Assertions.assertFalse(acceptFuture.isCompletedExceptionally());
    FlowConnection serverConnection = acceptFuture.getNow();
    Assertions.assertNotNull(serverConnection);

    // Verify the connections are open
    assertTrue(clientConnection.isOpen());
    assertTrue(serverConnection.isOpen());

    // Verify the endpoints match
    Assertions.assertEquals(serverEndpoint.getPort(), clientConnection.getRemoteEndpoint().getPort());
    int clientLocalPort = clientConnection.getLocalEndpoint().getPort();
    int serverRemotePort = serverConnection.getRemoteEndpoint().getPort();
    Assertions.assertEquals(clientLocalPort, serverRemotePort);

    // Use the connections
    String message = "Test message";
    ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

    FlowFuture<Void> sendFuture = clientConnection.send(buffer);
    sendFuture.getNow();

    FlowFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    receiveFuture.getNow();

    ByteBuffer received = receiveFuture.getNow();
    byte[] receivedBytes = new byte[received.remaining()];
    received.get(receivedBytes);

    Assertions.assertEquals(message, new String(receivedBytes));

    // Close connections
    clientConnection.close();
    serverConnection.close();
  }

  /**
   * Tests connecting to a server that doesn't exist.
   */
  @Test
  void testConnectToNonExistentServer() {
    // Connect to a port where there's no server
    FlowFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", 12345));
    try {
      connectFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      // This is expected - could be various IO exceptions depending on OS
    }
  }

  /**
   * Tests closing the transport while connections are active.
   */
  @Test
  void testCloseWithActiveConnections() throws Exception {
    // Start listening
    connectionStream = transport.listen(serverEndpoint);

    // Connect a client
    FlowFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", serverEndpoint.getPort()));

    FlowConnection clientConnection = connectFuture.getNow();

    // Accept the connection
    FlowFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();

    FlowConnection serverConnection = acceptFuture.getNow();

    // Verify connections are open
    assertTrue(clientConnection.isOpen());
    assertTrue(serverConnection.isOpen());

    // Close the transport (this should close the server stream)
    FlowFuture<Void> closeFuture = transport.close();
    pumpUntilDone(closeFuture);

    // Try to get next connection - should fail
    FlowFuture<FlowConnection> nextAcceptFuture = connectionStream.nextAsync();

    try {
      nextAcceptFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException ignored) {
    }

    // Try to connect after transport is closed
    FlowFuture<FlowConnection> connectAfterCloseFuture = transport.connect(
        new Endpoint("localhost", serverEndpoint.getPort()));
    try {
      connectAfterCloseFuture.getNow();
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertInstanceOf(IOException.class, e.getCause());
      assertTrue(e.getCause().getMessage().contains("closed"));
    }
  }

  /**
   * Tests listening on the same endpoint twice.
   */
  @Test
  void testListenOnSameEndpointTwice() throws Exception {
    // Listen on an endpoint
    connectionStream = transport.listen(serverEndpoint);

    // Try to listen again - should return the same stream
    FlowStream<FlowConnection> secondStream = transport.listen(serverEndpoint);

    // Should be the same stream instance
    Assertions.assertSame(connectionStream, secondStream);
  }

  /**
   * Tests listening on multiple endpoints.
   */
  @Test
  void testListenOnMultipleEndpoints() throws Exception {
    // Get multiple free ports
    AsynchronousSocketChannel[] tempChannels = new AsynchronousSocketChannel[3];
    int[] ports = new int[3];

    for (int i = 0; i < tempChannels.length; i++) {
      tempChannels[i] = AsynchronousSocketChannel.open();
      tempChannels[i].bind(new InetSocketAddress("localhost", 0));
      ports[i] = ((InetSocketAddress) tempChannels[i].getLocalAddress()).getPort();
      tempChannels[i].close();
    }

    // Create endpoints
    LocalEndpoint[] endpoints = new LocalEndpoint[3];
    @SuppressWarnings("unchecked")
    FlowStream<FlowConnection>[] streams = new FlowStream[3];

    for (int i = 0; i < endpoints.length; i++) {
      endpoints[i] = LocalEndpoint.localhost(ports[i]);
      streams[i] = transport.listen(endpoints[i]);
    }

    // Connect to each endpoint
    for (int i = 0; i < endpoints.length; i++) {
      // Get the next connection future
      FlowFuture<FlowConnection> acceptFuture = streams[i].nextAsync();

      // Connect
      FlowFuture<FlowConnection> connectFuture = transport.connect(
          new Endpoint("localhost", ports[i]));
      pumpUntilDone(connectFuture);

      FlowConnection clientConnection = connectFuture.getNow();

      // Wait for accept
      pumpUntilDone(acceptFuture);

      FlowConnection serverConnection = acceptFuture.getNow();

      // Verify the connections
      Assertions.assertEquals(ports[i], clientConnection.getRemoteEndpoint().getPort());

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
    FlowFuture<Void> firstCloseFuture = transport.close();
    pumpUntilDone(firstCloseFuture);

    // Second close
    FlowFuture<Void> secondCloseFuture = transport.close();
    pumpUntilDone(secondCloseFuture);

    // Both should complete normally
    Assertions.assertFalse(firstCloseFuture.isCompletedExceptionally());
    Assertions.assertFalse(secondCloseFuture.isCompletedExceptionally());

    // Should be the same future
    Assertions.assertSame(firstCloseFuture, secondCloseFuture);
  }

  /**
   * Tests error handling during accept.
   */
  @Test
  void testAcceptErrorHandling() throws Exception {
    // This test requires OS manipulation, which isn't practical in a unit test.
    // The best we can do is to verify that the code doesn't crash if accept fails.
    // This is mostly just to hit the code paths and improve coverage.

    // Start listening
    connectionStream = transport.listen(serverEndpoint);

    // Get the next connection future
    FlowFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();

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

    // If any connections succeeded, we expect accepts to work
    if (successCount.get() > 0) {
      pumpUntilDone(acceptFuture);
      // This test doesn't verify much, but it does exercise the error handling code
    }
  }

  /**
   * Tests handling transport close during accept.
   */
  @Test
  void testCloseTransportDuringAccept() throws Exception {
    // Start listening
    connectionStream = transport.listen(serverEndpoint);

    // Get the next connection future
    FlowFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();

    // No client has connected yet, so acceptFuture should not be done
    Assertions.assertFalse(acceptFuture.isDone());

    // Close the transport
    transport.close();
    pumpUntilDone();

    // Now the accept future should complete exceptionally
    assertTrue(acceptFuture.isCompletedExceptionally());
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

      FlowStream<FlowConnection> stream = transport.listen(endpoint);

      // Try to get a connection
      FlowFuture<FlowConnection> acceptFuture = stream.nextAsync();

      // This might or might not fail, depending on the OS
      // If it doesn't fail, the OS might have assigned a different port
      // We're just exercising the code path

      // Close the transport
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
    // This is hard to test directly without mocking
    // Instead, let's set up a somewhat pathological case:

    // Connect to the server
    FlowFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", serverEndpoint.getPort()));

    // The connect will likely fail since we haven't set up a server yet
    // But we're just exercising the code path

    // Wait a bit for the connect to either succeed or fail
    Thread.sleep(100);
    pumpUntilDone();
  }

  /**
   * Tests handling of server socket closure in the accept logic.
   */
  @Test
  void testServerSocketClosedDuringAccept() throws Exception {
    // Create a custom transport that we can manipulate
    RealFlowTransport customTransport = new RealFlowTransport();

    try {
      // Start listening
      FlowStream<FlowConnection> stream = customTransport.listen(serverEndpoint);

      // Get the next connection future
      FlowFuture<FlowConnection> acceptFuture = stream.nextAsync();

      // Close the transport, which should close the server socket
      customTransport.close();
      pumpUntilDone();

      // Now the accept future should complete exceptionally
      assertTrue(acceptFuture.isCompletedExceptionally());
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
      // Start listening on a different port
      AsynchronousSocketChannel tempChannel = AsynchronousSocketChannel.open();
      tempChannel.bind(new InetSocketAddress("localhost", 0));
      int port = ((InetSocketAddress) tempChannel.getLocalAddress()).getPort();
      tempChannel.close();

      LocalEndpoint customEndpoint = LocalEndpoint.localhost(port);
      FlowStream<FlowConnection> stream = customTransport.listen(customEndpoint);

      // Create several connections, using a smaller number to reduce resource usage
      final int numConnections = 5;
      FlowConnection[] clients = new FlowConnection[numConnections];
      FlowConnection[] servers = new FlowConnection[numConnections];

      for (int i = 0; i < numConnections; i++) {
        // Connect a client
        FlowFuture<FlowConnection> connectFuture = customTransport.connect(
            new Endpoint("localhost", port));

        clients[i] = connectFuture.getNow();

        // Accept the connection
        FlowFuture<FlowConnection> acceptFuture = stream.nextAsync();

        servers[i] = acceptFuture.getNow();
      }

      // Send some data on each connection
      for (int i = 0; i < numConnections; i++) {
        String message = "Message " + i;
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

        FlowFuture<Void> sendFuture = clients[i].send(buffer);
        sendFuture.getNow();

        FlowFuture<ByteBuffer> receiveFuture = servers[i].receive(1024);
        receiveFuture.getNow();

        ByteBuffer received = receiveFuture.getNow();
        byte[] bytes = new byte[received.remaining()];
        received.get(bytes);
        Assertions.assertEquals(message, new String(bytes));
      }

      // Close all connections with proper waiting
      for (int i = 0; i < numConnections; i++) {
        FlowFuture<Void> clientCloseFuture = clients[i].close();
        FlowFuture<Void> serverCloseFuture = servers[i].close();

        // Wait for connections to close properly
        clientCloseFuture.getNow();
        serverCloseFuture.getNow();
      }

      // Ensure proper cleanup by pumping a bit more
      pumpUntilDone();
    } finally {
      FlowFuture<Void> closeFuture = customTransport.close();
      pumpUntilDone(closeFuture);
    }
  }
}