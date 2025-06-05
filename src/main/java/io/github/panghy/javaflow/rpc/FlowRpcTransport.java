package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.io.network.Endpoint;

/**
 * The main entry point for RPC operations in the JavaFlow actor system.
 * This interface provides access to the endpoint resolver and RPC stub creation,
 * which manage service registration, resolution, and invocation.
 *
 * <p>FlowRpcTransport is the primary entry point for JavaFlow's RPC capabilities.
 * It builds on top of the lower-level {@link io.github.panghy.javaflow.io.network.FlowTransport}
 * to provide a higher-level RPC abstraction with location transparency.</p>
 *
 * <p>The RPC transport provides two types of service stubs:</p>
 * <ul>
 *   <li><b>RPC Stubs</b> - For remote services, handling network transport, serialization,
 *       and error handling transparently</li>
 *   <li><b>Local Stubs</b> - For local services, providing serialization for method
 *       parameters and return values but without network overhead</li>
 * </ul>
 *
 * <p>The interface exposes an {@link EndpointResolver} which provides:</p>
 * <ul>
 *   <li>Registration of service endpoints that can be called remotely</li>
 *   <li>Resolution of logical endpoint IDs to physical endpoints</li>
 *   <li>Retrieval of local service implementations</li>
 * </ul>
 *
 * <p>The endpoint resolution system supports three types of endpoints:</p>
 * <ul>
 *   <li>Loopback endpoints - Services that exist only in the local process with no network exposure</li>
 *   <li>Local endpoints - Services mounted on the local machine with network exposure</li>
 *   <li>Remote endpoints - Services located on other machines in the network</li>
 * </ul>
 *
 * <p>The endpoint resolver enables:</p>
 * <ul>
 *   <li>Mapping logical service IDs to physical network endpoints</li>
 *   <li>Registering multiple physical endpoints for a single service</li>
 *   <li>Automatic load balancing across multiple endpoints</li>
 *   <li>Targeting specific physical endpoints when needed</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Get the default transport instance and its endpoint resolver
 * FlowRpcTransport transport = FlowRpcTransport.getInstance();
 * EndpointResolver resolver = transport.getEndpointResolver();
 *
 * // Basic usage: register a loopback service endpoint
 * UserService userServiceImpl = new UserServiceImpl();
 * resolver.registerLoopbackEndpoint(
 *     new EndpointId("user-service"),
 *     userServiceImpl
 * );
 *
 * // Register a local service endpoint with network exposure
 * InventoryService inventoryServiceImpl = new InventoryServiceImpl();
 * resolver.registerLocalEndpoint(
 *     new EndpointId("inventory-service"),
 *     inventoryServiceImpl,
 *     new Endpoint("localhost", 8081)
 * );
 *
 * // Get a local stub for the user service
 * UserService userService = transport.getLocalStub(new EndpointId("user-service"), UserService.class);
 * // Use the service via the local stub (arguments are serialized but no network calls)
 * FlowFuture<UserInfo> userFuture = userService.getUserById(123);
 * UserInfo user = Flow.await(userFuture);
 *
 * // Register multiple physical endpoints for a service
 * EndpointId orderServiceId = new EndpointId("order-service");
 * resolver.registerRemoteEndpoints(orderServiceId, Arrays.asList(
 *     new Endpoint("order-host1", 9001),
 *     new Endpoint("order-host2", 9002),
 *     new Endpoint("order-host3", 9003)
 * ));
 *
 * // Get an RPC stub with default load balancing
 * OrderService orderService = transport.getRpcStub(orderServiceId, OrderService.class);
 *
 * // Get an RPC stub targeting a specific instance (by index)
 * OrderService specificOrderService = transport.getRpcStub(orderServiceId, OrderService.class, 1);
 *
 * // Get an RPC stub by directly specifying the endpoint
 * PaymentService paymentService = transport.getRpcStub(
 *     new Endpoint("payment-service.example.com", 8443),
 *     PaymentService.class
 * );
 * }</pre>
 *
 * @see EndpointId
 * @see EndpointResolver
 * @see io.github.panghy.javaflow.io.network.FlowTransport
 */
