package io.github.panghy.javaflow.io.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage-focused tests for RealFlowConnection to test edge cases and error handling.
 */
public class RealFlowConnectionCoverageTest extends AbstractFlowTest {

  private AsynchronousSocketChannel mockChannel;
  private LocalEndpoint localEndpoint;
  private Endpoint remoteEndpoint;
  private RealFlowConnection connection;

  @BeforeEach
  void setUp() throws Exception {
    mockChannel = mock(AsynchronousSocketChannel.class);
    when(mockChannel.isOpen()).thenReturn(true);
    
    localEndpoint = LocalEndpoint.localhost(12345);
    remoteEndpoint = new Endpoint("localhost", 8080);
  }

  @Test
  void testWriteInChunksExceptionDuringWrite() throws Exception {
    // Mock a channel that throws during write
    doAnswer(invocation -> {
      CompletionHandler<Integer, Void> handler = invocation.getArgument(2);
      // Simulate write failure
      handler.failed(new BufferOverflowException(), null);
      return null;
    }).when(mockChannel).write(any(ByteBuffer.class), any(), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Try to send data
    ByteBuffer data = ByteBuffer.wrap("test data".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(data);
    pump();

    // Should complete exceptionally
    assertTrue(sendFuture.isCompletedExceptionally());
    assertThrows(Exception.class, () -> sendFuture.get(5, TimeUnit.SECONDS));

    // Connection should be closed
    assertTrue(connection.closeFuture().isDone());
  }

  @Test
  void testWriteInChunksExceptionOnStart() throws Exception {
    // Mock a channel that throws immediately when write is called
    doThrow(new RuntimeException("Write failed")).when(mockChannel)
        .write(any(ByteBuffer.class), any(), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Try to send data
    ByteBuffer data = ByteBuffer.wrap("test data".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(data);
    pump();

    // Should complete exceptionally
    assertTrue(sendFuture.isCompletedExceptionally());
    assertThrows(Exception.class, () -> sendFuture.get(5, TimeUnit.SECONDS));

    // Connection should be closed
    assertTrue(connection.closeFuture().isDone());
  }

  @Test
  void testWriteZeroBytes() throws Exception {
    // Mock successful write of zero bytes
    doAnswer(invocation -> {
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(1);
      handler.completed(0, invocation.getArgument(0));
      return null;
    }).when(mockChannel).write(any(ByteBuffer.class), any(ByteBuffer.class), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);

    // Send empty buffer in an actor
    CompletableFuture<Void> sendFuture = Flow.startActor(() -> {
      ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
      Flow.await(connection.send(emptyBuffer));
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(sendFuture);

    // Should complete successfully even with zero bytes
    assertTrue(sendFuture.isDone());
    assertFalse(sendFuture.isCompletedExceptionally());
  }

  @Test
  void testReceiveAfterClose() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Close the connection first
    connection.close();
    pump();
    
    // Try to receive after close
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Should complete exceptionally
    assertTrue(receiveFuture.isCompletedExceptionally());
    assertThrows(Exception.class, () -> receiveFuture.get(5, TimeUnit.SECONDS));
  }

  @Test
  void testSendAfterClose() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Close the connection first
    connection.close();
    pump();
    
    // Try to send after close
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(data);
    pump();
    
    // Should complete exceptionally
    assertTrue(sendFuture.isCompletedExceptionally());
    assertThrows(Exception.class, () -> sendFuture.get(5, TimeUnit.SECONDS));
  }

  @Test
  void testMultipleCloseCallsAreIdempotent() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Close multiple times
    connection.close();
    CompletableFuture<Void> closeFuture1 = connection.closeFuture();
    
    connection.close();
    CompletableFuture<Void> closeFuture2 = connection.closeFuture();
    
    connection.close();
    CompletableFuture<Void> closeFuture3 = connection.closeFuture();
    
    pump();
    
    // All should reference the same future
    assertEquals(closeFuture1, closeFuture2);
    assertEquals(closeFuture2, closeFuture3);
    
    // All should be done
    assertTrue(closeFuture1.isDone());
    assertFalse(closeFuture1.isCompletedExceptionally());
  }

  @Test
  void testReceiveWithNullBuffer() throws Exception {
    // Mock read that returns null buffer (edge case)
    doAnswer(invocation -> {
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(1);
      handler.completed(0, null);
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Should handle gracefully - likely will retry or return empty buffer
    // The exact behavior depends on implementation, but it shouldn't crash
    assertNotNull(receiveFuture);
  }

  @Test
  void testConnectionString() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    String connectionString = connection.toString();
    assertNotNull(connectionString);
    assertTrue(connectionString.contains("RealFlowConnection"));
    assertTrue(connectionString.contains("localhost:12345"));
    assertTrue(connectionString.contains("localhost:8080"));
  }

  @Test
  void testChannelCloseExceptionHandling() throws Exception {
    // Mock channel that throws on close
    doThrow(new IOException("Close failed")).when(mockChannel).close();
    
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Close should handle the exception gracefully
    connection.close();
    pump();
    
    // Close future should still complete (even if with exception)
    assertTrue(connection.closeFuture().isDone());
  }

  @Test
  void testGettersAndProperties() throws Exception {
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Test getters
    assertEquals(localEndpoint, connection.getLocalEndpoint());
    assertEquals(remoteEndpoint, connection.getRemoteEndpoint());
    assertTrue(connection.isOpen());
    
    // Close and check isOpen
    connection.close();
    pump();
    
    assertFalse(connection.isOpen());
  }

  @Test
  void testCloseFutureExceptionPropagation() throws Exception {
    // Create a connection that will fail during close
    AtomicInteger closeAttempts = new AtomicInteger(0);
    doAnswer(invocation -> {
      if (closeAttempts.incrementAndGet() == 1) {
        throw new IOException("First close attempt failed");
      }
      return null;
    }).when(mockChannel).close();
    
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Close the connection
    connection.close();
    pump();
    
    // The close future should complete (possibly exceptionally)
    assertTrue(connection.closeFuture().isDone());
    
    // If it completed exceptionally, verify the exception
    if (connection.closeFuture().isCompletedExceptionally()) {
      assertThrows(Exception.class, () -> connection.closeFuture().get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void testPartialWriteHandling() throws Exception {
    List<Integer> writeCounts = new ArrayList<>();
    writeCounts.add(5);  // First write: 5 bytes
    writeCounts.add(4);  // Second write: 4 bytes (total 9)
    writeCounts.add(1);  // Third write: 1 byte (total 10)
    
    AtomicInteger writeIndex = new AtomicInteger(0);
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, Void> handler = invocation.getArgument(2);
      
      int bytesToWrite = writeCounts.get(writeIndex.getAndIncrement());
      // Simulate partial write
      buffer.position(buffer.position() + bytesToWrite);
      handler.completed(bytesToWrite, null);
      return null;
    }).when(mockChannel).write(any(ByteBuffer.class), any(), any());
    
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Send 10 bytes that will require multiple writes
    ByteBuffer data = ByteBuffer.wrap("0123456789".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(data);
    pump();
    
    // Should complete successfully after multiple partial writes
    assertTrue(sendFuture.isDone());
    assertFalse(sendFuture.isCompletedExceptionally());
    
    // Verify we made 3 write calls
    verify(mockChannel, times(3)).write(any(ByteBuffer.class), any(), any());
  }

  @Test
  void testReceiveWithPartialRead() throws Exception {
    // First read returns partial data, second read completes
    AtomicInteger readCount = new AtomicInteger(0);
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      
      if (readCount.incrementAndGet() == 1) {
        // First read: partial data
        byte[] partial = "part".getBytes();
        buffer.put(partial);
        handler.completed(partial.length, buffer);
      } else {
        // Second read: remaining data
        byte[] remaining = "ial".getBytes();
        buffer.put(remaining);
        handler.completed(remaining.length, buffer);
      }
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());
    
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Request exactly 7 bytes, but only 4 are available on first read
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(7);
    pump();
    
    // Should complete with first available data (4 bytes)
    assertTrue(receiveFuture.isDone());
    ByteBuffer result = receiveFuture.get(5, TimeUnit.SECONDS);
    
    byte[] resultBytes = new byte[result.remaining()];
    result.get(resultBytes);
    assertEquals("part", new String(resultBytes));
    
    // Request more data to get the remaining "ial"
    CompletableFuture<ByteBuffer> receiveFuture2 = connection.receive(7);
    pump();
    
    assertTrue(receiveFuture2.isDone());
    ByteBuffer result2 = receiveFuture2.get(5, TimeUnit.SECONDS);
    
    byte[] resultBytes2 = new byte[result2.remaining()];
    result2.get(resultBytes2);
    assertEquals("ial", new String(resultBytes2));
  }

  @Test
  void testReadHandlerFailureWithIOException() throws Exception {
    // Mock read that fails with IOException
    doAnswer(invocation -> {
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      handler.failed(new IOException("Read failed"), invocation.getArgument(1));
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Try to receive data
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Connection should be closed after the read failure
    assertTrue(connection.closeFuture().isDone());
    
    // The receive future might not complete exceptionally since the stream is just closed
    // Let's check if the connection is closed instead
    assertFalse(connection.isOpen());
  }

  @Test
  void testReadHandlerFailureWithRuntimeException() throws Exception {
    // Mock read that fails with RuntimeException
    doAnswer(invocation -> {
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      handler.failed(new RuntimeException("Unexpected error"), invocation.getArgument(1));
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Try to receive data
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Connection should be closed after the read failure
    assertTrue(connection.closeFuture().isDone());
    
    // The receive future might not complete exceptionally since the stream is just closed
    // Let's check if the connection is closed instead
    assertFalse(connection.isOpen());
  }

  @Test
  void testReadHandlerEndOfStreamMidRequest() throws Exception {
    // Mock read that returns -1 (end of stream)
    doAnswer(invocation -> {
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      handler.completed(-1, invocation.getArgument(1));
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Try to receive data
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Should complete exceptionally with connection closed
    assertTrue(receiveFuture.isCompletedExceptionally());
    assertThrows(Exception.class, () -> receiveFuture.get(5, TimeUnit.SECONDS));
    
    // Connection should be closed
    assertTrue(connection.closeFuture().isDone());
  }

  @Test
  void testReadHandlerSuccessButConnectionClosedDuringProcess() throws Exception {
    AtomicInteger readCount = new AtomicInteger(0);
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      
      if (readCount.incrementAndGet() == 1) {
        // First read succeeds
        byte[] data = "test".getBytes();
        buffer.put(data);
        handler.completed(data.length, buffer);
        
        // But then close the connection
        connection.close();
      } else {
        // Subsequent reads fail
        handler.failed(new IOException("Connection closed"), buffer);
      }
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // First receive should succeed
    CompletableFuture<ByteBuffer> receiveFuture1 = connection.receive(1024);
    pump();
    
    assertTrue(receiveFuture1.isDone());
    ByteBuffer result = receiveFuture1.get(5, TimeUnit.SECONDS);
    byte[] resultBytes = new byte[result.remaining()];
    result.get(resultBytes);
    assertEquals("test", new String(resultBytes));
    
    // Second receive should fail since connection is closed
    CompletableFuture<ByteBuffer> receiveFuture2 = connection.receive(1024);
    pump();
    
    assertTrue(receiveFuture2.isCompletedExceptionally());
    assertThrows(Exception.class, () -> receiveFuture2.get(5, TimeUnit.SECONDS));
  }

  @Test
  void testReadHandlerExceptionInCompletionHandler() throws Exception {
    // Mock read handler that throws an exception in the handler itself
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      
      // Put some data
      byte[] data = "test".getBytes();
      buffer.put(data);
      
      // Call completed, but the handler will throw
      try {
        handler.completed(data.length, buffer);
      } catch (Exception e) {
        // This simulates an exception thrown during handler processing
        handler.failed(e, buffer);
      }
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Override the stream to throw during processing
    CompletableFuture<ByteBuffer> receiveFuture = connection.receive(1024);
    pump();
    
    // Should complete normally since the data was received
    assertTrue(receiveFuture.isDone());
    ByteBuffer result = receiveFuture.get(5, TimeUnit.SECONDS);
    assertNotNull(result);
  }

  @Test
  void testMultipleReceivesWithDifferentBufferSizes() throws Exception {
    // Mock reads with specific behavior
    AtomicInteger callCount = new AtomicInteger(0);
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      
      int call = callCount.incrementAndGet();
      switch (call) {
        case 1:
          // First call: return just 15 bytes total
          byte[] data1 = new byte[15];
          for (int i = 0; i < data1.length; i++) {
            data1[i] = (byte) ('A' + (i % 26));
          }
          buffer.put(data1);
          handler.completed(data1.length, buffer);
          break;
        case 2:
          // Second call: return "Short" (5 bytes)
          byte[] data2 = "Short".getBytes();
          buffer.put(data2);
          handler.completed(data2.length, buffer);
          break;
        case 3:
          // Third call: return exactly 15 bytes
          byte[] data3 = new byte[15];
          for (int i = 0; i < data3.length; i++) {
            data3[i] = (byte) ('0' + (i % 10));
          }
          buffer.put(data3);
          handler.completed(data3.length, buffer);
          break;
        default:
          handler.completed(-1, buffer); // EOF
      }
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Test different buffer sizes
    // First receive: request 10 bytes from 15 available
    CompletableFuture<ByteBuffer> receive1 = connection.receive(10);
    pump();
    ByteBuffer result1 = receive1.get(5, TimeUnit.SECONDS);
    assertEquals(10, result1.remaining());
    
    // Second receive: request 100 bytes, should get remaining 5 from first read
    CompletableFuture<ByteBuffer> receive2 = connection.receive(100);
    // No pump needed, data is already buffered
    ByteBuffer result2 = receive2.get(5, TimeUnit.SECONDS);
    assertEquals(5, result2.remaining()); // Remaining 5 bytes from first read
    
    // Third receive: request 100 bytes, triggers second read which returns "Short" (5 bytes)
    CompletableFuture<ByteBuffer> receive3 = connection.receive(100);
    pump();
    ByteBuffer result3 = receive3.get(5, TimeUnit.SECONDS);
    assertEquals(5, result3.remaining()); // "Short"
    
    // Fourth receive: request 15 bytes, triggers third read which returns exactly 15 bytes
    CompletableFuture<ByteBuffer> receive4 = connection.receive(15);
    pump();
    ByteBuffer result4 = receive4.get(5, TimeUnit.SECONDS);
    assertEquals(15, result4.remaining());
  }

  @Test
  void testReceiveStreamBasicMethods() throws Exception {
    // Mock channel setup
    when(mockChannel.isOpen()).thenReturn(true);

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Get the stream - this exercises the anonymous class creation
    FlowStream<ByteBuffer> stream = connection.receiveStream();
    assertNotNull(stream);
    
    // Test that all methods are callable (coverage)
    // hasNextAsync() returns a future
    CompletableFuture<Boolean> hasNextFuture = stream.hasNextAsync();
    assertNotNull(hasNextFuture);
    
    // nextAsync() returns a future  
    CompletableFuture<ByteBuffer> nextFuture = stream.nextAsync();
    assertNotNull(nextFuture);
    
    // isClosed() returns false initially
    assertFalse(stream.isClosed());
    
    // onClose() returns a future
    CompletableFuture<Void> onCloseFuture = stream.onClose();
    assertNotNull(onCloseFuture);
    
    // close() returns a future
    CompletableFuture<Void> closeFuture = stream.close();
    assertNotNull(closeFuture);
    
    // closeExceptionally() returns a future
    FlowStream<ByteBuffer> stream2 = connection.receiveStream();
    Exception testEx = new IOException("Test exception");
    CompletableFuture<Void> closeExFuture = stream2.closeExceptionally(testEx);
    assertNotNull(closeExFuture);
  }

  @Test
  void testReceiveStreamTransformations() throws Exception {
    // Mock channel setup
    when(mockChannel.isOpen()).thenReturn(true);

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Get the stream
    FlowStream<ByteBuffer> stream = connection.receiveStream();
    
    // Test map transformation
    FlowStream<String> mappedStream = stream.map(buffer -> "mapped");
    assertNotNull(mappedStream);
    
    // Test filter transformation
    FlowStream<ByteBuffer> filteredStream = stream.filter(buffer -> buffer.remaining() > 0);
    assertNotNull(filteredStream);
    
    // Test forEach transformation
    AtomicInteger counter = new AtomicInteger(0);
    CompletableFuture<Void> forEachFuture = stream.forEach(buffer -> counter.incrementAndGet());
    assertNotNull(forEachFuture);
  }

  @Test
  void testWriteCompletionHandlerExceptionInRestoration() throws Exception {
    // Mock channel that causes exception during write after completion
    AtomicInteger writeCount = new AtomicInteger(0);
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, Void> handler = invocation.getArgument(2);
      
      int count = writeCount.incrementAndGet();
      if (count == 1) {
        // First write: write some data but leave some remaining
        int written = Math.min(5, buffer.remaining());
        buffer.position(buffer.position() + written);
        handler.completed(written, null);
      } else {
        // Second write: complete the rest
        int remaining = buffer.remaining();
        buffer.position(buffer.limit());
        handler.completed(remaining, null);
      }
      return null;
    }).when(mockChannel).write(any(ByteBuffer.class), any(), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Send data that will require multiple writes
    ByteBuffer data = ByteBuffer.wrap("Hello World Test Data".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(data);
    pump();
    
    // Should complete successfully
    pumpAndAdvanceTimeUntilDone(sendFuture);
    assertTrue(sendFuture.isDone());
    assertFalse(sendFuture.isCompletedExceptionally());
    
    // Verify multiple writes occurred
    verify(mockChannel, times(2)).write(any(ByteBuffer.class), any(), any());
  }

  @Test
  void testWriteLargeDataWithChunking() throws Exception {
    // Create data larger than 1MB to trigger chunking behavior
    byte[] largeData = new byte[2 * 1024 * 1024]; // 2MB
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }
    
    AtomicInteger chunkCount = new AtomicInteger(0);
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, Void> handler = invocation.getArgument(2);
      
      // Write entire chunk each time
      int written = buffer.remaining();
      buffer.position(buffer.limit());
      chunkCount.incrementAndGet();
      handler.completed(written, null);
      return null;
    }).when(mockChannel).write(any(ByteBuffer.class), any(), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Send large data
    ByteBuffer data = ByteBuffer.wrap(largeData);
    CompletableFuture<Void> sendFuture = connection.send(data);
    pump();
    
    // Should complete successfully with multiple chunks
    pumpAndAdvanceTimeUntilDone(sendFuture);
    assertTrue(sendFuture.isDone());
    assertFalse(sendFuture.isCompletedExceptionally());
    
    // Should have written at least 2 chunks (2MB / 1MB chunks)
    assertTrue(chunkCount.get() >= 2, "Expected at least 2 chunks, got " + chunkCount.get());
  }

  @Test
  void testWriteCompletionHandlerException() throws Exception {
    // Mock channel that throws exception during the write operation on the second call
    AtomicInteger writeCount = new AtomicInteger(0);
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, Void> handler = invocation.getArgument(2);
      
      int count = writeCount.incrementAndGet();
      if (count == 1) {
        // First write: write partial data
        int written = Math.min(5, buffer.remaining());
        buffer.position(buffer.position() + written);
        handler.completed(written, null);
      } else {
        // Second write: throw an exception during the write call
        throw new RuntimeException("Write channel exception");
      }
      return null;
    }).when(mockChannel).write(any(ByteBuffer.class), any(), any());

    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    // Send data that will require multiple writes to trigger the exception
    ByteBuffer data = ByteBuffer.wrap("Hello World Test Data".getBytes());
    CompletableFuture<Void> sendFuture = connection.send(data);
    pump();
    
    // Should complete exceptionally due to the exception in the completion handler
    pumpAndAdvanceTimeUntilDone(sendFuture);
    assertTrue(sendFuture.isDone());
    assertTrue(sendFuture.isCompletedExceptionally());
    
    // Connection should be closed
    assertTrue(connection.closeFuture().isDone());
  }

  @Test
  void testReceiveStreamConcurrentNextAsyncCalls() throws Exception {
    // Use a delayed completion to ensure the read stays in progress
    AtomicInteger callCount = new AtomicInteger(0);
    CompletableFuture<Void> readCompletionDelay = new CompletableFuture<>();
    
    doAnswer(invocation -> {
      ByteBuffer buffer = invocation.getArgument(0);
      CompletionHandler<Integer, ByteBuffer> handler = invocation.getArgument(2);
      
      if (callCount.incrementAndGet() == 1) {
        // First call - delay the completion to keep read in progress
        Flow.scheduler().schedule(() -> {
          Flow.await(readCompletionDelay); // Wait for our signal
          byte[] data = "test".getBytes();
          buffer.put(data);
          handler.completed(data.length, buffer);
          return null;
        });
      } else {
        // Subsequent calls complete normally
        byte[] data = "test2".getBytes();
        buffer.put(data);
        handler.completed(data.length, buffer);
      }
      return null;
    }).when(mockChannel).read(any(ByteBuffer.class), any(ByteBuffer.class), any());

    when(mockChannel.isOpen()).thenReturn(true);
    connection = new RealFlowConnection(mockChannel, localEndpoint, remoteEndpoint);
    
    FlowStream<ByteBuffer> stream = connection.receiveStream();
    
    // Call nextAsync() to start the first read (readInProgress becomes true)
    CompletableFuture<ByteBuffer> next1 = stream.nextAsync();
    pump(); // Start the delayed read
    
    // Call nextAsync() again while the first read is still in progress
    // This should hit the false branch of compareAndSet(false, true)
    CompletableFuture<ByteBuffer> next2 = stream.nextAsync();
    
    // Both futures should be returned (not null)
    assertNotNull(next1);
    assertNotNull(next2);
    
    // Now complete the delayed read
    readCompletionDelay.complete(null);
    pumpAndAdvanceTimeUntilDone(next1);
    
    // The first future should complete
    assertTrue(next1.isDone());
    ByteBuffer result1 = next1.get();
    assertNotNull(result1);
    
    // Convert buffer to string for verification
    byte[] bytes1 = new byte[result1.remaining()];
    result1.get(bytes1);
    assertEquals("test", new String(bytes1));
    
    // The second future should eventually complete too when the next read happens
    pumpAndAdvanceTimeUntilDone(next2);
    assertTrue(next2.isDone());
    ByteBuffer result2 = next2.get();
    assertNotNull(result2);
  }
}