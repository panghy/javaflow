package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.Endpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
  public void testGetAllEndpointInfo() {
    // Register a mix of local and remote endpoints
    EndpointId localId1 = new EndpointId("local-service-1");
    EndpointId localId2 = new EndpointId("local-service-2");
    EndpointId remoteId1 = new EndpointId("remote-service-1");
    EndpointId remoteId2 = new EndpointId("remote-service-2");
    
    Object impl1 = new TestService();
    Object impl2 = new TestService();
    Endpoint localEndpoint1 = new Endpoint("localhost", 8001);
    Endpoint localEndpoint2 = new Endpoint("localhost", 8002);
    Endpoint remoteEndpoint1 = new Endpoint("host1", 9001);
    Endpoint remoteEndpoint2 = new Endpoint("host2", 9002);
    Endpoint remoteEndpoint3 = new Endpoint("host3", 9003);
    
    // Register endpoints
    resolver.registerLocalEndpoint(localId1, impl1, localEndpoint1);
    resolver.registerLocalEndpoint(localId2, impl2, localEndpoint2);
    resolver.registerRemoteEndpoint(remoteId1, remoteEndpoint1);
    resolver.registerRemoteEndpoints(remoteId2, Arrays.asList(remoteEndpoint2, remoteEndpoint3));
    
    // Get all endpoint info
    Map<EndpointId, EndpointResolver.EndpointInfo> allInfo = resolver.getAllEndpointInfo();
    
    // Should have 4 entries
    assertEquals(4, allInfo.size());
    
    // Verify local endpoints
    assertTrue(allInfo.containsKey(localId1));
    EndpointResolver.EndpointInfo local1Info = allInfo.get(localId1);
    assertEquals(EndpointResolver.EndpointInfo.Type.LOCAL, local1Info.getType());
    assertEquals(1, local1Info.getPhysicalEndpoints().size());
    assertEquals(localEndpoint1, local1Info.getPhysicalEndpoints().get(0));
    assertEquals(impl1, local1Info.getImplementation());
    
    assertTrue(allInfo.containsKey(localId2));
    EndpointResolver.EndpointInfo local2Info = allInfo.get(localId2);
    assertEquals(EndpointResolver.EndpointInfo.Type.LOCAL, local2Info.getType());
    assertEquals(1, local2Info.getPhysicalEndpoints().size());
    assertEquals(localEndpoint2, local2Info.getPhysicalEndpoints().get(0));
    assertEquals(impl2, local2Info.getImplementation());
    
    // Verify remote endpoints
    assertTrue(allInfo.containsKey(remoteId1));
    EndpointResolver.EndpointInfo remote1Info = allInfo.get(remoteId1);
    assertEquals(EndpointResolver.EndpointInfo.Type.REMOTE, remote1Info.getType());
    assertEquals(1, remote1Info.getPhysicalEndpoints().size());
    assertEquals(remoteEndpoint1, remote1Info.getPhysicalEndpoints().get(0));
    assertEquals(null, remote1Info.getImplementation());
    
    assertTrue(allInfo.containsKey(remoteId2));
    EndpointResolver.EndpointInfo remote2Info = allInfo.get(remoteId2);
    assertEquals(EndpointResolver.EndpointInfo.Type.REMOTE, remote2Info.getType());
    assertEquals(2, remote2Info.getPhysicalEndpoints().size());
    assertTrue(remote2Info.getPhysicalEndpoints().contains(remoteEndpoint2));
    assertTrue(remote2Info.getPhysicalEndpoints().contains(remoteEndpoint3));
    assertEquals(null, remote2Info.getImplementation());
  }
  
  @Test
  public void testGetAllEndpointInfoEmpty() {
    // Get info from empty resolver
    Map<EndpointId, EndpointResolver.EndpointInfo> emptyInfo = resolver.getAllEndpointInfo();
    assertTrue(emptyInfo.isEmpty());
  }
  
  @Test
  public void testEndpointInfoFields() {
    Object impl = new TestService();
    List<Endpoint> endpoints = Arrays.asList(new Endpoint("host1", 8000));
    
    // Test local endpoint info
    EndpointResolver.EndpointInfo localInfo = new EndpointResolver.EndpointInfo(
        EndpointResolver.EndpointInfo.Type.LOCAL, impl, endpoints);
    assertEquals(EndpointResolver.EndpointInfo.Type.LOCAL, localInfo.getType());
    assertEquals(impl, localInfo.getImplementation());
    assertEquals(endpoints, localInfo.getPhysicalEndpoints());
    
    // Test remote endpoint info
    EndpointResolver.EndpointInfo remoteInfo = new EndpointResolver.EndpointInfo(
        EndpointResolver.EndpointInfo.Type.REMOTE, null, endpoints);
    assertEquals(EndpointResolver.EndpointInfo.Type.REMOTE, remoteInfo.getType());
    assertEquals(null, remoteInfo.getImplementation());
    assertEquals(endpoints, remoteInfo.getPhysicalEndpoints());
  }
  
  @Test
  public void testNullParameterValidation() {
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
  public void testFindEndpointIdsByPhysicalEndpoint() {
    // Test finding endpoint IDs by physical endpoint
    Endpoint sharedEndpoint = new Endpoint("localhost", 8080);
    Endpoint uniqueEndpoint = new Endpoint("localhost", 8081);
    
    EndpointId id1 = new EndpointId("service-1");
    EndpointId id2 = new EndpointId("service-2");
    EndpointId id3 = new EndpointId("service-3");
    
    // Register services on shared endpoint
    resolver.registerLocalEndpoint(id1, new TestService(), sharedEndpoint);
    resolver.registerRemoteEndpoint(id2, sharedEndpoint);
    
    // Register service on unique endpoint
    resolver.registerLocalEndpoint(id3, new TestService(), uniqueEndpoint);
    
    // Find endpoint IDs for shared endpoint
    var sharedIds = resolver.findEndpointIds(sharedEndpoint);
    assertEquals(2, sharedIds.size());
    assertTrue(sharedIds.contains(id1));
    assertTrue(sharedIds.contains(id2));
    
    // Find endpoint IDs for unique endpoint
    var uniqueIds = resolver.findEndpointIds(uniqueEndpoint);
    assertEquals(1, uniqueIds.size());
    assertTrue(uniqueIds.contains(id3));
    
    // Find endpoint IDs for non-existent endpoint
    var emptyIds = resolver.findEndpointIds(new Endpoint("unknown", 9999));
    assertTrue(emptyIds.isEmpty());
  }
  
  @Test
  public void testMultipleEndpointRegistrationAndRemoval() {
    EndpointId id = new EndpointId("multi-endpoint-service");
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    Endpoint endpoint3 = new Endpoint("host3", 8003);
    
    // Register endpoints one by one
    resolver.registerRemoteEndpoint(id, endpoint1);
    assertEquals(1, resolver.getAllEndpoints(id).size());
    
    resolver.registerRemoteEndpoint(id, endpoint2);
    assertEquals(2, resolver.getAllEndpoints(id).size());
    
    resolver.registerRemoteEndpoint(id, endpoint3);
    assertEquals(3, resolver.getAllEndpoints(id).size());
    
    // Test round-robin resolution cycles through all endpoints
    assertEquals(endpoint1, resolver.resolveEndpoint(id).get());
    assertEquals(endpoint2, resolver.resolveEndpoint(id).get());
    assertEquals(endpoint3, resolver.resolveEndpoint(id).get());
    assertEquals(endpoint1, resolver.resolveEndpoint(id).get()); // Wrap around
    
    // Remove endpoints one by one
    assertTrue(resolver.unregisterRemoteEndpoint(id, endpoint2));
    assertEquals(2, resolver.getAllEndpoints(id).size());
    assertFalse(resolver.getAllEndpoints(id).contains(endpoint2));
    
    // Test that round-robin skips removed endpoint
    assertEquals(endpoint3, resolver.resolveEndpoint(id).get());
    assertEquals(endpoint1, resolver.resolveEndpoint(id).get());
    assertEquals(endpoint3, resolver.resolveEndpoint(id).get()); // No endpoint2
    
    // Remove remaining endpoints
    assertTrue(resolver.unregisterRemoteEndpoint(id, endpoint1));
    assertTrue(resolver.unregisterRemoteEndpoint(id, endpoint3));
    assertTrue(resolver.getAllEndpoints(id).isEmpty());
    assertFalse(resolver.resolveEndpoint(id).isPresent());
  }
  
  @Test
  public void testRemoteEndpointInfoEdgeCases() {
    EndpointId id = new EndpointId("edge-case-service");
    
    // Test with empty endpoint list
    assertFalse(resolver.resolveEndpoint(id).isPresent());
    assertTrue(resolver.getAllEndpoints(id).isEmpty());
    
    // Test index-based access on empty list
    assertFalse(resolver.resolveEndpoint(id, 0).isPresent());
    assertFalse(resolver.resolveEndpoint(id, -1).isPresent());
    assertFalse(resolver.resolveEndpoint(id, 10).isPresent());
    
    // Test unregister on non-existent endpoint
    assertFalse(resolver.unregisterRemoteEndpoint(id, new Endpoint("host", 8000)));
    
    // Add endpoints
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    resolver.registerRemoteEndpoint(id, endpoint1);
    resolver.registerRemoteEndpoint(id, endpoint2);
    
    // Test duplicate registration (should be ignored) - this tests the addEndpoint branch
    int sizeBefore = resolver.getAllEndpoints(id).size();
    resolver.registerRemoteEndpoint(id, endpoint1); // duplicate
    resolver.registerRemoteEndpoint(id, endpoint2); // duplicate
    assertEquals(sizeBefore, resolver.getAllEndpoints(id).size()); // Size should not change
    
    // Test batch registration with duplicates and nulls
    List<Endpoint> mixedEndpoints = Arrays.asList(
        endpoint1,  // duplicate - tests the contains() branch
        null,       // should be filtered
        new Endpoint("host3", 8003),
        endpoint2,  // duplicate - tests the contains() branch
        null        // should be filtered
    );
    resolver.registerRemoteEndpoints(id, mixedEndpoints);
    assertEquals(3, resolver.getAllEndpoints(id).size()); // Only 3 unique endpoints
    
    // Test round-robin with modified endpoint list
    resolver.resolveEndpoint(id); // Move index forward
    assertTrue(resolver.unregisterRemoteEndpoint(id, endpoint1));
    // Should still work after removal
    Optional<Endpoint> next = resolver.resolveEndpoint(id);
    assertTrue(next.isPresent());
    assertFalse(endpoint1.equals(next.get()));
    
    // Test removing non-existent endpoint from existing list
    assertFalse(resolver.unregisterRemoteEndpoint(id, endpoint1)); // Already removed
    assertFalse(resolver.unregisterRemoteEndpoint(id, new Endpoint("nonexistent", 9999)));
  }
  
  @Test
  public void testLocalEndpointOverridesRemoteRegistration() {
    EndpointId id = new EndpointId("override-service");
    Endpoint remoteEndpoint = new Endpoint("remote", 9000);
    Endpoint localEndpoint = new Endpoint("localhost", 8000);
    Object impl = new TestService();
    
    // First register as remote
    resolver.registerRemoteEndpoint(id, remoteEndpoint);
    assertEquals(remoteEndpoint, resolver.resolveEndpoint(id).get());
    
    // Try to register more remote endpoints when local exists - should be ignored
    resolver.registerLocalEndpoint(id, impl, localEndpoint);
    resolver.registerRemoteEndpoint(id, new Endpoint("another", 9001));
    resolver.registerRemoteEndpoints(id, Arrays.asList(
        new Endpoint("host1", 9002),
        new Endpoint("host2", 9003)
    ));
    
    // Should still resolve to local endpoint
    assertEquals(localEndpoint, resolver.resolveEndpoint(id).get());
    assertEquals(1, resolver.getAllEndpoints(id).size());
    assertTrue(resolver.isLocalEndpoint(id));
  }
  
  @Test
  public void testFindEndpointIdsWithNull() {
    // Test with null parameter
    Set<EndpointId> result = resolver.findEndpointIds(null);
    assertTrue(result.isEmpty());
  }
  
  @Test
  public void testRoundRobinAfterEndpointListShrinks() {
    // This test ensures the modulo operation in RemoteEndpointInfo.getNextEndpoint works correctly
    // when the endpoint list shrinks while the index is larger than the new size
    EndpointId id = new EndpointId("shrinking-service");
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    Endpoint endpoint3 = new Endpoint("host3", 8003);
    Endpoint endpoint4 = new Endpoint("host4", 8004);
    
    // Register 4 endpoints
    resolver.registerRemoteEndpoints(id, Arrays.asList(endpoint1, endpoint2, endpoint3, endpoint4));
    
    // Cycle through them a few times to advance the index
    for (int i = 0; i < 10; i++) {
      resolver.resolveEndpoint(id);
    }
    
    // Now remove several endpoints to make the list smaller than the current index
    resolver.unregisterRemoteEndpoint(id, endpoint3);
    resolver.unregisterRemoteEndpoint(id, endpoint4);
    
    // The next resolution should still work correctly with modulo
    Optional<Endpoint> next = resolver.resolveEndpoint(id);
    assertTrue(next.isPresent());
    assertTrue(next.get().equals(endpoint1) || next.get().equals(endpoint2));
    
    // Continue cycling to ensure it still works
    for (int i = 0; i < 5; i++) {
      Optional<Endpoint> endpoint = resolver.resolveEndpoint(id);
      assertTrue(endpoint.isPresent());
      assertTrue(endpoint.get().equals(endpoint1) || endpoint.get().equals(endpoint2));
    }
  }
  
  @Test
  public void testRegisterLocalEndpointWithDifferentImplementation() {
    // Test line 51: IllegalStateException when registering with different implementation
    EndpointId id = new EndpointId("conflict-service");
    Object impl1 = new TestService();
    Object impl2 = new TestService(); // Different instance
    Endpoint endpoint = new Endpoint("localhost", 8080);
    
    // Register first implementation
    resolver.registerLocalEndpoint(id, impl1, endpoint);
    
    // Try to register different implementation - should throw
    try {
      resolver.registerLocalEndpoint(id, impl2, endpoint);
      assertTrue(false, "Should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("already registered with a different implementation"));
    }
    
    // Registering with same implementation should be fine
    resolver.registerLocalEndpoint(id, impl1, endpoint); // No exception
  }
  
  @Test
  public void testLocalEndpointIndexAccess() {
    // Test line 127: Local endpoint returns empty for non-zero index
    EndpointId id = new EndpointId("local-indexed");
    Object impl = new TestService();
    Endpoint endpoint = new Endpoint("localhost", 8080);
    
    resolver.registerLocalEndpoint(id, impl, endpoint);
    
    // Index 0 should return the endpoint
    assertTrue(resolver.resolveEndpoint(id, 0).isPresent());
    
    // Any other index should return empty (line 127)
    assertFalse(resolver.resolveEndpoint(id, 1).isPresent());
    assertFalse(resolver.resolveEndpoint(id, 2).isPresent());
    assertFalse(resolver.resolveEndpoint(id, -1).isPresent());
  }
  
  @Test
  public void testUnregisterLocalEndpoint() {
    // Test line 151: unregisterLocalEndpoint return value
    EndpointId id = new EndpointId("unregister-local");
    Object impl = new TestService();
    Endpoint endpoint = new Endpoint("localhost", 8080);
    
    // Unregister non-existent endpoint should return false
    assertFalse(resolver.unregisterLocalEndpoint(id));
    
    // Register and unregister should return true
    resolver.registerLocalEndpoint(id, impl, endpoint);
    assertTrue(resolver.unregisterLocalEndpoint(id));
    
    // Unregister again should return false
    assertFalse(resolver.unregisterLocalEndpoint(id));
  }
  
  @Test
  public void testRemoteEndpointEmptyList() {
    // Test lines 266 and 277: RemoteEndpointInfo returns empty when list is empty
    EndpointId id = new EndpointId("empty-remote");
    
    // Register and then remove all endpoints to create empty RemoteEndpointInfo
    Endpoint endpoint = new Endpoint("host", 8080);
    resolver.registerRemoteEndpoint(id, endpoint);
    resolver.unregisterRemoteEndpoint(id, endpoint);
    
    // Test line 266: getNextEndpoint returns empty when list is empty
    assertFalse(resolver.resolveEndpoint(id).isPresent());
    
    // Test line 277: getEndpointAt returns empty for out of bounds
    assertFalse(resolver.resolveEndpoint(id, 0).isPresent());
    assertFalse(resolver.resolveEndpoint(id, -1).isPresent());
    assertFalse(resolver.resolveEndpoint(id, 1).isPresent());
  }
  
  @Test
  public void testRemoteEndpointContainsEndpoint() {
    // Test the containsEndpoint method in RemoteEndpointInfo
    EndpointId id = new EndpointId("contains-test");
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    Endpoint endpoint3 = new Endpoint("host3", 8003);
    
    // Register endpoints
    resolver.registerRemoteEndpoint(id, endpoint1);
    resolver.registerRemoteEndpoint(id, endpoint2);
    
    // Use findEndpointIds to test containsEndpoint
    var endpointIds = resolver.findEndpointIds(endpoint1);
    assertTrue(endpointIds.contains(id));
    
    endpointIds = resolver.findEndpointIds(endpoint2);
    assertTrue(endpointIds.contains(id));
    
    // Test non-existent endpoint
    endpointIds = resolver.findEndpointIds(endpoint3);
    assertFalse(endpointIds.contains(id));
  }
  
  @Test
  public void testRemoteEndpointModuloEdgeCase() {
    // This test ensures proper coverage of the double modulo operation in RemoteEndpointInfo.getNextEndpoint
    EndpointId id = new EndpointId("modulo-edge");
    
    // Register exactly 3 endpoints
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002); 
    Endpoint endpoint3 = new Endpoint("host3", 8003);
    resolver.registerRemoteEndpoints(id, Arrays.asList(endpoint1, endpoint2, endpoint3));
    
    // Advance the counter to a high value that will trigger interesting modulo behavior
    // We need to call resolveEndpoint many times to increment the internal counter
    for (int i = 0; i < 100; i++) {
      resolver.resolveEndpoint(id);
    }
    
    // Now test round-robin still works correctly
    Set<Endpoint> seen = new HashSet<>();
    for (int i = 0; i < 6; i++) { // Two full cycles
      Optional<Endpoint> endpoint = resolver.resolveEndpoint(id);
      assertTrue(endpoint.isPresent());
      seen.add(endpoint.get());
    }
    
    // Should have seen all 3 endpoints
    assertEquals(3, seen.size());
    assertTrue(seen.contains(endpoint1));
    assertTrue(seen.contains(endpoint2));
    assertTrue(seen.contains(endpoint3));
  }

  // TODO: Update this test - registerLoopbackEndpoint no longer exists
  /*
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

  // TODO: Update this test - registerLoopbackEndpoint no longer exists
  /*
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