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
  
  @Test
  public void testNullParameterValidation() {
    // Test registerLoopbackEndpoint with null parameters
    try {
      resolver.registerLoopbackEndpoint(null, new TestService());
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    try {
      resolver.registerLoopbackEndpoint(new EndpointId("test"), null);
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    // Test registerLocalEndpoint with null parameters
    try {
      resolver.registerLocalEndpoint(null, new TestService(), new Endpoint("localhost", 8000));
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    try {
      resolver.registerLocalEndpoint(new EndpointId("test"), null, new Endpoint("localhost", 8000));
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    try {
      resolver.registerLocalEndpoint(new EndpointId("test"), new TestService(), null);
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    // Test registerRemoteEndpoint with null parameters
    try {
      resolver.registerRemoteEndpoint(null, new Endpoint("localhost", 8000));
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    try {
      resolver.registerRemoteEndpoint(new EndpointId("test"), null);
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    // Test registerRemoteEndpoints with null parameters
    try {
      resolver.registerRemoteEndpoints(null, Arrays.asList(new Endpoint("localhost", 8000)));
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
    
    try {
      resolver.registerRemoteEndpoints(new EndpointId("test"), null);
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("cannot be null"));
    }
  }

  @Test
  public void testLoopbackEndpointLifecycle() {
    EndpointId loopbackId = new EndpointId("loopback-service");
    TestService implementation = new TestService();
    
    // Register as loopback
    resolver.registerLoopbackEndpoint(loopbackId, implementation);
    
    // Verify loopback status
    assertTrue(resolver.isLoopbackEndpoint(loopbackId));
    assertTrue(resolver.isLocalEndpoint(loopbackId)); // isLocalEndpoint returns true for loopback too
    
    // Verify implementation retrieval
    Optional<Object> impl = resolver.getLocalImplementation(loopbackId);
    assertTrue(impl.isPresent());
    assertEquals(implementation, impl.get());
    
    // Verify endpoint resolution returns empty for loopback
    Optional<Endpoint> endpoint = resolver.resolveEndpoint(loopbackId);
    assertFalse(endpoint.isPresent());
    
    // Verify getAllEndpoints returns empty for loopback
    List<Endpoint> endpoints = resolver.getAllEndpoints(loopbackId);
    assertTrue(endpoints.isEmpty());
    
    // Verify index-based resolution returns empty for loopback
    Optional<Endpoint> indexEndpoint = resolver.resolveEndpoint(loopbackId, 0);
    assertFalse(indexEndpoint.isPresent());
    
    // Unregister loopback endpoint
    boolean unregistered = resolver.unregisterLocalEndpoint(loopbackId);
    assertTrue(unregistered);
    
    // Verify it's no longer loopback
    assertFalse(resolver.isLoopbackEndpoint(loopbackId));
    assertFalse(resolver.getLocalImplementation(loopbackId).isPresent());
  }

  @Test
  public void testRegistrationConflicts() {
    EndpointId conflictId = new EndpointId("conflict-service");
    TestService implementation = new TestService();
    Endpoint endpoint = new Endpoint("localhost", 8000);
    
    // Register as local first
    resolver.registerLocalEndpoint(conflictId, implementation, endpoint);
    assertTrue(resolver.isLocalEndpoint(conflictId));
    
    // Try to register as remote - should be ignored
    resolver.registerRemoteEndpoint(conflictId, endpoint);
    assertTrue(resolver.isLocalEndpoint(conflictId)); // Still local
    
    // Now register as loopback - should remove local registration
    resolver.registerLoopbackEndpoint(conflictId, implementation);
    assertTrue(resolver.isLoopbackEndpoint(conflictId));
    assertTrue(resolver.isLocalEndpoint(conflictId)); // isLocalEndpoint includes loopback
    
    // Try to register as remote when loopback exists - should be ignored
    resolver.registerRemoteEndpoint(conflictId, endpoint);
    assertTrue(resolver.isLoopbackEndpoint(conflictId)); // Still loopback
    
    // Test batch registration with conflict
    resolver.registerRemoteEndpoints(conflictId, Arrays.asList(endpoint));
    assertTrue(resolver.isLoopbackEndpoint(conflictId)); // Still loopback
  }

  @Test
  public void testIndexBasedResolutionEdgeCases() {
    EndpointId localId = new EndpointId("local-service");
    TestService implementation = new TestService();
    Endpoint localEndpoint = new Endpoint("localhost", 8000);
    
    // Register local endpoint
    resolver.registerLocalEndpoint(localId, implementation, localEndpoint);
    
    // Test index 0 for local endpoint
    Optional<Endpoint> endpoint0 = resolver.resolveEndpoint(localId, 0);
    assertTrue(endpoint0.isPresent());
    assertEquals(localEndpoint, endpoint0.get());
    
    // Test non-zero index for local endpoint
    Optional<Endpoint> endpoint1 = resolver.resolveEndpoint(localId, 1);
    assertFalse(endpoint1.isPresent());
    
    // Test negative index for local endpoint
    Optional<Endpoint> endpointNeg = resolver.resolveEndpoint(localId, -1);
    assertFalse(endpointNeg.isPresent());
  }

  @Test
  public void testRemoteEndpointInfoEdgeCases() {
    EndpointId remoteId = new EndpointId("remote-service");
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    
    // Test with empty endpoint list
    Optional<Endpoint> emptyEndpoint = resolver.resolveEndpoint(remoteId);
    assertFalse(emptyEndpoint.isPresent());
    
    // Test getAllEndpoints with non-existent ID
    List<Endpoint> emptyList = resolver.getAllEndpoints(remoteId);
    assertTrue(emptyList.isEmpty());
    
    // Register multiple endpoints
    resolver.registerRemoteEndpoints(remoteId, Arrays.asList(endpoint1, endpoint2));
    
    // Test index-based access with various indices
    Optional<Endpoint> at0 = resolver.resolveEndpoint(remoteId, 0);
    assertTrue(at0.isPresent());
    assertEquals(endpoint1, at0.get());
    
    Optional<Endpoint> at1 = resolver.resolveEndpoint(remoteId, 1);
    assertTrue(at1.isPresent());
    assertEquals(endpoint2, at1.get());
    
    // Test negative index
    Optional<Endpoint> atNeg = resolver.resolveEndpoint(remoteId, -1);
    assertFalse(atNeg.isPresent());
    
    // Test out-of-bounds index
    Optional<Endpoint> atOOB = resolver.resolveEndpoint(remoteId, 5);
    assertFalse(atOOB.isPresent());
    
    // Test round-robin wraparound by calling multiple times
    for (int i = 0; i < 5; i++) {
      Optional<Endpoint> roundRobin = resolver.resolveEndpoint(remoteId);
      assertTrue(roundRobin.isPresent());
      // Should alternate between endpoint1 and endpoint2
    }
  }

  @Test
  public void testDuplicateEndpointHandling() {
    EndpointId remoteId = new EndpointId("duplicate-test");
    Endpoint endpoint = new Endpoint("localhost", 8000);
    
    // Register same endpoint multiple times
    resolver.registerRemoteEndpoint(remoteId, endpoint);
    resolver.registerRemoteEndpoint(remoteId, endpoint);
    resolver.registerRemoteEndpoint(remoteId, endpoint);
    
    // Should only appear once
    List<Endpoint> endpoints = resolver.getAllEndpoints(remoteId);
    assertEquals(1, endpoints.size());
    assertEquals(endpoint, endpoints.get(0));
  }

  @Test
  public void testNullEndpointFiltering() {
    EndpointId remoteId = new EndpointId("null-filter-test");
    Endpoint validEndpoint = new Endpoint("localhost", 8000);
    
    // Register with list containing null endpoints
    List<Endpoint> endpointsWithNulls = Arrays.asList(validEndpoint, null, validEndpoint);
    resolver.registerRemoteEndpoints(remoteId, endpointsWithNulls);
    
    // Should only have one unique endpoint (nulls filtered out, duplicates removed)
    List<Endpoint> endpoints = resolver.getAllEndpoints(remoteId);
    assertEquals(1, endpoints.size());
    assertEquals(validEndpoint, endpoints.get(0));
  }

  @Test
  public void testFindEndpointIdsWithNullParameter() {
    // Test findEndpointIds with null parameter
    assertEquals(0, resolver.findEndpointIds(null).size());
  }

  @Test
  public void testUnregisterEndpointCleanup() {
    EndpointId remoteId = new EndpointId("cleanup-test");
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    
    // Register multiple endpoints
    resolver.registerRemoteEndpoint(remoteId, endpoint1);
    resolver.registerRemoteEndpoint(remoteId, endpoint2);
    
    // Unregister one endpoint
    boolean removed1 = resolver.unregisterRemoteEndpoint(remoteId, endpoint1);
    assertTrue(removed1);
    
    // Verify one endpoint remains
    List<Endpoint> remaining = resolver.getAllEndpoints(remoteId);
    assertEquals(1, remaining.size());
    assertEquals(endpoint2, remaining.get(0));
    
    // Unregister last endpoint - should trigger cleanup
    boolean removed2 = resolver.unregisterRemoteEndpoint(remoteId, endpoint2);
    assertTrue(removed2);
    
    // Verify endpoint ID is completely removed
    List<Endpoint> empty = resolver.getAllEndpoints(remoteId);
    assertTrue(empty.isEmpty());
    
    // Trying to unregister from now-empty list should return false
    boolean notRemoved = resolver.unregisterRemoteEndpoint(remoteId, endpoint1);
    assertFalse(notRemoved);
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