package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.FlowTransport;
import io.github.panghy.javaflow.rpc.error.RpcConnectionException;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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

  // Base delay before retrying a connection (in seconds)
  private static final double BASE_RETRY_DELAY = 0.5;
  
  // Maximum delay before retrying a connection (in seconds)
  private static final double MAX_RETRY_DELAY = 30.0;
  
  // Maximum number of retry attempts before giving up
  private static final int MAX_RETRY_ATTEMPTS = 10;
  
  // The underlying network transport
  private final FlowTransport transport;
  
  // Maps endpoint IDs to their physical endpoints
  private final Map<EndpointId, Endpoint> endpointMapping;
  
  // Maps endpoint IDs to their active connections
  private final Map<EndpointId, FlowConnection> activeConnections = new ConcurrentHashMap<>();
  
  // Maps endpoint IDs to connection pools
  private final Map<EndpointId, Queue<FlowConnection>> connectionPools = new ConcurrentHashMap<>();
  
  // Maps endpoint IDs to connection promises (for in-progress connection attempts)
  private final Map<EndpointId, FlowPromise<FlowConnection>> pendingConnections = new ConcurrentHashMap<>();
  
  // Maps endpoint IDs to retry counts
  private final Map<EndpointId, Integer> retryCounters = new ConcurrentHashMap<>();
  
  // Closed state flag
  private final AtomicBoolean closed = new AtomicBoolean(false);
  
  /**
   * Creates a new ConnectionManager.
   *
   * @param transport       The network transport to use
   * @param endpointMapping The mapping of endpoint IDs to physical endpoints
   */
  public ConnectionManager(FlowTransport transport, Map<EndpointId, Endpoint> endpointMapping) {
    this.transport = transport;
    this.endpointMapping = endpointMapping;
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
    
    // Check if we already have an active connection
    FlowConnection existingConnection = activeConnections.get(endpointId);
    if (existingConnection != null) {
      return FlowFuture.completed(existingConnection);
    }
    
    // Check if we have a pending connection attempt
    FlowPromise<FlowConnection> pendingPromise = pendingConnections.get(endpointId);
    if (pendingPromise != null) {
      return pendingPromise.getFuture();
    }
    
    // Check if we have a connection in the pool
    Queue<FlowConnection> pool = connectionPools.computeIfAbsent(
        endpointId, k -> new ConcurrentLinkedQueue<>());
    FlowConnection pooledConnection = pool.poll();
    if (pooledConnection != null) {
      // TODO: Check if the pooled connection is still valid
      activeConnections.put(endpointId, pooledConnection);
      return FlowFuture.completed(pooledConnection);
    }
    
    // No existing connection, so establish a new one
    return establishConnection(endpointId, 0);
  }
  
  /**
   * Establishes a connection to an endpoint with retry.
   *
   * @param endpointId   The endpoint ID
   * @param retryAttempt The current retry attempt number
   * @return A future that completes with the connection
   */
  private FlowFuture<FlowConnection> establishConnection(EndpointId endpointId, int retryAttempt) {
    // Get the physical endpoint
    Endpoint physicalEndpoint = endpointMapping.get(endpointId);
    if (physicalEndpoint == null) {
      return FlowFuture.failed(
          new IllegalArgumentException("Unknown endpoint: " + endpointId));
    }
    
    // Create a future for the connection
    FlowFuture<FlowConnection> future = new FlowFuture<>();
    FlowPromise<FlowConnection> promise = future.getPromise();
    
    // Register the pending connection
    pendingConnections.put(endpointId, promise);
    
    // Connect to the endpoint
    transport.connect(physicalEndpoint)
        .whenComplete((connection, ex) -> {
          pendingConnections.remove(endpointId);
          
          if (ex != null) {
            // Connection failed, handle retry if appropriate
            handleConnectionFailure(endpointId, retryAttempt, promise, ex);
          } else {
            // Connection succeeded, set up monitoring and complete the promise
            activeConnections.put(endpointId, connection);
            retryCounters.put(endpointId, 0); // Reset retry counter on success
            monitorConnection(endpointId, connection);
            promise.complete(connection);
          }
        });
    
    return future;
  }
  
  /**
   * Handles a connection failure, possibly retrying the connection.
   *
   * @param endpointId   The endpoint ID
   * @param retryAttempt The current retry attempt number
   * @param promise      The promise to complete when connection is established
   * @param exception    The exception that caused the failure
   */
  private void handleConnectionFailure(EndpointId endpointId, int retryAttempt,
                                      FlowPromise<FlowConnection> promise, Throwable exception) {
    if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
      // Too many retries, give up
      promise.completeExceptionally(
          new RpcConnectionException(endpointId, 
              "Failed to connect to endpoint after " + retryAttempt + " attempts", exception));
      return;
    }
    
    // Calculate retry delay with exponential backoff
    double delay = Math.min(
        BASE_RETRY_DELAY * Math.pow(2, retryAttempt),
        MAX_RETRY_DELAY);
    
    // Retry after delay
    Flow.delay(delay).whenComplete((v, ex) -> {
      if (closed.get()) {
        promise.completeExceptionally(
            new IllegalStateException("ConnectionManager was closed during retry delay"));
        return;
      }
      
      // Try to establish the connection again
      establishConnection(endpointId, retryAttempt + 1)
          .whenComplete((connection, retryEx) -> {
            if (retryEx != null) {
              // Propagate the exception (this shouldn't normally happen as retries
              // are handled in establishConnection)
              promise.completeExceptionally(retryEx);
            } else {
              promise.complete(connection);
            }
          });
    });
  }
  
  /**
   * Monitors a connection for failures and handles reconnection when needed.
   *
   * @param endpointId The endpoint ID
   * @param connection The connection to monitor
   */
  private void monitorConnection(EndpointId endpointId, FlowConnection connection) {
    // Start an actor to monitor the connection
    Flow.startActor(() -> {
      try {
        // Wait for the connection to close or fail
        // (This future completes when the connection is closed)
        Flow.await(connection.close());
      } catch (Exception e) {
        // Connection failed unexpectedly
        System.err.println("Connection to " + endpointId + " failed: " + e.getMessage());
      } finally {
        // Remove the connection from active connections
        activeConnections.remove(endpointId, connection);
        
        // If not closed, attempt to reconnect
        if (!closed.get()) {
          reconnect(endpointId);
        }
      }
      return null;
    });
  }
  
  /**
   * Attempts to reconnect to an endpoint after a connection failure.
   *
   * @param endpointId The endpoint ID
   */
  private void reconnect(EndpointId endpointId) {
    // If the connection manager is closed, don't reconnect
    if (closed.get()) {
      return;
    }
    
    // Get the current retry count
    int retryCount = retryCounters.getOrDefault(endpointId, 0);
    
    // Increment the retry counter
    retryCounters.put(endpointId, retryCount + 1);
    
    // Calculate retry delay with exponential backoff
    double delay = Math.min(
        BASE_RETRY_DELAY * Math.pow(2, retryCount),
        MAX_RETRY_DELAY);
    
    // Retry after delay
    Flow.delay(delay).whenComplete((v, ex) -> {
      if (closed.get()) {
        return;
      }
      
      // Try to establish the connection again
      getConnection(endpointId)
          .whenComplete((connection, retryEx) -> {
            if (retryEx != null) {
              // Reconnection failed, but we don't need to do anything special here
              // as the getConnection method already handles retries
              System.err.println("Failed to reconnect to " + endpointId + ": " + retryEx.getMessage());
            } else {
              System.out.println("Successfully reconnected to " + endpointId);
            }
          });
    });
  }
  
  /**
   * Returns a connection to the pool when it's no longer needed.
   *
   * @param endpointId The endpoint ID
   * @param connection The connection to return to the pool
   */
  public void releaseConnection(EndpointId endpointId, FlowConnection connection) {
    if (closed.get()) {
      // If closed, just close the connection
      connection.close();
      return;
    }
    
    // Remove from active connections if it's the current active connection
    activeConnections.remove(endpointId, connection);
    
    // Add to the connection pool
    Queue<FlowConnection> pool = connectionPools.computeIfAbsent(
        endpointId, k -> new ConcurrentLinkedQueue<>());
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