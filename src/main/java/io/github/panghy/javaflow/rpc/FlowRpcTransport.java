package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.io.network.Endpoint;

import java.util.List;

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
 * <p>The implementation supports three types of endpoints:</p>
 * <ul>
 *   <li>Loopback endpoints - Services that exist only in the local process with no network exposure</li>
 *   <li>Local endpoints - Services mounted on the local machine with network exposure</li>
 *   <li>Remote endpoints - Services located on other machines in the network</li>
 * </ul>
 *
 * <p>The endpoint resolution system allows:</p>
 * <ul>
 *   <li>Mapping logical service IDs to physical network endpoints</li>
 *   <li>Registering multiple physical endpoints for a single service</li>
 *   <li>Automatic load balancing across multiple endpoints</li>
 *   <li>Targeting specific physical endpoints when needed</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Basic usage: register a loopback service endpoint
 * UserServiceInterface userService = new UserServiceInterface();
 * FlowRpcTransport.getInstance().registerLoopbackEndpoint(
 *     new EndpointId("user-service"),
 *     userService
 * );
 *
 * // Register a local service endpoint with network exposure
 * InventoryServiceInterface inventoryService = new InventoryServiceInterface();
 * FlowRpcTransport.getInstance().registerLocalEndpoint(
 *     new EndpointId("inventory-service"),
 *     inventoryService,
 *     new Endpoint("localhost", 8081)
 * );
 *
 * // Get a local service instance directly (no proxy)
 * UserServiceInterface localService = FlowRpcTransport.getInstance()
 *     .getLocalEndpoint(new EndpointId("user-service"), UserServiceInterface.class);
 *
 * // Use the service
 * FlowFuture<UserInfo> userFuture = localService.getUserAsync(new GetUserRequest(userId));
 * UserInfo user = Flow.await(userFuture);
 *
 * // Register multiple physical endpoints for a service
 * EndpointId serviceId = new EndpointId("distributed-service");
 * FlowRpcTransport.getInstance().registerRemoteEndpoints(serviceId, Arrays.asList(
 *     new Endpoint("host1", 9001),
 *     new Endpoint("host2", 9002),
 *     new Endpoint("host3", 9003)
 * ));
 *
 * // Get a proxy with round-robin load balancing
 * ServiceInterface loadBalancedService = FlowRpcTransport.getInstance()
 *     .getRoundRobinEndpoint(serviceId, ServiceInterface.class);
 *
 * // Target a specific backend instance by physical endpoint
 * Endpoint specificHost = new Endpoint("host2", 9002);
 * ServiceInterface specificBackend = FlowRpcTransport.getInstance()
 *     .getSpecificEndpoint(serviceId, ServiceInterface.class, specificHost);
 * 
 * // Get all available endpoints for a service
 * List<Endpoint> availableEndpoints = FlowRpcTransport.getInstance()
 *     .getRemoteEndpoints(serviceId);
 * }</pre>
 *
 * @see EndpointId
 * @see io.github.panghy.javaflow.io.network.FlowTransport
 */
public interface FlowRpcTransport {

  /**
   * Registers a loopback endpoint, which exists only in the local process with no network exposure.
   * This is the most efficient type of endpoint for local-only communication as it
   * bypasses all network serialization and transport overhead.
   *
   * @param id             The logical endpoint ID
   * @param implementation The implementation object
   */
  void registerLoopbackEndpoint(EndpointId id, Object implementation);

  /**
   * Registers a local service endpoint with a specific physical endpoint.
   * This makes the interface's methods available for remote invocation and
   * allows the caller to specify exactly where the endpoint is located.
   * This method also mounts the service with the network layer, starting
   * to listen for connections on the specified physical endpoint.
   *
   * @param id               The logical endpoint ID
   * @param implementation   The implementation object
   * @param physicalEndpoint The physical network endpoint where this service is hosted
   */
  void registerLocalEndpoint(EndpointId id, Object implementation, Endpoint physicalEndpoint);

  /**
   * Registers a remote endpoint, making it available for connection.
   *
   * @param id               The logical endpoint ID
   * @param physicalEndpoint The physical network endpoint where the service is hosted
   */
  void registerRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint);

  /**
   * Registers multiple physical endpoints for a remote service, enabling load balancing.
   *
   * @param id                The logical endpoint ID
   * @param physicalEndpoints The list of physical network endpoints where the service is hosted
   */
  void registerRemoteEndpoints(EndpointId id, List<Endpoint> physicalEndpoints);


  /**
   * Gets a reference to a local endpoint directly, with no RPC proxy.
   * This method returns the actual implementation object if it's available locally,
   * allowing direct method calls with no serialization or network overhead.
   *
   * @param id             The ID of the local endpoint
   * @param interfaceClass The interface class that the endpoint implements
   * @param <T>            The interface type
   * @return The local implementation, or null if not found locally
   * @throws ClassCastException If the local implementation doesn't implement the requested interface
   */
  <T> T getLocalEndpoint(EndpointId id, Class<T> interfaceClass);

  /**
   * Gets a proxy to a remote endpoint using round-robin load balancing.
   * The returned proxy will forward method calls to available physical endpoints using
   * a round-robin strategy if multiple physical endpoints are registered.
   *
   * @param id             The ID of the remote endpoint
   * @param interfaceClass The interface class that the endpoint implements
   * @param <T>            The interface type
   * @return A proxy that implements the specified interface
   */
  <T> T getRoundRobinEndpoint(EndpointId id, Class<T> interfaceClass);

  /**
   * Gets a proxy to a remote endpoint that targets a specific physical endpoint.
   * The returned proxy will forward all method calls to the specific physical endpoint instance.
   *
   * @param id               The ID of the remote endpoint
   * @param interfaceClass   The interface class that the endpoint implements
   * @param physicalEndpoint The specific physical endpoint to target
   * @param <T>              The interface type
   * @return A proxy that implements the specified interface
   * @throws IllegalArgumentException If the endpoint is invalid or not registered for this endpoint ID
   */
  <T> T getSpecificEndpoint(EndpointId id, Class<T> interfaceClass, Endpoint physicalEndpoint);

  /**
   * Gets all physical endpoints registered for a specific logical endpoint ID.
   * This can be used to discover available instances of a service.
   *
   * @param id The logical endpoint ID
   * @return A list of all physical endpoints registered for this ID, or an empty list if none are found
   */
  List<Endpoint> getRemoteEndpoints(EndpointId id);

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