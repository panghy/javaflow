package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for RealFlowFile using Mockito to mock the underlying AsynchronousFileChannel.
 * This allows precise control over the completion handlers to test specific code paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealFlowFileMockTest extends AbstractFlowTest {

  @Mock
  private AsynchronousFileChannel mockChannel;

  private Path testPath;
  private RealFlowFile file;

  @BeforeEach
  void setUp() {
    testPath = Paths.get("/mock/test-file.txt");
    file = new RealFlowFile(mockChannel, testPath);
  }

  /**
   * Tests the write completion handler with partial writes that trigger
   * multiple write operations due to the buffer not being fully consumed.
   */
  @Test
  void testWriteWithMultipleOperations() throws Exception {
    // Create test data
    byte[] testData = new byte[100];
    for (int i = 0; i < testData.length; i++) {
      testData[i] = (byte) i;
    }
    ByteBuffer testBuffer = ByteBuffer.wrap(testData);
    
    // When write is called, capture the completion handler and simulate a partial write
    doAnswer((Answer<Void>) invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      Object attachment = invocation.getArgument(2);
      CompletionHandler<Integer, Object> handler = invocation.getArgument(3);

      // Only write half the data to trigger another write
      int bytesToWrite = 50;

      // Advance the buffer position to simulate writing
      int oldPosition = buffer.position();
      buffer.position(oldPosition + bytesToWrite);

      // Complete the operation
      handler.completed(bytesToWrite, attachment);
      return null;
    }).when(mockChannel).write(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Call the method
    FlowFuture<Void> result = file.write(0, testBuffer);
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(result);
    
    // Verify write was called at least twice (one for partial, one for completion)
    verify(mockChannel, times(2)).write(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Verify no exception
    result.getNow(); // Should not throw
  }
  
  /**
   * Tests the write completion handler's failed path.
   */
  @Test
  void testWriteFailure() throws Exception {
    // Create test data
    ByteBuffer testData = ByteBuffer.wrap(new byte[10]);
    
    // When write is called, capture the completion handler and simulate a failure
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        CompletionHandler<Integer, Object> handler = invocation.getArgument(3);
        handler.failed(new IOException("Simulated write failure"), null);
        return null;
      }
    }).when(mockChannel).write(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Call the method
    FlowFuture<Void> result = file.write(0, testData);
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(result);
    
    // Verify write was called once
    verify(mockChannel, times(1)).write(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Verify exception
    ExecutionException e = assertThrows(ExecutionException.class, () -> result.getNow());
    assertEquals("Simulated write failure", e.getCause().getMessage());
  }
  
  /**
   * Tests the read completion handler's behavior with end-of-file condition.
   */
  @Test
  void testReadEOF() throws Exception {
    // When read is called, capture the completion handler and simulate EOF
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        ByteBuffer buffer = invocation.getArgument(0);
        CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(3);
        
        // -1 indicates EOF
        handler.completed(-1, buffer);
        return null;
      }
    }).when(mockChannel).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Call the method
    FlowFuture<ByteBuffer> result = file.read(0, 10);
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(result);
    
    // Verify read was called once
    verify(mockChannel, times(1)).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Verify result is an empty buffer
    ByteBuffer buffer = result.getNow();
    assertEquals(0, buffer.remaining());
  }
  
  /**
   * Tests the read completion handler's behavior with partial data.
   */
  @Test
  void testReadPartial() throws Exception {
    // When read is called, capture the completion handler and simulate partial read
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        ByteBuffer buffer = invocation.getArgument(0);
        int requestedLength = buffer.remaining();
        int partialLength = requestedLength / 2; // Read half of requested
        
        CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(3);
        
        // Fill buffer with some data
        buffer.clear();
        for (int i = 0; i < partialLength; i++) {
          buffer.put((byte) i);
        }
        
        // Complete with partial read
        handler.completed(partialLength, buffer);
        return null;
      }
    }).when(mockChannel).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Call the method
    FlowFuture<ByteBuffer> result = file.read(0, 10);
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(result);
    
    // Verify read was called once
    verify(mockChannel, times(1)).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Verify result has partial data
    ByteBuffer buffer = result.getNow();
    assertEquals(5, buffer.remaining()); // Half of the requested 10
  }
  
  /**
   * Tests the read completion handler's behavior with full data.
   */
  @Test
  void testReadFull() throws Exception {
    // When read is called, capture the completion handler and simulate full read
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        ByteBuffer buffer = invocation.getArgument(0);
        int requestedLength = buffer.remaining();
        
        CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(3);
        
        // Fill buffer with data
        buffer.clear();
        for (int i = 0; i < requestedLength; i++) {
          buffer.put((byte) i);
        }
        
        // Complete with full read
        handler.completed(requestedLength, buffer);
        return null;
      }
    }).when(mockChannel).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Call the method
    int requestSize = 10;
    FlowFuture<ByteBuffer> result = file.read(0, requestSize);
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(result);
    
    // Verify read was called once
    verify(mockChannel, times(1)).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Verify result has full data
    ByteBuffer buffer = result.getNow();
    assertEquals(requestSize, buffer.remaining());
  }
  
  /**
   * Tests the read completion handler's failure path.
   */
  @Test
  void testReadFailure() throws Exception {
    // When read is called, capture the completion handler and simulate failure
    doAnswer((Answer<Void>) invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(3);

      handler.failed(new IOException("Simulated read failure"), buffer);
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Call the method
    FlowFuture<ByteBuffer> result = file.read(0, 10);
    
    // Wait for completion
    pumpAndAdvanceTimeUntilDone(result);
    
    // Verify read was called once
    verify(mockChannel, times(1)).read(any(ByteBuffer.class), anyLong(), any(), any());
    
    // Verify exception
    ExecutionException e = assertThrows(ExecutionException.class, () -> result.getNow());
    assertEquals("Simulated read failure", e.getCause().getMessage());
  }
}