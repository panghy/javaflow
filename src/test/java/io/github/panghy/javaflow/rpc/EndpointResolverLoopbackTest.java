package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.Endpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the local endpoint functionality in {@link EndpointResolver}.
 * <p>
 * Note: This test file has been updated to reflect the simplified two-tier endpoint model.
 * The previous three-tier model (loopback, local, remote) has been replaced with a 
 * two-tier model (local with network exposure, remote).
 */
public class EndpointResolverLoopbackTest {

  private DefaultEndpointResolver resolver;
  
  @BeforeEach
  public void setUp() {
    resolver = new DefaultEndpointResolver();
  }
  
  @Test
  public void testRegisterLocalEndpoint() {
    // Create a test endpoint
    EndpointId id = new EndpointId("local-service");
    Object implementation = new TestService();
    Endpoint physicalEndpoint = new Endpoint("localhost", 8080);
    
    // Register as local with network exposure
    resolver.registerLocalEndpoint(id, implementation, physicalEndpoint);
    
    // Verify it's registered as local
    assertTrue(resolver.isLocalEndpoint(id));
    
    // Verify we can retrieve the implementation
    Optional<Object> retrievedImpl = resolver.getLocalImplementation(id);
    assertTrue(retrievedImpl.isPresent());
    assertSame(implementation, retrievedImpl.get());
    
    // Verify that resolveEndpoint returns the physical endpoint
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertTrue(resolvedEndpoint.isPresent());
    assertEquals(physicalEndpoint, resolvedEndpoint.get());
    
    // Verify that resolveEndpoint with index returns the endpoint
    Optional<Endpoint> resolvedEndpointWithIndex = resolver.resolveEndpoint(id, 0);
    assertTrue(resolvedEndpointWithIndex.isPresent());
    assertEquals(physicalEndpoint, resolvedEndpointWithIndex.get());
    
    // Verify getAllEndpoints returns the physical endpoint
    List<Endpoint> allEndpoints = resolver.getAllEndpoints(id);
    assertEquals(1, allEndpoints.size());
    assertEquals(physicalEndpoint, allEndpoints.getFirst());
  }
  
  @Test
  public void testLocalEndpointOverridesRemote() {
    // Create test endpoints
    EndpointId id = new EndpointId("overridden-service");
    Object implementation = new TestService();
    Endpoint localPhysicalEndpoint = new Endpoint("localhost", 8000);
    Endpoint remoteEndpoint = new Endpoint("remote-host", 9000);
    
    // First register as remote
    resolver.registerRemoteEndpoint(id, remoteEndpoint);
    
    // Verify it's registered as remote
    assertFalse(resolver.isLocalEndpoint(id));
    assertEquals(remoteEndpoint, resolver.resolveEndpoint(id).get());
    
    // Now register as local
    resolver.registerLocalEndpoint(id, implementation, localPhysicalEndpoint);
    
    // Verify it's now registered as local
    assertTrue(resolver.isLocalEndpoint(id));
    
    // Verify physical endpoint is now the local one
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertTrue(resolvedEndpoint.isPresent());
    assertEquals(localPhysicalEndpoint, resolvedEndpoint.get());
    
    // Verify remote endpoint is no longer available
    List<Endpoint> allEndpoints = resolver.getAllEndpoints(id);
    assertEquals(1, allEndpoints.size());
    assertEquals(localPhysicalEndpoint, allEndpoints.getFirst());
  }
  
  @Test
  public void testLocalEndpointUnregister() {
    // Create a test endpoint
    EndpointId id = new EndpointId("unregister-service");
    Object implementation = new TestService();
    Endpoint physicalEndpoint = new Endpoint("localhost", 8080);
    
    // Register as local
    resolver.registerLocalEndpoint(id, implementation, physicalEndpoint);
    
    // Verify it's registered as local
    assertTrue(resolver.isLocalEndpoint(id));
    
    // Unregister
    boolean unregistered = resolver.unregisterLocalEndpoint(id);
    
    // Verify unregistration was successful
    assertTrue(unregistered);
    
    // Verify it's no longer registered
    assertFalse(resolver.isLocalEndpoint(id));
    assertFalse(resolver.getLocalImplementation(id).isPresent());
    assertFalse(resolver.resolveEndpoint(id).isPresent());
  }
  
  @Test
  public void testUnregisterAllWithLocal() {
    // Create test endpoints
    EndpointId localId = new EndpointId("local-service");
    Object implementation = new TestService();
    Endpoint localEndpoint = new Endpoint("localhost", 8080);
    
    // Register as local
    resolver.registerLocalEndpoint(localId, implementation, localEndpoint);
    
    // Register other endpoints
    EndpointId remoteId = new EndpointId("remote-service");
    Endpoint endpoint1 = new Endpoint("host1", 9001);
    Endpoint endpoint2 = new Endpoint("host2", 9002);
    resolver.registerRemoteEndpoints(remoteId, Arrays.asList(endpoint1, endpoint2));
    
    // Verify registration
    assertTrue(resolver.isLocalEndpoint(localId));
    assertFalse(resolver.isLocalEndpoint(remoteId));
    
    // Unregister all endpoints for both IDs
    boolean unregisteredLocal = resolver.unregisterAllEndpoints(localId);
    boolean unregisteredRemote = resolver.unregisterAllEndpoints(remoteId);
    
    // Verify unregistration was successful
    assertTrue(unregisteredLocal);
    assertTrue(unregisteredRemote);
    
    // Verify they're no longer registered
    assertFalse(resolver.isLocalEndpoint(localId));
    assertEquals(0, resolver.getAllEndpoints(localId).size());
    assertEquals(0, resolver.getAllEndpoints(remoteId).size());
  }
  
  /**
   * Simple test service for testing local endpoints.
   */
  private static class TestService {
    public String getMessage() {
      return "Hello, world!";
    }
  }
}