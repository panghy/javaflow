package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.ExecutionException;
/**
 * Tests for the listenOnAvailablePort method in SimulatedFlowTransport.
 * Specifically targets coverage for the SimulatedFlowTransport.listenOnAvailablePort method.
 */
public class SimulatedFlowTransportListenAvailablePortTest extends AbstractFlowTest {

  @Test
  void testListenOnAvailablePort() throws Exception {
    // Create a simulated transport with default parameters
    SimulatedFlowTransport transport = new SimulatedFlowTransport();
    
    try {
      // Call listenOnAvailablePort with a specific port
      LocalEndpoint requestedEndpoint = LocalEndpoint.localhost(8080);
      ConnectionListener listener = transport.listenOnAvailablePort(requestedEndpoint);
      
      // Verify the listener was created correctly
      assertNotNull(listener);
      assertNotNull(listener.getStream());
      
      // The port should be the same as requested (since this is simulated)
      assertEquals(8080, listener.getPort());
      assertEquals(requestedEndpoint.getHost(), listener.getBoundEndpoint().getHost());
      
      // Connect to the listener
      FlowStream<FlowConnection> connectionStream = listener.getStream();
      CompletableFuture<FlowConnection> acceptFuture = connectionStream.nextAsync();
      
      // Client connects to server
      CompletableFuture<FlowConnection> clientConnectionFuture = transport.connect(listener.getBoundEndpoint());
      
      // Wait for both to complete
      pumpAndAdvanceTimeUntilDone(clientConnectionFuture, acceptFuture);
      
      // Get the connections
      FlowConnection clientConnection = Flow.await(clientConnectionFuture);
      FlowConnection serverConnection = Flow.await(acceptFuture);
      
      // Verify connections were established
      assertNotNull(clientConnection);
      assertNotNull(serverConnection);
      
      // Send data from client to server
      String testMessage = "Testing listenOnAvailablePort";
      ByteBuffer buffer = ByteBuffer.wrap(testMessage.getBytes());
      clientConnection.send(buffer);
      
      // Receive on server
      CompletableFuture<ByteBuffer> receiveFuture = serverConnection.receive(1024);
      pumpAndAdvanceTimeUntilDone(receiveFuture);
      
      // Verify the message
      ByteBuffer received = Flow.await(receiveFuture);
      byte[] bytes = new byte[received.remaining()];
      received.get(bytes);
      assertEquals(testMessage, new String(bytes));
      
      // Close the connections
      clientConnection.close();
      serverConnection.close();
      pumpAndAdvanceTimeUntilDone();
    } finally {
      transport.close();
    }
  }
  
  @Test
  void testListenOnAvailablePortWithZeroPort() throws Exception {
    // Create a simulated transport with default parameters
    SimulatedFlowTransport transport = new SimulatedFlowTransport();
    
    try {
      // Call listenOnAvailablePort with port 0 (should assign a random port)
      LocalEndpoint requestedEndpoint = LocalEndpoint.localhost(0);
      ConnectionListener listener = transport.listenOnAvailablePort(requestedEndpoint);
      
      // Verify the listener was created correctly
      assertNotNull(listener);
      assertNotNull(listener.getStream());
      
      // The port should be different from 0
      assertTrue(listener.getPort() > 0, "Port should be assigned a value greater than 0");
      assertEquals(requestedEndpoint.getHost(), listener.getBoundEndpoint().getHost());
      
      // Connect to the listener
      CompletableFuture<FlowConnection> clientConnectionFuture = transport.connect(listener.getBoundEndpoint());
      pumpAndAdvanceTimeUntilDone(clientConnectionFuture);
      
      // Verify connection was established
      FlowConnection clientConnection = Flow.await(clientConnectionFuture);
      assertNotNull(clientConnection);
      
      // Close the connection
      clientConnection.close();
      pumpAndAdvanceTimeUntilDone();
    } finally {
      transport.close();
    }
  }
  
  @Test
  void testListenOnAvailablePortWithTransportClosed() throws Exception {
    // Create a simulated transport
    SimulatedFlowTransport transport = new SimulatedFlowTransport();
    
    // Close the transport
    transport.close();
    pumpAndAdvanceTimeUntilDone();
    
    // Now try to listen - this should handle the closed state
    LocalEndpoint endpoint = LocalEndpoint.localhost(8080);
    ConnectionListener listener = transport.listenOnAvailablePort(endpoint);
    
    // The listener should be created but its stream should be closed
    assertNotNull(listener);
    
    // Try to get a connection from the stream - should fail with a stream closed exception
    CompletableFuture<FlowConnection> acceptFuture = listener.getStream().nextAsync();
    pumpAndAdvanceTimeUntilDone(acceptFuture);
    
    // The future should be completed exceptionally
    assertTrue(acceptFuture.isCompletedExceptionally());
    try {
      acceptFuture.getNow(null);
    } catch (Exception e) {
      // The cause should be an IOException with a message about the transport being closed
      if (e instanceof ExecutionException) {
        assertTrue(e.getCause() instanceof IOException);
        assertTrue(e.getCause().getMessage().contains("closed"));
      }
    }
  }
  
  @Test
  void testListenOnAvailablePortDefaultImplementation() throws Exception {
    // Create a simulated transport
    SimulatedFlowTransport transport = new SimulatedFlowTransport();
    
    try {
      // Call the no-arg version which should delegate to localhost(0)
      ConnectionListener listener = transport.listenOnAvailablePort();
      
      // Verify the listener was created correctly
      assertNotNull(listener);
      assertNotNull(listener.getStream());
      
      // The port should be > 0 (random port)
      assertTrue(listener.getPort() > 0);
      assertEquals("localhost", listener.getBoundEndpoint().getHost());
      
      // Connect to the listener
      CompletableFuture<FlowConnection> clientConnectionFuture = transport.connect(listener.getBoundEndpoint());
      pumpAndAdvanceTimeUntilDone(clientConnectionFuture);
      
      // Verify connection was established
      FlowConnection clientConnection = Flow.await(clientConnectionFuture);
      assertNotNull(clientConnection);
      
      // Close the connection
      clientConnection.close();
      pumpAndAdvanceTimeUntilDone();
    } finally {
      transport.close();
    }
  }
  
  @Test
  void testGetParameters() {
    // Create a simulated transport with custom parameters
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setConnectDelay(0.5)
        .setSendDelay(0.2)
        .setDisconnectProbability(0.1);
    
    SimulatedFlowTransport transport = new SimulatedFlowTransport(params);
    
    // Call getParameters and verify it returns the correct object
    NetworkSimulationParameters retrievedParams = transport.getParameters();
    
    assertNotNull(retrievedParams);
    assertEquals(params, retrievedParams);
    assertEquals(0.5, retrievedParams.getConnectDelay());
    assertEquals(0.2, retrievedParams.getSendDelay());
    assertEquals(0.1, retrievedParams.getDisconnectProbability());
    
    // Clean up
    transport.close();
  }
}