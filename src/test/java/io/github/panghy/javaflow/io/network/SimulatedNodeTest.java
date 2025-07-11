package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SimulatedFlowTransport.SimulatedNode inner class functionality.
 * This test focuses on network node behaviors like disconnection and node management.
 */
public class SimulatedNodeTest extends AbstractFlowTest {

  private SimulatedFlowTransport transport;
  private NetworkSimulationParameters params;
  
  @BeforeEach
  void setUp() {
    params = new NetworkSimulationParameters();
    transport = new SimulatedFlowTransport(params);
  }
  
  @Test
  void testNodeDisconnection() throws ExecutionException {
    // Define endpoints
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8091);
    LocalEndpoint clientEndpoint = LocalEndpoint.localhost(8092);
    
    // Start listening on both endpoints
    FlowStream<FlowConnection> serverStream = transport.listen(serverEndpoint);
    FlowStream<FlowConnection> clientStream = transport.listen(clientEndpoint);
    
    // Create connections between them
    CompletableFuture<FlowConnection> client1Future = transport.connect(serverEndpoint);
    CompletableFuture<FlowConnection> client2Future = transport.connect(serverEndpoint);
    
    // Wait for connections
    pumpAndAdvanceTimeUntilDone(client1Future, client2Future);
    
    FlowConnection client1 = client1Future.getNow(null);
    FlowConnection client2 = client2Future.getNow(null);
    
    // Get server connections
    CompletableFuture<FlowConnection> server1Future = serverStream.nextAsync();
    CompletableFuture<FlowConnection> server2Future = serverStream.nextAsync();
    pumpAndAdvanceTimeUntilDone(server1Future, server2Future);
    
    FlowConnection server1 = server1Future.getNow(null);
    FlowConnection server2 = server2Future.getNow(null);
    
    // Verify all connections are active
    assertTrue(client1.isOpen());
    assertTrue(client2.isOpen());
    assertTrue(server1.isOpen());
    assertTrue(server2.isOpen());
    
    // Now disconnect server from specific clients
    transport.createPartition(serverEndpoint, client1.getLocalEndpoint());
    pumpAndAdvanceTimeUntilDone();
    
    // Try to send data from client1 to server - should fail or get disconnected
    CompletableFuture<Void> sendFuture = client1.send(ByteBuffer.wrap("test".getBytes()));
    pumpAndAdvanceTimeUntilDone(sendFuture);
    
    // Client1 connection should be closed or sending should fail
    boolean connectionFailed = !client1.isOpen() || sendFuture.isCompletedExceptionally();
    assertTrue(connectionFailed, "Connection should fail after partition");
    
    // Client2 should still be able to send
    CompletableFuture<Void> send2Future = client2.send(ByteBuffer.wrap("test2".getBytes()));
    pumpAndAdvanceTimeUntilDone(send2Future);
    
    assertFalse(send2Future.isCompletedExceptionally(), "Unpartitioned connection should work");
    
    // Heal the partition
    transport.healPartition(serverEndpoint, client1.getLocalEndpoint());
    
    // Create a new connection from client1 endpoint
    CompletableFuture<FlowConnection> newClientFuture = transport.connect(serverEndpoint);
    pumpAndAdvanceTimeUntilDone(newClientFuture);
    
    FlowConnection newClient = newClientFuture.getNow(null);
    assertTrue(newClient.isOpen(), "New connection should work after healing partition");
    
