package io.github.panghy.javaflow.rpc.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Tests for the RpcMessage class.
 * These tests verify that messages can be properly serialized and deserialized.
 */
public class RpcMessageTest {

  @Test
  void testMessageGetters() {
    // Create a message
    RpcMessageHeader header = new RpcMessageHeader(
        RpcMessageHeader.MessageType.REQUEST, UUID.randomUUID(), "testMethod", 10);
    List<UUID> promiseIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
    ByteBuffer payload = ByteBuffer.wrap("payload".getBytes(StandardCharsets.UTF_8));
    
    RpcMessage message = new RpcMessage(header, promiseIds, payload);
    
    // Verify getters return correct values
    assertEquals(header, message.getHeader());
    assertEquals(promiseIds, message.getPromiseIds());
    assertEquals(payload.duplicate().remaining(), message.getPayload().remaining());
    
    // Check payload contents
    byte[] originalBytes = new byte[payload.remaining()];
    byte[] retrievedBytes = new byte[message.getPayload().remaining()];
    payload.duplicate().get(originalBytes);
    message.getPayload().get(retrievedBytes);
    assertTrue(Arrays.equals(originalBytes, retrievedBytes));
  }
  
  @Test
  void testAlternativeConstructor() {
    // Test the alternative constructor
    RpcMessageHeader.MessageType type = RpcMessageHeader.MessageType.REQUEST;
    UUID messageId = UUID.randomUUID();
    String methodId = "testMethod";
    List<UUID> promiseIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
    ByteBuffer payload = ByteBuffer.wrap("payload".getBytes(StandardCharsets.UTF_8));
    
    RpcMessage message = new RpcMessage(type, messageId, methodId, promiseIds, payload);
    
    // Verify header is created correctly
    assertEquals(type, message.getHeader().getType());
    assertEquals(messageId, message.getHeader().getMessageId());
    assertEquals(methodId, message.getHeader().getMethodId());
    assertEquals(payload.remaining(), message.getHeader().getPayloadLength());
    
    // Verify other fields
    assertEquals(promiseIds, message.getPromiseIds());
    assertEquals(payload.duplicate().remaining(), message.getPayload().remaining());
  }
  
  @Test
  void testSerializeAndDeserialize() {
    // Create a message
    String testData = "testPayload";
    ByteBuffer payload = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    int payloadLength = payload.remaining();
    
    RpcMessageHeader header = new RpcMessageHeader(
        RpcMessageHeader.MessageType.REQUEST, UUID.randomUUID(), "testMethod", payloadLength);
    List<UUID> promiseIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
    
    RpcMessage message = new RpcMessage(header, promiseIds, payload.duplicate());
    
    // Serialize the message
    ByteBuffer buffer = message.serialize();
    assertNotNull(buffer);
    
    // Deserialize the message
    RpcMessage deserialized = RpcMessage.deserialize(buffer);
    
    // Verify the deserialized message
    assertEquals(header.getType(), deserialized.getHeader().getType());
    assertEquals(header.getMessageId(), deserialized.getHeader().getMessageId());
    assertEquals(header.getMethodId(), deserialized.getHeader().getMethodId());
    assertEquals(promiseIds.size(), deserialized.getPromiseIds().size());
    
    // Verify promise IDs
    for (int i = 0; i < promiseIds.size(); i++) {
      assertEquals(promiseIds.get(i), deserialized.getPromiseIds().get(i));
    }
    
    // Verify payload
    assertNotNull(deserialized.getPayload());
    assertEquals(payloadLength, deserialized.getHeader().getPayloadLength());
    
    // Get the deserialized payload as a string for comparison
    ByteBuffer deserializedPayload = deserialized.getPayload();
    byte[] bytes = new byte[deserializedPayload.remaining()];
    deserializedPayload.get(bytes);
    String deserializedData = new String(bytes, StandardCharsets.UTF_8);
    
    // Verify the payload contents
    assertEquals(testData, deserializedData);
  }
  
  @Test
  void testWithNullValues() {
    // Create a message with null values
    RpcMessage message = new RpcMessage(
        new RpcMessageHeader(RpcMessageHeader.MessageType.RESPONSE, null, null, 0),
        null, null);
    
    // Serialize the message
    ByteBuffer buffer = message.serialize();
    
    // Deserialize the message
    RpcMessage deserialized = RpcMessage.deserialize(buffer);
    
    // Verify fields
    assertEquals(RpcMessageHeader.MessageType.RESPONSE, deserialized.getHeader().getType());
    assertNull(deserialized.getHeader().getMessageId());
    assertNull(deserialized.getHeader().getMethodId());
    assertEquals(0, deserialized.getPromiseIds().size());
    assertEquals(0, deserialized.getHeader().getPayloadLength());
    assertNull(deserialized.getPayload());
  }
  
