package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SimulatedFlowFile implementation.
 */
class SimulatedFlowFileTest extends AbstractFlowTest {
  
  private SimulationParameters params;
  private SimulatedFlowFile file;
  private final Path testPath = Paths.get("/test/file.txt");
  
  @Override
  protected void onSetUp() {
    // Set up simulation parameters
    params = new SimulationParameters();
    // Use small delays for faster tests
    params.setReadDelay(0.001);
    params.setWriteDelay(0.001);
    params.setMetadataDelay(0.001);
    
    // Create the file
    file = new SimulatedFlowFile(testPath, params);
  }
  
  @Test
  void testWriteAndRead() throws Exception {
    final String testData = "Hello, simulated file!";
    final ByteBuffer writeBuffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Wrap all file operations in an actor to properly handle Flow.delay internally used by SimulatedFlowFile
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Write data to file
      Flow.await(file.write(0, writeBuffer));
      
      // Check file size
      long size = Flow.await(file.size());
      if (size != testData.length()) {
        throw new AssertionError("File size doesn't match: expected " + testData.length() + ", got " + size);
      }
      
      // Read data back
      ByteBuffer readBuffer = Flow.await(file.read(0, testData.length()));
      
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Get the result and verify it
    String result = testFuture.getNow();
    assertEquals(testData, result);
  }
  
  @Test
  void testPartialRead() throws Exception {
    final String testData = "This is a longer test string for partial reading.";
    final ByteBuffer writeBuffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    final int offset = 5;
    final int partialLength = 10;
    
    // Wrap all file operations in an actor to properly handle Flow.delay internally used by SimulatedFlowFile
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Write data to file
      Flow.await(file.write(0, writeBuffer));
      
      // Read only part of the data
      ByteBuffer readBuffer = Flow.await(file.read(offset, partialLength));
      
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Get the result and verify it
    String result = testFuture.getNow();
    assertEquals(testData.substring(offset, offset + partialLength), result);
  }
  
  @Test
  void testReadPastEndOfFile() throws Exception {
    final String testData = "Short data.";
    final ByteBuffer writeBuffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Wrap all file operations in an actor
    FlowFuture<Integer> testFuture = Flow.startActor(() -> {
      // Write data to file
      Flow.await(file.write(0, writeBuffer));
      
      // Read past end of file
      ByteBuffer readBuffer = Flow.await(file.read(testData.length(), 10));
      
      // Return the buffer's remaining bytes (should be 0)
      return readBuffer.remaining();
    });
    
    // Wait for the actor to complete
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Verify the result - should be an empty buffer (0 bytes remaining)
    assertEquals(0, testFuture.getNow().intValue());
  }
  
  @Test
  void testTruncate() throws Exception {
    String testData = "This string will be truncated.";
    ByteBuffer writeBuffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    final int truncateSize = 10;
    
    // Wrap all file operations in an actor
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Write data to file
      Flow.await(file.write(0, writeBuffer));
      
      // Check initial size
      long initialSize = Flow.await(file.size());
      if (initialSize != testData.length()) {
        throw new AssertionError("Initial file size doesn't match: expected " + testData.length() 
            + ", got " + initialSize);
      }
      
      // Truncate the file
      Flow.await(file.truncate(truncateSize));
      
      // Check new size
      long newSize = Flow.await(file.size());
      if (newSize != truncateSize) {
        throw new AssertionError("File size after truncate doesn't match: expected " + truncateSize 
            + ", got " + newSize);
      }
      
      // Read data after truncate
      ByteBuffer readBuffer = Flow.await(file.read(0, truncateSize));
      
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Verify the result
    String result = testFuture.getNow();
    assertEquals(testData.substring(0, truncateSize), result);
  }
  
