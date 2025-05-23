package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.io.network.Endpoint;

/**
 * Base interface for RPC service interfaces in the JavaFlow RPC framework.
 * All service interfaces should extend this interface to provide common
 * registration and lifecycle methods.
 *
 * <p>The RPC framework uses interfaces containing PromiseStreams for each
 * request type, following the design pattern outlined in the RPC design
 * document. This base interface provides common methods for all service
 * interfaces.</p>
 *
 * <p>A typical service interface looks like:</p>
 * <pre>{@code
 * public class UserServiceInterface extends RpcServiceInterface {
 *     // One-shot request with reply
 *     public final PromiseStream<Pair<GetUserRequest, FlowPromise<UserInfo>>> getUser;
 *
 *     // One-way notification
 *     public final PromiseStream<UserStatusUpdate> statusUpdate;
 *
 *     // Stream of data
 *     public final PromiseStream<Pair<ListUsersRequest, PromiseStream<UserInfo>>> listUsers;
 *
 *     public UserServiceInterface() {
 *         // Initialize all promise streams
 *         this.getUser = new PromiseStream<>();
 *         this.statusUpdate = new PromiseStream<>();
 *         this.listUsers = new PromiseStream<>();
 *     }
 *
 *     // Convenience methods for common patterns
 *     public FlowFuture<UserInfo> getUserAsync(GetUserRequest request) {
 *         FlowFuture<UserInfo> future = new FlowFuture<>();
 *         getUser.send(new Pair<>(request, future.getPromise()));
 *         return future;
 *     }
 * }
 * }</pre>
 */
public interface RpcServiceInterface {

  /**
   * Registers this interface as a loopback endpoint with the RPC transport.
   * This makes the interface's methods available for in-process invocation only.
   *
   * @param endpointId The endpoint ID to register with
   */
  default void registerAsLoopback(EndpointId endpointId) {
    FlowRpcTransport.getInstance().getEndpointResolver().registerLoopbackEndpoint(endpointId, this);
  }

  /**
   * Registers this interface as a local endpoint with the RPC transport.
   * This makes the interface's methods available for both local and remote invocation.
   * The service is mounted with the network layer to accept connections on the specified endpoint.
   *
   * @param endpointId       The endpoint ID to register with
   * @param physicalEndpoint The physical network endpoint to use
   */
  default void registerAsLocal(EndpointId endpointId, Endpoint physicalEndpoint) {
    FlowRpcTransport.getInstance().getEndpointResolver().registerLocalEndpoint(endpointId, this, physicalEndpoint);
  }

  /**
   * Gets a future that completes when this service is registered and ready.
   * This can be used to wait for the service to be fully initialized before use.
   *
   * @return A future that completes when the service is ready
   */
  default FlowFuture<Void> ready() {
    // By default, services are ready immediately
    return FlowFuture.completed(null);
  }

  /**
   * Gets a future that completes when this service is shut down.
   * This can be used to wait for the service to complete any pending operations.
   *
   * @return A future that completes when the service is shut down
   */
  default FlowFuture<Void> onClose() {
    // By default, create a new future that will be completed when the service is closed
    FlowFuture<Void> future = new FlowFuture<>();
    return future;
  }
}