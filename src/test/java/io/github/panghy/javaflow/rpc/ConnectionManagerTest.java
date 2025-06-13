package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.FlowTransport;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.error.RpcConnectionException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ConnectionManager}.
 */
public class ConnectionManagerTest extends AbstractFlowTest {

  private ConnectionManager manager;
  private SimulatedFlowTransport transport;
  private Map<EndpointId, Endpoint> endpointMapping;
  private MockEndpointResolver testResolver;

  /**
   * Mock implementation of EndpointResolver for testing.
   */
  private static class MockEndpointResolver implements EndpointResolver {
    private final Map<EndpointId, Endpoint> endpoints;

    MockEndpointResolver(Map<EndpointId, Endpoint> endpoints) {
      this.endpoints = endpoints;
    }

    @Override
    public void registerLoopbackEndpoint(EndpointId id, Object implementation) {
      // Not used in connection manager tests
    }

    @Override
    public void registerLocalEndpoint(EndpointId id, Object implementation, Endpoint physicalEndpoint) {
      endpoints.put(id, physicalEndpoint);
    }

    @Override
    public void registerRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint) {
      endpoints.put(id, physicalEndpoint);
    }

    @Override
    public void registerRemoteEndpoints(EndpointId id, List<Endpoint> physicalEndpoints) {
      if (!physicalEndpoints.isEmpty()) {
        endpoints.put(id, physicalEndpoints.get(0));
      }
    }

    @Override
    public boolean isLocalEndpoint(EndpointId id) {
      return false;
    }

    @Override
    public boolean isLoopbackEndpoint(EndpointId id) {
      return false;
    }

    @Override
    public Optional<Object> getLocalImplementation(EndpointId id) {
      return Optional.empty();
    }

    @Override
    public Optional<Endpoint> resolveEndpoint(EndpointId id) {
      return Optional.ofNullable(endpoints.get(id));
    }

    @Override
    public Optional<Endpoint> resolveEndpoint(EndpointId id, int index) {
      return resolveEndpoint(id);
    }

    @Override
    public List<Endpoint> getAllEndpoints(EndpointId id) {
      Endpoint endpoint = endpoints.get(id);
      return endpoint != null ? List.of(endpoint) : List.of();
    }

    @Override
    public boolean unregisterLocalEndpoint(EndpointId id) {
      return endpoints.remove(id) != null;
    }

