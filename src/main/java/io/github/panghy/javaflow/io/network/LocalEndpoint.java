package io.github.panghy.javaflow.io.network;

import java.net.InetSocketAddress;

/**
 * Represents a local network endpoint in the JavaFlow actor system.
 * A local endpoint is an endpoint that is bound to the local machine
 * and can be used for listening for incoming connections.
 * 
 * <p>LocalEndpoint extends Endpoint with functionality specific to binding
 * network servers on the local machine. It provides factory methods for creating
 * common local network configurations, such as binding to all interfaces or
 * just to localhost.</p>
 * 
 * <p>This class is used with {@link FlowTransport#listen(LocalEndpoint)} to
 * start listening for incoming connections on a specific local network interface
 * and port.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create a local endpoint that binds to all interfaces on port 8080
 * LocalEndpoint anyEndpoint = LocalEndpoint.anyHost(8080);
 * 
 * // Create a local endpoint that binds only to localhost on port 8080
 * LocalEndpoint localhostEndpoint = LocalEndpoint.localhost(8080);
 * 
 * // Start listening for connections
 * FlowStream<FlowConnection> connections = 
 *     FlowTransport.getDefault().listen(localhostEndpoint);
 * 
 * // Accept connections in an actor
 * Flow.startActor(() -> {
 *   while (true) {
 *     FlowConnection connection = Flow.await(connections.nextAsync());
 *     // Handle the connection
 *   }
 *   return null;
 * });
 * }</pre>
 * 
 * <p>The factory methods provide convenient shortcuts for the most common
 * use cases. For more specific binding requirements, use the constructor.</p>
 * 
 * @see Endpoint
 * @see FlowTransport
 */
public class LocalEndpoint extends Endpoint {

  /**
   * Creates a new local endpoint with the specified host and port.
   *
   * @param host The hostname or IP address to bind to
   * @param port The port number to bind to
   */
  public LocalEndpoint(String host, int port) {
    super(host, port);
  }

  /**
   * Creates a new local endpoint from an InetSocketAddress.
   *
   * @param address The socket address to bind to
   */
  public LocalEndpoint(InetSocketAddress address) {
    super(address);
  }

  /**
   * Creates a local endpoint that binds to all interfaces on the specified port.
   *
   * @param port The port number to bind to
   * @return A local endpoint bound to all interfaces
   */
  public static LocalEndpoint anyHost(int port) {
    return new LocalEndpoint("0.0.0.0", port);
  }

  /**
   * Creates a local endpoint that binds to localhost on the specified port.
   *
   * @param port The port number to bind to
   * @return A local endpoint bound to localhost
   */
  public static LocalEndpoint localhost(int port) {
    return new LocalEndpoint("localhost", port);
  }
  
  /**
   * Creates a local endpoint with the specified host and port.
   * This is a factory method alternative to using the constructor.
   *
   * @param host The hostname or IP address to bind to
   * @param port The port number to bind to
   * @return A local endpoint with the specified host and port
   */
  public static LocalEndpoint create(String host, int port) {
    return new LocalEndpoint(host, port);
  }
}