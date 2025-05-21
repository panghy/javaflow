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
 * Tests for the loopback endpoint functionality in {@link EndpointResolver}.
 */
public class EndpointResolverLoopbackTest {

  private DefaultEndpointResolver resolver;
  
  @BeforeEach
  public void setUp() {
    resolver = new DefaultEndpointResolver();
  }
  
  @Test
  public void testRegisterLoopbackEndpoint() {
    // Create a test endpoint
    EndpointId id = new EndpointId("loopback-service");
    Object implementation = new TestService();
    
    // Register as loopback
    resolver.registerLoopbackEndpoint(id, implementation);
    
    // Verify it's registered as loopback
    assertTrue(resolver.isLoopbackEndpoint(id));
    assertTrue(resolver.isLocalEndpoint(id));
    
    // Verify we can retrieve the implementation
    Optional<Object> retrievedImpl = resolver.getLocalImplementation(id);
    assertTrue(retrievedImpl.isPresent());
    assertSame(implementation, retrievedImpl.get());
    
    // Verify that resolveEndpoint returns empty (no physical endpoint)
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertFalse(resolvedEndpoint.isPresent());
    
    // Verify that resolveEndpoint with index returns empty
    Optional<Endpoint> resolvedEndpointWithIndex = resolver.resolveEndpoint(id, 0);
    assertFalse(resolvedEndpointWithIndex.isPresent());
    
    // Verify getAllEndpoints returns an empty list
    List<Endpoint> allEndpoints = resolver.getAllEndpoints(id);
    assertTrue(allEndpoints.isEmpty());
  }
  
  @Test
  public void testLoopbackOverridesExistingEndpoints() {
    // Create test endpoints
    EndpointId id = new EndpointId("overridden-service");
    Object implementation = new TestService();
    Endpoint physicalEndpoint = new Endpoint("localhost", 8000);
    
    // First register as a local endpoint
    resolver.registerLocalEndpoint(id, implementation, physicalEndpoint);
    
    // Verify it's registered as local
    assertTrue(resolver.isLocalEndpoint(id));
    assertFalse(resolver.isLoopbackEndpoint(id));
    
    // Now register as loopback
    resolver.registerLoopbackEndpoint(id, implementation);
    
    // Verify it's now registered as loopback
    assertTrue(resolver.isLoopbackEndpoint(id));
    assertTrue(resolver.isLocalEndpoint(id));
    
    // Verify physical endpoint is no longer available
    Optional<Endpoint> resolvedEndpoint = resolver.resolveEndpoint(id);
    assertFalse(resolvedEndpoint.isPresent());
    
    // Try the same with a remote endpoint
    EndpointId remoteId = new EndpointId("remote-to-loopback");
    Endpoint remoteEndpoint = new Endpoint("remote-host", 9000);
    
    // Register as remote
    resolver.registerRemoteEndpoint(remoteId, remoteEndpoint);
    
    // Verify it's not local or loopback
    assertFalse(resolver.isLocalEndpoint(remoteId));
    assertFalse(resolver.isLoopbackEndpoint(remoteId));
    
    // Now register as loopback
    resolver.registerLoopbackEndpoint(remoteId, implementation);
    
    // Verify it's now registered as loopback
    assertTrue(resolver.isLoopbackEndpoint(remoteId));
    assertTrue(resolver.isLocalEndpoint(remoteId));
    
    // Verify remote endpoint is no longer available
    resolvedEndpoint = resolver.resolveEndpoint(remoteId);
    assertFalse(resolvedEndpoint.isPresent());
  }
  
  @Test
  public void testLoopbackUnregister() {
    // Create a test endpoint
    EndpointId id = new EndpointId("unregister-service");
    Object implementation = new TestService();
    
    // Register as loopback
    resolver.registerLoopbackEndpoint(id, implementation);
    
    // Verify it's registered as loopback
    assertTrue(resolver.isLoopbackEndpoint(id));
    
    // Unregister
    boolean unregistered = resolver.unregisterLocalEndpoint(id);
    
    // Verify unregistration was successful
    assertTrue(unregistered);
    
    // Verify it's no longer registered
    assertFalse(resolver.isLoopbackEndpoint(id));
    assertFalse(resolver.isLocalEndpoint(id));
    assertFalse(resolver.getLocalImplementation(id).isPresent());
  }
  
  @Test
  public void testUnregisterAllWithLoopback() {
    // Create test endpoints
    EndpointId id = new EndpointId("multi-type-service");
    Object implementation = new TestService();
    
    // Register as loopback
    resolver.registerLoopbackEndpoint(id, implementation);
    
    // Register other endpoints
    EndpointId remoteId = new EndpointId("remote-service");
    Endpoint endpoint1 = new Endpoint("host1", 9001);
    Endpoint endpoint2 = new Endpoint("host2", 9002);
    resolver.registerRemoteEndpoints(remoteId, Arrays.asList(endpoint1, endpoint2));
    
    // Verify registration
    assertTrue(resolver.isLoopbackEndpoint(id));
    assertFalse(resolver.isLocalEndpoint(remoteId));
    
    // Unregister all endpoints for both IDs
    boolean unregisteredLoopback = resolver.unregisterAllEndpoints(id);
    boolean unregisteredRemote = resolver.unregisterAllEndpoints(remoteId);
    
    // Verify unregistration was successful
    assertTrue(unregisteredLoopback);
    assertTrue(unregisteredRemote);
    
    // Verify they're no longer registered
    assertFalse(resolver.isLoopbackEndpoint(id));
    assertFalse(resolver.isLocalEndpoint(id));
    assertEquals(0, resolver.getAllEndpoints(remoteId).size());
  }
  
  /**
   * Simple test service for testing loopback endpoints.
   */
  private static class TestService {
    public String getMessage() {
      return "Hello, world!";
    }
  }
}