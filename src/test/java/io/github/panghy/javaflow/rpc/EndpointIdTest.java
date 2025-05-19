package io.github.panghy.javaflow.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the EndpointId class.
 */
public class EndpointIdTest {

  @Test
  void testConstructorWithValidId() {
    // Create a new endpoint ID with a valid identifier
    String id = "test-endpoint";
    EndpointId endpointId = new EndpointId(id);
    
    // Verify that the ID is stored correctly
    assertEquals(id, endpointId.getId());
  }
  
  @Test
  void testConstructorWithNullId() {
    // Verify that a NullPointerException is thrown if the ID is null
    assertThrows(NullPointerException.class, () -> {
      new EndpointId(null);
    });
  }
  
  @Test
  void testEquals() {
    // Create two endpoint IDs with the same identifier
    EndpointId id1 = new EndpointId("test-endpoint");
    EndpointId id2 = new EndpointId("test-endpoint");
    
    // Verify that they are equal
    assertEquals(id1, id2);
    assertEquals(id2, id1);
    assertTrue(id1.equals(id2));
    assertTrue(id2.equals(id1));
    
    // Verify that an endpoint ID is equal to itself
    assertTrue(id1.equals(id1));
  }
  
  @Test
  void testNotEquals() {
    // Create two endpoint IDs with different identifiers
    EndpointId id1 = new EndpointId("test-endpoint-1");
    EndpointId id2 = new EndpointId("test-endpoint-2");
    
    // Verify that they are not equal
    assertNotEquals(id1, id2);
    assertNotEquals(id2, id1);
    assertFalse(id1.equals(id2));
    assertFalse(id2.equals(id1));
    
    // Verify that an endpoint ID is not equal to null
    assertFalse(id1.equals(null));
    
    // Verify that an endpoint ID is not equal to an object of a different type
    assertFalse(id1.equals("test-endpoint-1"));
  }
  
  @Test
  void testHashCode() {
    // Create two endpoint IDs with the same identifier
    EndpointId id1 = new EndpointId("test-endpoint");
    EndpointId id2 = new EndpointId("test-endpoint");
    
    // Verify that they have the same hash code
    assertEquals(id1.hashCode(), id2.hashCode());
    
    // Create two endpoint IDs with different identifiers
    EndpointId id3 = new EndpointId("test-endpoint-1");
    EndpointId id4 = new EndpointId("test-endpoint-2");
    
    // Verify that they have different hash codes
    assertNotEquals(id3.hashCode(), id4.hashCode());
  }
  
  @Test
  void testToString() {
    // Create an endpoint ID
    String id = "test-endpoint";
    EndpointId endpointId = new EndpointId(id);
    
    // Verify that toString returns a string containing the ID
    String toString = endpointId.toString();
    assertNotNull(toString);
    assertTrue(toString.contains(id));
    assertEquals("EndpointId[" + id + "]", toString);
  }
  
  @Test
  void testWithEmptyId() {
    // Create an endpoint ID with an empty string
    EndpointId endpointId = new EndpointId("");
    
    // Verify that the ID is stored correctly
    assertEquals("", endpointId.getId());
    
    // Verify that toString works correctly with an empty ID
    assertEquals("EndpointId[]", endpointId.toString());
  }
  
  @Test
  void testWithSpecialCharacters() {
    // Create an endpoint ID with special characters
    String id = "test@endpoint#123$%^&*()";
    EndpointId endpointId = new EndpointId(id);
    
    // Verify that the ID is stored correctly
    assertEquals(id, endpointId.getId());
    
    // Verify that toString works correctly with special characters
    assertEquals("EndpointId[" + id + "]", endpointId.toString());
  }
}