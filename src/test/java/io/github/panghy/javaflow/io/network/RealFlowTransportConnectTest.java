package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

/**
 * Tests for RealFlowTransport's connect method, focusing on edge cases and error handling.
 */
public class RealFlowTransportConnectTest extends AbstractFlowTest {

  private AsynchronousChannelGroup channelGroup;
  private ExecutorService executorService;
  private RealFlowTransport transport;

  @BeforeEach
  void setUp() throws IOException {
    executorService = Executors.newFixedThreadPool(2);
    channelGroup = AsynchronousChannelGroup.withThreadPool(executorService);
    transport = new RealFlowTransport(channelGroup);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (transport != null) {
      transport.close();
    }
    if (channelGroup != null && !channelGroup.isShutdown()) {
      channelGroup.shutdown();
    }
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
    }
  }

  @Test
  void testConnectRefusedError() throws IOException {
    // Create an endpoint with a port that's not listening
    int unusedPort = findUnusedPort();
    Endpoint endpoint = new Endpoint("localhost", unusedPort);

    // Start the connect in an actor
    CompletableFuture<FlowConnection> connectFuture = Flow.startActor(() -> 
        Flow.await(transport.connect(endpoint))
    );

    // Should fail with connection refused
    assertThrows(Exception.class, () -> connectFuture.get(5, TimeUnit.SECONDS));
  }

  @Test
  void testConnectGetLocalAddressError() throws Exception {
    // Use mockito to simulate an error in getting local address
    AsynchronousSocketChannel mockChannel = mock(AsynchronousSocketChannel.class);
    when(mockChannel.getLocalAddress()).thenThrow(new IOException("Simulated getLocalAddress failure"));
    when(mockChannel.isOpen()).thenReturn(true);
    doNothing().when(mockChannel).close();

    // Mock connect to succeed
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      CompletionHandler<Void, Object> handler = invocation.getArgument(2);
      handler.completed(null, invocation.getArgument(1));
      return null;
    }).when(mockChannel).connect(any(InetSocketAddress.class), any(), any());

    // Create a custom transport that uses our mock channel
    try (MockedStatic<AsynchronousSocketChannel> mockedStatic = 
            Mockito.mockStatic(AsynchronousSocketChannel.class)) {
      mockedStatic.when(() -> AsynchronousSocketChannel.open(any(AsynchronousChannelGroup.class)))
          .thenReturn(mockChannel);

      CompletableFuture<FlowConnection> connectFuture = Flow.startActor(() ->
          Flow.await(transport.connect(new Endpoint("localhost", 8080)))
      );

      pumpAndAdvanceTimeUntilDone(connectFuture);
      
      assertTrue(connectFuture.isCompletedExceptionally());
      try {
        connectFuture.getNow(null);
        fail("Expected CompletionException");
      } catch (Exception e) {
        Throwable cause = e.getCause();
        assertInstanceOf(IOException.class, cause,
            "Cause should be the IOException from getLocalAddress");
        assertEquals("Simulated getLocalAddress failure", cause.getMessage(),
            "Message should match");
      }
    }
  }

  @Test
  void testChannelOpenError() throws Exception {
    // Simulate error when opening channel
    try (MockedStatic<AsynchronousSocketChannel> mockedStatic = 
            Mockito.mockStatic(AsynchronousSocketChannel.class)) {
      
      String simulatedErrorMessage = "Simulated channel.open failure";
      mockedStatic.when(() -> AsynchronousSocketChannel.open(Mockito.any(AsynchronousChannelGroup.class)))
          .thenThrow(new IOException(simulatedErrorMessage));

      CompletableFuture<FlowConnection> connectFuture = Flow.startActor(() ->
          Flow.await(transport.connect(new Endpoint("localhost", 8080)))
      );

      pumpAndAdvanceTimeUntilDone(connectFuture);
      
      assertTrue(connectFuture.isCompletedExceptionally());
      try {
        connectFuture.getNow(null);
        fail("Expected CompletionException");
      } catch (Exception e) {
        Throwable cause = e.getCause();
        assertInstanceOf(IOException.class, cause, "Cause should be the IOException from channel.open()");
        assertEquals(simulatedErrorMessage, cause.getMessage(),
            "Message should match the simulated error");
      }
    }
  }

  @Test
  @Disabled("Real network operations don't work well with simulated scheduler - needs refactoring")
  void testSimultaneousConnections() throws Exception {
    // Start a simple server to accept connections
    int serverPort = findUnusedPort();
    ServerSocket serverSocket = new ServerSocket(serverPort);
    
    ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
    AtomicInteger acceptedConnections = new AtomicInteger(0);
    CountDownLatch serverReady = new CountDownLatch(1);
    CountDownLatch allConnectionsAccepted = new CountDownLatch(3);
    
    serverExecutor.submit(() -> {
      try {
        serverReady.countDown();
        while (acceptedConnections.get() < 3) {
          serverSocket.accept();
          acceptedConnections.incrementAndGet();
          allConnectionsAccepted.countDown();
        }
      } catch (IOException ignored) {
        // Server socket closed
      }
    });
    
    // Wait for server to be ready
    serverReady.await();
    
    try {
      // Create multiple connections simultaneously
      List<CompletableFuture<FlowConnection>> connectionFutures = new ArrayList<>();
      
      for (int i = 0; i < 3; i++) {
        CompletableFuture<FlowConnection> future = Flow.startActor(() ->
            Flow.await(transport.connect(new Endpoint("localhost", serverPort)))
        );
        connectionFutures.add(future);
      }
      
      // All connections should succeed (using real timeouts as these are real network operations)
      for (CompletableFuture<FlowConnection> future : connectionFutures) {
        FlowConnection connection = future.get(5, TimeUnit.SECONDS);
        assertNotNull(connection);
        assertTrue(connection.isOpen());
        connection.close();
      }
      
      // Verify all connections were accepted
      assertTrue(allConnectionsAccepted.await(5, TimeUnit.SECONDS));
      assertEquals(3, acceptedConnections.get());
      
    } finally {
      serverSocket.close();
      serverExecutor.shutdown();
    }
  }

  @Test
  void testConnectToInvalidHost() {
    // Use an invalid hostname
    Endpoint endpoint = new Endpoint("invalid.host.that.does.not.exist.example", 8080);
    
    CompletableFuture<FlowConnection> connectFuture = Flow.startActor(() ->
        Flow.await(transport.connect(endpoint))
    );
    
    // Should fail with unknown host or similar error
    assertThrows(Exception.class, () -> connectFuture.get(10, TimeUnit.SECONDS));
  }

  @Test
  void testConnectAfterTransportClosed() throws IOException {
    // Close the transport first
    transport.close();
    
    CompletableFuture<FlowConnection> connectFuture = Flow.startActor(() ->
        Flow.await(transport.connect(new Endpoint("localhost", 8080)))
    );
    
    // Should fail since transport is closed
    assertThrows(Exception.class, () -> connectFuture.get(5, TimeUnit.SECONDS));
  }

  @Test
  @Disabled("Real network operations don't work well with simulated scheduler - needs refactoring")
  void testMultipleConnectionsToSameEndpoint() throws Exception {
    // Start a server
    int serverPort = findUnusedPort();
    ServerSocket serverSocket = new ServerSocket(serverPort);
    
    ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
    AtomicInteger acceptedConnections = new AtomicInteger(0);
    
    serverExecutor.submit(() -> {
      try {
        while (!serverSocket.isClosed()) {
          serverSocket.accept();
          acceptedConnections.incrementAndGet();
        }
      } catch (IOException ignored) {
        // Server socket closed
      }
    });
    
    try {
      Endpoint endpoint = new Endpoint("localhost", serverPort);
      
      // Create multiple connections to the same endpoint
      FlowConnection conn1 = Flow.startActor(() -> 
          Flow.await(transport.connect(endpoint))
      ).get(5, TimeUnit.SECONDS);
      
      FlowConnection conn2 = Flow.startActor(() -> 
          Flow.await(transport.connect(endpoint))
      ).get(5, TimeUnit.SECONDS);
      
      // Both should be valid but different connections
      assertNotNull(conn1);
      assertNotNull(conn2);
      assertNotSame(conn1, conn2);
      assertTrue(conn1.isOpen());
      assertTrue(conn2.isOpen());
      
      // Verify both can send data
      ByteBuffer data = ByteBuffer.wrap("test".getBytes());
      conn1.send(data.duplicate()).get(5, TimeUnit.SECONDS);
      conn2.send(data.duplicate()).get(5, TimeUnit.SECONDS);
      
      // Clean up
      conn1.close();
      conn2.close();
      
      // Should have accepted 2 connections
      Thread.sleep(100); // Give server time to process
      assertEquals(2, acceptedConnections.get());
      
    } finally {
      serverSocket.close();
      serverExecutor.shutdown();
    }
  }

  @Test
  @Disabled("Real network operations don't work well with simulated scheduler - needs refactoring")
  void testConnectWithLargeTimeout() throws Exception {
    // Create a server that accepts but doesn't complete handshake
    int serverPort = findUnusedPort();
    ServerSocket serverSocket = new ServerSocket(serverPort);
    serverSocket.setSoTimeout(100); // Short timeout for accept
    
    ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
    CountDownLatch connectionAccepted = new CountDownLatch(1);
    AtomicReference<Exception> serverError = new AtomicReference<>();
    
    serverExecutor.submit(() -> {
      try {
        serverSocket.accept(); // Accept but don't do anything
        connectionAccepted.countDown();
        Thread.sleep(10000); // Keep connection open but inactive
      } catch (Exception e) {
        serverError.set(e);
      }
    });
    
    try {
      // Attempt connection in an actor
      CompletableFuture<FlowConnection> connectFuture = Flow.startActor(() ->
          Flow.await(transport.connect(new Endpoint("localhost", serverPort)))
      );
      
      // This test uses real network operations, so we need to use real timeouts
      // The connection should succeed as the server accepts the connection
      FlowConnection connection = connectFuture.get(30, TimeUnit.SECONDS);
      assertNotNull(connection);
      assertTrue(connection.isOpen());
      
      connection.close();
      
    } finally {
      serverSocket.close();
      serverExecutor.shutdownNow();
    }
  }

  private int findUnusedPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}