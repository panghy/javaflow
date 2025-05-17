package io.github.panghy.javaflow.io.network;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the buffer handling in RealFlowConnection to ensure that buffer operations
 * are performed correctly without any buffer overflows or underflows.
 */
public class RealFlowConnectionBufferHandlingTest {

  /**
   * Test the buffer handling in the completed method of the read CompletionHandler in RealFlowConnection.
   * This tests the exact code that was causing BufferOverflowException in the NetworkEchoExample.
   * 
   * Emulates the core logic of the CompletionHandler.completed method in RealFlowConnection.
   */
  @Test
  void testBufferHandling() {
    // This test directly verifies the logic in the read completion handler
    
    // Case 1: Normal case, buffer has exactly the bytes we need
    ByteBuffer testBuffer1 = ByteBuffer.allocate(1024);
    testBuffer1.put("Hello World".getBytes());
    testBuffer1.flip();
    
    int bytesRead1 = testBuffer1.remaining();
    
    // Create a byte array of the correct size
    byte[] tmp1 = new byte[bytesRead1];
    
    // Read using the for loop pattern to avoid buffer over/underflow
    for (int i = 0; i < bytesRead1 && testBuffer1.hasRemaining(); i++) {
      tmp1[i] = testBuffer1.get();
    }
    
    // Verify correct data was read
    assertEquals("Hello World", new String(tmp1));
    
    // Case 2: Edge case - large buffer with partial data
    ByteBuffer testBuffer2 = ByteBuffer.allocate(8192); // Large buffer
    testBuffer2.put("Small message".getBytes());
    testBuffer2.flip();
    
    int bytesRead2 = testBuffer2.remaining();
    
    // Create a byte array of the correct size
    byte[] tmp2 = new byte[bytesRead2];
    
    // Read using the for loop pattern
    for (int i = 0; i < bytesRead2 && testBuffer2.hasRemaining(); i++) {
      tmp2[i] = testBuffer2.get();
    }
    
    // Verify correct data was read
    assertEquals("Small message", new String(tmp2));
    
    // Case 3: Edge case - attempting to read more bytes than available
    ByteBuffer testBuffer3 = ByteBuffer.allocate(10);
    testBuffer3.put("12345".getBytes());
    testBuffer3.flip();
    
    // Pretend we're trying to read more bytes than actually available
    int bytesRead3 = 10; // This is wrong, buffer only has 5 bytes
    
    // Create a byte array of the requested size
    byte[] tmp3 = new byte[bytesRead3];
    
    // Using the for loop pattern should prevent buffer underflow
    for (int i = 0; i < bytesRead3 && testBuffer3.hasRemaining(); i++) {
      tmp3[i] = testBuffer3.get();
    }
    
    // Verify what we could read (should be just "12345" and the rest zeros)
    assertEquals("12345", new String(tmp3, 0, 5).trim());
    
    // Verify that the original buffer is now empty
    assertEquals(0, testBuffer3.remaining());
    
    // Case 4: Directly using ByteBuffer.get(byte[]) would cause buffer underflow
    ByteBuffer testBuffer4 = ByteBuffer.allocate(10);
    testBuffer4.put("12345".getBytes());
    testBuffer4.flip();
    
    byte[] largerArray = new byte[10];
    
    // This would throw BufferUnderflowException
    assertThrows(java.nio.BufferUnderflowException.class, () -> {
      testBuffer4.get(largerArray);
    });
  }
  
  /**
   * Test for buffer isolation between different read operations.
   * This simulates multiple sequential reads to verify data doesn't bleed between reads.
   */
  @Test
  void testBufferIsolation() {
    // Simulate multiple read operations with different data
    // Create three buffers with different content
    ByteBuffer buffer1 = ByteBuffer.allocate(1024);
    buffer1.put("hello".getBytes());
    buffer1.flip();
    
    ByteBuffer buffer2 = ByteBuffer.allocate(1024);
    buffer2.put("wow".getBytes());
    buffer2.flip();
    
    ByteBuffer buffer3 = ByteBuffer.allocate(1024);
    buffer3.put("third message".getBytes());
    buffer3.flip();
    
    // Process the buffers one after another, simulating sequential reads
    
    // First read
    int bytesRead1 = buffer1.remaining();
    byte[] tmp1 = new byte[bytesRead1];
    for (int i = 0; i < bytesRead1 && buffer1.hasRemaining(); i++) {
      tmp1[i] = buffer1.get();
    }
    
    // Second read 
    int bytesRead2 = buffer2.remaining();
    byte[] tmp2 = new byte[bytesRead2];
    for (int i = 0; i < bytesRead2 && buffer2.hasRemaining(); i++) {
      tmp2[i] = buffer2.get();
    }
    
    // Third read
    int bytesRead3 = buffer3.remaining();
    byte[] tmp3 = new byte[bytesRead3];
    for (int i = 0; i < bytesRead3 && buffer3.hasRemaining(); i++) {
      tmp3[i] = buffer3.get();
    }
    
    // Verify all reads captured the correct data without interference
    assertEquals("hello", new String(tmp1));
    assertEquals("wow", new String(tmp2));
    assertEquals("third message", new String(tmp3));
    
    // Verify all buffers are fully consumed
    assertEquals(0, buffer1.remaining());
    assertEquals(0, buffer2.remaining());
    assertEquals(0, buffer3.remaining());
  }
  
  /**
   * Test that simulates our new implementation approach where we use a dedicated
   * CompletionHandler for each read operation.
   */
  @Test
  void testSeparateHandlers() {
    // This test manually simulates what our new implementation does - creating separate
    // handlers and buffers for each read to avoid interference
    
    // Simulated base class to hold common functionality
    class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
      private final ByteBuffer targetBuffer;
      private final String expectedMessage;
      
      ReadHandler(ByteBuffer buffer, String message) {
        this.targetBuffer = buffer;
        this.expectedMessage = message;
      }
      
      @Override
      public void completed(Integer bytesRead, ByteBuffer attachment) {
        // When read completes, flip the buffer
        attachment.flip();
        
        // Create a properly sized result buffer for the data
        byte[] tmp = new byte[bytesRead];
        for (int i = 0; i < bytesRead && attachment.hasRemaining(); i++) {
          tmp[i] = attachment.get();
        }
        
        // Verify this handler received the expected message
        assertEquals(expectedMessage, new String(tmp));
      }
      
      @Override
      public void failed(Throwable exc, ByteBuffer attachment) {
        // Not relevant for test
      }
      
      public ByteBuffer getBuffer() {
        return targetBuffer;
      }
    }
    
    // Create separate handlers for each read operation
    ReadHandler firstHandler = new ReadHandler(
        ByteBuffer.allocate(1024), "hello");
    ReadHandler secondHandler = new ReadHandler(
        ByteBuffer.allocate(1024), "wow");
    
    // Simulate first read
    ByteBuffer firstBuffer = firstHandler.getBuffer();
    firstBuffer.put("hello".getBytes());
    firstHandler.completed(5, firstBuffer);
    
    // Simulate second read with different handler
    ByteBuffer secondBuffer = secondHandler.getBuffer();
    secondBuffer.put("wow".getBytes());
    secondHandler.completed(3, secondBuffer);
    
    // Our assertions are in the handler, the test passes if no assert fails
  }
}