  @Test
  void testWriteAtOffset() throws Exception {
    String initialData = "Initial data.";
    ByteBuffer initialBuffer = ByteBuffer.wrap(initialData.getBytes(StandardCharsets.UTF_8));
    String appendData = " More data.";
    ByteBuffer appendBuffer = ByteBuffer.wrap(appendData.getBytes(StandardCharsets.UTF_8));
    
    // Wrap all file operations in an actor
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Write initial data
      Flow.await(file.write(0, initialBuffer));
      
      // Write at offset
      Flow.await(file.write(initialData.length(), appendBuffer));
      
      // Read all data
      ByteBuffer readBuffer = Flow.await(file.read(0, initialData.length() + appendData.length()));
      
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Verify the result
    String result = testFuture.getNow();
    assertEquals(initialData + appendData, result);
  }
  
  @Test
  void testSync() throws Exception {
    // Wrap the sync operation in an actor
    FlowFuture<Boolean> testFuture = Flow.startActor(() -> {
      // Sync should complete successfully
      Flow.await(file.sync());
      return true; // If we get here, it worked
    });
    
    // Wait for the actor to complete
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Verify sync completed without exception
    assertTrue(testFuture.getNow());
  }
  
  @Test
  void testClose() throws Exception {
    // First action: close the file
    FlowFuture<Boolean> closeFuture = Flow.startActor(() -> {
      Flow.await(file.close());
      return true; // If we get here, it worked
    });
    
    // Wait for the close operation to complete
    pumpAndAdvanceTimeUntilDone(closeFuture);
    assertTrue(closeFuture.getNow());
    assertTrue(file.isClosed(), "File should be marked as closed");

    // Second action: try to read from the closed file
    FlowFuture<Boolean> readFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.read(0, 10));
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected exception
        return e.getMessage().contains("File is closed");
      }
    });

    // Wait for the read operation to complete
    pumpAndAdvanceTimeUntilDone(readFuture);
    assertTrue(readFuture.getNow(), "Should fail with 'File is closed' message");
  }
  
  @Test
  void testWriteAfterClose() throws Exception {
    // Setup: write some data then close the file
    FlowFuture<Void> setupFuture = Flow.startActor(() -> {
      String testData = "Test data";
      ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
      Flow.await(file.write(0, buffer));
      Flow.await(file.close());
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(setupFuture);
    assertTrue(file.isClosed(), "File should be marked as closed");
    
    // Test: attempt to write to the closed file
    FlowFuture<String> writeFuture = Flow.startActor(() -> {
      try {
        ByteBuffer buffer = ByteBuffer.wrap("More data".getBytes(StandardCharsets.UTF_8));
        Flow.await(file.write(0, buffer));
        return "Write succeeded unexpectedly";
      } catch (Exception e) {
        return e.getMessage();
      }
    });
    
    pumpAndAdvanceTimeUntilDone(writeFuture);
    assertEquals("File is closed", writeFuture.getNow(), "Write should fail with 'File is closed'");
  }
  
  @Test
  void testTruncateAfterClose() throws Exception {
    // Setup: write some data then close the file
    FlowFuture<Void> setupFuture = Flow.startActor(() -> {
      String testData = "Test data for truncation";
      ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
      Flow.await(file.write(0, buffer));
      Flow.await(file.close());
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(setupFuture);
    assertTrue(file.isClosed(), "File should be marked as closed");
    
    // Test: attempt to truncate the closed file
    FlowFuture<String> truncateFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.truncate(5));
        return "Truncate succeeded unexpectedly";
      } catch (Exception e) {
        return e.getMessage();
      }
    });
    
    pumpAndAdvanceTimeUntilDone(truncateFuture);
    assertEquals("File is closed", truncateFuture.getNow(), "Truncate should fail with 'File is closed'");
  }
  
  @Test
  void testSyncAfterClose() throws Exception {
    // Setup: close the file
    FlowFuture<Void> setupFuture = Flow.startActor(() -> {
      Flow.await(file.close());
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(setupFuture);
    assertTrue(file.isClosed(), "File should be marked as closed");
    
    // Test: attempt to sync the closed file
    FlowFuture<String> syncFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.sync());
        return "Sync succeeded unexpectedly";
      } catch (Exception e) {
        return e.getMessage();
      }
    });
    
    pumpAndAdvanceTimeUntilDone(syncFuture);
    assertEquals("File is closed", syncFuture.getNow(), "Sync should fail with 'File is closed'");
  }
  
  @Test
  void testSizeAfterClose() throws Exception {
    // Setup: write some data then close the file
    FlowFuture<Void> setupFuture = Flow.startActor(() -> {
      String testData = "Test data for size check";
      ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
      Flow.await(file.write(0, buffer));
      Flow.await(file.close());
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(setupFuture);
    assertTrue(file.isClosed(), "File should be marked as closed");
    
    // Test: attempt to get size of the closed file
    FlowFuture<String> sizeFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.size());
        return "Size operation succeeded unexpectedly";
      } catch (Exception e) {
        return e.getMessage();
      }
    });
    
    pumpAndAdvanceTimeUntilDone(sizeFuture);
    assertEquals("File is closed", sizeFuture.getNow(), "Size operation should fail with 'File is closed'");
  }
  
  @Test
  void testMultipleClose() throws Exception {
    // Test closing a file multiple times (should be idempotent)
    FlowFuture<Boolean> multipleFuture = Flow.startActor(() -> {
      // First close
      Flow.await(file.close());
      assertTrue(file.isClosed(), "File should be marked as closed after first close");
      
      // Second close - should not throw an exception
      Flow.await(file.close());
      assertTrue(file.isClosed(), "File should still be marked as closed after second close");
      
      // Third close - should not throw an exception
      Flow.await(file.close());
      assertTrue(file.isClosed(), "File should still be marked as closed after third close");
      
      return true;
    });
    
    pumpAndAdvanceTimeUntilDone(multipleFuture);
    assertTrue(multipleFuture.getNow(), "Multiple close operations should succeed");
    assertTrue(file.isClosed(), "File should remain marked as closed");
  }
  
  @Test
  void testGetPath() {
    // Path should match what was passed to constructor
    assertEquals(testPath, file.getPath());
  }
  
  @Test
  void testInvalidParameters() throws Exception {
    // Test all parameter validation in a sequence of actor operations
    // Each operation is independent and should fail with the appropriate exception
    
    ByteBuffer testBuffer = ByteBuffer.wrap("test".getBytes());
    
    // Test negative position for read
    FlowFuture<Class<?>> readFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.read(-1, 10));
        return null; // Should not reach here
      } catch (Exception e) {
        return e.getClass();
      }
    });
    pumpAndAdvanceTimeUntilDone(readFuture);
    assertEquals(IllegalArgumentException.class, readFuture.getNow());
    
    // Test zero length for read
    FlowFuture<Class<?>> zeroLengthFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.read(0, 0));
        return null; // Should not reach here
      } catch (Exception e) {
        return e.getClass();
      }
    });
    pumpAndAdvanceTimeUntilDone(zeroLengthFuture);
    assertEquals(IllegalArgumentException.class, zeroLengthFuture.getNow());
    
    // Test negative position for write
    FlowFuture<Class<?>> writeFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.write(-1, testBuffer));
        return null; // Should not reach here
      } catch (Exception e) {
        return e.getClass();
      }
    });
    pumpAndAdvanceTimeUntilDone(writeFuture);
    assertEquals(IllegalArgumentException.class, writeFuture.getNow());
    
    // Test negative size for truncate
    FlowFuture<Class<?>> truncateFuture = Flow.startActor(() -> {
      try {
        Flow.await(file.truncate(-1));
        return null; // Should not reach here
      } catch (Exception e) {
        return e.getClass();
      }
    });
    pumpAndAdvanceTimeUntilDone(truncateFuture);
    assertEquals(IllegalArgumentException.class, truncateFuture.getNow());
  }
}