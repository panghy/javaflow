package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.Endpoint;

import java.util.List;
import java.util.Optional;

/**
 * Resolves logical endpoint identifiers to physical network endpoints.
 * This interface provides a way to map between {@link EndpointId} objects
 * (which represent logical service identifiers) and physical {@link Endpoint}
 * objects (which represent actual network locations).
 * 
 * <p>The EndpointResolver is used by the RPC transport to determine where to send
 * messages for a given endpoint. It supports three types of endpoints:</p>
 * <ul>
 *   <li>Loopback endpoints - Services that exist only in the local process with no network exposure</li>
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
   * Registers a loopback endpoint, which exists only in the local process with no network exposure.
   * This is the most efficient type of endpoint for local-only communication as it
   * bypasses all network serialization and transport overhead.
   *
   * @param id The logical endpoint ID
   * @param implementation The implementation object
   */
  void registerLoopbackEndpoint(EndpointId id, Object implementation);

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
   * Checks if an endpoint is registered locally or as a loopback.
   *
   * @param id The logical endpoint ID
   * @return true if the endpoint is registered locally or as a loopback, false otherwise
   */
  boolean isLocalEndpoint(EndpointId id);
  
  /**
   * Checks if an endpoint is registered as a loopback (in-process only).
   *
   * @param id The logical endpoint ID
   * @return true if the endpoint is registered as a loopback, false otherwise
   */
  boolean isLoopbackEndpoint(EndpointId id);

  /**
   * Gets the local implementation of an endpoint, if it exists.
   * This will return implementations for both local endpoints and loopback endpoints.
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
   * <p>For loopback endpoints, this will return an empty Optional since they
   * don't have physical network endpoints.</p>
   *
   * @param id The logical endpoint ID
   * @return The physical endpoint, or empty if not found or if this is a loopback endpoint
   */
  Optional<Endpoint> resolveEndpoint(EndpointId id);

  /**
   * Retrieves a specific physical endpoint for the given logical endpoint ID.
   * This allows targeting a specific instance instead of using load balancing.
   * 
   * <p>For loopback endpoints, this will return an empty Optional since they
   * don't have physical network endpoints.</p>
   *
   * @param id The logical endpoint ID
   * @param index The index of the physical endpoint to retrieve
   * @return The physical endpoint, or empty if not found, index out of range, or if this is a loopback endpoint
   */
  Optional<Endpoint> resolveEndpoint(EndpointId id, int index);

  /**
   * Gets all physical endpoints registered for a logical endpoint ID.
   * 
   * <p>For loopback endpoints, this will return an empty list since they
   * don't have physical network endpoints.</p>
   *
   * @param id The logical endpoint ID
   * @return The list of physical endpoints, or an empty list if none are registered or if this is a loopback endpoint
   */
  List<Endpoint> getAllEndpoints(EndpointId id);

  /**
   * Unregisters a local or loopback endpoint.
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
}