    @Override
    public boolean unregisterRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint) {
      Endpoint current = endpoints.get(id);
      if (current != null && current.equals(physicalEndpoint)) {
        endpoints.remove(id);
        return true;
      }
      return false;
    }

    @Override
    public boolean unregisterAllEndpoints(EndpointId id) {
      return endpoints.remove(id) != null;
    }

    @Override
    public Set<EndpointId> findEndpointIds(Endpoint physicalEndpoint) {
      Set<EndpointId> result = new HashSet<>();
      for (Map.Entry<EndpointId, Endpoint> entry : endpoints.entrySet()) {
        if (entry.getValue().equals(physicalEndpoint)) {
          result.add(entry.getKey());
        }
      }
      return result;
    }
  }

  @Override
  protected void onSetUp() {
    transport = new SimulatedFlowTransport();
    endpointMapping = new HashMap<>();
    testResolver = new MockEndpointResolver(endpointMapping);
    manager = new ConnectionManager(transport, testResolver);
  }

  @Override
  protected void onTearDown() {
    if (manager != null) {
      manager.close();
    }
  }

  @Test
  public void testGetConnectionSuccess() throws Exception {
    // Setup endpoint mapping
    EndpointId endpointId = new EndpointId("test-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8080);
    endpointMapping.put(endpointId, endpoint);

    // Start server to accept connections
    FlowStream<FlowConnection> serverStream = transport.listen(endpoint);

    // Server starts accepting and client starts connecting in parallel
    FlowFuture<FlowConnection> serverConnectionFuture = serverStream.nextAsync();
    FlowFuture<FlowConnection> clientConnectionFuture = manager.getConnection(endpointId);

    // Wait for both to complete
    pumpAndAdvanceTimeUntilDone(serverConnectionFuture, clientConnectionFuture);

    FlowConnection connection = clientConnectionFuture.getNow();
    assertNotNull(connection);
    assertTrue(connection.isOpen());
  }

  @Test
  public void testGetConnectionWithUnknownEndpoint() {
    EndpointId unknownId = new EndpointId("unknown");

    FlowFuture<FlowConnection> future = manager.getConnection(unknownId);
    pumpAndAdvanceTimeUntilDone(future);

    Throwable error = future.getException();
    assertNotNull(error);
    assertInstanceOf(IllegalArgumentException.class, error);
    assertTrue(error.getMessage().contains("Unknown endpoint"));
  }

  @Test
  public void testGetConnectionReuseExisting() throws Exception {
    EndpointId endpointId = new EndpointId("reuse-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8081);
    endpointMapping.put(endpointId, endpoint);

    transport.listen(endpoint);

    // Get first connection
    FlowFuture<FlowConnection> future = manager.getConnection(endpointId);
    pumpAndAdvanceTimeUntilDone(future);
    FlowConnection conn1 = future.getNow();

    // Get second connection - should return the same one
    FlowFuture<FlowConnection> future2 = manager.getConnection(endpointId);
    pumpAndAdvanceTimeUntilDone(future2);
    FlowConnection conn2 = future2.getNow();

    // Should be the same connection
    assertSame(conn1, conn2);
  }

  @Test
  public void testConnectionFailureWithRetry() throws Exception {
    EndpointId endpointId = new EndpointId("retry-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8082);
    endpointMapping.put(endpointId, endpoint);

    // Don't start listening immediately to simulate initial failure
    AtomicInteger connectAttempts = new AtomicInteger(0);

    // Mock transport that fails first two attempts
    FlowTransport mockTransport = new FlowTransport() {
      private final SimulatedFlowTransport delegate = new SimulatedFlowTransport();

      @Override
      public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
        int attempt = connectAttempts.incrementAndGet();
        if (attempt <= 2) {
          return FlowFuture.failed(new RuntimeException("Connection failed"));
        }
        // Start listening on third attempt
        delegate.listen((LocalEndpoint) endpoint);
        return delegate.connect(endpoint);
      }

      @Override
      public FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint) {
        return delegate.listen(localEndpoint);
      }

      @Override
      public FlowFuture<Void> close() {
        return delegate.close();
      }
    };

    ConnectionManager retryManager = new ConnectionManager(mockTransport, testResolver);

    FlowFuture<FlowConnection> future = retryManager.getConnection(endpointId);
    pumpAndAdvanceTimeUntilDone(future);

    FlowConnection connection = future.getNow();
    assertNotNull(connection);
    assertEquals(3, connectAttempts.get()); // Should have tried 3 times

    retryManager.close();
  }

  @Test
  public void testMaxRetryAttempts() throws Exception {
    EndpointId endpointId = new EndpointId("max-retry-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8083);
    endpointMapping.put(endpointId, endpoint);

    FlowTransport failingTransport = new FlowTransport() {
      @Override
      public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
        return FlowFuture.failed(new RuntimeException("Connection always fails"));
      }

      @Override
      public FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint) {
        throw new UnsupportedOperationException("Not used in test");
      }

      @Override
      public FlowFuture<Void> close() {
        return FlowFuture.completed(null);
      }
    };

    // Create configuration with no connection timeout to allow all retry attempts
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .connectionTimeoutMs(0)  // Disable connection timeout
        .build();
    ConnectionManager failingManager = new ConnectionManager(failingTransport, testResolver, config);

    FlowFuture<FlowConnection> future = failingManager.getConnection(endpointId);
    pumpAndAdvanceTimeUntilDone(future);

    assertThat(future.getException()).isInstanceOf(RpcConnectionException.class)
        .hasMessageContaining("Failed to connect to endpoint");

    failingManager.close();
  }

  @Test
  public void testReleaseAndReuseConnection() throws Exception {
    EndpointId endpointId = new EndpointId("pool-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8084);
    endpointMapping.put(endpointId, endpoint);

    transport.listen(endpoint);

    // Get connection
    FlowFuture<FlowConnection> future = manager.getConnection(endpointId);
    pumpAndAdvanceTimeUntilDone(future);
    FlowConnection conn1 = future.getNow();

    // Release the connection
    manager.releaseConnection(endpoint, conn1);

    // Get connection again - should get pooled connection
    FlowFuture<FlowConnection> future2 = manager.getConnection(endpointId);

    pumpAndAdvanceTimeUntilDone(future2);
    FlowConnection conn2 = future2.getNow();

    // Should be the same connection from the pool
    assertSame(conn1, conn2);
  }

  @Test
  public void testCloseManager() throws Exception {
    EndpointId endpointId = new EndpointId("close-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8085);
    endpointMapping.put(endpointId, endpoint);

    transport.listen(endpoint);

    FlowFuture<FlowConnection> future = manager.getConnection(endpointId);

    pumpAndAdvanceTimeUntilDone(future);
    FlowConnection connection = future.getNow();
    assertTrue(connection.isOpen());

    // Close the manager
    FlowFuture<Void> closeFuture = manager.close();
    pumpAndAdvanceTimeUntilDone(closeFuture);

    // Connection should be closed
    assertFalse(connection.isOpen());

    // Try to get a connection after close
    future = manager.getConnection(endpointId);

    pumpAndAdvanceTimeUntilDone(future);
    Throwable error = future.getException();

    assertNotNull(error);
    assertInstanceOf(IllegalStateException.class, error);
    assertTrue(error.getMessage().contains("ConnectionManager is closed"));
  }

  @Test
  public void testConnectionMonitoringAndReconnect() throws Exception {
    EndpointId endpointId = new EndpointId("monitor-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8086);
    endpointMapping.put(endpointId, endpoint);

    transport.listen(endpoint);

    // Get initial connection
    FlowFuture<FlowConnection> future = manager.getConnection(endpointId);

    pumpAndAdvanceTimeUntilDone(future);
    FlowConnection conn1 = future.getNow();

    // Close the connection to simulate failure
    FlowFuture<Void> closeF = conn1.close();
    pumpAndAdvanceTimeUntilDone(closeF);

    // Allow time for monitoring to detect failure and reconnect
    pump();

    // Get connection again - should trigger new connection since old one failed
    FlowFuture<FlowConnection> future2 = manager.getConnection(endpointId);
    pumpAndAdvanceTimeUntilDone(future2);
    FlowConnection conn2 = future2.getNow();

    // Should be a different connection
    assertNotSame(conn1, conn2);
    assertTrue(conn2.isOpen());
  }

  @Test
  public void testPendingConnectionDeduplication() throws Exception {
    EndpointId endpointId = new EndpointId("pending-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8087);
    endpointMapping.put(endpointId, endpoint);

    // Use a blocking transport to control connection timing
    AtomicBoolean allowConnect = new AtomicBoolean(false);
    FlowTransport blockingTransport = new FlowTransport() {
      private final SimulatedFlowTransport delegate = new SimulatedFlowTransport();

      @Override
      public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
        delegate.listen((LocalEndpoint) endpoint);

        FlowFuture<FlowConnection> future = new FlowFuture<>();
        startActor(() -> {
          while (!allowConnect.get()) {
            await(Flow.delay(0.1));
          }
          FlowConnection conn = await(delegate.connect(endpoint));
          future.getPromise().complete(conn);
          return null;
        });
        return future;
      }

      @Override
      public FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint) {
        return delegate.listen(localEndpoint);
      }

      @Override
      public FlowFuture<Void> close() {
        return delegate.close();
      }
    };

    ConnectionManager blockingManager = new ConnectionManager(blockingTransport, testResolver);

    // Start two connection requests while blocked
    CompletableFuture<FlowConnection> future1 = new CompletableFuture<>();
    CompletableFuture<FlowConnection> future2 = new CompletableFuture<>();

    startActor(() -> {
      try {
        FlowConnection conn = await(blockingManager.getConnection(endpointId));
        future1.complete(conn);
      } catch (Exception e) {
        future1.completeExceptionally(e);
      }
      return null;
    });

    startActor(() -> {
      try {
        FlowConnection conn = await(blockingManager.getConnection(endpointId));
        future2.complete(conn);
      } catch (Exception e) {
        future2.completeExceptionally(e);
      }
      return null;
    });

    // Pump to let actors start
    testScheduler.pump();

    // Allow connection
    allowConnect.set(true);

    // Pump until both complete
    for (int i = 0; i < 20; i++) {
      testScheduler.pump();
      testScheduler.advanceTime(0.1);
    }

    FlowConnection conn1 = future1.get(5, TimeUnit.SECONDS);
    FlowConnection conn2 = future2.get(5, TimeUnit.SECONDS);

    // Should get the same connection (deduplication of pending connections)
    assertSame(conn1, conn2);

    blockingManager.close();
  }

  @Test
  public void testCloseWithPendingConnections() throws Exception {
    EndpointId endpointId = new EndpointId("close-pending-endpoint");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8088);
    endpointMapping.put(endpointId, endpoint);

    // Use a blocking transport that never completes
    FlowTransport blockingTransport = new FlowTransport() {
      @Override
      public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
        // Return a future that never completes
        return new FlowFuture<>();
      }

      @Override
      public FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint) {
        throw new UnsupportedOperationException("Not used in test");
      }

      @Override
      public FlowFuture<Void> close() {
        return FlowFuture.completed(null);
      }
    };

    ConnectionManager blockingManager = new ConnectionManager(blockingTransport, testResolver);

    // Start a connection request
    FlowFuture<FlowConnection> future1 = blockingManager.getConnection(endpointId);

    // Pump to let actor start
    testScheduler.pump();

    // Close the manager while connection is pending
    blockingManager.close();
    testScheduler.pump();

    Throwable error = future1.getException();
    assertNotNull(error);
    assertInstanceOf(IllegalStateException.class, error);
    assertTrue(error.getMessage().contains("ConnectionManager was closed"));
  }

  @Test
  public void testReleaseConnectionAfterClose() throws Exception {
    EndpointId endpointId = new EndpointId("release-after-close");
    LocalEndpoint endpoint = LocalEndpoint.localhost(8089);
    endpointMapping.put(endpointId, endpoint);

    transport.listen(endpoint);

    // Get a connection
    FlowFuture<FlowConnection> future = manager.getConnection(endpointId);

    pumpAndAdvanceTimeUntilDone(future);
    FlowConnection connection = future.getNow();
    assertTrue(connection.isOpen());

    // Close the manager
    manager.close();
    pumpAndAdvanceTimeUntilDone();

    // Try to release connection after close
    manager.releaseConnection(endpoint, connection);

    // Connection should be closed
    assertFalse(connection.isOpen());
  }

  @Test
  public void testMultipleCloseOperations() {
    // First close
    FlowFuture<Void> close1 = manager.close();
    pumpAndAdvanceTimeUntilDone();
    assertTrue(close1.isDone());

    // Second close - should complete immediately
    FlowFuture<Void> close2 = manager.close();
    assertTrue(close2.isDone());
  }
}