public interface FlowRpcTransport {

  /**
   * Gets the endpoint resolver associated with this transport.
   * The endpoint resolver provides methods for registering and resolving endpoints.
   *
   * @return The endpoint resolver
   */
  EndpointResolver getEndpointResolver();

  /**
   * Gets an RPC stub to a remote endpoint that implements the specified interface.
   * This method will use the default load balancing strategy (typically round-robin)
   * if multiple physical endpoints are registered for the endpoint ID.
   *
   * <p>The returned stub acts as a proxy to the remote service, handling the
   * serialization, transport, and deserialization of method calls and their results.</p>
   *
   * @param id             The ID of the remote endpoint
   * @param interfaceClass The interface class that the endpoint implements
   * @param <T>            The interface type
   * @return A stub that implements the specified interface
   */
  <T> T getRpcStub(EndpointId id, Class<T> interfaceClass);

  /**
   * Gets an RPC stub to a remote endpoint that targets a specific physical endpoint directly.
   * The returned stub will forward all method calls to the specified physical endpoint.
   *
   * <p>This method allows bypassing the endpoint resolver and connecting directly
   * to a known physical endpoint. It's useful when you need to explicitly target
   * a specific instance of a service rather than relying on the endpoint resolver's
   * load balancing or when the target endpoint is not registered with the resolver.</p>
   *
   * <p>The returned stub acts as a proxy to the remote service, handling the
   * serialization, transport, and deserialization of method calls and their results.</p>
   *
   * <p>Example usage:</p>
   * <pre>{@code
   * // Connect directly to a known endpoint
   * Endpoint serverEndpoint = new Endpoint("backend-server.example.com", 8080);
   * UserService userService = transport.getRpcStub(serverEndpoint, UserService.class);
   *
   * // Use the service via the stub
   * FlowFuture<User> userFuture = userService.getUserById(123);
   * User user = Flow.await(userFuture);
   * }</pre>
   *
   * @param endpoint       The physical endpoint to connect to
   * @param interfaceClass The interface class that the endpoint implements
   * @param <T>            The interface type
   * @return A stub that implements the specified interface
   * @throws IllegalArgumentException If the endpoint is invalid
   */
  <T> T getRpcStub(Endpoint endpoint, Class<T> interfaceClass);

  /**
   * Gets a local stub that implements the specified interface.
   * This method returns a proxy to the local implementation that performs serialization
   * of method arguments and return values (to prevent by-reference access and side effects),
   * but does not incur any network overhead since it executes locally.
   *
   * <p>Using a local stub instead of direct object references ensures consistency
   * between local and remote invocation semantics while still offering better
   * performance than remote calls.</p>
   *
   * <p>Example usage:</p>
   * <pre>{@code
   * // Get the endpoint ID for the service
   * EndpointId cacheServiceId = new EndpointId("cache-service");
   *
   * // Check if the service is available locally
   * if (transport.getEndpointResolver().isLocalEndpoint(cacheServiceId)) {
   *     // Get a local stub for the service
   *     CacheService cacheService = transport.getLocalStub(cacheServiceId, CacheService.class);
   *
   *     // Use the service via the local stub (arguments will be serialized but no network calls)
   *     FlowFuture<CachedValue> valueFuture = cacheService.get("user-profile-123");
   *     CachedValue value = Flow.await(valueFuture);
   * }
   * }</pre>
   *
   * @param id             The ID of the local endpoint
   * @param interfaceClass The interface class that the endpoint implements
   * @param <T>            The interface type
   * @return A local stub that implements the specified interface
   * @throws IllegalArgumentException If no local implementation is found for the endpoint
   * @throws ClassCastException       If the local implementation doesn't implement the requested interface
   */
  <T> T getLocalStub(EndpointId id, Class<T> interfaceClass);

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