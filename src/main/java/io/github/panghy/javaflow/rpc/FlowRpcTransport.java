package io.github.panghy.javaflow.rpc;
import java.util.concurrent.CompletableFuture;

import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.LocalEndpoint;

/**
 * The main entry point for RPC operations in the JavaFlow actor system.
 * This interface provides access to the endpoint resolver and RPC stub creation,
 * which manage service registration, resolution, and invocation.
 *
 * <p>FlowRpcTransport is the primary entry point for JavaFlow's RPC capabilities.
 * It builds on top of the lower-level {@link io.github.panghy.javaflow.io.network.FlowTransport}
 * to provide a higher-level RPC abstraction with location transparency.</p>
 *
 * <p>The RPC transport provides service stubs for invoking services, handling network
 * transport, serialization, and error handling transparently. When a service is registered
 * locally, the transport automatically optimizes calls to avoid network overhead.</p>
 *
 * <p>The interface exposes an {@link EndpointResolver} which provides:</p>
 * <ul>
 *   <li>Registration of service endpoints that can be called remotely</li>
 *   <li>Resolution of logical endpoint IDs to physical endpoints</li>
 *   <li>Retrieval of local service implementations</li>
 * </ul>
 *
 * <p>The endpoint resolution system supports two types of endpoints:</p>
 * <ul>
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
 * // Get the default transport instance
 * FlowRpcTransport transport = FlowRpcTransport.getInstance();
 *
 * // Register a local service endpoint with network exposure
 * UserService userServiceImpl = new UserServiceImpl();
 * transport.registerServiceAndListen(
 *     new EndpointId("user-service"),
 *     userServiceImpl,
 *     UserService.class,
 *     new LocalEndpoint(8080)
 * );
 *
 * // Register another local service endpoint
 * InventoryService inventoryServiceImpl = new InventoryServiceImpl();
 * transport.registerServiceAndListen(
 *     new EndpointId("inventory-service"),
 *     inventoryServiceImpl,
 *     InventoryService.class,
 *     new LocalEndpoint(8081)
 * );
 *
 * // Get an RPC stub for a service (automatically uses local optimization if available)
 * UserService userService = transport.getRpcStub(new EndpointId("user-service"), UserService.class);
 * CompletableFuture<UserInfo> userFuture = userService.getUserById(123);
 * UserInfo user = Flow.await(userFuture);
 *
 * // Register multiple physical endpoints for a remote service
 * EndpointId orderServiceId = new EndpointId("order-service");
 * transport.getEndpointResolver().registerRemoteEndpoints(orderServiceId, Arrays.asList(
 *     new Endpoint("order-host1", 9001),
 *     new Endpoint("order-host2", 9002),
 *     new Endpoint("order-host3", 9003)
 * ));
 *
 * // Get an RPC stub with default load balancing
 * OrderService orderService = transport.getRpcStub(orderServiceId, OrderService.class);
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
   * CompletableFuture<User> userFuture = userService.getUserById(123);
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
   * Registers a service implementation and starts listening on the specified local endpoint.
   * This method automatically registers the service in the EndpointResolver for local invocation
   * and starts accepting incoming connections on the provided endpoint.
   * 
   * <p>Multiple services can be registered on the same LocalEndpoint, and calls will be
   * routed to the appropriate service implementation based on the method signature.</p>
   * 
   * <p>The service is automatically registered in the EndpointResolver, making it available
   * for both local and remote invocations. Local invocations will be optimized to avoid
   * network overhead.</p>
   *
   * @param endpointId     The logical endpoint ID for this service
   * @param implementation The service implementation
   * @param interfaceClass The interface class that the service implements
   * @param localEndpoint  The local endpoint to listen on
   * @throws IllegalStateException If the endpointId is already registered with a different implementation
   */
  void registerServiceAndListen(EndpointId endpointId,
                                Object implementation,
                                Class<?> interfaceClass,
                                LocalEndpoint localEndpoint);

  /**
   * Closes this transport and all associated connections.
   *
   * @return A future that completes when the transport is closed
   */
  CompletableFuture<Void> close();

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