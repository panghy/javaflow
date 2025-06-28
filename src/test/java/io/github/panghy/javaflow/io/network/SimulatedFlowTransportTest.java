package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SimulatedFlowTransport class.
 */
public class SimulatedFlowTransportTest extends AbstractNetworkTest {

  private SimulatedFlowTransport transport;
  private NetworkSimulationParameters params;

  @Override
  protected void onSetUp() {
    // Create default simulation parameters
    params = new NetworkSimulationParameters();
    
    // Create a simulated transport with default parameters
    transport = new SimulatedFlowTransport(params);
  }

  @Override
  protected void onTearDown() {
    // Close the transport if it's not already closed
    try {
      if (transport != null) {
        transport.close().toCompletableFuture().get(1, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      // Ignore exceptions during cleanup
    }
  }

  @Test
  void testConnectAndSendReceive() throws Exception {
    // Define server and client endpoints
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8080);
    
    // Start the server listening for connections
    FlowStream<FlowConnection> connectionStream = transport.listen(serverEndpoint);
    
    // Client connects to server
    CompletableFuture<FlowConnection> clientConnectionFuture = transport.connect(serverEndpoint);
    
    // Server accepts connection
    CompletableFuture<FlowConnection> serverConnectionFuture = connectionStream.nextAsync();
    
    // Wait for both to complete
    pumpAndAdvanceTimeUntilDone(clientConnectionFuture, serverConnectionFuture);
    
    // Get the connections
    FlowConnection clientConnection = Flow.await(clientConnectionFuture);
    FlowConnection serverConnection = Flow.await(serverConnectionFuture);
    
    // Verify connections were established
    assertNotNull(clientConnection);
    assertNotNull(serverConnection);
    
    // Send data from client to server
    String testMessage = "Hello from client";
    clientConnection.send(createBuffer(testMessage));
    
    // Receive on server
    CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    pumpAndAdvanceTimeUntilDone(receiveFuture);
    
    // Verify the message
    ByteBuffer received = Flow.await(receiveFuture);
    assertEquals(testMessage, bufferToString(received));
    
    // Close the connections
    clientConnection.close();
    serverConnection.close();
    pumpAndAdvanceTimeUntilDone();
    
    // Verify connections are closed
    assertFalse(clientConnection.isOpen());
    assertFalse(serverConnection.isOpen());
  }

  @Test
  void testNetworkPartitioning() throws Exception {
    // Define server endpoint
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8080);
    
    // Create a new connection with 100% send error probability
    NetworkSimulationParameters errorParams = new NetworkSimulationParameters()
        .setSendErrorProbability(1.0);
    SimulatedFlowTransport errorTransport = new SimulatedFlowTransport(errorParams);
    
    try {
      // Start listening
      errorTransport.listen(serverEndpoint);
      
      // Connect and verify connection works
      CompletableFuture<FlowConnection> clientConnectionFuture = errorTransport.connect(serverEndpoint);
      pumpAndAdvanceTimeUntilDone(clientConnectionFuture);
      FlowConnection clientConnection = Flow.await(clientConnectionFuture);
      
      // Verify we can get a connection despite high error probability
      assertNotNull(clientConnection);
      assertTrue(clientConnection.isOpen());
      
      // But sending should fail with 100% probability
      CompletableFuture<Void> sendFuture = clientConnection.send(createBuffer("This should fail"));
      pumpAndAdvanceTimeUntilDone(sendFuture);
      
      // Verify send failed
      assertTrue(sendFuture.isCompletedExceptionally());
      
      try {
        Flow.await(sendFuture);
        // Should not reach here
        assertTrue(false, "Expected send to fail but it succeeded");
      } catch (Exception e) {
        // Expected exception
        assertTrue(e instanceof IOException);
      }
      
      // Now reset error probability and try again
      errorParams.setSendErrorProbability(0.0);
      
      // This should now succeed
      CompletableFuture<Void> successSendFuture = clientConnection.send(createBuffer("This should succeed"));
      pumpAndAdvanceTimeUntilDone(successSendFuture);
      
      // Verify it succeeded
      assertTrue(successSendFuture.isDone() && !successSendFuture.isCompletedExceptionally());
      
      // Clean up
      clientConnection.close();
    } finally {
      errorTransport.close();
    }
  }

  @Test
  void testReceiveStream() throws Exception {
    // Define server endpoint
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8080);
    
    // Start the server listening for connections
    FlowStream<FlowConnection> connectionStream = transport.listen(serverEndpoint);
    
