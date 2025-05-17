package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the RealFlowTransport class.
 * These tests use real network sockets, so they may fail if the ports are in use.
 * 
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
  private int basePort;

  @BeforeEach
  public void setUp() {
    try {
      // Find an available port for testing
      try (ServerSocket socket = new ServerSocket(0)) {
        basePort = socket.getLocalPort();
      }
      
      // Create a real transport
      transport = new RealFlowTransport();
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize RealFlowTransportTest", e);
    }
  }

  @AfterEach
  public void tearDown() {
    // Close the transport
    try {
      if (transport != null) {
        transport.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      // Ignore exceptions during cleanup
    }
  }

  @Test
  void testBasicConnectionAndCommunication() throws Exception {
    // Define server endpoint
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(basePort);
    
    // Start the server listening for connections
    FlowStream<FlowConnection> connectionStream = transport.listen(serverEndpoint);
    
    // Create latches to synchronize operations
    CountDownLatch serverAcceptLatch = new CountDownLatch(1);
    CountDownLatch messageSentLatch = new CountDownLatch(1);
    CountDownLatch messageReceivedLatch = new CountDownLatch(1);
    
    // Storage for connections and messages
    AtomicReference<FlowConnection> serverConnection = new AtomicReference<>();
    AtomicReference<String> receivedMessage = new AtomicReference<>();
    
    // Start an actor to accept connections
    Flow.startActor(() -> {
      try {
        // Wait for a connection
        FlowConnection connection = Flow.await(connectionStream.nextAsync());
        serverConnection.set(connection);
        serverAcceptLatch.countDown();
        
        // Wait for a message
        ByteBuffer buffer = Flow.await(connection.receive(1024));
        receivedMessage.set(bufferToString(buffer));
        messageReceivedLatch.countDown();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    });
    
    // Connect to the server
    FlowConnection clientConnection = Flow.startActor(() -> {
      return Flow.await(transport.connect(serverEndpoint));
    }).toCompletableFuture().get(5, TimeUnit.SECONDS);
    
    // Wait for the server to accept the connection
    assertTrue(serverAcceptLatch.await(5, TimeUnit.SECONDS));
    assertNotNull(serverConnection.get());
    
    // Send a message from client to server
    String testMessage = "Hello, server!";
    Flow.startActor(() -> {
      Flow.await(clientConnection.send(createBuffer(testMessage)));
      messageSentLatch.countDown();
      return null;
    });
    
    // Wait for the message to be sent and received
    assertTrue(messageSentLatch.await(5, TimeUnit.SECONDS));
    assertTrue(messageReceivedLatch.await(5, TimeUnit.SECONDS));
    
    // Verify the message was received correctly
    assertEquals(testMessage, receivedMessage.get());
    
    // Close the connections
    serverConnection.get().close().toCompletableFuture().get(5, TimeUnit.SECONDS);
    clientConnection.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  void testReceiveStream() throws Exception {
    // Define server endpoint
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(basePort + 1);
    
    // Start the server listening for connections
    FlowStream<FlowConnection> connectionStream = transport.listen(serverEndpoint);
    
    // Create latches to synchronize operations
    CountDownLatch serverAcceptLatch = new CountDownLatch(1);
    CountDownLatch allMessagesReceivedLatch = new CountDownLatch(1);
    
    // Storage for connections and messages
    AtomicReference<FlowConnection> serverConnection = new AtomicReference<>();
    List<String> receivedMessages = new ArrayList<>();
    
    // Define test messages - we'll only send one to simplify the test
    String testMessage = "Message 1";
    
    // Start an actor to accept connections and receive messages via stream
    Flow.startActor(() -> {
      try {
        // Wait for a connection
        FlowConnection connection = Flow.await(connectionStream.nextAsync());
        serverConnection.set(connection);
        serverAcceptLatch.countDown();
        
        // Get the receive stream
        FlowStream<ByteBuffer> receiveStream = connection.receiveStream();
        
        // Receive one message
        ByteBuffer buffer = Flow.await(receiveStream.nextAsync());
        receivedMessages.add(bufferToString(buffer));
        
        allMessagesReceivedLatch.countDown();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    });
    
    // Connect to the server
    FlowConnection clientConnection = Flow.startActor(() -> {
      return Flow.await(transport.connect(serverEndpoint));
    }).toCompletableFuture().get(5, TimeUnit.SECONDS);
    
    // Wait for the server to accept the connection
    assertTrue(serverAcceptLatch.await(5, TimeUnit.SECONDS));
    
    // Send a message
    Flow.startActor(() -> {
      Flow.await(clientConnection.send(createBuffer(testMessage)));
      return null;
    }).toCompletableFuture().get(2, TimeUnit.SECONDS);
    
    // Wait for all messages to be received
    assertTrue(allMessagesReceivedLatch.await(5, TimeUnit.SECONDS));
    
    // Verify message
    assertEquals(1, receivedMessages.size());
    assertEquals(testMessage, receivedMessages.get(0));
    
    // Close the connections
    serverConnection.get().close().toCompletableFuture().get(5, TimeUnit.SECONDS);
    clientConnection.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  void testMultipleClients() throws Exception {
    // Define server endpoint
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(basePort + 2);
    
    // Start the server listening for connections
    FlowStream<FlowConnection> connectionStream = transport.listen(serverEndpoint);
    
    // Number of clients to create
    int numClients = 3;
    
    // Create latches to synchronize operations
    CountDownLatch allClientsConnectedLatch = new CountDownLatch(numClients);
    CountDownLatch allMessagesReceivedLatch = new CountDownLatch(numClients);
    
    // Storage for connections
    List<FlowConnection> serverConnections = new ArrayList<>();
    List<FlowConnection> clientConnections = new ArrayList<>();
    
    // Start an actor to accept connections
    Flow.startActor(() -> {
      try {
        for (int i = 0; i < numClients; i++) {
          FlowConnection connection = Flow.await(connectionStream.nextAsync());
          serverConnections.add(connection);
          
          // Start a handler for this connection
          Flow.startActor(() -> {
            try {
              // Receive a message
              ByteBuffer buffer = Flow.await(connection.receive(1024));
              
              // Echo it back
              Flow.await(connection.send(buffer.duplicate()));
              
              // Count this response
              allMessagesReceivedLatch.countDown();
            } catch (Exception e) {
              e.printStackTrace();
            }
            return null;
          });
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    });
    
    // Create multiple clients
    for (int i = 0; i < numClients; i++) {
      final int clientId = i;
      
      // Connect to the server
      FlowConnection clientConnection = Flow.startActor(() -> {
        FlowConnection conn = Flow.await(transport.connect(serverEndpoint));
        clientConnections.add(conn);
        allClientsConnectedLatch.countDown();
        
        // Send a message unique to this client
        String message = "Hello from client " + clientId;
        ByteBuffer sendBuffer = createBuffer(message);
        Flow.await(conn.send(sendBuffer));
        
        // Receive the echo
        ByteBuffer receiveBuffer = Flow.await(conn.receive(1024));
        String receivedMessage = bufferToString(receiveBuffer);
        
        // Verify the message
        assertEquals(message, receivedMessage);
        
        return conn;
      }).toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
    
    // Wait for all clients to connect and all messages to be received
    assertTrue(allClientsConnectedLatch.await(10, TimeUnit.SECONDS));
    assertTrue(allMessagesReceivedLatch.await(10, TimeUnit.SECONDS));
    
    // Verify the number of connections
    assertEquals(numClients, serverConnections.size());
    assertEquals(numClients, clientConnections.size());
    
    // Close all connections
    for (FlowConnection conn : serverConnections) {
      conn.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
    for (FlowConnection conn : clientConnections) {
      conn.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
  }
}