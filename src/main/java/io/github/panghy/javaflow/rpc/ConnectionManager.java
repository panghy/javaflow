package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.FlowTransport;
import io.github.panghy.javaflow.rpc.error.RpcConnectionException;
import io.github.panghy.javaflow.rpc.error.RpcTimeoutException;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.delay;
import static io.github.panghy.javaflow.Flow.startActor;
import static io.github.panghy.javaflow.util.LoggingUtil.warn;

/**
 * Manages connections to remote endpoints for the RPC framework.
 * This class handles establishing, monitoring, and re-establishing connections
 * when they are broken. It also provides connection pooling capabilities.
 *
 * <p>The ConnectionManager is responsible for:</p>
 * <ul>
 *   <li>Establishing connections to remote endpoints</li>
 *   <li>Monitoring connections for failures</li>
 *   <li>Automatically re-establishing broken connections</li>
 *   <li>Managing connection pools for endpoint reuse</li>
 *   <li>Throttling connection attempts to avoid excessive reconnection attempts</li>
 * </ul>
 *
 * <p>The manager uses a retry policy with exponential backoff for reconnection
 * attempts, to avoid overwhelming the network or remote endpoints with
 * reconnection attempts when they are unavailable.</p>
 */
public class ConnectionManager {

  private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());

  // Base delay before retrying a connection (in seconds)
  private static final double BASE_RETRY_DELAY = 0.5;

  // Maximum delay before retrying a connection (in seconds)
  private static final double MAX_RETRY_DELAY = 30.0;

  // Maximum number of retry attempts before giving up
  private static final int MAX_RETRY_ATTEMPTS = 10;

  // The underlying network transport
  private final FlowTransport transport;

  // The endpoint resolver for dynamic endpoint resolution
  private final EndpointResolver endpointResolver;

  // Configuration for the RPC transport
  private final FlowRpcConfiguration configuration;

  // Maps physical endpoints to their active connections
  private final Map<Endpoint, FlowConnection> activeConnections = new ConcurrentHashMap<>();

  // Maps physical endpoints to connection pools
  private final Map<Endpoint, Queue<FlowConnection>> connectionPools = new ConcurrentHashMap<>();

  // Maps physical endpoints to connection promises (for in-progress connection attempts)
  private final Map<Endpoint, FlowPromise<FlowConnection>> pendingConnections = new ConcurrentHashMap<>();

  // Maps physical endpoints to retry counts
  private final Map<Endpoint, Integer> retryCounters = new ConcurrentHashMap<>();

  // Closed state flag
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * Creates a new ConnectionManager.
   *
   * @param transport        The network transport to use
   * @param endpointResolver The endpoint resolver for dynamic endpoint resolution
   */
  public ConnectionManager(FlowTransport transport, EndpointResolver endpointResolver) {
    this(transport, endpointResolver, FlowRpcConfiguration.defaultConfig());
  }

  /**
   * Creates a new ConnectionManager with specific configuration.
   *
   * @param transport        The network transport to use
   * @param endpointResolver The endpoint resolver for dynamic endpoint resolution
   * @param configuration    The RPC configuration
   */
  public ConnectionManager(FlowTransport transport, EndpointResolver endpointResolver,
                           FlowRpcConfiguration configuration) {
    this.transport = transport;
    this.endpointResolver = endpointResolver;
    this.configuration = configuration;
  }

  /**
   * Gets or establishes a connection to an endpoint.
   *
   * @param endpointId The endpoint ID
   * @return A future that completes with the connection
   */
  public FlowFuture<FlowConnection> getConnection(EndpointId endpointId) {
    if (closed.get()) {
      return FlowFuture.failed(new IllegalStateException("ConnectionManager is closed"));
    }

    // Resolve the endpoint ID to a physical endpoint
    // This supports round-robin by potentially returning different endpoints
    Optional<Endpoint> endpointOpt = endpointResolver.resolveEndpoint(endpointId);
    if (endpointOpt.isEmpty()) {
      return FlowFuture.failed(new IllegalArgumentException("Unknown endpoint: " + endpointId));
    }

    return getConnectionToEndpoint(endpointOpt.get());
  }

  /**
   * Gets or establishes a connection to a specific physical endpoint.
   *
   * @param endpoint The physical endpoint
   * @return A future that completes with the connection
   */
  public FlowFuture<FlowConnection> getConnectionToEndpoint(Endpoint endpoint) {
    if (closed.get()) {
      return FlowFuture.failed(new IllegalStateException("ConnectionManager is closed"));
    }

    // Check if we already have an active connection
    FlowConnection existingConnection = activeConnections.get(endpoint);
    if (existingConnection != null && existingConnection.isOpen()) {
      return FlowFuture.completed(existingConnection);
    }

    // Check if we have a pending connection attempt
    FlowPromise<FlowConnection> pendingPromise = pendingConnections.get(endpoint);
    if (pendingPromise != null) {
      return pendingPromise.getFuture();
    }

    // Check if we have a connection in the pool
    Queue<FlowConnection> pool = connectionPools.computeIfAbsent(
        endpoint, k -> new ConcurrentLinkedQueue<>());
    FlowConnection pooledConnection = pool.poll();
    if (pooledConnection != null && pooledConnection.isOpen()) {
      activeConnections.put(endpoint, pooledConnection);
      return FlowFuture.completed(pooledConnection);
    }

    // No existing connection, so establish a new one
    return establishConnection(endpoint, 0);
  }

  /**
   * Establishes a connection to an endpoint with retry.
   *
   * @param endpoint     The physical endpoint
   * @param retryAttempt The current retry attempt number
   * @return A future that completes with the connection
   */
  private FlowFuture<FlowConnection> establishConnection(Endpoint endpoint, int retryAttempt) {
    // Create a future for the connection
    FlowFuture<FlowConnection> future = new FlowFuture<>();
    FlowPromise<FlowConnection> promise = future.getPromise();

    // Register the pending connection
    pendingConnections.put(endpoint, promise);

    // Start an actor to handle the connection establishment with timeout
    startActor(() -> {
      FlowFuture<FlowConnection> connectFuture = transport.connect(endpoint);

      // Only set up timeout if timeout is greater than 0
      if (configuration.getConnectionTimeoutMs() > 0) {
        // Create a timeout future (now inside an actor context)
        FlowFuture<Void> timeoutFuture = delay(configuration.getConnectionTimeoutMs() / 1000.0);

        // Race between connection and timeout
        startActor(() -> {
          await(timeoutFuture);
          if (!future.isDone()) {
            // Timeout occurred before connection was established
            pendingConnections.remove(endpoint);
            // Cancel the connection attempt
            connectFuture.cancel();
            promise.completeExceptionally(
                new RpcTimeoutException(RpcTimeoutException.TimeoutType.CONNECTION,
                    configuration.getConnectionTimeoutMs(),
                    "Connection to " + endpoint + " timed out after " +
                    configuration.getConnectionTimeoutMs() + "ms"));
          }
          return null;
        });
      }

      // Handle connection completion
      connectFuture.whenComplete((connection, ex) -> {
        // Execute completion within an actor to ensure proper Flow context
        startActor(() -> {
          pendingConnections.remove(endpoint);

          if (ex != null) {
            // Connection failed, handle retry if appropriate
            handleConnectionFailure(endpoint, retryAttempt, promise, ex);
          } else {
            // Connection succeeded, set up monitoring and complete the promise
            activeConnections.put(endpoint, connection);
            retryCounters.put(endpoint, 0); // Reset retry counter on success
            monitorConnection(endpoint, connection);
            promise.complete(connection);
          }
          return null;
        });
      });

      return null;
    });

    return future;
  }

  /**
   * Handles a connection failure, possibly retrying the connection.
   *
   * @param endpoint     The physical endpoint
   * @param retryAttempt The current retry attempt number
   * @param promise      The promise to complete when connection is established
   * @param exception    The exception that caused the failure
   */
  private void handleConnectionFailure(Endpoint endpoint, int retryAttempt,
                                       FlowPromise<FlowConnection> promise, Throwable exception) {
    if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
      // Too many retries, give up
      promise.completeExceptionally(
          new RpcConnectionException(null,
              "Failed to connect to endpoint " + endpoint + " after " + retryAttempt + " attempts", exception));
      return;
    }

    // Calculate retry delay with exponential backoff
    double retryDelay = Math.min(
        BASE_RETRY_DELAY * Math.pow(2, retryAttempt),
        MAX_RETRY_DELAY);

    // Start an actor to handle the retry after delay
    startActor(() -> {
      // Wait for the retry delay
      await(delay(retryDelay));
      
      if (closed.get()) {
        promise.completeExceptionally(
            new IllegalStateException("ConnectionManager was closed during retry delay"));
        return null;
      }

      // Try to establish the connection again
      establishConnection(endpoint, retryAttempt + 1)
          .whenComplete((connection, retryEx) -> {
            if (retryEx != null) {
              // Propagate the exception
              promise.completeExceptionally(retryEx);
            } else {
              promise.complete(connection);
            }
          });
      return null;
    });
  }

  /**
   * Monitors a connection for failures and handles reconnection when needed.
   *
   * @param endpoint   The physical endpoint
   * @param connection The connection to monitor
   */
  private void monitorConnection(Endpoint endpoint, FlowConnection connection) {
    // Start an actor to monitor the connection
    startActor(() -> {
      try {
        // Wait for the connection to close or fail
        // (This future completes when the connection is closed)
        await(connection.closeFuture());
      } catch (Exception e) {
        warn(LOGGER, "Error monitoring connection to " + endpoint, e);
      } finally {
        // Remove the connection from active connections
        activeConnections.remove(endpoint, connection);

        // If not closed, we don't automatically reconnect
        // The next request will establish a new connection if needed
      }
      return null;
    });
  }


  /**
   * Returns a connection to the pool when it's no longer needed.
   * <p>
   * TODO: Connection pooling with dynamic endpoint resolution requires tracking
   * which EndpointId was used to obtain a connection to a specific Endpoint.
   * For now, connections are not pooled when using round-robin resolution.
   *
   * @param endpoint   The physical endpoint
   * @param connection The connection to return to the pool
   */
  public void releaseConnection(Endpoint endpoint, FlowConnection connection) {
    if (closed.get()) {
      // If closed, just close the connection
      connection.close();
      return;
    }

    // Remove from active connections if it's the current active connection
    activeConnections.remove(endpoint, connection);

    // Add to the connection pool
    Queue<FlowConnection> pool = connectionPools.computeIfAbsent(
        endpoint, k -> new ConcurrentLinkedQueue<>());
    pool.add(connection);
  }

  /**
   * Closes the connection manager and all managed connections.
   *
   * @return A future that completes when the manager is closed
   */
  public FlowFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      // Create a list of futures for all connections being closed
      FlowFuture<Void> closeFuture = new FlowFuture<>();
      FlowPromise<Void> closePromise = closeFuture.getPromise();

      try {
        // Close all active connections
        for (FlowConnection connection : activeConnections.values()) {
          connection.close();
        }
        activeConnections.clear();

        // Close all pooled connections
        for (Queue<FlowConnection> pool : connectionPools.values()) {
          for (FlowConnection connection : pool) {
            connection.close();
          }
        }
        connectionPools.clear();

        // Complete all pending connection promises with an exception
        for (FlowPromise<FlowConnection> promise : pendingConnections.values()) {
          promise.completeExceptionally(
              new IllegalStateException("ConnectionManager was closed"));
        }
        pendingConnections.clear();

        // Clear other state
        retryCounters.clear();

        // Complete the close promise
        closePromise.complete(null);
      } catch (Exception e) {
        closePromise.completeExceptionally(e);
      }

      return closeFuture;
    }

    // Already closed
    return FlowFuture.completed(null);
  }
}