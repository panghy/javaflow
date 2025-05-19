package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowStream;

/**
 * Represents a listener for connections with information about the local endpoint.
 * This class wraps a FlowStream of FlowConnections along with the bound local endpoint,
 * which makes it easier to write tests that need to know the actual bound port.
 * 
 * <p>This approach eliminates the race condition that can occur when a test:
 * 1. Creates a temporary socket to find an available port
 * 2. Closes that socket
 * 3. Tries to bind to the same port later
 * 
 * <p>Instead, the transport implementation binds to an available port (port 0) and then
 * reports the actual bound port through this class.
 */
public class ConnectionListener {
  private final FlowStream<FlowConnection> stream;
  private final LocalEndpoint boundEndpoint;
  
  /**
   * Creates a new connection listener.
   *
   * @param stream        The stream of incoming connections
   * @param boundEndpoint The local endpoint that was bound
   */
  public ConnectionListener(FlowStream<FlowConnection> stream, LocalEndpoint boundEndpoint) {
    this.stream = stream;
    this.boundEndpoint = boundEndpoint;
  }
  
  /**
   * Gets the stream of incoming connections.
   *
   * @return The connection stream
   */
  public FlowStream<FlowConnection> getStream() {
    return stream;
  }
  
  /**
   * Gets the local endpoint that was bound.
   *
   * @return The bound local endpoint
   */
  public LocalEndpoint getBoundEndpoint() {
    return boundEndpoint;
  }
  
  /**
   * Gets the port that was bound.
   *
   * @return The bound port
   */
  public int getPort() {
    return boundEndpoint.getPort();
  }
}