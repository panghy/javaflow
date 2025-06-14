package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.Endpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of the {@link EndpointResolver} interface.
 * This implementation uses in-memory storage of endpoint mappings and
 * provides round-robin load balancing for multiple physical endpoints.
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Thread-safe storage of endpoint mappings</li>
 *   <li>Round-robin selection of physical endpoints</li>
 *   <li>Support for local and remote endpoints</li>
 *   <li>Per-endpoint counters for load balancing</li>
 * </ul>
 * 
 * <p>This class is designed to be thread-safe and used in concurrent environments.
 * It uses concurrent collections and atomic operations to ensure safety.</p>
 */
public class DefaultEndpointResolver implements EndpointResolver {

  // Maps logical endpoint IDs to their local implementation (with network exposure)
  private final Map<EndpointId, LocalEndpointInfo> localEndpoints = new ConcurrentHashMap<>();

  // Maps logical endpoint IDs to their remote physical endpoints
  private final Map<EndpointId, RemoteEndpointInfo> remoteEndpoints = new ConcurrentHashMap<>();



  @Override
  public void registerLocalEndpoint(EndpointId id, Object implementation, Endpoint physicalEndpoint) {
    if (id == null || implementation == null || physicalEndpoint == null) {
      throw new IllegalArgumentException("EndpointId, implementation, and physicalEndpoint cannot be null");
    }
    // Check if already registered
    LocalEndpointInfo existing = localEndpoints.get(id);
    if (existing != null && existing.getImplementation() != implementation) {
      throw new IllegalStateException("EndpointId " + id + " is already registered with a different implementation");
    }
    // When registering locally, remove from remote lists if present
    localEndpoints.put(id, new LocalEndpointInfo(implementation, physicalEndpoint));
    remoteEndpoints.remove(id);
  }

