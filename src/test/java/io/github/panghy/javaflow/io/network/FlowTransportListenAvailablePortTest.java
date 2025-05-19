package io.github.panghy.javaflow.io.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for the default listenOnAvailablePort implementation in the FlowTransport interface.
 * Focuses on coverage of the default implementation methods.
 */
public class FlowTransportListenAvailablePortTest extends AbstractFlowTest {

  /**
   * A simple transport implementation that only handles the essential methods required for testing
   * the default listenOnAvailablePort method.
   */
  private static class TestFlowTransport implements FlowTransport {
    private FlowStream<FlowConnection> lastStream;
    private LocalEndpoint lastEndpoint;

    @Override
    public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
      return new FlowFuture<>(); // Not used in this test
    }

    @Override
    public FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint) {
      // Store the endpoint for later verification and return a new stream
      lastEndpoint = localEndpoint;
      PromiseStreamAdapter<FlowConnection> newStream = new PromiseStreamAdapter<>(new FlowConnection[0]);
      lastStream = newStream;
      return newStream;
    }

    @Override
    public FlowFuture<Void> close() {
      return new FlowFuture<>(); // Not used in this test
    }
    
    // Helper method to get the last endpoint used in listen()
    public LocalEndpoint getLastEndpoint() {
      return lastEndpoint;
    }
    
    // Helper method to get the last stream created in listen()
    public FlowStream<FlowConnection> getLastStream() {
      return lastStream;
    }
  }
  
  /**
   * Simple stream adapter to help test.
   */
  private static class PromiseStreamAdapter<T> implements FlowStream<T> {
    private final T[] items;
    private int index = 0;
    
    PromiseStreamAdapter(T[] items) {
      this.items = items;
    }

    @Override
    public FlowFuture<T> nextAsync() {
      return new FlowFuture<>();
    }

    @Override
    public FlowFuture<Boolean> hasNextAsync() {
      return new FlowFuture<>();
    }

    @Override
    public FlowFuture<Void> closeExceptionally(Throwable exception) {
      return new FlowFuture<>();
    }

    @Override
    public FlowFuture<Void> close() {
      return new FlowFuture<>();
    }

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public <R> FlowStream<R> map(java.util.function.Function<? super T, ? extends R> mapper) {
      return null;
    }

    @Override
    public FlowStream<T> filter(java.util.function.Predicate<? super T> predicate) {
      return null;
    }

    @Override
    public FlowFuture<Void> forEach(java.util.function.Consumer<? super T> action) {
      return new FlowFuture<>();
    }
  }

  @Test
  void testDefaultListenOnAvailablePort() {
    TestFlowTransport transport = new TestFlowTransport();
    
    // Call the default implementation with a specific endpoint
    LocalEndpoint testEndpoint = LocalEndpoint.localhost(8080);
    ConnectionListener listener = transport.listenOnAvailablePort(testEndpoint);
    
    // Verify the implementation passed the endpoint to listen() and created a ConnectionListener
    assertNotNull(listener);
    assertEquals(testEndpoint, transport.getLastEndpoint());
    assertEquals(transport.getLastStream(), listener.getStream());
    assertEquals(testEndpoint, listener.getBoundEndpoint());
  }
  
  @Test
  void testNoArgListenOnAvailablePort() {
    TestFlowTransport transport = new TestFlowTransport();
    
    // Call the no-arg version which should use localhost(0)
    ConnectionListener listener = transport.listenOnAvailablePort();
    
    // Verify the endpoint was localhost with port 0
    LocalEndpoint expectedEndpoint = LocalEndpoint.localhost(0);
    assertEquals(expectedEndpoint.getHost(), transport.getLastEndpoint().getHost());
    assertEquals(0, transport.getLastEndpoint().getPort());
    assertEquals(transport.getLastStream(), listener.getStream());
  }
}