    // Client connects to server
    CompletableFuture<FlowConnection> clientConnectionFuture = transport.connect(serverEndpoint);
    CompletableFuture<FlowConnection> serverConnectionFuture = connectionStream.nextAsync();
    
    // Wait for connections to be established
    pumpAndAdvanceTimeUntilDone(clientConnectionFuture, serverConnectionFuture);
    FlowConnection clientConnection = Flow.await(clientConnectionFuture);
    FlowConnection serverConnection = Flow.await(serverConnectionFuture);
    
    // Get the receive stream from the server connection
    FlowStream<ByteBuffer> receiveStream = serverConnection.receiveStream();
    
    // Send multiple messages from client to server
    String[] messages = {"Message 1", "Message 2", "Message 3"};
    List<CompletableFuture<Void>> sendFutures = new ArrayList<>();
    
    for (String message : messages) {
      CompletableFuture<Void> sendFuture = clientConnection.send(createBuffer(message));
      sendFutures.add(sendFuture);
      // Advance time a bit between sends
      advanceTime(0.01);
    }
    
    // Receive messages from the stream
    List<String> receivedMessages = new ArrayList<>();
    for (int i = 0; i < messages.length; i++) {
      CompletableFuture<ByteBuffer> receiveFuture = receiveStream.nextAsync();
      pumpAndAdvanceTimeUntilDone(receiveFuture);
      ByteBuffer data = Flow.await(receiveFuture);
      receivedMessages.add(bufferToString(data));
    }
    
    // Verify all messages were received in order
    assertEquals(messages.length, receivedMessages.size());
    for (int i = 0; i < messages.length; i++) {
      assertEquals(messages[i], receivedMessages.get(i));
    }
    
