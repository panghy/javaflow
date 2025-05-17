package io.github.panghy.javaflow.io.network;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the LocalEndpoint class.
 */
public class LocalEndpointTest {

  @Test
  void testLocalhost() {
    // Create a localhost endpoint with the factory method
    LocalEndpoint localhost = LocalEndpoint.localhost(8080);
    
    // Verify it has the correct host and port
    assertEquals("localhost", localhost.getHost());
    assertEquals(8080, localhost.getPort());
  }
  
  @Test
  void testAnyHost() {
    // Create an anyHost endpoint
    LocalEndpoint anyHost = LocalEndpoint.anyHost(8080);
    
    // Verify it has the correct host and port
    assertEquals("0.0.0.0", anyHost.getHost());
    assertEquals(8080, anyHost.getPort());
  }
  
  @Test
  void testConstructor() {
    // Create a local endpoint with a specific host
    LocalEndpoint endpoint = new LocalEndpoint("example.com", 8080);
    
    // Verify it has the correct host and port
    assertEquals("example.com", endpoint.getHost());
    assertEquals(8080, endpoint.getPort());
  }
  
  @Test
  void testInetSocketAddressConstructor() {
    // Create a local endpoint from an InetSocketAddress
    InetSocketAddress address = new InetSocketAddress("example.com", 8080);
    LocalEndpoint endpoint = new LocalEndpoint(address);
    
    // Verify it has the correct host and port
    assertEquals("example.com", endpoint.getHost());
    assertEquals(8080, endpoint.getPort());
  }
  
  @Test
  void testInheritance() {
    // Create a local endpoint
    LocalEndpoint endpoint = new LocalEndpoint("example.com", 8080);
    
    // Verify it's an instance of Endpoint
    assertTrue(endpoint instanceof Endpoint);
    
    // Verify methods from Endpoint work
    assertEquals("example.com", endpoint.getHost());
    assertEquals(8080, endpoint.getPort());
    assertEquals("example.com:8080", endpoint.toString());
    
    // Verify conversion to InetSocketAddress
    InetSocketAddress address = endpoint.toInetSocketAddress();
    assertEquals("example.com", address.getHostString());
    assertEquals(8080, address.getPort());
  }
  
  @Test
  void testEqualsAndHashCode() {
    // Create two equal endpoints
    LocalEndpoint endpoint1 = new LocalEndpoint("example.com", 8080);
    LocalEndpoint endpoint2 = new LocalEndpoint("example.com", 8080);
    
    // Verify they're equal and have the same hashCode
    assertEquals(endpoint1, endpoint2);
    assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
    
    // Create a different endpoint
    LocalEndpoint endpoint3 = new LocalEndpoint("example.com", 8081);
    
    // Verify it's not equal to the first
    assertNotEquals(endpoint1, endpoint3);
  }
  
  @Test
  void testToString() {
    // Create a local endpoint
    LocalEndpoint endpoint = new LocalEndpoint("example.com", 8080);
    
    // Verify toString returns the expected format
    assertEquals("example.com:8080", endpoint.toString());
  }
}