package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;

/**
 * The main entry point for RPC operations in the JavaFlow actor system.
 * This interface provides methods for registering endpoints and obtaining
 * remote interfaces.
 * 
 * <p>FlowRpcTransport is the primary entry point for JavaFlow's RPC capabilities.
 * It builds on top of the lower-level {@link io.github.panghy.javaflow.io.network.FlowTransport}
 * to provide a higher-level RPC abstraction with location transparency.</p>
 * 
 * <p>The interface has two primary capabilities:</p>
 * <ul>
 *   <li>Registering service endpoints that can be called remotely</li>
 *   <li>Obtaining proxies to remote service endpoints</li>
 * </ul>
 * 
 * <p>The implementation will have two implementations:</p>
 * <ul>
 *   <li>RealFlowRpcTransport - Uses real network I/O for production use</li>
 *   <li>SimulatedFlowRpcTransport - Uses in-memory simulation for deterministic testing</li>
 * </ul>
 * 
 * <p>The appropriate implementation is automatically selected based on
 * whether Flow is running in simulation mode or not. Use the static {@code getInstance()}
 * method to get the appropriate transport for the current mode.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Register a service endpoint
 * UserServiceInterface userService = new UserServiceInterface();
 * FlowRpcTransport.getInstance().registerEndpoint(
 *     new EndpointId("user-service"), 
 *     userService
 * );
 * 
 * // Get a remote service proxy
 * UserServiceInterface remoteService = FlowRpcTransport.getInstance()
 *     .getEndpoint(new EndpointId("user-service"), UserServiceInterface.class);
 * 
 * // Use the remote service
 * FlowFuture<UserInfo> userFuture = remoteService.getUserAsync(new GetUserRequest(userId));
 * UserInfo user = Flow.await(userFuture);
 * }</pre>
 * 
 * @see EndpointId
 * @see io.github.panghy.javaflow.io.network.FlowTransport
 */
public interface FlowRpcTransport {

  /**
   * Registers an object as an endpoint that can be called remotely.
   * This makes the interface's methods available for remote invocation.
   *
   * @param id               The logical endpoint ID for this service
   * @param endpointInterface The object implementing the service interface
   */
  void registerEndpoint(EndpointId id, Object endpointInterface);

  /**
   * Obtains a proxy to a remote endpoint.
   * The returned proxy will forward all method calls to the remote endpoint.
   *
   * @param id            The ID of the remote endpoint
   * @param interfaceClass The interface class that the endpoint implements
   * @param <T>           The interface type
   * @return A proxy that implements the specified interface
   */
  <T> T getEndpoint(EndpointId id, Class<T> interfaceClass);

  /**
   * Closes this transport and all associated connections.
   *
   * @return A future that completes when the transport is closed
   */
  FlowFuture<Void> close();

  /**
   * Gets the default instance of FlowRpcTransport.
   * In normal mode, this returns a real network transport implementation.
   * In simulation mode, this returns a simulated transport.
   *
   * @return The default FlowRpcTransport instance
   */
  static FlowRpcTransport getInstance() {
    // Will be implemented to return the appropriate transport based on mode
    return FlowRpcProvider.getDefaultTransport();
  }
}