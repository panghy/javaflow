package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the pull-based behavior of RealFlowConnection.
 * These tests verify that reads are only performed when requested.
 */
public class RealFlowConnectionPullBehaviorTest extends AbstractFlowTest {

  private AsynchronousSocketChannel mockChannel;
  private LocalEndpoint localEndpoint;
  private Endpoint remoteEndpoint;
  private RealFlowConnection connection;
  private AtomicInteger readOperationCount;
  private List<CompletionHandler<Integer, ByteBuffer>> capturedHandlers;
  private List<ByteBuffer> capturedBuffers;

  @BeforeEach
  void setUp() throws Exception {
    mockChannel = mock(AsynchronousSocketChannel.class);
    when(mockChannel.isOpen()).thenReturn(true);
    
    localEndpoint = LocalEndpoint.localhost(12345);
    remoteEndpoint = new Endpoint("localhost", 8080);
    
    readOperationCount = new AtomicInteger(0);
    capturedHandlers = new ArrayList<>();
    capturedBuffers = new ArrayList<>();
    
    // Mock read operations to capture handlers instead of invoking them immediately
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      readOperationCount.incrementAndGet();
      capturedHandlers.add(handler);
      capturedBuffers.add(buffer);
      // Don't invoke the handler immediately - let the test control when data arrives
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());
  }

  @Test
  void testNoReadUntilReceiveIsCalled() throws Exception {
    // Create connection
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Wait a bit to ensure no automatic reads happen
    advanceTime(0.1);
    pump();
    
    // Verify no read operations were started
    assertEquals(0, readOperationCount.get(), "No reads should occur before receive() is called");
  }

  @Test
  void testSingleReadOnReceive() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Call receive
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Verify exactly one read operation was started
    assertEquals(1, readOperationCount.get(), "Exactly one read should occur when receive() is called");
    assertFalse(receiveFuture.isDone(), "Receive future should not be done until data arrives");
    
    // Simulate data arrival - put data in captured buffer
    ByteBuffer readBuffer = capturedBuffers.get(0);
    byte[] testData = "test data".getBytes();
    readBuffer.put(testData);
    capturedHandlers.get(0).completed(testData.length, readBuffer);
    pump();
    
    // Verify receive completes
    assertTrue(receiveFuture.isDone(), "Receive future should complete when data arrives");
    ByteBuffer result = receiveFuture.get(5, TimeUnit.SECONDS);
    assertNotNull(result);
    assertEquals(testData.length, result.remaining());
  }

  @Test
  void testMultipleReceivesQueuedProperly() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Call receive multiple times
    CompletableFuture<ByteBuffer> receive1 = connection.receive(1024);
    CompletableFuture<ByteBuffer> receive2 = connection.receive(1024);
    CompletableFuture<ByteBuffer> receive3 = connection.receive(1024);
    pump();
    
    // Only one read should be active
    assertEquals(1, readOperationCount.get(), "Only one read should be active at a time");
    
    // Complete first read
    byte[] data1 = "data1".getBytes();
    ByteBuffer buffer1 = capturedBuffers.get(0);
    buffer1.put(data1);
    capturedHandlers.get(0).completed(data1.length, buffer1);
    pump();
    
    // Second read should start automatically
    assertEquals(2, readOperationCount.get(), "Second read should start after first completes");
    assertTrue(receive1.isDone());
    assertFalse(receive2.isDone());
    
    // Complete second read
    byte[] data2 = "data2".getBytes();
    ByteBuffer buffer2 = capturedBuffers.get(1);
    buffer2.put(data2);
    capturedHandlers.get(1).completed(data2.length, buffer2);
    pump();
    
    // Third read should start
    assertEquals(3, readOperationCount.get(), "Third read should start after second completes");
    assertTrue(receive2.isDone());
    assertFalse(receive3.isDone());
    
    // Complete third read
    byte[] data3 = "data3".getBytes();
    ByteBuffer buffer3 = capturedBuffers.get(2);
    buffer3.put(data3);
    capturedHandlers.get(2).completed(data3.length, buffer3);
    pump();
    
    // All receives should be complete
    assertTrue(receive3.isDone());
    
    // Verify data
    assertEquals("data1", new String(toArray(receive1.get(5, TimeUnit.SECONDS))));
    assertEquals("data2", new String(toArray(receive2.get(5, TimeUnit.SECONDS))));
    assertEquals("data3", new String(toArray(receive3.get(5, TimeUnit.SECONDS))));
  }

  @Test
  void testNoExtraReadsAfterReceivesComplete() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Call receive
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    assertEquals(1, readOperationCount.get());
    
    // Complete the read
    byte[] data = "test".getBytes();
    ByteBuffer buffer = capturedBuffers.get(0);
    buffer.put(data);
    capturedHandlers.get(0).completed(data.length, buffer);
    pump();
    
    assertTrue(receiveFuture.isDone());
    
    // Wait and pump to ensure no extra reads happen
    advanceTime(0.1);
    pump();
    
    // Still only one read total
    assertEquals(1, readOperationCount.get(), "No extra reads should occur after receive completes");
  }

  @Test
  void testConnectionCloseStopsReads() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Queue multiple receives
    CompletableFuture<ByteBuffer> receive1 = connection.receive(1024);
    CompletableFuture<ByteBuffer> receive2 = connection.receive(1024);
    pump();
    
    assertEquals(1, readOperationCount.get());
    
    // Close connection
    connection.close();
    pump();
    
    // Complete the pending read with end-of-stream
    capturedHandlers.get(0).completed(-1, capturedBuffers.get(0));
    pump();
    
    // No new read should start
    assertEquals(1, readOperationCount.get(), "No new reads should start after connection close");
    
    // Both receives should complete exceptionally
    assertTrue(receive1.isCompletedExceptionally());
    assertTrue(receive2.isCompletedExceptionally());
  }

  @Test
  void testReadErrorHandling() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Call receive
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    assertEquals(1, readOperationCount.get());
    
    // Simulate read error
    IOException error = new IOException("Read failed");
    capturedHandlers.get(0).failed(error, capturedBuffers.get(0));
    pump();
    
    // Receive should complete exceptionally
    assertTrue(receiveFuture.isCompletedExceptionally());
    
    // Connection should be closed
    assertTrue(connection.closeFuture().isDone());
  }

  @Test
  void testZeroBytesReadContinuesReading() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Call receive
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Simulate zero bytes read (should retry)
    capturedHandlers.get(0).completed(0, capturedBuffers.get(0));
    pump();
    
    // Should trigger another read
    assertEquals(2, readOperationCount.get(), "Zero-byte read should trigger retry");
    assertFalse(receiveFuture.isDone(), "Receive should not complete on zero-byte read");
    
    // Complete with actual data
    byte[] data = "data".getBytes();
    ByteBuffer buffer = capturedBuffers.get(1);
    buffer.put(data);
    capturedHandlers.get(1).completed(data.length, buffer);
    pump();
    
    assertTrue(receiveFuture.isDone());
    assertEquals("data", new String(toArray(receiveFuture.get(5, TimeUnit.SECONDS))));
  }

  private byte[] toArray(ByteBuffer buffer) {
    byte[] array = new byte[buffer.remaining()];
    buffer.get(array);
    return array;
  }
}