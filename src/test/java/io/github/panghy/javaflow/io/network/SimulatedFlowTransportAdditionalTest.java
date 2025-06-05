package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional tests for SimulatedFlowTransport to improve code coverage.
 */
public class SimulatedFlowTransportAdditionalTest extends AbstractFlowTest {

  @Test
  void testErrorInjection() throws ExecutionException {
    // Create parameters with error injection
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setConnectErrorProbability(1.0) // 100% connect errors
        .setSendErrorProbability(1.0)    // 100% send errors
        .setReceiveErrorProbability(1.0) // 100% receive errors
        .setDisconnectProbability(0.0);  // No random disconnects

    // Create transport
    SimulatedFlowTransport transport = new SimulatedFlowTransport(params);
    
    try {
      // Start listening
      LocalEndpoint endpoint = LocalEndpoint.localhost(8080);
      transport.listen(endpoint);
      
      // Try to connect - should fail with 100% probability
      FlowFuture<FlowConnection> connectFuture = transport.connect(endpoint);
      pumpUntilDone(connectFuture);
      
      assertTrue(connectFuture.isCompletedExceptionally(), "Connect should fail with 100% error probability");
      
      // Try again with normal parameters to test that at least one connection succeeds
      params.setConnectErrorProbability(0.0);
      FlowFuture<FlowConnection> successFuture = transport.connect(endpoint);
      pumpUntilDone(successFuture);
      
      assertFalse(successFuture.isCompletedExceptionally(), "Connect should succeed with 0% error probability");
      
      // Test send errors
      FlowConnection connection = successFuture.getNow();
      assertNotNull(connection);
      
      // Send should fail with 100% probability
      ByteBuffer data = ByteBuffer.wrap("test".getBytes());
      FlowFuture<Void> sendFuture = connection.send(data);
      pumpUntilDone(sendFuture);
      
      assertTrue(sendFuture.isCompletedExceptionally(), "Send should fail with 100% error probability");
      
      // Receive should fail with 100% probability
      FlowFuture<ByteBuffer> receiveFuture = connection.receive(1024);
      pumpUntilDone(receiveFuture);
      
      assertTrue(receiveFuture.isCompletedExceptionally(), "Receive should fail with 100% error probability");
    } finally {
      // Clean up
      transport.close();
    }
  }
  
  @Test
  void testDisconnectProbability() throws ExecutionException {
    // Create parameters with disconnect probability
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setConnectErrorProbability(0.0)
        .setDisconnectProbability(1.0); // 100% disconnect probability

    // Create transport
    SimulatedFlowTransport transport = new SimulatedFlowTransport(params);
    
    try {
      // Start listening
      LocalEndpoint endpoint = LocalEndpoint.localhost(8080);
      transport.listen(endpoint);
      
      // Connect (should succeed)
      FlowFuture<FlowConnection> connectFuture = transport.connect(endpoint);
      pumpUntilDone(connectFuture);
      
      assertFalse(connectFuture.isCompletedExceptionally(), "Connect should succeed");
      
      FlowConnection connection = connectFuture.getNow();
      assertNotNull(connection);
      
      // Send with 100% disconnect probability should cause connection to close
      ByteBuffer data = ByteBuffer.wrap("test".getBytes());
      FlowFuture<Void> sendFuture = connection.send(data);
      pumpUntilDone(sendFuture);
      
      assertTrue(sendFuture.isCompletedExceptionally(), "Send should fail due to disconnect");
      assertFalse(connection.isOpen(), "Connection should be closed after disconnect");
    } finally {
      // Clean up
      transport.close();
    }
  }
  
