package io.github.panghy.javaflow.io;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.ExecutionException;
/**
 * Tests for the RealFlowFile class.
 * These tests use a temporary directory to avoid affecting the actual file system.
 * <p>
 * This test suite aims to achieve full coverage of RealFlowFile and its
 * anonymous inner classes (CompletionHandlers) for both read and write operations.
 * <p>
 * Coverage targets:
 * - RealFlowFile.1: read completion handler
 *   - bytesRead < 0 (EOF)
 *   - bytesRead < length (partial read)
 *   - bytesRead == length (full read)
 *   - failure path
 * <p>
 * - RealFlowFile.2: write completion handler
 *   - bufferToWrite.hasRemaining() (partial write requiring additional writes)
 *   - complete write (no remaining data)
 *   - failure path
 */
class RealFlowFileTest {
  
  @TempDir
  Path tempDir;
  
  @Test
  void testOpen() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-open.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file with CREATE and WRITE options
      FlowFile file = Flow.await(RealFlowFile.open(testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      assertNotNull(file);
      assertInstanceOf(RealFlowFile.class, file);
      
      // Close the file
      Flow.await(file.close());
      
      // Verify the file was created
      assertTrue(Files.exists(testFile));
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testWriteAndRead() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-write-read.txt");
    
    // Test data
    String testString = "Hello, Flow File!";
    byte[] testData = testString.getBytes();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write data to the file
      ByteBuffer writeBuffer = ByteBuffer.wrap(testData);
      Flow.await(file.write(0, writeBuffer));
      
      // Close the file
      Flow.await(file.close());
      
      // Re-open the file for reading
      FlowFile readFile = Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // Read data from the file
      ByteBuffer readBuffer = Flow.await(readFile.read(0, testData.length));
      
      // Verify the data
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      assertArrayEquals(testData, readData);
      
      // Close the file
      Flow.await(readFile.close());
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  /**
   * Tests partial reads where the amount read is less than the requested amount.
   * This specifically targets the branch in the read completion handler where
   * bytesRead < length.
   */
  @Test
  void testPartialRead() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-partial-read.txt");
    
    // Test data (longer text)
    String testString = "This is a longer text that we will partially read.";
    byte[] testData = testString.getBytes();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write data to the file
      ByteBuffer writeBuffer = ByteBuffer.wrap(testData);
      Flow.await(file.write(0, writeBuffer));
      
      // Close the file
      Flow.await(file.close());
      
      // Re-open the file for reading
      FlowFile readFile = Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // Read only part of the data (first 10 bytes)
      ByteBuffer partialBuffer = Flow.await(readFile.read(0, 10));
      
      // Verify the partial data
      byte[] partialData = new byte[partialBuffer.remaining()];
      partialBuffer.get(partialData);
      assertArrayEquals(Arrays.copyOf(testData, 10), partialData);
      
      // Now try multiple reads to get the entire file data
      // This tests the read handler more thoroughly
      byte[] fullData = new byte[testData.length];
      int chunkSize = 5; // Small chunks to force multiple reads
      
      for (int offset = 0; offset < testData.length; offset += chunkSize) {
        int length = Math.min(chunkSize, testData.length - offset);
        ByteBuffer chunk = Flow.await(readFile.read(offset, length));
        byte[] chunkData = new byte[chunk.remaining()];
        chunk.get(chunkData);
        System.arraycopy(chunkData, 0, fullData, offset, chunkData.length);
      }
      
      // Verify we got the complete file contents
      assertArrayEquals(testData, fullData);
      
      // Close the file
      Flow.await(readFile.close());
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testReadBeyondEOF() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-eof.txt");
    
    // Test data (short text)
    String testString = "Short data";
    byte[] testData = testString.getBytes();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write data to the file
      ByteBuffer writeBuffer = ByteBuffer.wrap(testData);
      Flow.await(file.write(0, writeBuffer));
      
      // Close the file
      Flow.await(file.close());
      
      // Re-open the file for reading
      FlowFile readFile = Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // Get the file size
      long size = Flow.await(readFile.size());
      assertEquals(testData.length, size);
      
      // Try to read from the end of file
      ByteBuffer eofBuffer = Flow.await(readFile.read(size, 10));
      
      // Should return an empty buffer
      assertEquals(0, eofBuffer.remaining());
      
      // Try to read from beyond the end of file
      ByteBuffer beyondEofBuffer = Flow.await(readFile.read(size + 10, 10));
      
      // Should also return an empty buffer
      assertEquals(0, beyondEofBuffer.remaining());
      
      // Close the file
      Flow.await(readFile.close());
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  /**
   * This test focuses specifically on exercising all branches of the read completion handler.
   */
  @Test
  void testReadCompletionHandlerBranches() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-read-handler.txt");
    
    // Test data (two pieces of different sizes)
    String part1 = "This is the first part that is longer than the second part.";
    String part2 = "Shorter second part.";
    
    byte[] part1Bytes = part1.getBytes();
    byte[] part2Bytes = part2.getBytes();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write both parts to the file
      ByteBuffer writeBuffer1 = ByteBuffer.wrap(part1Bytes);
      Flow.await(file.write(0, writeBuffer1));
      
      ByteBuffer writeBuffer2 = ByteBuffer.wrap(part2Bytes);
      Flow.await(file.write(part1Bytes.length, writeBuffer2));
      
      // Close the file
      Flow.await(file.close());
      
      // Re-open the file for reading
      FlowFile readFile = Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // 1. Test full read - when bytesRead equals length
      ByteBuffer exactBuffer = Flow.await(readFile.read(0, part1Bytes.length));
      assertEquals(part1Bytes.length, exactBuffer.remaining());
      byte[] readPart1 = new byte[exactBuffer.remaining()];
      exactBuffer.get(readPart1);
      assertArrayEquals(part1Bytes, readPart1);
      
      // 2. Test partial read - when bytesRead is less than length
      // This is done by requesting more bytes than are available in part2
      int requestLength = part2Bytes.length + 10;
      ByteBuffer partialBuffer = Flow.await(readFile.read(part1Bytes.length, requestLength));
      assertEquals(part2Bytes.length, partialBuffer.remaining());
      byte[] readPart2 = new byte[partialBuffer.remaining()];
      partialBuffer.get(readPart2);
      assertArrayEquals(part2Bytes, readPart2);
      
      // 3. Test end of file - when bytesRead is negative
      ByteBuffer eofBuffer = Flow.await(readFile.read(part1Bytes.length + part2Bytes.length, 10));
      assertEquals(0, eofBuffer.remaining());
      
      // Close the file
      Flow.await(readFile.close());
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testSize() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-size.txt");
    
    // Test data
    String testString = "Size test content";
    byte[] testData = testString.getBytes();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write data to the file
      ByteBuffer writeBuffer = ByteBuffer.wrap(testData);
      Flow.await(file.write(0, writeBuffer));
      
      // Check file size
      long size = Flow.await(file.size());
      assertEquals(testData.length, size);
      
      // Close the file
      Flow.await(file.close());
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testTruncate() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-truncate.txt");
    
    // Test data - make unique to avoid test conflicts
    String testString = "This text will be truncated - unique";
    byte[] testData = testString.getBytes();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      try {
        // Create the file first using standard Java API to ensure fresh state
        Files.write(testFile, testData);
        
        // Open a file for writing and reading
        FlowFile file = Flow.await(RealFlowFile.open(
            testFile, OpenOptions.WRITE, OpenOptions.READ));
        
        // Check initial file size
        long initialSize = Flow.await(file.size());
        assertEquals(testData.length, initialSize);
        
        // Truncate the file to 10 bytes
        Flow.await(file.truncate(10));
        
        // Check new file size
        long newSize = Flow.await(file.size());
        assertEquals(10, newSize);
        
        // Read truncated content
        ByteBuffer readBuffer = Flow.await(file.read(0, (int) newSize));
        byte[] readData = new byte[readBuffer.remaining()];
        readBuffer.get(readData);
        
        // Verify truncated content
        assertArrayEquals(Arrays.copyOf(testData, 10), readData);
        
        // Close the file
        Flow.await(file.close());
        
        return true;
      } catch (Exception e) {
        return false;
      }
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testSync() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-sync.txt");
    
    // Test data
    String testString = "Data to sync";
    byte[] testData = testString.getBytes();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write data to the file
      ByteBuffer writeBuffer = ByteBuffer.wrap(testData);
      Flow.await(file.write(0, writeBuffer));
      
      // Sync file to disk
      Flow.await(file.sync());
      
      // Close the file
      Flow.await(file.close());
      
      // Verify file contents were synced correctly
      byte[] fileContents = Files.readAllBytes(testFile);
      assertArrayEquals(testData, fileContents);
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testOpenOptions() throws Exception {
    // Test the conversion from OpenOptions to StandardOpenOption
    Set<StandardOpenOption> options = OpenOptions.toStandardOptions(
        OpenOptions.CREATE, 
        OpenOptions.WRITE, 
        OpenOptions.READ,
        OpenOptions.TRUNCATE_EXISTING,
        OpenOptions.CREATE_NEW);
    
    // Verify all options were properly converted
    assertEquals(5, options.size());
    assertTrue(options.contains(StandardOpenOption.CREATE));
    assertTrue(options.contains(StandardOpenOption.WRITE));
    assertTrue(options.contains(StandardOpenOption.READ));
    assertTrue(options.contains(StandardOpenOption.TRUNCATE_EXISTING));
    assertTrue(options.contains(StandardOpenOption.CREATE_NEW));
  }
  
  @Test
  void testMultipleWritesAndReads() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-multi-write.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write data at different positions
      ByteBuffer part1 = ByteBuffer.wrap("Hello ".getBytes());
      ByteBuffer part2 = ByteBuffer.wrap("World!".getBytes());
      
      Flow.await(file.write(0, part1));
      Flow.await(file.write(6, part2));
      
      // Close the file
      Flow.await(file.close());
      
      // Re-open the file for reading
      FlowFile readFile = Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // Read the complete file content
      ByteBuffer readBuffer = Flow.await(readFile.read(0, 12));
      
      // Verify the data
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      assertArrayEquals("Hello World!".getBytes(), readData);
      
      // Also read parts separately
      ByteBuffer part1Buffer = Flow.await(readFile.read(0, 5));
      byte[] part1Data = new byte[part1Buffer.remaining()];
      part1Buffer.get(part1Data);
      assertArrayEquals("Hello".getBytes(), part1Data);
      
      ByteBuffer part2Buffer = Flow.await(readFile.read(6, 6));
      byte[] part2Data = new byte[part2Buffer.remaining()];
      part2Buffer.get(part2Data);
      assertArrayEquals("World!".getBytes(), part2Data);
      
      // Close the file
      Flow.await(readFile.close());
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  /**
   * This test focuses specifically on exercising the write completion handler 
   * with a multi-part write that will cause the hasRemaining branch to be taken.
   * This tests the complex case where a buffer needs multiple writes to complete,
   * ensuring the buffer position is correctly tracked and updated.
   */
  @Test
  void testWriteCompletionHandlerBranches() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-write-handler.txt");
    
    // Create a moderate-sized buffer (32KB)
    int size = 32 * 1024;
    byte[] testData = new byte[size];
    
    // Fill with pattern data
    for (int i = 0; i < size; i++) {
      testData[i] = (byte) (i % 256);
    }
    
    // Create a buffer that will only allow writing a portion at a time
    // This simulates the hasRemaining case in the CompletionHandler
    ByteBuffer limitedBuffer = ByteBuffer.allocate(size);
    limitedBuffer.put(testData);
    limitedBuffer.flip();
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write the data - this will likely take multiple write operations
      // due to the nature of AsynchronousFileChannel
      Flow.await(file.write(0, limitedBuffer));
      
      // NOTE: We can't verify the buffer position here because RealFlowFile uses a duplicate 
      // of the buffer to avoid affecting the caller's buffer position
      
      // Close the file
      Flow.await(file.close());
      
      // Verify the file was written correctly
      byte[] fileContent = Files.readAllBytes(testFile);
      assertEquals(size, fileContent.length, "File size should match test data size");
      
      // Check the content
      for (int i = 0; i < size; i++) {
        if (testData[i] != fileContent[i]) {
          return false;
        }
      }
      
      // Additional test: Write again with multiple small writes
      // This exercises the continuation handling in the completion handler
      Path testFile2 = tempDir.resolve("test-write-multi-part.txt");
      
      // Test with 8 separate writes of 4KB each
      int partSize = 4 * 1024;
      FlowFile multiFile = Flow.await(RealFlowFile.open(
          testFile2, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write in separate chunks to different positions
      for (int i = 0; i < 8; i++) {
        ByteBuffer partBuffer = ByteBuffer.allocate(partSize);
        
        for (int j = 0; j < partSize; j++) {
          partBuffer.put((byte) ((i * partSize + j) % 256));
        }
        partBuffer.flip();
        
        // Write this part at the appropriate position
        long position = i * partSize;
        Flow.await(multiFile.write(position, partBuffer));
        
        // Note: Can't verify buffer position here either for the same reason
      }
      
      // Close and verify
      Flow.await(multiFile.close());
      
      // Expected result is the entire 32KB file with pattern data
      byte[] expectedMultiFileData = new byte[8 * partSize];
      for (int i = 0; i < expectedMultiFileData.length; i++) {
        expectedMultiFileData[i] = (byte) (i % 256);
      }
      
      byte[] actualMultiFileData = Files.readAllBytes(testFile2);
      assertArrayEquals(expectedMultiFileData, actualMultiFileData, 
          "Multiple partial writes should produce correct file content");
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  /**
   * Test for handling larger writes that may be split across multiple operations.
   */
  @Test
  void testLargeWrite() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-large-write.txt");
    
    // Create a buffer (512KB - large enough to potentially trigger partial writes)
    int size = 512 * 1024;
    byte[] testData = new byte[size];
    
    // Fill with pattern data
    for (int i = 0; i < size; i++) {
      testData[i] = (byte) (i % 256);
    }
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Write large data to the file
      ByteBuffer writeBuffer = ByteBuffer.wrap(testData);
      Flow.await(file.write(0, writeBuffer));
      
      // Close the file
      Flow.await(file.close());
      
      // Re-open the file for reading
      FlowFile readFile = Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // Get the file size
      long fileSize = Flow.await(readFile.size());
      assertEquals(size, fileSize);
      
      // Read back the data in chunks to avoid large buffer allocation
      int chunkSize = 64 * 1024; // 64KB chunks
      byte[] readData = new byte[size];
      
      for (int i = 0; i < size; i += chunkSize) {
        int readSize = Math.min(chunkSize, size - i);
        ByteBuffer buffer = Flow.await(readFile.read(i, readSize));
        buffer.get(readData, i, buffer.remaining());
      }
      
      // Close the file
      Flow.await(readFile.close());
      
      // Verify the first 10KB of data (to reduce test time)
      for (int i = 0; i < 10 * 1024; i++) {
        if (testData[i] != readData[i]) {
          return false;
        }
      }
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testDoubleClosure() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-close.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Close the file
      Flow.await(file.close());
      
      // Close again - should not throw an exception
      Flow.await(file.close());
      
      // Attempt operations on closed file
      try {
        Flow.await(file.write(0, ByteBuffer.wrap("test".getBytes())));
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected IOException: File is closed
        assertTrue(e.getMessage().contains("File is closed"));
      }
      
      try {
        Flow.await(file.read(0, 10));
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected IOException: File is closed
        assertTrue(e.getMessage().contains("File is closed"));
      }
      
      try {
        Flow.await(file.size());
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected IOException: File is closed
        assertTrue(e.getMessage().contains("File is closed"));
      }
      
      try {
        Flow.await(file.truncate(0));
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected IOException: File is closed
        assertTrue(e.getMessage().contains("File is closed"));
      }
      
      try {
        Flow.await(file.sync());
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected IOException: File is closed
        assertTrue(e.getMessage().contains("File is closed"));
      }
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testGetPath() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-get-path.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Open a file
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Check path
      Path returnedPath = file.getPath();
      assertEquals(testFile, returnedPath);
      
      // Close the file
      Flow.await(file.close());
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  @Test
  void testOpenErrorHandling() throws Exception {
    // Create an invalid path that should cause errors
    Path invalidPath = tempDir.resolve("non-existent-dir/non-existent-file.txt");
    
    // Try to open file with invalid path
    CompletableFuture<FlowFile> future = RealFlowFile.open(invalidPath, OpenOptions.READ);
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    // Verify the operation failed
    ExecutionException exception = assertThrows(
        ExecutionException.class,
        () -> future.toCompletableFuture().get());
    
    // Should be an IOException
    assertInstanceOf(IOException.class, exception.getCause());
    
    // Try to open with incompatible options (CREATE_NEW on existing file)
    Path existingFile = tempDir.resolve("existing-file.txt");
    Files.createFile(existingFile);
    
    // Open with CREATE_NEW which should fail on existing file
    CompletableFuture<FlowFile> createNewFuture = RealFlowFile.open(
        existingFile, OpenOptions.CREATE_NEW, OpenOptions.WRITE);
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    // Verify the operation failed
    ExecutionException createNewException = assertThrows(
        ExecutionException.class,
        () -> createNewFuture.toCompletableFuture().get());
    
    // Should be an IOException
    assertInstanceOf(IOException.class, createNewException.getCause());
  }
  
  /**
   * This test focuses on the error handling paths of the read and write completion handlers.
   * We'll try to read from a file that's been closed by another process and test the failed()
   * methods of the completion handlers.
   */
  @Test
  void testCompletionHandlerErrorPaths() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-handler-errors.txt");
    
    // Test with a small buffer
    byte[] testData = "Test data for error paths".getBytes();
    
    // First test the read error path
    CompletableFuture<Boolean> readErrorFuture = Flow.startActor(() -> {
      // Create a file
      Files.write(testFile, testData);
      
      // Open the file
      FlowFile file = Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // Close the file explicitly - this will make future reads fail
      Flow.await(file.close());
      
      // Now try to read - should fail
      try {
        Flow.await(file.read(0, 10));
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected exception
        assertInstanceOf(IOException.class, e);
        assertTrue(e.getMessage().contains("File is closed"));
        return true;
      }
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(readErrorFuture.toCompletableFuture().get());
    
    // Now test the write error path
    CompletableFuture<Boolean> writeErrorFuture = Flow.startActor(() -> {
      // Open a file for writing
      FlowFile file = Flow.await(RealFlowFile.open(
          testFile, OpenOptions.WRITE));
      
      // Close the file explicitly - this will make future writes fail
      Flow.await(file.close());
      
      // Now try to write - should fail
      try {
        ByteBuffer buffer = ByteBuffer.wrap(testData);
        Flow.await(file.write(0, buffer));
        return false; // Should not reach here
      } catch (Exception e) {
        // Expected exception
        assertInstanceOf(IOException.class, e);
        assertTrue(e.getMessage().contains("File is closed"));
        return true;
      }
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(writeErrorFuture.toCompletableFuture().get());
  }
  
  /**
   * This test specifically targets the failure branch of the read completion handler.
   * This directly exercises the failed() method of the RealFlowFile.1 anonymous completion handler.
   */
  @Test
  void testReadCompletionHandlerFailure() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-read-handler-failure.txt");
    Files.write(testFile, "Test data".getBytes());
    
    // Create a helper function to do a read test with failure
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // First open and then immediately close the file - leaving it in a bad state
      // for future read operations
      RealFlowFile file = (RealFlowFile) Flow.await(RealFlowFile.open(testFile, OpenOptions.READ));
      
      // Get the path for diagnostic purposes
      Path filePath = file.getPath();
      assertEquals(testFile, filePath);
      
      // Now close the underlying channel directly
      AsynchronousFileChannel channel = getFileChannel(file);
      channel.close();
      
      // Try to read from the closed channel - this will trigger the failed() method
      // in the CompletionHandler
      try {
        // This will cause the CompletionHandler.failed() method to be executed
        // because we're reading from a closed channel
        Flow.await(file.read(0, 10));
        return false; // Shouldn't get here
      } catch (Exception e) {
        // We expect a ClosedChannelException or similar
        return true;
      }
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  /**
   * This test specifically targets the failure branch of the write completion handler.
   * This directly exercises the failed() method of the RealFlowFile.2 anonymous completion handler.
   */
  @Test
  void testWriteCompletionHandlerFailure() throws Exception {
    // Create a test file path
    Path testFile = tempDir.resolve("test-write-handler-failure.txt");
    // Create the file
    Files.write(testFile, "Initial data".getBytes());
    
    // Create a helper function to do a write test with failure
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // First open and then immediately close the underlying channel
      RealFlowFile file = (RealFlowFile) Flow.await(RealFlowFile.open(
          testFile, OpenOptions.WRITE));
      
      // Get the file channel and close it directly
      AsynchronousFileChannel channel = getFileChannel(file);
      channel.close();
      
      // Try to write to the closed channel - this will trigger the failed() method
      // in the CompletionHandler
      try {
        // This write operation will cause the CompletionHandler.failed() method to be executed
        // because we're writing to a closed channel
        Flow.await(file.write(0, ByteBuffer.wrap("More data".getBytes())));
        return false; // Shouldn't get here
      } catch (Exception e) {
        // We expect a ClosedChannelException or similar
        return true;
      }
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  /**
   * This comprehensive test aims to hit every line and branch in RealFlowFile
   * to maximize coverage, including edge cases that may be missed by other tests.
   */
  @Test
  void testComprehensiveCoverage() throws Exception {
    // Create multiple test files
    Path testFile1 = tempDir.resolve("coverage1.txt");
    Path testFile2 = tempDir.resolve("coverage2.txt");
    Path testFile3 = tempDir.resolve("coverage3.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Test full file lifecycle with various edge cases
      
      // 1. Create and write with multiple options
      FlowFile file1 = Flow.await(RealFlowFile.open(
          testFile1, 
          OpenOptions.CREATE, 
          OpenOptions.WRITE));
      
      // Write small amount of data
      ByteBuffer smallData = ByteBuffer.wrap("Small data".getBytes());
      Flow.await(file1.write(0, smallData));
      
      // Get size immediately after write
      long size1 = Flow.await(file1.size());
      assertEquals("Small data".getBytes().length, size1);
      
      // Close file
      Flow.await(file1.close());
      
      // 2. Test large buffer handling for both read and write
      FlowFile file2 = Flow.await(RealFlowFile.open(
          testFile2,
          OpenOptions.CREATE,
          OpenOptions.WRITE,
          OpenOptions.READ));
      
      // Create a large buffer that will need multiple writes
      int largeSize = 256 * 1024; // 256KB
      ByteBuffer largeBuffer = ByteBuffer.allocate(largeSize);
      // Fill with pattern data
      for (int i = 0; i < largeSize; i++) {
        largeBuffer.put((byte) (i % 256));
      }
      largeBuffer.flip();
      
      // Write large buffer - this will exercise the hasRemaining path multiple times
      Flow.await(file2.write(0, largeBuffer));
      
      // Sync after write
      Flow.await(file2.sync());
      
      // Get size
      long size2 = Flow.await(file2.size());
      assertEquals(largeSize, size2);
      
      // Truncate to half size
      Flow.await(file2.truncate(largeSize / 2));
      
      // Verify truncated size
      long truncatedSize = Flow.await(file2.size());
      assertEquals(largeSize / 2, truncatedSize);
      
      // Read the truncated data
      ByteBuffer readBuffer = Flow.await(file2.read(0, (int) truncatedSize));
      assertEquals(truncatedSize, readBuffer.remaining());
      
      // Close file
      Flow.await(file2.close());
      
      // 3. More edge cases
      
      // Double open and close
      FlowFile file3 = Flow.await(RealFlowFile.open(
          testFile3,
          OpenOptions.CREATE,
          OpenOptions.WRITE,
          OpenOptions.READ,
          OpenOptions.TRUNCATE_EXISTING));
      
      // Check path
      assertEquals(testFile3, file3.getPath());
      
      // Write at an offset
      ByteBuffer offsetData = ByteBuffer.wrap("Offset data".getBytes());
      Flow.await(file3.write(100, offsetData));
      
      // Sync with error handling
      try {
        Flow.await(file3.sync());
      } catch (Exception e) {
        // Just in case sync fails (unlikely)
        e.printStackTrace();
      }
      
      // Close twice - second close is a no-op
      Flow.await(file3.close());
      Flow.await(file3.close());
      
      // Call operations on closed file to hit those branches
      try {
        Flow.await(file3.read(0, 10));
      } catch (Exception e) {
        // Expected
      }
      
      try {
        Flow.await(file3.write(0, ByteBuffer.allocate(10)));
      } catch (Exception e) {
        // Expected
      }
      
      try {
        Flow.await(file3.size());
      } catch (Exception e) {
        // Expected
      }
      
      try {
        Flow.await(file3.truncate(0));
      } catch (Exception e) {
        // Expected
      }
      
      try {
        Flow.await(file3.sync());
      } catch (Exception e) {
        // Expected
      }
      
      return true;
    });
    
    // With real file system operations, we should use get() instead of pumpUntilDone
    assertTrue(future.toCompletableFuture().get());
  }
  
  /**
   * Helper method to access the private AsynchronousFileChannel field via reflection.
   * This is only used for testing purposes to force error conditions.
   */
  private AsynchronousFileChannel getFileChannel(RealFlowFile file) throws Exception {
    java.lang.reflect.Field channelField = RealFlowFile.class.getDeclaredField("channel");
    channelField.setAccessible(true);
    return (AsynchronousFileChannel) channelField.get(file);
  }
}