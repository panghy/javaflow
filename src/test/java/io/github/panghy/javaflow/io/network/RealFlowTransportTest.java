package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for the RealFlowTransport class.
 * These tests use real network sockets, so they may fail if the ports are in use.
 * <p>
 * Tagged as "integration" to allow selectively running or skipping these tests.
 */
@Tag("integration")
public class RealFlowTransportTest {

  /**
   * Creates a ByteBuffer from a UTF-8 encoded string.
   *
   * @param message The message to convert to a ByteBuffer
   * @return A ByteBuffer containing the UTF-8 encoded message
   */
  protected ByteBuffer createBuffer(String message) {
    return ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Converts a ByteBuffer to a UTF-8 encoded string.
   *
   * @param buffer The ByteBuffer to convert
   * @return A string decoded from the ByteBuffer using UTF-8 encoding
   */
  protected String bufferToString(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private RealFlowTransport transport;

  @BeforeEach
  public void setUp() {
    try {
      // Create a real transport
      transport = new RealFlowTransport();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize RealFlowTransportTest", e);
    }
  }

  @AfterEach
  public void tearDown() {
    // Close the transport
    try {
      if (transport != null) {
        transport.close().get(1, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      // Ignore exceptions during cleanup
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testBasicConnectionAndCommunication() throws Exception {
    // Start the server listening on an available port
    ConnectionListener listener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> connectionStream = listener.getStream();
    LocalEndpoint serverEndpoint = listener.getBoundEndpoint();

    // Create latches to synchronize operations
    CountDownLatch serverAcceptLatch = new CountDownLatch(1);
    CountDownLatch messageSentLatch = new CountDownLatch(1);
    CountDownLatch messageReceivedLatch = new CountDownLatch(1);

    // Storage for connections and messages
    AtomicReference<FlowConnection> serverConnection = new AtomicReference<>();
    AtomicReference<String> receivedMessage = new AtomicReference<>();

    // Start an actor to accept connections
    startActor(() -> {
      try {
        // Wait for a connection
        FlowConnection connection = await(connectionStream.nextAsync());
        serverConnection.set(connection);
        serverAcceptLatch.countDown();

        // Wait for a message
        ByteBuffer buffer = await(connection.receive(1024));
        receivedMessage.set(bufferToString(buffer));
        messageReceivedLatch.countDown();
      } catch (Exception e) {
        fail("Server actor failed: " + e.getMessage());
      }
      return null;
    });

    // Connect to the server
    FlowConnection clientConnection = startActor(() ->
        await(transport.connect(serverEndpoint))).get(5, TimeUnit.SECONDS);

    // Wait for the server to accept the connection
    assertTrue(serverAcceptLatch.await(30, TimeUnit.SECONDS));
    assertNotNull(serverConnection.get());

    // Send a message from client to server
    String testMessage = "Hello, server!";
    startActor(() -> {
      await(clientConnection.send(createBuffer(testMessage)));
      messageSentLatch.countDown();
      return null;
    });

    // Wait for the message to be sent and received
    assertTrue(messageSentLatch.await(5, TimeUnit.SECONDS));
    assertTrue(messageReceivedLatch.await(5, TimeUnit.SECONDS));

    // Verify the message was received correctly
    assertEquals(testMessage, receivedMessage.get());

    // Close the connections
    if (serverConnection.get() != null) {
      serverConnection.get().close().get(1, TimeUnit.SECONDS);
    }
    if (clientConnection != null) {
      clientConnection.close().get(1, TimeUnit.SECONDS);
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testReceiveStream() throws Exception {
    // Start the server listening on an available port
    ConnectionListener listener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> connectionStream = listener.getStream();
    LocalEndpoint serverEndpoint = listener.getBoundEndpoint();

    // Create latches to synchronize operations
    CountDownLatch serverAcceptLatch = new CountDownLatch(1);
    CountDownLatch allMessagesReceivedLatch = new CountDownLatch(1);

    // Storage for connections and messages
    AtomicReference<FlowConnection> serverConnection = new AtomicReference<>();
    List<String> receivedMessages = new ArrayList<>();

    // Define test messages - we'll only send one to simplify the test
    String testMessage = "Message 1";

    // Start an actor to accept connections and receive messages via stream
    startActor(() -> {
      try {
        // Wait for a connection
        FlowConnection connection = await(connectionStream.nextAsync());
        serverConnection.set(connection);
        serverAcceptLatch.countDown();

        // Get the receive stream
        FlowStream<ByteBuffer> receiveStream = connection.receiveStream();

        // Receive one message
        ByteBuffer buffer = await(receiveStream.nextAsync());
        receivedMessages.add(bufferToString(buffer));

        allMessagesReceivedLatch.countDown();
      } catch (Exception e) {
        fail("Server actor failed: " + e.getMessage());
      }
      return null;
    });

    // Connect to the server
    FlowConnection clientConnection = startActor(() ->
        await(transport.connect(serverEndpoint))).get(5, TimeUnit.SECONDS);

    // Wait for the server to accept the connection
    assertTrue(serverAcceptLatch.await(30, TimeUnit.SECONDS));

    // Send a message
    startActor(() -> {
      await(clientConnection.send(createBuffer(testMessage)));
      return null;
    }).get(5, TimeUnit.SECONDS);

    // Wait for all messages to be received
    assertTrue(allMessagesReceivedLatch.await(30, TimeUnit.SECONDS));

    // Verify message
    assertEquals(1, receivedMessages.size());
    assertEquals(testMessage, receivedMessages.getFirst());

    // Close the connections
    if (serverConnection.get() != null) {
      serverConnection.get().close().get(1, TimeUnit.SECONDS);
    }
    if (clientConnection != null) {
      clientConnection.close().get(1, TimeUnit.SECONDS);
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testMultipleClients() throws Exception {
    // Start the server listening on an available port
    ConnectionListener listener = transport.listenOnAvailablePort();
    FlowStream<FlowConnection> connectionStream = listener.getStream();
    LocalEndpoint serverEndpoint = listener.getBoundEndpoint();

    // Number of clients to create
    int numClients = 3;

    // Create latches to synchronize operations
    CountDownLatch allClientsConnectedLatch = new CountDownLatch(numClients);
    CountDownLatch allMessagesReceivedLatch = new CountDownLatch(numClients);

    // Storage for connections
    List<FlowConnection> serverConnections = new ArrayList<>();
    List<FlowConnection> clientConnections = new ArrayList<>();

    // Start an actor to accept connections
    startActor(() -> {
      try {
        for (int i = 0; i < numClients; i++) {
          FlowConnection connection = await(connectionStream.nextAsync());
          serverConnections.add(connection);

          // Start a handler for this connection
          startActor(() -> {
            try {
              // Receive a message
              ByteBuffer buffer = await(connection.receive(1024));

              // Echo it back
              await(connection.send(buffer.duplicate()));

              // Count this response
              allMessagesReceivedLatch.countDown();
            } catch (Exception e) {
              fail("Client handler failed: " + e.getMessage());
            }
            return null;
          });
        }
      } catch (Exception e) {
        fail("Server actor failed: " + e.getMessage());
      }
      return null;
    });

    // Create multiple clients
    for (int i = 0; i < numClients; i++) {
      final int clientId = i;

      // Connect to the server
      startActor(() -> {
        FlowConnection conn = await(transport.connect(serverEndpoint));
        clientConnections.add(conn);
        allClientsConnectedLatch.countDown();

        // Send a message unique to this client
        String message = "Hello from client " + clientId;
        ByteBuffer sendBuffer = createBuffer(message);
        await(conn.send(sendBuffer));

        // Receive the echo
        ByteBuffer receiveBuffer = await(conn.receive(1024));
        String receivedMessage = bufferToString(receiveBuffer);

        // Verify the message
        assertEquals(message, receivedMessage);

        return conn;
      }).get(5, TimeUnit.SECONDS);
    }

    // Wait for all clients to connect and all messages to be received
    assertTrue(allClientsConnectedLatch.await(30, TimeUnit.SECONDS));
    assertTrue(allMessagesReceivedLatch.await(30, TimeUnit.SECONDS));

    // Verify the number of connections
    assertEquals(numClients, serverConnections.size());
    assertEquals(numClients, clientConnections.size());

    // Close all connections
    for (FlowConnection conn : serverConnections) {
      if (conn != null) {
        conn.close().get(1, TimeUnit.SECONDS);
      }
    }
    for (FlowConnection conn : clientConnections) {
      if (conn != null) {
        conn.close().get(1, TimeUnit.SECONDS);
      }
    }
  }
}