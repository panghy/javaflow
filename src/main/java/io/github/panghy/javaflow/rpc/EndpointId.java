package io.github.panghy.javaflow.rpc;

import java.util.Objects;

/**
 * Represents a logical identifier for an RPC endpoint in the JavaFlow actor system.
 * An EndpointId uniquely identifies an RPC service endpoint and can be mapped to
 * a physical network location.
 *
 * <p>Unlike a network {@link io.github.panghy.javaflow.io.network.Endpoint}, which represents
 * a physical network address (host and port), an EndpointId can be location-independent,
 * allowing for flexibility in service discovery and routing.</p>
 *
 * <p>EndpointIds are used throughout the RPC framework to reference services endpoints
 * regardless of their physical location. This enables location transparency where
 * code written against an EndpointId works the same whether the endpoint is local
 * or remote.</p>
 *
 * <p>EndpointIds are immutable and can be safely shared between threads or actors.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create an endpoint ID for a service
 * EndpointId userServiceId = new EndpointId("user-service");
 *
 * // Register a service implementation with network exposure
 * FlowRpcTransport transport = FlowRpcTransport.getInstance();
 * transport.registerServiceAndListen(
 *     userServiceId,
 *     new UserServiceImpl(),
 *     UserService.class,
 *     new LocalEndpoint("localhost", 8080)
 * );
 *
 * // Get an RPC stub for the service (works for both local and remote)
 * UserService userService = transport.getRpcStub(
 *     userServiceId,
 *     UserService.class
 * );
 * }</pre>
 *
 * <p>The toString() method returns a string representation of the endpoint ID,
 * which is useful for logging and debugging.</p>
 */
public record EndpointId(String id) {
  /**
   * Creates a new endpoint ID with the specified identifier.
   *
   * @param id The unique identifier for this endpoint
   */
  public EndpointId(String id) {
    this.id = Objects.requireNonNull(id, "Endpoint ID cannot be null");
  }
}