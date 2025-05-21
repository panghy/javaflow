package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.Endpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link DefaultEndpointResolver} class.
 */
public class DefaultEndpointResolverTest {

  private DefaultEndpointResolver resolver;
  
  @BeforeEach
  public void setUp() {
    resolver = new DefaultEndpointResolver();
  }
  
  @Test
  public void testRegisterLocalEndpoint() {
    // Create a test endpoint
    EndpointId id = new EndpointId("test-service");
    Object implementation = new TestService();
    Endpoint physicalEndpoint = new Endpoint("localhost", 8000);
    
    // Register it
    resolver.registerLocalEndpoint(id, implementation, physicalEndpoint);
    
    // Verify it's registered as local
    assertTrue(resolver.isLocalEndpoint(id));
    
    // Verify we can retrieve the implementation
    Optional<Object> retrievedImpl = resolver.getLocalImplementation(id);
    assertTrue(retrievedImpl.isPresent());
    assertEquals(implementation, retrievedImpl.get());
    
    // Verify we can resolve the endpoint
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertTrue(resolvedEndpoint.isPresent());
    assertEquals(physicalEndpoint, resolvedEndpoint.get());
    
    // Verify getAllEndpoints returns the single endpoint
    List<Endpoint> allEndpoints = resolver.getAllEndpoints(id);
    assertEquals(1, allEndpoints.size());
    assertEquals(physicalEndpoint, allEndpoints.get(0));
  }
  
  @Test
  public void testRegisterRemoteEndpoint() {
    // Create a test endpoint
    EndpointId id = new EndpointId("remote-service");
    Endpoint physicalEndpoint = new Endpoint("remote-host", 9000);
    
    // Register it
    resolver.registerRemoteEndpoint(id, physicalEndpoint);
    
    // Verify it's not registered as local
    assertFalse(resolver.isLocalEndpoint(id));
    
    // Verify we can't retrieve a local implementation
    Optional<Object> retrievedImpl = resolver.getLocalImplementation(id);
    assertFalse(retrievedImpl.isPresent());
    
    // Verify we can resolve the endpoint
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertTrue(resolvedEndpoint.isPresent());
    assertEquals(physicalEndpoint, resolvedEndpoint.get());
    
    // Verify getAllEndpoints returns the single endpoint
    List<Endpoint> allEndpoints = resolver.getAllEndpoints(id);
    assertEquals(1, allEndpoints.size());
    assertEquals(physicalEndpoint, allEndpoints.get(0));
  }
  
  @Test
  public void testRegisterMultipleRemoteEndpoints() {
    // Create a test endpoint
    EndpointId id = new EndpointId("multi-service");
    Endpoint endpoint1 = new Endpoint("host1", 9001);
    Endpoint endpoint2 = new Endpoint("host2", 9002);
    Endpoint endpoint3 = new Endpoint("host3", 9003);
    
    // Register multiple endpoints
    resolver.registerRemoteEndpoints(id, Arrays.asList(endpoint1, endpoint2, endpoint3));
    
    // Verify we can resolve the endpoint (first resolution)
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertTrue(resolvedEndpoint.isPresent());
    assertEquals(endpoint1, resolvedEndpoint.get()); // First round-robin call should give the first endpoint
    
    // Verify we can resolve by index
    Optional<Endpoint> endpoint2Result = resolver.resolveEndpoint(id, 1);
    assertTrue(endpoint2Result.isPresent());
    assertEquals(endpoint2, endpoint2Result.get());
    
    // Verify getAllEndpoints returns all endpoints
    List<Endpoint> allEndpoints = resolver.getAllEndpoints(id);
    assertEquals(3, allEndpoints.size());
    assertTrue(allEndpoints.contains(endpoint1));
    assertTrue(allEndpoints.contains(endpoint2));
    assertTrue(allEndpoints.contains(endpoint3));
  }
  
