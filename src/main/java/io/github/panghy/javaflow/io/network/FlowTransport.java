package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;

/**
 * Represents the transport layer for network communication in JavaFlow.
 * This interface provides methods for creating connections to endpoints
 * and listening for incoming connections.
 * 
 * <p>FlowTransport is the entry point for all network operations in JavaFlow.
 * It provides two primary capabilities:</p>
 * <ul>
 *   <li>Opening outbound connections to remote endpoints</li>
 *   <li>Listening for inbound connections on local endpoints</li>
 * </ul>
 * 
 * <p>The interface has two implementations:</p>
 * <ul>
 *   <li>RealFlowTransport - Uses Java NIO's AsynchronousChannelGroup for real I/O</li>
 *   <li>SimulatedFlowTransport - Uses in-memory simulation for deterministic testing</li>
 * </ul>
 * 
 * <p>The appropriate implementation is automatically selected based on
 * whether Flow is running in simulation mode or not. Use the static {@code getDefault()}
 * method to get the appropriate transport for the current mode.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Get the default transport for the current mode
 * FlowTransport transport = FlowTransport.getDefault();
 * 
 * // Open a connection to a remote endpoint
 * Endpoint remoteEndpoint = new Endpoint("example.com", 8080);
 * FlowConnection connection = Flow.await(transport.connect(remoteEndpoint));
 * 
 * // Listen for incoming connections
 * LocalEndpoint localEndpoint = LocalEndpoint.localhost(8080);
 * FlowStream<FlowConnection> connectionStream = transport.listen(localEndpoint);
 * 
 * // Accept connections in an actor
 * Flow.startActor(() -> {
 *   while (true) {
 *     FlowConnection incoming = Flow.await(connectionStream.nextAsync());
 *     // Handle the connection
 *   }
 *   return null;
 * });
 * 
 * // Close the transport when done
 * Flow.await(transport.close());
 * }</pre>
 * 
 * <p>FlowTransport implementations ensure that all network operations integrate
 * properly with Flow's cooperative multitasking model, using promises and futures
 * for asynchronous operations rather than blocking threads.</p>
 * 
 * @see FlowConnection
 * @see RealFlowTransport
 * @see SimulatedFlowTransport
 * @see Endpoint
 * @see LocalEndpoint
 */
public interface FlowTransport {

  /**
   * Opens a connection to the specified remote endpoint.
   *
   * @param endpoint The endpoint to connect to
   * @return A future that completes with the established connection
   */
  FlowFuture<FlowConnection> connect(Endpoint endpoint);

  /**
   * Starts listening for incoming connections on the specified local endpoint.
   *
   * @param localEndpoint The local endpoint to listen on
   * @return A stream of incoming connections
   */
  FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint);

  /**
   * Gets the default instance of FlowTransport.
   * In normal mode, this returns a real network transport implementation.
   * In simulation mode, this returns a simulated transport.
   *
   * @return The default FlowTransport instance
   */
  static FlowTransport getDefault() {
    // This will be implemented to return the appropriate transport based on mode
    return TransportProvider.getDefaultTransport();
  }

  /**
   * Closes this transport and all associated connections.
   *
   * @return A future that completes when the transport is closed
   */
  FlowFuture<Void> close();
}