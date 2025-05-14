package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SimulatedFlowFileSystem implementation.
 */
class SimulatedFlowFileSystemTest extends AbstractFlowTest {
  
  private SimulatedFlowFileSystem fileSystem;
  private SimulationParameters params;
  
  @Override
  protected void onSetUp() {
    // Set up simulation parameters
    params = new SimulationParameters();
    // Use small delays for faster tests
    params.setReadDelay(0.001);
    params.setWriteDelay(0.001);
    params.setMetadataDelay(0.001);
    
    // Create the file system
    fileSystem = new SimulatedFlowFileSystem(params);
  }
  
  @Test
  void testCreateAndExistsFile() throws Exception {
    Path path = Paths.get("/test/file.txt");
    
    // Wrap all file operations in an actor
    FlowFuture<Boolean> testFuture = Flow.startActor(() -> {
      // File should not exist initially
      boolean exists = Flow.await(fileSystem.exists(path));
      if (exists) {
        throw new AssertionError("File should not exist initially");
      }
      
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create and open file
      FlowFile file = Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE));
      
      // Close the file
      Flow.await(file.close());
      
      // File should exist now
      return Flow.await(fileSystem.exists(path));
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify the file exists
    assertTrue(testFuture.getNow());
  }
  
  @Test
  void testDirectFileWriteAndRead() throws Exception {
    // Test with direct file access - bypassing the file system
    Path path = Paths.get("/test/direct-file.txt");
    String testData = "Hello, direct file!";
    ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Create a simulated file directly
    SimulatedFlowFile file = new SimulatedFlowFile(path, params);
    
    // Wrap all file operations in an actor
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Write to file
      Flow.await(file.write(0, buffer));
      
      // Check file size
      long size = Flow.await(file.size());
      if (size != testData.length()) {
        throw new AssertionError("File size doesn't match: expected " + testData.length() 
            + ", got " + size);
      }
      
      // Read file contents
      ByteBuffer readBuffer = Flow.await(file.read(0, testData.length()));
      
      // Convert buffer to string
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify the file contents
    assertEquals(testData, testFuture.getNow());
  }
  
  @Test
  void testWriteAndReadFile() throws Exception {
    // This test uses our improved SimulatedFlowFileSystem implementation
    // that correctly shares data between file instances
    Path path = Paths.get("/test/fs-file.txt");
    String testData = "Hello, simulated world!";
    ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Wrap all file system operations in an actor
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create and write to file through the file system
      FlowFile file = Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file.write(0, buffer.duplicate()));
      
      // Flush without closing
      Flow.await(file.sync());
      
      // Verify file exists
      boolean exists = Flow.await(fileSystem.exists(path));
      if (!exists) {
        throw new AssertionError("File should exist after creation");
      }
      
      // Read the file back while keeping the first file open
      // Should work with our shared data store implementation
      FlowFile readFile = Flow.await(fileSystem.open(path, OpenOptions.READ));
      
      // Check size - should now work correctly with shared data store
      long size = Flow.await(readFile.size());
      if (size != testData.length()) {
        throw new AssertionError("File size doesn't match: expected " + testData.length() 
            + ", got " + size);
      }
      
      // Read content
      ByteBuffer readBuffer = Flow.await(readFile.read(0, (int) size));
      
      // Close both files
      Flow.await(readFile.close());
      Flow.await(file.close());
      
