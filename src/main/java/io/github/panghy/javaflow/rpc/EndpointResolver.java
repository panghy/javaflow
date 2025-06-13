package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.Endpoint;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves logical endpoint identifiers to physical network endpoints.
 * This interface provides a way to map between {@link EndpointId} objects
 * (which represent logical service identifiers) and physical {@link Endpoint}
 * objects (which represent actual network locations).
 * 
 * <p>The EndpointResolver is used by the RPC transport to determine where to send
 * messages for a given endpoint. It supports two types of endpoints:</p>
 * <ul>
 *   <li>Local endpoints - Services mounted on the local machine with network exposure</li>
 *   <li>Remote endpoints - Services located on other machines in the network</li>
 * </ul>
 * 
 * <p>Implementations may use different strategies to resolve endpoints, such as:</p>
 * <ul>
 *   <li>Static configuration (e.g., from a properties file)</li>
 *   <li>Dynamic service discovery (e.g., using a registry service)</li>
 *   <li>DNS-based resolution</li>
 *   <li>Manual registration by the application</li>
 * </ul>
 * 
 * <p>The resolver also supports multiple physical endpoints for a single logical
 * endpoint, enabling load balancing strategies like round-robin.</p>
 */
public interface EndpointResolver {



  /**
   * Registers a local endpoint, making it available for other nodes to connect to.
   * A local endpoint can be invoked locally without network communication, but is also
   * exposed on the network at the specified physical endpoint.
   *
   * @param id The logical endpoint ID
   * @param implementation The implementation object
   * @param physicalEndpoint The physical network endpoint where this service is hosted
   */
  void registerLocalEndpoint(EndpointId id, Object implementation, Endpoint physicalEndpoint);

  /**
   * Registers a remote endpoint, making it available for this node to connect to.
   *
   * @param id The logical endpoint ID
   * @param physicalEndpoint The physical network endpoint where the service is hosted
   */
  void registerRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint);

  /**
   * Registers multiple physical endpoints for a remote service, enabling load balancing.
   *
   * @param id The logical endpoint ID
   * @param physicalEndpoints The list of physical network endpoints where the service is hosted
   */
  void registerRemoteEndpoints(EndpointId id, List<Endpoint> physicalEndpoints);

  /**
   * Checks if an endpoint is registered locally.
   *
   * @param id The logical endpoint ID
   * @return true if the endpoint is registered locally, false otherwise
   */
  boolean isLocalEndpoint(EndpointId id);

  /**
   * Gets the local implementation of an endpoint, if it exists.
   *
   * @param id The logical endpoint ID
   * @return The implementation object, or empty if not registered locally
   */
  Optional<Object> getLocalImplementation(EndpointId id);

  /**
   * Retrieves the next physical endpoint to use for the given logical endpoint ID.
   * If multiple physical endpoints are registered, this method will use a load balancing
   * strategy (typically round-robin) to select one.
   *
   * @param id The logical endpoint ID
   * @return The physical endpoint, or empty if not found
   */
  Optional<Endpoint> resolveEndpoint(EndpointId id);

  /**
   * Retrieves a specific physical endpoint for the given logical endpoint ID.
   * This allows targeting a specific instance instead of using load balancing.
   *
   * @param id The logical endpoint ID
   * @param index The index of the physical endpoint to retrieve
   * @return The physical endpoint, or empty if not found or index out of range
   */
  Optional<Endpoint> resolveEndpoint(EndpointId id, int index);

  /**
   * Gets all physical endpoints registered for a logical endpoint ID.
   *
   * @param id The logical endpoint ID
   * @return The list of physical endpoints, or an empty list if none are registered
   */
  List<Endpoint> getAllEndpoints(EndpointId id);

  /**
   * Unregisters a local endpoint.
   *
   * @param id The logical endpoint ID
   * @return true if the endpoint was unregistered, false if it wasn't registered
   */
  boolean unregisterLocalEndpoint(EndpointId id);

  /**
   * Unregisters a specific physical endpoint for a remote service.
   *
   * @param id The logical endpoint ID
   * @param physicalEndpoint The physical endpoint to unregister
   * @return true if the endpoint was unregistered, false if it wasn't registered
   */
  boolean unregisterRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint);

  /**
   * Unregisters all physical endpoints for a remote service.
   *
   * @param id The logical endpoint ID
   * @return true if any endpoints were unregistered, false if none were registered
   */
  boolean unregisterAllEndpoints(EndpointId id);

  /**
   * Finds all logical endpoint IDs associated with a given physical endpoint.
   * This performs a reverse lookup to find which EndpointIds are registered
   * with the specified physical endpoint. A single physical endpoint may host
   * multiple services.
   *
   * @param physicalEndpoint The physical endpoint to search for
   * @return The set of associated EndpointIds, or empty set if not found
   */
  Set<EndpointId> findEndpointIds(Endpoint physicalEndpoint);

  /**
   * Retrieves all endpoint information including both local and remote endpoints.
   * This method provides a comprehensive view of all registered endpoints.
   *
   * @return A map where keys are EndpointIds and values are EndpointInfo objects
   *         containing the type (LOCAL or REMOTE) and associated endpoints
   */
  Map<EndpointId, EndpointInfo> getAllEndpointInfo();

  /**
   * Information about an endpoint including its type and physical endpoints.
   */
  class EndpointInfo {
    enum Type { LOCAL, REMOTE }
    
    private final Type type;
    private final Object implementation;  // Only for local endpoints
    private final List<Endpoint> physicalEndpoints;

    public EndpointInfo(Type type, Object implementation, List<Endpoint> physicalEndpoints) {
      this.type = type;
      this.implementation = implementation;
      this.physicalEndpoints = physicalEndpoints;
    }

    public Type getType() {
      return type;
    }
    
    public Object getImplementation() {
      return implementation;
    }
    
    public List<Endpoint> getPhysicalEndpoints() {
      return physicalEndpoints;
    }
  }
}