  @Test
  void testMultipleListeners() throws ExecutionException {
    // Create a transport
    SimulatedFlowTransport transport = new SimulatedFlowTransport();
    
    try {
      // Create multiple endpoints and listeners
      LocalEndpoint endpoint1 = LocalEndpoint.localhost(8081);
      LocalEndpoint endpoint2 = LocalEndpoint.localhost(8082);
      
      // Start listening on both endpoints
      FlowStream<FlowConnection> stream1 = transport.listen(endpoint1);
      FlowStream<FlowConnection> stream2 = transport.listen(endpoint2);
      
      // Connect to both
      FlowFuture<FlowConnection> connectFuture1 = transport.connect(endpoint1);
      FlowFuture<FlowConnection> connectFuture2 = transport.connect(endpoint2);
      
      // Wait for connections
      pumpUntilDone(connectFuture1, connectFuture2);
      
      // Get connections from listeners
      FlowFuture<FlowConnection> acceptFuture1 = stream1.nextAsync();
      FlowFuture<FlowConnection> acceptFuture2 = stream2.nextAsync();
      pumpUntilDone(acceptFuture1, acceptFuture2);
      
      // Verify all connections were established
      assertFalse(connectFuture1.isCompletedExceptionally());
      assertFalse(connectFuture2.isCompletedExceptionally());
      assertFalse(acceptFuture1.isCompletedExceptionally());
      assertFalse(acceptFuture2.isCompletedExceptionally());
      
      // Clean up connections
      connectFuture1.getNow().close();
      connectFuture2.getNow().close();
      acceptFuture1.getNow().close();
      acceptFuture2.getNow().close();
      
      // Wait for close operations
      pumpUntilDone();
    } finally {
      // Clean up
      transport.close();
    }
  }
  
  @Test
  void testTransportCloseWhileOperationsInProgress() throws ExecutionException {
    // Create a transport
    SimulatedFlowTransport transport = new SimulatedFlowTransport();
    
    // Start listening
    LocalEndpoint endpoint = LocalEndpoint.localhost(8080);
    FlowStream<FlowConnection> stream = transport.listen(endpoint);
    
    // Create a connection
    FlowFuture<FlowConnection> connectFuture = transport.connect(endpoint);
    FlowFuture<FlowConnection> acceptFuture = stream.nextAsync();
    pumpUntilDone(connectFuture, acceptFuture);
    
    // Get the connections
    FlowConnection clientConn = connectFuture.getNow();
    FlowConnection serverConn = acceptFuture.getNow();
    
    // Start operations that will be interrupted by close
    AtomicReference<Exception> sendException = new AtomicReference<>();
    AtomicReference<Exception> receiveException = new AtomicReference<>();
    AtomicInteger closeFutureCompletions = new AtomicInteger(0);
    
    // Send operation
    FlowFuture<Void> sendFuture = clientConn.send(ByteBuffer.wrap("test".getBytes()));
    sendFuture.whenComplete((v, ex) -> {
      if (ex != null) {
        sendException.set((Exception) ex);
      }
    });
    
    // Register close future handlers
    clientConn.closeFuture().whenComplete((v, ex) -> closeFutureCompletions.incrementAndGet());
    serverConn.closeFuture().whenComplete((v, ex) -> closeFutureCompletions.incrementAndGet());
    
    // Close the transport while operations are in progress
    FlowFuture<Void> closeFuture = transport.close();
    pumpUntilDone(closeFuture);
    
    // Verify all futures completed
    assertTrue(closeFuture.isDone() && !closeFuture.isCompletedExceptionally(), 
        "Transport close should complete successfully");
    
    // Wait for callbacks to execute
    pumpUntilDone();
    
    // Verify all closeFutures completed
    assertEquals(2, closeFutureCompletions.get(), "All connection closeFutures should have completed");
    
    // Verify all connections are closed
    assertFalse(clientConn.isOpen(), "Client connection should be closed");
    assertFalse(serverConn.isOpen(), "Server connection should be closed");
    
    // Try to connect/listen after close - this should fail
    FlowFuture<FlowConnection> postCloseFuture = transport.connect(endpoint);
    pumpUntilDone(postCloseFuture);
    
    assertTrue(postCloseFuture.isCompletedExceptionally(), "Connect after close should fail");
  }
}