      // Convert buffer to string
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify file contents
    assertEquals(testData, testFuture.getNow());
  }
  
  @Test
  void testCreateDirectoryAndList() throws Exception {
    Path dirPath = Paths.get("/test/mydir");
    Path file1Path = Paths.get("/test/mydir/file1.txt");
    Path file2Path = Paths.get("/test/mydir/file2.txt");
    
    // Wrap all file system operations in an actor
    FlowFuture<List<Path>> testFuture = Flow.startActor(() -> {
      // Create directory
      Flow.await(fileSystem.createDirectories(dirPath));
      
      // Create file1
      FlowFile file1 = Flow.await(fileSystem.open(file1Path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file1.close());
      
      // Create file2
      FlowFile file2 = Flow.await(fileSystem.open(file2Path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file2.close());
      
      // List directory contents
      return Flow.await(fileSystem.list(dirPath));
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify directory contents
    List<Path> files = testFuture.getNow();
    assertEquals(2, files.size());
    
    // Sort the results to make the test stable
    List<String> filePaths = files.stream()
        .map(Path::toString)
        .sorted()
        .toList();
    
    assertEquals("/test/mydir/file1.txt", filePaths.get(0));
    assertEquals("/test/mydir/file2.txt", filePaths.get(1));
  }
  
  @Test
  void testDeleteFile() throws Exception {
    Path path = Paths.get("/test/to-delete.txt");
    
    // Wrap all file system operations in an actor
    FlowFuture<Boolean> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create file
      FlowFile file = Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file.close());
      
      // Verify file exists
      boolean exists = Flow.await(fileSystem.exists(path));
      if (!exists) {
        throw new AssertionError("File should exist after creation");
      }
      
      // Delete file
      Flow.await(fileSystem.delete(path));
      
      // Verify file no longer exists
      return !Flow.await(fileSystem.exists(path));
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify file was deleted
    assertTrue(testFuture.getNow(), "File should no longer exist after deletion");
  }
  
  @Test
  void testTruncateFile() throws Exception {
    Path path = Paths.get("/test/truncate.txt");
    String testData = "This is a test of file truncation.";
    ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Calculate truncate size
    int truncateSize = testData.length() / 2;
    
    // Wrap all file system operations in an actor
    FlowFuture<Long> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create and write to file
      FlowFile file = Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file.write(0, buffer.duplicate()));
      
      // Verify initial size
      long initialSize = Flow.await(file.size());
      if (initialSize != testData.length()) {
        throw new AssertionError("Initial file size doesn't match: expected " + testData.length() 
            + ", got " + initialSize);
      }
      
      // Truncate to half size
      Flow.await(file.truncate(truncateSize));
      
      // Check size
      long newSize = Flow.await(file.size());
      
      // Close file
      Flow.await(file.close());
      
      return newSize;
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify truncate size
    assertEquals(truncateSize, testFuture.getNow().intValue());
  }
  
  @Test
  void testMoveFile() throws Exception {
    Path source = Paths.get("/test/source.txt");
    Path target = Paths.get("/test/target.txt");
    String testData = "Test data for move operation";
    ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Wrap all file system operations in an actor
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create and write to source file
      FlowFile file = Flow.await(fileSystem.open(source, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file.write(0, buffer.duplicate()));
      Flow.await(file.close());
      
      // Move file
      Flow.await(fileSystem.move(source, target));
      
      // Verify source no longer exists
      boolean sourceExists = Flow.await(fileSystem.exists(source));
      if (sourceExists) {
        throw new AssertionError("Source file should not exist after move");
      }
      
      // Verify target exists
      boolean targetExists = Flow.await(fileSystem.exists(target));
      if (!targetExists) {
        throw new AssertionError("Target file should exist after move");
      }
      
      // Read from target file
      FlowFile readFile = Flow.await(fileSystem.open(target, OpenOptions.READ));
      ByteBuffer readBuffer = Flow.await(readFile.read(0, testData.length()));
      Flow.await(readFile.close());
      
      // Convert to string
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify file contents
    assertEquals(testData, testFuture.getNow());
  }
  
  @Test
  void testNonExistentFile() throws Exception {
    Path path = Paths.get("/nonexistent.txt");
    
    // Wrap test in an actor
    FlowFuture<Class<?>> testFuture = Flow.startActor(() -> {
      try {
        // Try to open a non-existent file for reading
        Flow.await(fileSystem.open(path, OpenOptions.READ));
        // Should not reach here
        return null;
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          return cause.getCause().getClass();
        }
        return cause.getClass();
      } catch (Exception e) {
        // In case of a different exception
        return e.getClass();
      }
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify exception type
    assertEquals(NoSuchFileException.class, testFuture.getNow());
  }
  
  @Test
  void testCreateFileAlreadyExists() throws Exception {
    Path path = Paths.get("/test/exists.txt");
    
    // Wrap test in an actor
    FlowFuture<Class<?>> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create file first time
      FlowFile file = Flow.await(fileSystem.open(path, OpenOptions.CREATE_NEW, OpenOptions.WRITE));
      Flow.await(file.close());
      
      try {
        // Try to create file second time with CREATE_NEW
        Flow.await(fileSystem.open(path, OpenOptions.CREATE_NEW, OpenOptions.WRITE));
        // Should not reach here
        return null;
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          return cause.getCause().getClass();
        }
        return cause.getClass();
      } catch (Exception e) {
        // In case of a different exception
        return e.getClass();
      }
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify exception type
    assertEquals(FileAlreadyExistsException.class, testFuture.getNow());
  }
  
  @Test
  void testFileSystemFileClose() throws Exception {
    Path path = Paths.get("/test/close-test.txt");
    String testData = "Test data for close test";
    ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Wrap test in an actor
    FlowFuture<Boolean> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create and write to file
      FlowFile file = Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file.write(0, buffer.duplicate()));
      
      // Check file is a SimulatedFlowFile
      if (!(file instanceof SimulatedFlowFile)) {
        throw new AssertionError("File should be a SimulatedFlowFile");
      }
      
      SimulatedFlowFile simFile = (SimulatedFlowFile) file;
      assertTrue(!simFile.isClosed(), "File should not be closed initially");
      
      // Close the file
      Flow.await(file.close());
      
      // Verify file is closed
      assertTrue(simFile.isClosed(), "File should be closed after close()");
      
      try {
        // Try to write to closed file
        Flow.await(file.write(0, ByteBuffer.wrap("More data".getBytes())));
        return false; // Should not reach here
      } catch (Exception e) {
        // Make sure it's a "File is closed" exception
        return e.getMessage().contains("File is closed");
      }
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify exception was thrown with correct message
    assertTrue(testFuture.getNow(), "Should have received 'File is closed' error");
  }
  
  @Test
  void testOpenClosedFileAgain() throws Exception {
    Path path = Paths.get("/test/reopen-test.txt");
    String initialData = "Initial data";
    String updatedData = "Updated data through new file handle";
    
    // Wrap test in an actor
    FlowFuture<String> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Create and write to file, then close it
      FlowFile file1 = Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file1.write(0, ByteBuffer.wrap(initialData.getBytes())));
      Flow.await(file1.close());
      
      // Verify first file is closed
      SimulatedFlowFile simFile1 = (SimulatedFlowFile) file1;
      assertTrue(simFile1.isClosed(), "First file should be closed");
      
      // Open the file again for writing
      FlowFile file2 = Flow.await(fileSystem.open(path, OpenOptions.WRITE));
      
      // Verify second file is not closed and is a different instance
      SimulatedFlowFile simFile2 = (SimulatedFlowFile) file2;
      assertTrue(!simFile2.isClosed(), "Second file should not be closed");
      assertTrue(simFile1 != simFile2, "Should be different file instances");
      
      // Write to the new file handle
      Flow.await(file2.truncate(0)); // Clear existing content
      Flow.await(file2.write(0, ByteBuffer.wrap(updatedData.getBytes())));
      Flow.await(file2.close());
      
      // Open again for reading
      FlowFile file3 = Flow.await(fileSystem.open(path, OpenOptions.READ));
      ByteBuffer readBuffer = Flow.await(file3.read(0, updatedData.length()));
      Flow.await(file3.close());
      
      // Convert to string
      byte[] readData = new byte[readBuffer.remaining()];
      readBuffer.get(readData);
      return new String(readData, StandardCharsets.UTF_8);
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify data was updated
    assertEquals(updatedData, testFuture.getNow(), 
        "Content should match what was written by the second file handle");
  }
  
  @Test
  void testMultipleFileOperationsWithClose() throws Exception {
    Path path = Paths.get("/test/multi-op-test.txt");
    
    // Wrap test in an actor
    FlowFuture<Boolean> testFuture = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectories(Paths.get("/test")));
      
      // Test sequence:
      // 1. Create file
      // 2. Write data
      // 3. Close
      // 4. Try to read (should fail)
      // 5. Open again
      // 6. Read (should work)
      // 7. Close
      
      // Step 1 & 2: Create and write
      FlowFile file1 = Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file1.write(0, ByteBuffer.wrap("Test data".getBytes())));
      
      // Step 3: Close
      Flow.await(file1.close());
      
      // Step 4: Try to read from closed file
      boolean readFailedAsExpected = false;
      try {
        Flow.await(file1.read(0, 10));
      } catch (Exception e) {
        readFailedAsExpected = e.getMessage().contains("File is closed");
      }
      
      if (!readFailedAsExpected) {
        throw new AssertionError("Reading from closed file should fail");
      }
      
      // Step 5: Open again
      FlowFile file2 = Flow.await(fileSystem.open(path, OpenOptions.READ));
      
      // Step 6: Read
      ByteBuffer buffer = Flow.await(file2.read(0, 10));
      String readData = new String(buffer.array(), buffer.position(), buffer.remaining());
      boolean dataCorrect = "Test data".equals(readData);
      
      // Step 7: Close
      Flow.await(file2.close());
      
      return dataCorrect;
    });
    
    // Wait for the actor to complete
    pumpUntilDone(testFuture);
    
    // Verify operations worked as expected
    assertTrue(testFuture.getNow(), "File operations should work correctly with proper closing");
  }
}