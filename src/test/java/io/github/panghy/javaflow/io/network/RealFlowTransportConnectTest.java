package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for RealFlowTransport's connect method, focusing on edge cases and error handling.
 */
public class RealFlowTransportConnectTest extends AbstractFlowTest {

  private AsynchronousChannelGroup channelGroup;
  private RealFlowTransport transport;
  private static final String SIMULATED_ERROR_MESSAGE = "Simulated error";

  @BeforeEach
  void setUp() throws Exception {
    channelGroup = AsynchronousChannelGroup.withThreadPool(
        Executors.newFixedThreadPool(2));
    transport = new RealFlowTransport(channelGroup);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (transport != null) {
      transport.close();
    }
    if (channelGroup != null && !channelGroup.isShutdown()) {
      channelGroup.shutdownNow();
    }
  }

  /**
   * Tests that IOException during getLocalAddress() in the connect completion handler
   * is properly handled and propagated.
   * <p>
   * This test uses Mockito to mock the static AsynchronousSocketChannel.open() method
   * to return a mock channel that throws IOException when getLocalAddress() is called.
   */
  @Test
  void testConnectGetLocalAddressIOException() throws Exception {
    // Mock the AsynchronousSocketChannel and related behavior
    AsynchronousSocketChannel mockChannel = Mockito.mock(AsynchronousSocketChannel.class);

    // Make getLocalAddress throw IOException to trigger the error path
    Mockito.when(mockChannel.getLocalAddress()).thenThrow(new IOException("Simulated getLocalAddress failure"));

    // Setup the channel to "successfully" connect by immediately executing the completion handler
    Mockito.doAnswer(invocation -> {
      // Extract the completion handler and call completed()
      @SuppressWarnings("unchecked")
      CompletionHandler<Void, Void> handler = invocation.getArgument(2);
      handler.completed(null, null);
      return null;
    }).when(mockChannel).connect(
        Mockito.any(InetSocketAddress.class),
        Mockito.any(),
        Mockito.<CompletionHandler<Void, Void>>any());

    // Use MockedStatic to replace the static AsynchronousSocketChannel.open method
    try (MockedStatic<AsynchronousSocketChannel> mockedStatic =
             Mockito.mockStatic(AsynchronousSocketChannel.class)) {

      // Make open() return our mock channel
      mockedStatic.when(() -> AsynchronousSocketChannel.open(Mockito.any(AsynchronousChannelGroup.class)))
          .thenReturn(mockChannel);

      // Call connect
      FlowFuture<FlowConnection> connectFuture = transport.connect(
          new Endpoint("localhost", 12345));

      // Verify the exception is an IOException
      try {
        connectFuture.getNow();
        fail("Expected ExecutionException");
      } catch (ExecutionException e) {
        assertInstanceOf(IOException.class, e.getCause(),
            "Cause should be the IOException from getLocalAddress");
        assertEquals("Simulated getLocalAddress failure", e.getCause().getMessage(),
            "Exception message should match");
      }

      // Verify the channel was closed
      Mockito.verify(mockChannel).close();
    }
  }

  /**
   * Tests that an IOException thrown by AsynchronousSocketChannel.open() is properly
   * propagated through the connect future.
   * <p>
   * This test uses Mockito to mock the static AsynchronousSocketChannel.open() method
   * to throw an IOException when called, simulating a failure to create the channel.
   */
  @Test
  void testConnectChannelOpenIOException() throws Exception {
    // Use MockedStatic to replace the static AsynchronousSocketChannel.open method
    try (MockedStatic<AsynchronousSocketChannel> mockedStatic =
             Mockito.mockStatic(AsynchronousSocketChannel.class)) {

      // Make open() throw IOException to trigger the error path
      mockedStatic.when(() -> AsynchronousSocketChannel.open(Mockito.any(AsynchronousChannelGroup.class)))
          .thenThrow(new IOException(SIMULATED_ERROR_MESSAGE));

      // Call connect
      FlowFuture<FlowConnection> connectFuture = transport.connect(
          new Endpoint("localhost", 12345));

      // Verify the exception is an IOException
      try {
        connectFuture.getNow();
        fail("Expected ExecutionException");
      } catch (ExecutionException e) {
        assertInstanceOf(IOException.class, e.getCause(), "Cause should be the IOException from channel.open()");
        assertEquals(SIMULATED_ERROR_MESSAGE, e.getCause().getMessage(),
            "Exception message should match");
      }
    }
  }
}