    // Clean up all connections
    if (client1.isOpen()) {
      client1.close();
    }
    client2.close();
    server1.close();
    server2.close();
    newClient.close();
    pumpAndAdvanceTimeUntilDone();
  }
  
  @Test
  void testNodeCleanupOnClose() throws ExecutionException {
    // Define endpoints
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8093);
    
    // Start the server
    FlowStream<FlowConnection> serverStream = transport.listen(serverEndpoint);
    
    // Connect multiple clients
    CompletableFuture<FlowConnection> client1Future = transport.connect(serverEndpoint);
    CompletableFuture<FlowConnection> client2Future = transport.connect(serverEndpoint);
    CompletableFuture<FlowConnection> client3Future = transport.connect(serverEndpoint);
    
    // Wait for client connections
    pumpAndAdvanceTimeUntilDone(client1Future, client2Future, client3Future);
    
    FlowConnection client1 = client1Future.getNow(null);
    FlowConnection client2 = client2Future.getNow(null);
    FlowConnection client3 = client3Future.getNow(null);
    
    // Accept server connections
    CompletableFuture<FlowConnection> server1Future = serverStream.nextAsync();
    CompletableFuture<FlowConnection> server2Future = serverStream.nextAsync();
    CompletableFuture<FlowConnection> server3Future = serverStream.nextAsync();
    
    pumpAndAdvanceTimeUntilDone(server1Future, server2Future, server3Future);
    
    FlowConnection server1 = server1Future.getNow(null);
    FlowConnection server2 = server2Future.getNow(null);
    FlowConnection server3 = server3Future.getNow(null);
    
    // Verify all connections are active
    assertTrue(client1.isOpen());
    assertTrue(client2.isOpen());
    assertTrue(client3.isOpen());
    assertTrue(server1.isOpen());
    assertTrue(server2.isOpen());
    assertTrue(server3.isOpen());
    
    // Close the transport - should close all connections
    CompletableFuture<Void> closeFuture = transport.close();
    pumpAndAdvanceTimeUntilDone(closeFuture);
    
    // Verify all connections are closed
    assertFalse(client1.isOpen());
    assertFalse(client2.isOpen());
    assertFalse(client3.isOpen());
    assertFalse(server1.isOpen());
    assertFalse(server2.isOpen());
    assertFalse(server3.isOpen());
    
    // Verify transport is closed
    assertTrue(closeFuture.isDone());
    assertFalse(closeFuture.isCompletedExceptionally());
  }
  
  @Test
  void testNodeCleanupOnConnectionClose() throws ExecutionException {
    // Define endpoints
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(8094);
    
    // Start the server
    FlowStream<FlowConnection> serverStream = transport.listen(serverEndpoint);
    
    // Connect client
    CompletableFuture<FlowConnection> clientFuture = transport.connect(serverEndpoint);
    pumpAndAdvanceTimeUntilDone(clientFuture);
    
    FlowConnection client = clientFuture.getNow(null);
    
    // Accept server connection
    CompletableFuture<FlowConnection> serverFuture = serverStream.nextAsync();
    pumpAndAdvanceTimeUntilDone(serverFuture);
    
    FlowConnection server = serverFuture.getNow(null);
    
    // Verify connections are active
    assertTrue(client.isOpen());
    assertTrue(server.isOpen());
    
    // Get close futures
    CompletableFuture<Void> clientCloseFuture = client.closeFuture();
    CompletableFuture<Void> serverCloseFuture = server.closeFuture();
    
    assertFalse(clientCloseFuture.isDone());
    assertFalse(serverCloseFuture.isDone());
    
    // Close one connection
    client.close();
    pumpAndAdvanceTimeUntilDone();
    
    // Verify close futures are completed
    assertTrue(clientCloseFuture.isDone());
    assertTrue(serverCloseFuture.isDone());
    
    // Verify both ends are closed
    assertFalse(client.isOpen());
    assertFalse(server.isOpen());
    
    // Verify we can create new connections
    CompletableFuture<FlowConnection> newClientFuture = transport.connect(serverEndpoint);
    pumpAndAdvanceTimeUntilDone(newClientFuture);
    
    FlowConnection newClient = newClientFuture.getNow(null);
    assertTrue(newClient.isOpen());
    
    // Clean up
    newClient.close();
    pumpAndAdvanceTimeUntilDone();
  }
}