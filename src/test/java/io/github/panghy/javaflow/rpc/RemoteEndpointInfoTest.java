package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.Endpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the RemoteEndpointInfo inner class to improve branch coverage.
 */
public class RemoteEndpointInfoTest {

  private DefaultEndpointResolver.RemoteEndpointInfo info;

  @BeforeEach
  public void setUp() {
    info = new DefaultEndpointResolver.RemoteEndpointInfo();
  }

  @Test
  public void testGetNextEndpointWithEmptyList() {
    // Test line 265-267: getNextEndpoint returns empty when list is empty
    Optional<Endpoint> result = info.getNextEndpoint();
    assertFalse(result.isPresent());
  }

  @Test
  public void testGetEndpointAtWithEmptyList() {
    // Test that getEndpointAt handles empty list
    assertFalse(info.getEndpointAt(0).isPresent());
    assertFalse(info.getEndpointAt(1).isPresent());
    assertFalse(info.getEndpointAt(-1).isPresent());
  }

  @Test
  public void testGetEndpointAtWithNegativeIndex() {
    // Add some endpoints
    info.addEndpoint(new Endpoint("host1", 8001));
    info.addEndpoint(new Endpoint("host2", 8002));
    
    // Test line 276: negative index returns empty
    Optional<Endpoint> result = info.getEndpointAt(-1);
    assertFalse(result.isPresent());
    
    // Also test -2 for good measure
    assertFalse(info.getEndpointAt(-2).isPresent());
  }

  @Test
  public void testGetEndpointAtWithIndexOutOfBounds() {
    // Add some endpoints
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    info.addEndpoint(endpoint1);
    info.addEndpoint(endpoint2);
    
    // Valid indices should work
    assertEquals(endpoint1, info.getEndpointAt(0).get());
    assertEquals(endpoint2, info.getEndpointAt(1).get());
    
    // Test line 276-278: index >= size returns empty
    Optional<Endpoint> result = info.getEndpointAt(2);
    assertFalse(result.isPresent());
    
    // Test with larger indices
    assertFalse(info.getEndpointAt(3).isPresent());
    assertFalse(info.getEndpointAt(100).isPresent());
  }

  @Test
  public void testAddEndpointPreventsNulls() {
    // The addEndpoint method checks if endpoint is already in the list
    // Let's test the branch where we try to add duplicate
    Endpoint endpoint = new Endpoint("host", 8080);
    
    // Add it once
    info.addEndpoint(endpoint);
    assertEquals(1, info.getAllEndpoints().size());
    
    // Try to add it again - should be ignored
    info.addEndpoint(endpoint);
    assertEquals(1, info.getAllEndpoints().size());
  }

  @Test
  public void testRemoveEndpointReturnValue() {
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    
    // Test removing non-existent endpoint
    assertFalse(info.removeEndpoint(endpoint1));
    
    // Add and remove
    info.addEndpoint(endpoint1);
    info.addEndpoint(endpoint2);
    
    // Remove existing endpoint
    assertTrue(info.removeEndpoint(endpoint1));
    assertEquals(1, info.getAllEndpoints().size());
    
    // Try to remove it again
    assertFalse(info.removeEndpoint(endpoint1));
    
    // Remove the last endpoint
    assertTrue(info.removeEndpoint(endpoint2));
    assertTrue(info.isEmpty());
  }

  @Test
  public void testContainsEndpoint() {
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    Endpoint endpoint3 = new Endpoint("host3", 8003);
    
    // Initially empty
    assertFalse(info.containsEndpoint(endpoint1));
    
    // Add endpoints
    info.addEndpoint(endpoint1);
    info.addEndpoint(endpoint2);
    
    // Test contains
    assertTrue(info.containsEndpoint(endpoint1));
    assertTrue(info.containsEndpoint(endpoint2));
    assertFalse(info.containsEndpoint(endpoint3));
  }

  @Test
  public void testRoundRobinWithEmptyThenPopulated() {
    // First test with empty list
    assertFalse(info.getNextEndpoint().isPresent());
    
    // Now add endpoints and test round-robin works
    Endpoint endpoint1 = new Endpoint("host1", 8001);
    Endpoint endpoint2 = new Endpoint("host2", 8002);
    info.addEndpoint(endpoint1);
    info.addEndpoint(endpoint2);
    
    // Should cycle through endpoints
    assertEquals(endpoint1, info.getNextEndpoint().get());
    assertEquals(endpoint2, info.getNextEndpoint().get());
    assertEquals(endpoint1, info.getNextEndpoint().get());
  }
}