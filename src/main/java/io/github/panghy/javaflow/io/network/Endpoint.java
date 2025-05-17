package io.github.panghy.javaflow.io.network;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Represents a network endpoint (address) in the JavaFlow actor system.
 * An endpoint is a combination of host and port that identifies a network location.
 * 
 * <p>Endpoints are used throughout the JavaFlow network API to represent both
 * remote and local network addresses. They provide a higher-level abstraction
 * over Java's InetSocketAddress, with convenient methods for creating common
 * endpoint types.</p>
 * 
 * <p>Endpoints are immutable and can be safely shared between threads or actors.
 * They provide methods for conversion to and from InetSocketAddress for
 * compatibility with Java's standard network APIs.</p>
 * 
 * <p>For local binding purposes, use the {@link LocalEndpoint} subclass, which
 * provides additional factory methods for common local configurations.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create an endpoint for a remote server
 * Endpoint serverEndpoint = new Endpoint("example.com", 8080);
 * 
 * // Connect to the endpoint
 * FlowConnection connection = Flow.await(
 *     FlowTransport.getDefault().connect(serverEndpoint));
 * 
 * // Get endpoint information from a connection
 * Endpoint localEnd = connection.getLocalEndpoint();
 * Endpoint remoteEnd = connection.getRemoteEndpoint();
 * }</pre>
 * 
 * <p>The toString() method returns the endpoint in the standard "host:port" format,
 * which is useful for logging and debugging.</p>
 * 
 * @see LocalEndpoint
 * @see FlowTransport
 * @see FlowConnection
 */
public class Endpoint {
  private final String host;
  private final int port;

  /**
   * Creates a new endpoint with the specified host and port.
   *
   * @param host The hostname or IP address
   * @param port The port number
   */
  public Endpoint(String host, int port) {
    this.host = Objects.requireNonNull(host, "Host cannot be null");
    this.port = port;
    
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("Port must be between 0 and 65535");
    }
  }

  /**
   * Creates a new endpoint from an InetSocketAddress.
   *
   * @param address The socket address
   */
  public Endpoint(InetSocketAddress address) {
    this(address.getHostString(), address.getPort());
  }

  /**
   * Gets the host of this endpoint.
   *
   * @return The host
   */
  public String getHost() {
    return host;
  }

  /**
   * Gets the port of this endpoint.
   *
   * @return The port
   */
  public int getPort() {
    return port;
  }

  /**
   * Converts this endpoint to an InetSocketAddress.
   *
   * @return The socket address
   */
  public InetSocketAddress toInetSocketAddress() {
    return new InetSocketAddress(host, port);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } 
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Endpoint endpoint = (Endpoint) o;
    return port == endpoint.port && host.equals(endpoint.host);
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, port);
  }

  @Override
  public String toString() {
    return host + ":" + port;
  }
}