    // Clean up
    clientConnection.close();
    serverConnection.close();
    pumpAndAdvanceTimeUntilDone();
  }

  @Test
  void testSimulatedErrors() throws Exception {
    // Create parameters with high error probabilities
    NetworkSimulationParameters errorParams = new NetworkSimulationParameters()
        .setConnectErrorProbability(0.9)
        .setSendErrorProbability(0.9)
        .setReceiveErrorProbability(0.9);
    
    // Create a transport with error-prone parameters
    SimulatedFlowTransport errorTransport = new SimulatedFlowTransport(errorParams);
    
    try {
      // Define endpoints
      LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8081);
      
      // Start server
      errorTransport.listen(serverEndpoint);
      
      // Try to connect multiple times - at least one should fail
      boolean connectErrorOccurred = false;
      for (int i = 0; i < 5 && !connectErrorOccurred; i++) {
        CompletableFuture<FlowConnection> connectFuture = errorTransport.connect(serverEndpoint);
        pumpAndAdvanceTimeUntilDone(connectFuture);
        if (connectFuture.isCompletedExceptionally()) {
          connectErrorOccurred = true;
        }
      }
      
      assertTrue(connectErrorOccurred, "Expected at least one connect error with high error probability");
      
      // Create a new transport with only send errors for testing send/receive errors
      NetworkSimulationParameters sendErrorParams = new NetworkSimulationParameters()
          .setConnectErrorProbability(0.0)
          .setSendErrorProbability(0.9)
          .setReceiveErrorProbability(0.9);
      
      SimulatedFlowTransport sendErrorTransport = new SimulatedFlowTransport(sendErrorParams);
      
      try {
        // Start server and connect client
        FlowStream<FlowConnection> connectionStream = sendErrorTransport.listen(serverEndpoint);
        FlowConnection clientConnection = awaitFuture(sendErrorTransport.connect(serverEndpoint), true);
        FlowConnection serverConnection = awaitFuture(connectionStream.nextAsync(), true);
        
        // Try to send multiple times - at least one should fail
        boolean sendErrorOccurred = false;
        for (int i = 0; i < 5 && !sendErrorOccurred; i++) {
          CompletableFuture<Void> sendFuture = clientConnection.send(createBuffer("Test message"));
          pumpAndAdvanceTimeUntilDone(sendFuture);
          if (sendFuture.isCompletedExceptionally()) {
            sendErrorOccurred = true;
          }
        }
        
        assertTrue(sendErrorOccurred, "Expected at least one send error with high error probability");
        
        // Try to receive - should eventually fail
        boolean receiveErrorOccurred = false;
        for (int i = 0; i < 5 && !receiveErrorOccurred; i++) {
          // First send a message that should succeed
          ByteBuffer testData = createBuffer("Test message");
          try {
            clientConnection.send(createBuffer("Test message")).toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            // Try to receive
            CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
            pumpAndAdvanceTimeUntilDone(receiveFuture);
            if (receiveFuture.isCompletedExceptionally()) {
              receiveErrorOccurred = true;
            }
          } catch (Exception e) {
            // We may get an exception on the send rather than the receive
            receiveErrorOccurred = true;
          }
        }
        
        assertTrue(receiveErrorOccurred, "Expected at least one receive error with high error probability");
        
        // Clean up
        clientConnection.close();
        serverConnection.close();
      } finally {
        sendErrorTransport.close();
      }
    } finally {
      errorTransport.close();
    }
  }

  @Test
  void testTransportClose() throws Exception {
    // Define server endpoint
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8082);
    
    // Start the server and connect a client
    FlowStream<FlowConnection> connectionStream = transport.listen(serverEndpoint);
    FlowConnection clientConnection = awaitFuture(transport.connect(serverEndpoint), true);
    FlowConnection serverConnection = awaitFuture(connectionStream.nextAsync(), true);
    
    // Verify connections are open
    assertTrue(clientConnection.isOpen());
    assertTrue(serverConnection.isOpen());
    
    // Close the transport
    CompletableFuture<Void> closeFuture = transport.close();
    pumpAndAdvanceTimeUntilDone(closeFuture);
    
    // Verify connections are closed when transport is closed
    assertFalse(clientConnection.isOpen());
    assertFalse(serverConnection.isOpen());
    
    // Verify we can't connect or listen after close
    AtomicBoolean listenFailed = new AtomicBoolean(false);
    AtomicBoolean connectFailed = new AtomicBoolean(false);
    
    // Try to listen
    try {
      FlowStream<FlowConnection> newStream = transport.listen(serverEndpoint);
      CompletableFuture<FlowConnection> nextFuture = newStream.nextAsync();
      pumpAndAdvanceTimeUntilDone(nextFuture);
      if (nextFuture.isCompletedExceptionally()) {
        listenFailed.set(true);
      }
    } catch (Exception e) {
      listenFailed.set(true);
    }
    
    // Try to connect
    try {
      CompletableFuture<FlowConnection> connectFuture = transport.connect(serverEndpoint);
      pumpAndAdvanceTimeUntilDone(connectFuture);
      if (connectFuture.isCompletedExceptionally()) {
        connectFailed.set(true);
      }
    } catch (Exception e) {
      connectFailed.set(true);
    }
    
    assertTrue(listenFailed.get(), "Listen should fail after transport is closed");
    assertTrue(connectFailed.get(), "Connect should fail after transport is closed");
  }
  
  @Test
  void testConnectionLifeCycle() throws Exception {
    // Define server endpoint
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8083);
    
    // Start the server and connect a client
    FlowStream<FlowConnection> connectionStream = transport.listen(serverEndpoint);
    FlowConnection clientConnection = awaitFuture(transport.connect(serverEndpoint), true);
    FlowConnection serverConnection = awaitFuture(connectionStream.nextAsync(), true);
    
    // Verify connection information
    assertEquals(serverEndpoint, serverConnection.getLocalEndpoint());
    assertEquals(clientConnection.getLocalEndpoint(), serverConnection.getRemoteEndpoint());
    assertEquals(serverEndpoint, clientConnection.getRemoteEndpoint());
    
    // Test the closeFuture() method
    CompletableFuture<Void> clientCloseFuture = clientConnection.closeFuture();
    CompletableFuture<Void> serverCloseFuture = serverConnection.closeFuture();
    
    assertFalse(clientCloseFuture.isDone());
    assertFalse(serverCloseFuture.isDone());
    
    // Close the client connection
    clientConnection.close();
    pumpAndAdvanceTimeUntilDone();
    
    // Both futures should be completed
    assertTrue(clientCloseFuture.isDone());
    assertTrue(serverCloseFuture.isDone());
    
    // Both connections should be closed
    assertFalse(clientConnection.isOpen());
    assertFalse(serverConnection.isOpen());
    
    // Verify operations on closed connections fail
    CompletableFuture<Void> sendFuture = clientConnection.send(createBuffer("Should fail"));
    pumpAndAdvanceTimeUntilDone(sendFuture);
    assertTrue(sendFuture.isCompletedExceptionally());
    
    CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
    pumpAndAdvanceTimeUntilDone(receiveFuture);
    assertTrue(receiveFuture.isCompletedExceptionally());
  }
}