  @Override
  public void registerRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint) {
    if (id == null || physicalEndpoint == null) {
      throw new IllegalArgumentException("EndpointId and physicalEndpoint cannot be null");
    }
    
    // Skip if this is a local or loopback endpoint
    if (isLocalEndpoint(id)) {
      return;
    }
    
    RemoteEndpointInfo info = remoteEndpoints.computeIfAbsent(
        id, k -> new RemoteEndpointInfo());
    info.addEndpoint(physicalEndpoint);
  }

  @Override
  public void registerRemoteEndpoints(EndpointId id, List<Endpoint> physicalEndpoints) {
    if (id == null || physicalEndpoints == null) {
      throw new IllegalArgumentException("EndpointId and physicalEndpoints cannot be null");
    }
    
    // Skip if this is a local or loopback endpoint
    if (isLocalEndpoint(id)) {
      return;
    }
    
    RemoteEndpointInfo info = remoteEndpoints.computeIfAbsent(
        id, k -> new RemoteEndpointInfo());
    
    for (Endpoint endpoint : physicalEndpoints) {
      if (endpoint != null) {
        info.addEndpoint(endpoint);
      }
    }
  }

  @Override
  public boolean isLocalEndpoint(EndpointId id) {
    return localEndpoints.containsKey(id);
  }

  @Override
  public Optional<Object> getLocalImplementation(EndpointId id) {
    LocalEndpointInfo info = localEndpoints.get(id);
    return info != null ? Optional.of(info.getImplementation()) : Optional.empty();
  }

  @Override
  public Optional<Endpoint> resolveEndpoint(EndpointId id) {
    // Check if this is a local endpoint
    LocalEndpointInfo localInfo = localEndpoints.get(id);
    if (localInfo != null) {
      return Optional.of(localInfo.getPhysicalEndpoint());
    }
    
    // Otherwise, try to find a remote endpoint
    RemoteEndpointInfo remoteInfo = remoteEndpoints.get(id);
    return remoteInfo != null ? remoteInfo.getNextEndpoint() : Optional.empty();
  }
  
  @Override
  public Optional<Endpoint> resolveEndpoint(EndpointId id, int index) {
    // Check if this is a local endpoint
    LocalEndpointInfo localInfo = localEndpoints.get(id);
    if (localInfo != null) {
      if (index == 0) {
        return Optional.of(localInfo.getPhysicalEndpoint());
      } else {
        return Optional.empty(); // Local endpoint only has one physical endpoint
      }
    }
    
    // Otherwise, try to find a remote endpoint at the specified index
    RemoteEndpointInfo remoteInfo = remoteEndpoints.get(id);
    return remoteInfo != null ? remoteInfo.getEndpointAt(index) : Optional.empty();
  }

  @Override
  public List<Endpoint> getAllEndpoints(EndpointId id) {
    // Check if this is a local endpoint
    LocalEndpointInfo localInfo = localEndpoints.get(id);
    if (localInfo != null) {
      return Collections.singletonList(localInfo.getPhysicalEndpoint());
    }
    
    // Otherwise, get all remote endpoints
    RemoteEndpointInfo remoteInfo = remoteEndpoints.get(id);
    return remoteInfo != null ? remoteInfo.getAllEndpoints() : Collections.emptyList();
  }

  @Override
  public boolean unregisterLocalEndpoint(EndpointId id) {
    return localEndpoints.remove(id) != null;
  }

  @Override
  public boolean unregisterRemoteEndpoint(EndpointId id, Endpoint physicalEndpoint) {
    RemoteEndpointInfo info = remoteEndpoints.get(id);
    if (info != null) {
      boolean removed = info.removeEndpoint(physicalEndpoint);
      if (info.isEmpty()) {
        remoteEndpoints.remove(id);
      }
      return removed;
    }
    return false;
  }

  @Override
  public boolean unregisterAllEndpoints(EndpointId id) {
    boolean removedLocal = (localEndpoints.remove(id) != null);
    boolean removedRemote = (remoteEndpoints.remove(id) != null);
    return removedLocal || removedRemote;
  }

  @Override
  public Set<EndpointId> findEndpointIds(Endpoint physicalEndpoint) {
    if (physicalEndpoint == null) {
      return Collections.emptySet();
    }

    Set<EndpointId> result = new HashSet<>();

    // Check local endpoints first
    for (Map.Entry<EndpointId, LocalEndpointInfo> entry : localEndpoints.entrySet()) {
      if (physicalEndpoint.equals(entry.getValue().getPhysicalEndpoint())) {
        result.add(entry.getKey());
      }
    }

    // Then check remote endpoints
    for (Map.Entry<EndpointId, RemoteEndpointInfo> entry : remoteEndpoints.entrySet()) {
      if (entry.getValue().containsEndpoint(physicalEndpoint)) {
        result.add(entry.getKey());
      }
    }

    return result;
  }

  @Override
  public Map<EndpointId, EndpointInfo> getAllEndpointInfo() {
    Map<EndpointId, EndpointInfo> result = new HashMap<>();
    
    // Add local endpoints
    for (Map.Entry<EndpointId, LocalEndpointInfo> entry : localEndpoints.entrySet()) {
      LocalEndpointInfo localInfo = entry.getValue();
      result.put(entry.getKey(), new EndpointInfo(
          EndpointInfo.Type.LOCAL,
          localInfo.getImplementation(),
          Collections.singletonList(localInfo.getPhysicalEndpoint())
      ));
    }
    
    // Add remote endpoints
    for (Map.Entry<EndpointId, RemoteEndpointInfo> entry : remoteEndpoints.entrySet()) {
      result.put(entry.getKey(), new EndpointInfo(
          EndpointInfo.Type.REMOTE,
          null,  // No implementation for remote endpoints
          entry.getValue().getAllEndpoints()
      ));
    }
    
    return result;
  }

  /**
   * Helper class to store information about a local endpoint.
   */
  private static class LocalEndpointInfo {
    private final Object implementation;
    private final Endpoint physicalEndpoint;
    
    LocalEndpointInfo(Object implementation, Endpoint physicalEndpoint) {
      this.implementation = implementation;
      this.physicalEndpoint = physicalEndpoint;
    }
    
    public Object getImplementation() {
      return implementation;
    }
    
    public Endpoint getPhysicalEndpoint() {
      return physicalEndpoint;
    }
  }
  
  /**
   * Helper class to store information about remote endpoints.
   * Includes support for round-robin selection of physical endpoints.
   */
  static class RemoteEndpointInfo {
    private final List<Endpoint> endpoints = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextIndex = new AtomicInteger(0);
    
    public void addEndpoint(Endpoint endpoint) {
      if (!endpoints.contains(endpoint)) {
        endpoints.add(endpoint);
      }
    }
    
    public boolean removeEndpoint(Endpoint endpoint) {
      return endpoints.remove(endpoint);
    }
    
    public Optional<Endpoint> getNextEndpoint() {
      if (endpoints.isEmpty()) {
        return Optional.empty();
      }
      
      // Round-robin selection
      int size = endpoints.size();
      int index = nextIndex.getAndUpdate(i -> (i + 1) % size) % size;
      return Optional.of(endpoints.get(index));
    }
    
    public Optional<Endpoint> getEndpointAt(int index) {
      if (index < 0 || index >= endpoints.size()) {
        return Optional.empty();
      }
      return Optional.of(endpoints.get(index));
    }
    
    public List<Endpoint> getAllEndpoints() {
      return new ArrayList<>(endpoints);
    }
    
    public boolean isEmpty() {
      return endpoints.isEmpty();
    }
    
    public boolean containsEndpoint(Endpoint endpoint) {
      return endpoints.contains(endpoint);
    }
  }
}