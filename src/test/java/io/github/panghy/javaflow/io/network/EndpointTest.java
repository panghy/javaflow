package io.github.panghy.javaflow.io.network;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the Endpoint class.
 */
public class EndpointTest {

  @Test
  void testConstructor() {
    // Create an endpoint
    Endpoint endpoint = new Endpoint("example.com", 8080);
    
    // Verify it has the correct host and port
    assertEquals("example.com", endpoint.getHost());
    assertEquals(8080, endpoint.getPort());
  }
  
  @Test
  void testInetSocketAddressConstructor() {
    // Create an endpoint from an InetSocketAddress
    InetSocketAddress address = new InetSocketAddress("example.com", 8080);
    Endpoint endpoint = new Endpoint(address);
    
    // Verify it has the correct host and port
    assertEquals("example.com", endpoint.getHost());
    assertEquals(8080, endpoint.getPort());
  }
  
  @Test
  void testNullHostThrowsException() {
    // Verify that null host throws NullPointerException
    assertThrows(NullPointerException.class, () -> new Endpoint(null, 8080));
  }
  
  @Test
  void testInvalidPortThrowsException() {
    // Verify that invalid port throws IllegalArgumentException
    assertThrows(IllegalArgumentException.class, () -> new Endpoint("example.com", -1));
    assertThrows(IllegalArgumentException.class, () -> new Endpoint("example.com", 65536));
  }
  
  @Test
  void testToInetSocketAddress() {
    // Create an endpoint
    Endpoint endpoint = new Endpoint("example.com", 8080);
    
    // Convert to InetSocketAddress
    InetSocketAddress address = endpoint.toInetSocketAddress();
    
    // Verify it's correct
    assertEquals("example.com", address.getHostString());
    assertEquals(8080, address.getPort());
  }
  
  @Test
  void testEqualsAndHashCode() {
    // Create two equal endpoints
    Endpoint endpoint1 = new Endpoint("example.com", 8080);
    Endpoint endpoint2 = new Endpoint("example.com", 8080);
    
    // Verify they're equal and have the same hashCode
    assertEquals(endpoint1, endpoint2);
    assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
    
    // Create different endpoints
    Endpoint endpoint3 = new Endpoint("example.com", 8081);
    Endpoint endpoint4 = new Endpoint("example.org", 8080);
    
    // Verify they're not equal to the first
    assertNotEquals(endpoint1, endpoint3);
    assertNotEquals(endpoint1, endpoint4);
    
    // Verify non-endpoint objects are not equal
    assertNotEquals(endpoint1, "not an endpoint");
    assertNotEquals(endpoint1, null);
    
    // Endpoint should equal itself
    assertEquals(endpoint1, endpoint1);
  }
  
  @Test
  void testToString() {
    // Create an endpoint
    Endpoint endpoint = new Endpoint("example.com", 8080);
    
    // Verify toString returns the expected format
    assertEquals("example.com:8080", endpoint.toString());
  }
  
  @Test
  void testEqualsWithDifferentClasses() {
    // Create an endpoint and a local endpoint with the same host/port
    Endpoint endpoint = new Endpoint("example.com", 8080);
    LocalEndpoint localEndpoint = new LocalEndpoint("example.com", 8080);
    
    // They should not be equal because they're different classes
    assertNotEquals(endpoint, localEndpoint);
  }
}