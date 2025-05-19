package io.github.panghy.javaflow.rpc.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Tests for the RpcMessageHeader class.
 * These tests verify that headers can be properly serialized and deserialized.
 */
public class RpcMessageHeaderTest {

  @Test
  void testHeaderGetters() {
    // Create a message header
    RpcMessageHeader.MessageType type = RpcMessageHeader.MessageType.REQUEST;
    UUID messageId = UUID.randomUUID();
    String methodId = "testMethod";
    int payloadLength = 100;
    
    RpcMessageHeader header = new RpcMessageHeader(type, messageId, methodId, payloadLength);
    
    // Verify getters return correct values
    assertEquals(type, header.getType());
    assertEquals(messageId, header.getMessageId());
    assertEquals(methodId, header.getMethodId());
    assertEquals(payloadLength, header.getPayloadLength());
  }
  
  @Test
  void testSerializeAndDeserialize() {
    // Create a message header
    RpcMessageHeader.MessageType type = RpcMessageHeader.MessageType.REQUEST;
    UUID messageId = UUID.randomUUID();
    String methodId = "testMethod";
    int payloadLength = 100;
    
    RpcMessageHeader header = new RpcMessageHeader(type, messageId, methodId, payloadLength);
    
    // Serialize the header
    ByteBuffer buffer = header.serialize();
    assertNotNull(buffer);
    
    // Deserialize the header
    RpcMessageHeader deserialized = RpcMessageHeader.deserialize(buffer);
    
    // Verify the deserialized header matches the original
    assertEquals(type, deserialized.getType());
    assertEquals(messageId, deserialized.getMessageId());
    assertEquals(methodId, deserialized.getMethodId());
    assertEquals(payloadLength, deserialized.getPayloadLength());
  }
  
  @Test
  void testSerializeWithNullValues() {
    // Create a header with null values
    RpcMessageHeader header = new RpcMessageHeader(
        RpcMessageHeader.MessageType.RESPONSE, null, null, 0);
    
    // Serialize the header
    ByteBuffer buffer = header.serialize();
    
    // Deserialize the header
    RpcMessageHeader deserialized = RpcMessageHeader.deserialize(buffer);
    
    // Verify the deserialized header
    assertEquals(RpcMessageHeader.MessageType.RESPONSE, deserialized.getType());
    assertNull(deserialized.getMessageId());
    assertNull(deserialized.getMethodId());
    assertEquals(0, deserialized.getPayloadLength());
  }
  
  @Test
  void testAllMessageTypes() {
    // Test all message types can be serialized and deserialized
    for (RpcMessageHeader.MessageType type : RpcMessageHeader.MessageType.values()) {
      RpcMessageHeader header = new RpcMessageHeader(
          type, UUID.randomUUID(), "method_" + type.name(), 10);
      
      ByteBuffer buffer = header.serialize();
      RpcMessageHeader deserialized = RpcMessageHeader.deserialize(buffer);
      
      assertEquals(type, deserialized.getType());
    }
  }
  
  @Test
  void testMessageTypeFromCode() {
    // Test that we can convert codes to message types
    for (RpcMessageHeader.MessageType type : RpcMessageHeader.MessageType.values()) {
      assertEquals(type, RpcMessageHeader.MessageType.fromCode(type.getCode()));
    }
    
    // Test invalid code
    assertThrows(IllegalArgumentException.class, () -> {
      RpcMessageHeader.MessageType.fromCode(999);
    });
  }
  
  @Test
  void testLongMethodId() {
    // Create a message header with a long method ID
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      sb.append("method");
    }
    String longMethodId = sb.toString();
    
    RpcMessageHeader header = new RpcMessageHeader(
        RpcMessageHeader.MessageType.REQUEST, UUID.randomUUID(), longMethodId, 100);
    
    // Serialize and deserialize
    ByteBuffer buffer = header.serialize();
    RpcMessageHeader deserialized = RpcMessageHeader.deserialize(buffer);
    
    // Verify long method ID is preserved
    assertEquals(longMethodId, deserialized.getMethodId());
  }
  
  @Test
  void testToString() {
    // Create a header
    UUID messageId = UUID.randomUUID();
    RpcMessageHeader header = new RpcMessageHeader(
        RpcMessageHeader.MessageType.REQUEST, messageId, "testMethod", 100);
    
    // Verify toString contains all fields
    String result = header.toString();
    assertNotNull(result);
    
    // Verify the string contains all important information
    assertTrue(result.contains("REQUEST"));
    assertTrue(result.contains(messageId.toString()));
    assertTrue(result.contains("testMethod"));
    assertTrue(result.contains("100"));
  }
  
  private void assertTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("Expected true but got false");
    }
  }
}