  @Test
  public void testRoundRobinResolution() {
    // Create a test endpoint
    EndpointId id = new EndpointId("round-robin-service");
    Endpoint endpoint1 = new Endpoint("host1", 9001);
    Endpoint endpoint2 = new Endpoint("host2", 9002);
    Endpoint endpoint3 = new Endpoint("host3", 9003);
    
    // Register multiple endpoints
    resolver.registerRemoteEndpoints(id, Arrays.asList(endpoint1, endpoint2, endpoint3));
    
    // First resolution should give the first endpoint
    Optional<Endpoint> resolution1 = resolver.resolveEndpoint(id);
    assertTrue(resolution1.isPresent());
    assertEquals(endpoint1, resolution1.get());
    
    // Second resolution should give the second endpoint
    Optional<Endpoint> resolution2 = resolver.resolveEndpoint(id);
    assertTrue(resolution2.isPresent());
    assertEquals(endpoint2, resolution2.get());
    
    // Third resolution should give the third endpoint
    Optional<Endpoint> resolution3 = resolver.resolveEndpoint(id);
    assertTrue(resolution3.isPresent());
    assertEquals(endpoint3, resolution3.get());
    
    // Fourth resolution should wrap around to the first endpoint
    Optional<Endpoint> resolution4 = resolver.resolveEndpoint(id);
    assertTrue(resolution4.isPresent());
    assertEquals(endpoint1, resolution4.get());
  }
  
  @Test
  public void testLocalOverridesRemote() {
    // Create endpoints
    EndpointId id = new EndpointId("overridden-service");
    Endpoint remoteEndpoint = new Endpoint("remote-host", 9000);
    Endpoint localEndpoint = new Endpoint("localhost", 8000);
    Object implementation = new TestService();
    
    // First register as remote
    resolver.registerRemoteEndpoint(id, remoteEndpoint);
    
    // Then register as local
    resolver.registerLocalEndpoint(id, implementation, localEndpoint);
    
    // Verify it's now registered as local
    assertTrue(resolver.isLocalEndpoint(id));
    
    // Verify we can retrieve the implementation
    Optional<Object> retrievedImpl = resolver.getLocalImplementation(id);
    assertTrue(retrievedImpl.isPresent());
    
    // Verify resolution gives the local endpoint
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertTrue(resolvedEndpoint.isPresent());
    assertEquals(localEndpoint, resolvedEndpoint.get());
    
    // Verify getAllEndpoints returns only the local endpoint
    List<Endpoint> allEndpoints = resolver.getAllEndpoints(id);
    assertEquals(1, allEndpoints.size());
    assertEquals(localEndpoint, allEndpoints.get(0));
  }
  
  @Test
  public void testUnregisterEndpoints() {
    // Create endpoints
    EndpointId localId = new EndpointId("local-service");
    EndpointId remoteId = new EndpointId("remote-service");
    Endpoint localEndpoint = new Endpoint("localhost", 8000);
    Endpoint remoteEndpoint1 = new Endpoint("remote1", 9001);
    Endpoint remoteEndpoint2 = new Endpoint("remote2", 9002);
    Object implementation = new TestService();
    
    // Register endpoints
    resolver.registerLocalEndpoint(localId, implementation, localEndpoint);
    resolver.registerRemoteEndpoints(remoteId, Arrays.asList(remoteEndpoint1, remoteEndpoint2));
    
    // Verify they're registered
    assertTrue(resolver.isLocalEndpoint(localId));
    assertFalse(resolver.isLocalEndpoint(remoteId));
    assertEquals(2, resolver.getAllEndpoints(remoteId).size());
    
    // Unregister local endpoint
    boolean localUnregistered = resolver.unregisterLocalEndpoint(localId);
    assertTrue(localUnregistered);
    
    // Verify it's gone
    assertFalse(resolver.isLocalEndpoint(localId));
    assertFalse(resolver.getLocalImplementation(localId).isPresent());
    assertTrue(resolver.getAllEndpoints(localId).isEmpty());
    
    // Unregister one remote endpoint
    boolean remoteUnregistered = resolver.unregisterRemoteEndpoint(remoteId, remoteEndpoint1);
    assertTrue(remoteUnregistered);
    
    // Verify only one remains
    List<Endpoint> remainingEndpoints = resolver.getAllEndpoints(remoteId);
    assertEquals(1, remainingEndpoints.size());
    assertEquals(remoteEndpoint2, remainingEndpoints.get(0));
    
    // Unregister all remaining endpoints
    boolean allUnregistered = resolver.unregisterAllEndpoints(remoteId);
    assertTrue(allUnregistered);
    
    // Verify all are gone
    assertTrue(resolver.getAllEndpoints(remoteId).isEmpty());
  }
  
  /**
   * Simple test service for local endpoint registration.
   */
  private static class TestService {
    public String getMessage() {
      return "Hello, world!";
    }
  }
}