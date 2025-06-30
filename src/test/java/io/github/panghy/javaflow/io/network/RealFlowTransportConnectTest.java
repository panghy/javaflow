package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  void tearDown() {
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
  void testChannelOpenError() {
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
  void testConnectAfterTransportClosed() {
    // Close the transport first
    transport.close();

    CompletableFuture<FlowConnection> connectFuture = Flow.startActor(() ->
        Flow.await(transport.connect(new Endpoint("localhost", 8080)))
    );

    // Should fail since transport is closed
    assertThrows(Exception.class, () -> connectFuture.get(5, TimeUnit.SECONDS));
  }

  private int findUnusedPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}