  @Test
  void testWithEmptyPromiseIds() {
    // Create a message with empty promise IDs
    String testData = "payload";
    ByteBuffer payload = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    int payloadLength = payload.remaining();
    
    RpcMessage message = new RpcMessage(
        new RpcMessageHeader(RpcMessageHeader.MessageType.REQUEST, UUID.randomUUID(), "method", payloadLength),
        Collections.emptyList(),
        payload);
    
    // Serialize and deserialize
    ByteBuffer buffer = message.serialize();
    RpcMessage deserialized = RpcMessage.deserialize(buffer);
    
    // Verify empty promise IDs
    assertNotNull(deserialized.getPromiseIds());
    assertEquals(0, deserialized.getPromiseIds().size());
    
    // Verify payload
    assertNotNull(deserialized.getPayload());
    assertEquals(payloadLength, deserialized.getHeader().getPayloadLength());
    
    // Get the deserialized payload as a string for comparison
    ByteBuffer deserializedPayload = deserialized.getPayload();
    byte[] bytes = new byte[deserializedPayload.remaining()];
    deserializedPayload.get(bytes);
    String deserializedData = new String(bytes, StandardCharsets.UTF_8);
    
    // Verify the payload contents
    assertEquals(testData, deserializedData);
  }
  
  @Test
  void testWithManyPromiseIds() {
    // Create a message with many promise IDs
    List<UUID> manyPromises = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      manyPromises.add(UUID.randomUUID());
    }
    
    String testData = "payload";
    ByteBuffer payload = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    int payloadLength = payload.remaining();
    
    RpcMessage message = new RpcMessage(
        new RpcMessageHeader(RpcMessageHeader.MessageType.REQUEST, UUID.randomUUID(), "method", payloadLength),
        manyPromises,
        payload);
    
    // Serialize and deserialize
    ByteBuffer buffer = message.serialize();
    RpcMessage deserialized = RpcMessage.deserialize(buffer);
    
    // Verify all promise IDs are preserved
    assertEquals(manyPromises.size(), deserialized.getPromiseIds().size());
    for (int i = 0; i < manyPromises.size(); i++) {
      assertEquals(manyPromises.get(i), deserialized.getPromiseIds().get(i));
    }
    
    // Verify payload
    assertNotNull(deserialized.getPayload());
    assertEquals(payloadLength, deserialized.getHeader().getPayloadLength());
    
    // Get the deserialized payload as a string for comparison
    ByteBuffer deserializedPayload = deserialized.getPayload();
    byte[] bytes = new byte[deserializedPayload.remaining()];
    deserializedPayload.get(bytes);
    String deserializedData = new String(bytes, StandardCharsets.UTF_8);
    
    // Verify the payload contents
    assertEquals(testData, deserializedData);
  }
  
  @Test
  void testWithLargePayload() {
    // Create a message with a large payload
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10000; i++) {
      sb.append("payload");
    }
    ByteBuffer largePayload = ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8));
    
    RpcMessage message = new RpcMessage(
        RpcMessageHeader.MessageType.REQUEST,
        UUID.randomUUID(),
        "testMethod",
        Collections.singletonList(UUID.randomUUID()),
        largePayload);
    
    // Serialize and deserialize
    ByteBuffer buffer = message.serialize();
    RpcMessage deserialized = RpcMessage.deserialize(buffer);
    
    // Verify payload size
    assertEquals(largePayload.remaining(), deserialized.getPayload().remaining());
    
    // Verify header payload length field
    assertEquals(largePayload.remaining(), deserialized.getHeader().getPayloadLength());
  }
  
  @Test
  void testToString() {
    // Create a message
    UUID messageId = UUID.randomUUID();
    UUID promiseId = UUID.randomUUID();
    RpcMessage message = new RpcMessage(
        new RpcMessageHeader(
            RpcMessageHeader.MessageType.REQUEST, messageId, "testMethod", 10),
        Collections.singletonList(promiseId),
        ByteBuffer.wrap("payload".getBytes(StandardCharsets.UTF_8)));
    
    // Verify toString contains important information
    String result = message.toString();
    assertNotNull(result);
    
    // Check that the string contains important fields
    assertTrue(result.contains("REQUEST"));
    assertTrue(result.contains(messageId.toString()));
    assertTrue(result.contains("testMethod"));
    assertTrue(result.contains(promiseId.toString()));
    assertTrue(result.contains("7")); // Payload size (length of "payload")
  }
}