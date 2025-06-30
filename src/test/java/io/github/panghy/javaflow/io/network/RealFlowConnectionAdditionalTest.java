package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for RealFlowConnection using real socket connections.
 * These tests focus on real-world usage patterns and error conditions that
 * can't be easily tested with mocks.
 */
public class RealFlowConnectionAdditionalTest extends AbstractFlowTest {

  /**
   * Tests that attempting to read from a connection after the transport is closed
   * results in an IOException. This tests the error propagation from the 
   * underlying channel to the FlowFuture.
   */
  @Test
  void testReadAfterTransportClose() throws Exception {
    // Create a transport and connection
    RealFlowTransport transport = new RealFlowTransport();
    
    // Create server on a random port
    AsynchronousSocketChannel tempChannel = AsynchronousSocketChannel.open();
    tempChannel.bind(new InetSocketAddress("localhost", 0));
    int port = ((InetSocketAddress) tempChannel.getLocalAddress()).getPort();
    tempChannel.close();
    
    // Set up a server
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(port);
    transport.listen(serverEndpoint);
    
    // Connect a client
    CompletableFuture<FlowConnection> connectFuture = transport.connect(
        new Endpoint("localhost", port));
    FlowConnection connection = connectFuture.get(5, TimeUnit.SECONDS);
    
    // Close the transport
    transport.close().get(5, TimeUnit.SECONDS);
    
    // Try to read from the connection after transport is closed
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    
    try {
      receiveFuture.get(5, TimeUnit.SECONDS);
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      assertTrue(connection.closeFuture().isDone());
      assertFalse(connection.isOpen());
    }
  }
}