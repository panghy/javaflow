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
 * // Register a service interface with this ID
 * userService.registerRemote(userServiceId);
 * 
 * // Get access to a remote service
 * UserServiceInterface remoteService = FlowTransport.getInstance()
 *     .getEndpoint(userServiceId, UserServiceInterface.class);
 * }</pre>
 * 
 * <p>The toString() method returns a string representation of the endpoint ID,
 * which is useful for logging and debugging.</p>
 */
public class EndpointId {
  private final String id;

  /**
   * Creates a new endpoint ID with the specified identifier.
   *
   * @param id The unique identifier for this endpoint
   */
  public EndpointId(String id) {
    this.id = Objects.requireNonNull(id, "Endpoint ID cannot be null");
  }

  /**
   * Gets the string identifier of this endpoint.
   *
   * @return The endpoint ID string
   */
  public String getId() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EndpointId endpointId = (EndpointId) o;
    return id.equals(endpointId.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "EndpointId[" + id + "]";
  }
}