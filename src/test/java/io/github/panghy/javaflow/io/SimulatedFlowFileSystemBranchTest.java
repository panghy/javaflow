package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional tests for SimulatedFlowFileSystem to increase branch coverage.
 */
class SimulatedFlowFileSystemBranchTest extends AbstractFlowTest {
  
  private SimulatedFlowFileSystem fileSystem;
  
  @Override
  protected void onSetUp() {
    // Create file system with small delays for faster tests
    SimulationParameters params = new SimulationParameters()
        .setReadDelay(0.001)
        .setWriteDelay(0.001)
        .setMetadataDelay(0.001);
    
    fileSystem = new SimulatedFlowFileSystem(params);
  }
  
  /**
   * Test that covers the case where create directories is called on a path that already exists.
   */
  @Test
  void testCreateDirectoriesExistingPath() throws Exception {
    // Create a directory structure first
    Path dir = Paths.get("/test");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directory first time
      Flow.await(fileSystem.createDirectory(dir));
      
      // This should succeed on a directory that already exists
      Flow.await(fileSystem.createDirectories(dir));
      
      return Flow.await(fileSystem.exists(dir));
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test that covers the edge case in createDirectories where a file exists with the name.
   */
  @Test
  void testCreateDirectoriesFileExists() throws Exception {
    // Create a file first
    Path filePath = Paths.get("/testfile");
    
    CompletableFuture<String> future = Flow.startActor(() -> {
      // Create a file
      System.out.println("Creating file at " + filePath);
      Flow.await(fileSystem.open(filePath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Verify file exists before attempting createDirectories
      boolean fileExistsBefore = Flow.await(fileSystem.exists(filePath));
      System.out.println("File exists before createDirectories: " + fileExistsBefore);
      
      try {
        // Try to create directories with same name as file
        System.out.println("Attempting to call createDirectories on " + filePath);
        Flow.await(fileSystem.createDirectories(filePath));
        System.out.println("createDirectories completed without exception");
        return "NO_EXCEPTION";
      } catch (Exception e) {
        System.out.println("Exception caught: " + e.getClass().getName());
        if (e.getCause() != null) {
          System.out.println("Cause: " + e.getCause().getClass().getName());
          if (e.getCause() != null) {
            System.out.println("Root cause: " + e.getCause().getClass().getName());
          }
        }
        
        // Expected behavior: we should get a FileAlreadyExistsException
        // The exception might come directly, not wrapped in RuntimeException
        if (e instanceof FileAlreadyExistsException) {
          // Verify the file still exists after the failed operation
          boolean fileExistsAfter = Flow.await(fileSystem.exists(filePath));
          System.out.println("File exists after exception (direct): " + fileExistsAfter);
          return "EXPECTED_EXCEPTION_FILE_EXISTS_" + fileExistsAfter;
        } else if (e.getCause() instanceof RuntimeException && 
            e.getCause() instanceof FileAlreadyExistsException) {
          // Alternatively, it might be wrapped in RuntimeException
          boolean fileExistsAfter = Flow.await(fileSystem.exists(filePath));
          System.out.println("File exists after exception (wrapped): " + fileExistsAfter);
          return "EXPECTED_EXCEPTION_FILE_EXISTS_" + fileExistsAfter;
        }
        return "UNEXPECTED_EXCEPTION";
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    System.out.println("Test result: " + future.getNow(null));
    assertEquals("EXPECTED_EXCEPTION_FILE_EXISTS_true", future.getNow(null));
  }
  
  /**
   * Test creating nested directories in one call.
   */
  @Test
  void testCreateNestedDirectories() throws Exception {
    Path deepPath = Paths.get("/level1/level2/level3");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create all directories at once
      Flow.await(fileSystem.createDirectories(deepPath));
      
      // Check parent directories were created too
      boolean level1Exists = Flow.await(fileSystem.exists(Paths.get("/level1")));
      boolean level2Exists = Flow.await(fileSystem.exists(Paths.get("/level1/level2")));
      boolean level3Exists = Flow.await(fileSystem.exists(deepPath));
      
      return level1Exists && level2Exists && level3Exists;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test that covers path normalization with trailing slashes.
   */
  @Test
  void testPathNormalization() throws Exception {
    Path pathWithTrailingSlash = Paths.get("/test/");
    Path pathWithoutTrailingSlash = Paths.get("/test");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directory using path with trailing slash
      Flow.await(fileSystem.createDirectory(pathWithTrailingSlash));
      
      // Check it exists using path without trailing slash
      return Flow.await(fileSystem.exists(pathWithoutTrailingSlash));
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test path normalization for relative paths.
   */
  @Test
  void testRelativePathNormalization() throws Exception {
    Path relativePath = Paths.get("relative");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directory with relative path
      Flow.await(fileSystem.createDirectory(relativePath));
      
      // Check it exists with absolute path
      return Flow.await(fileSystem.exists(Paths.get("/relative")));
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test moving a directory with children.
   */
  @Test
  void testMoveDirectoryWithChildren() throws Exception {
    Path parentDir = Paths.get("/parentdir");
    Path childFile = Paths.get("/parentdir/childfile.txt");
    Path childDir = Paths.get("/parentdir/childdir");
    Path targetDir = Paths.get("/targetdir");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directory structure
      Flow.await(fileSystem.createDirectory(parentDir));
      Flow.await(fileSystem.open(childFile, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      Flow.await(fileSystem.createDirectory(childDir));
      
      // Move parent directory
      Flow.await(fileSystem.move(parentDir, targetDir));
      
      // Check original directory is gone
      boolean parentGone = !Flow.await(fileSystem.exists(parentDir));
      
      // Check target directory exists with children
      boolean targetExists = Flow.await(fileSystem.exists(targetDir));
      boolean childFileExists = Flow.await(fileSystem.exists(Paths.get("/targetdir/childfile.txt")));
      boolean childDirExists = Flow.await(fileSystem.exists(Paths.get("/targetdir/childdir")));
      
      return parentGone && targetExists && childFileExists && childDirExists;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test that covers the root directory case in getParentPath.
   */
  @Test
  void testRootDirectoryOperations() throws Exception {
    Path root = Paths.get("/");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // List the root directory
      List<Path> rootEntries = Flow.await(fileSystem.list(root));
      
      // Verify it can be listed
      return rootEntries != null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test that covers the parent/child relationship with root directory.
   */
  @Test
  void testRootAsParent() throws Exception {
    Path root = Paths.get("/");
    Path childFile = Paths.get("/rootfile.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create a file at root level
      Flow.await(fileSystem.open(childFile, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // List the root directory
      List<Path> rootEntries = Flow.await(fileSystem.list(root));
      
      // Root should contain the file
      return rootEntries.contains(childFile);
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test file opening without needed options (READ/WRITE/CREATE).
   */
  @Test
  void testOpenWithoutRequiredOptions() throws Exception {
    Path filePath = Paths.get("/testfile.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Try to open a non-existent file without CREATE
      try {
        Flow.await(fileSystem.open(filePath)).close();
        return false; // Should not reach here
      } catch (Exception e) {
        // Should get NoSuchFileException
        return e.getCause() instanceof NoSuchFileException;
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test trying to open a file with CREATE_NEW when it already exists.
   */
  @Test
  void testOpenCreateNewWithExistingFile() throws Exception {
    Path filePath = Paths.get("/testfile.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create file first
      Flow.await(fileSystem.open(filePath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Try to create again with CREATE_NEW
      try {
        Flow.await(fileSystem.open(filePath, OpenOptions.CREATE_NEW, OpenOptions.WRITE)).close();
        return false; // Should not reach here
      } catch (Exception e) {
        // Should get FileAlreadyExistsException
        return e.getCause() instanceof FileAlreadyExistsException;
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test trying to list a non-existent directory.
   */
  @Test
  void testListNonExistentDirectory() throws Exception {
    Path nonExistentDir = Paths.get("/nonexistentdir");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      try {
        Flow.await(fileSystem.list(nonExistentDir));
        return false; // Should not reach here
      } catch (Exception e) {
        // Should get NoSuchFileException
        return e.getCause() instanceof NoSuchFileException;
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test null path handling in various methods.
   */
  @Test
  void testNullPathHandling() throws Exception {
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      boolean allPassedNullTests = true;
      
      // Test null path handling in open
      try {
        CompletableFuture<FlowFile> openFuture = fileSystem.open(null);
        Flow.await(openFuture);
        allPassedNullTests = false; // Should not reach here
      } catch (Exception e) {
        // Expected: NullPointerException
        if (!(e instanceof NullPointerException)) {
          allPassedNullTests = false;
        }
      }
      
      // Test null path handling in delete
      try {
        CompletableFuture<Void> deleteFuture = fileSystem.delete(null);
        // Not awaiting, just checking the returned future
        if (!deleteFuture.isCompletedExceptionally()) {
          allPassedNullTests = false;
        }
      } catch (Exception e) {
        // Also acceptable: immediate exception
      }
      
      // Test null path handling in exists
      try {
        CompletableFuture<Boolean> existsFuture = fileSystem.exists(null);
        // Not awaiting, just checking the returned future
        if (!existsFuture.isCompletedExceptionally()) {
          allPassedNullTests = false;
        }
      } catch (Exception e) {
        // Also acceptable: immediate exception
      }
      
      // Test null path handling in createDirectory
      try {
        CompletableFuture<Void> createDirFuture = fileSystem.createDirectory(null);
        // Not awaiting, just checking the returned future
        if (!createDirFuture.isCompletedExceptionally()) {
          allPassedNullTests = false;
        }
      } catch (Exception e) {
        // Also acceptable: immediate exception
      }
      
      // Test null path handling in createDirectories
      try {
        CompletableFuture<Void> createDirsFuture = fileSystem.createDirectories(null);
        // Not awaiting, just checking the returned future
        if (!createDirsFuture.isCompletedExceptionally()) {
          allPassedNullTests = false;
        }
      } catch (Exception e) {
        // Also acceptable: immediate exception
      }
      
      // Test null path handling in list
      try {
        CompletableFuture<List<Path>> listFuture = fileSystem.list(null);
        // Not awaiting, just checking the returned future
        if (!listFuture.isCompletedExceptionally()) {
          allPassedNullTests = false;
        }
      } catch (Exception e) {
        // Also acceptable: immediate exception
      }
      
      return allPassedNullTests;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test trying to move a file to an existing target path.
   */
  @Test
  void testMoveToExistingTarget() throws Exception {
    Path sourcePath = Paths.get("/source.txt");
    Path targetPath = Paths.get("/target.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create both source and target files
      Flow.await(fileSystem.open(sourcePath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      Flow.await(fileSystem.open(targetPath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Try to move
      try {
        Flow.await(fileSystem.move(sourcePath, targetPath));
        return false; // Should not reach here
      } catch (Exception e) {
        // Should get FileAlreadyExistsException
        return e.getCause() instanceof FileAlreadyExistsException;
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test deleting a non-empty directory.
   */
  @Test
  void testDeleteNonEmptyDirectory() throws Exception {
    Path dirPath = Paths.get("/nonemptydir");
    Path filePath = Paths.get("/nonemptydir/file.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directory and file inside it
      Flow.await(fileSystem.createDirectory(dirPath));
      Flow.await(fileSystem.open(filePath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Try to delete the directory
      try {
        Flow.await(fileSystem.delete(dirPath));
        return false; // Should not reach here
      } catch (Exception e) {
        // Should get IOException with "Directory not empty"
        String message = e.getCause().getMessage();
        return message != null && message.contains("Directory not empty");
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test creating directory that already exists.
   */
  @Test
  void testCreateExistingDirectory() throws Exception {
    Path dirPath = Paths.get("/existingdir");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directory first time
      Flow.await(fileSystem.createDirectory(dirPath));
      
      // Try to create it again
      try {
        Flow.await(fileSystem.createDirectory(dirPath));
        return false; // Should not reach here
      } catch (Exception e) {
        // Should get FileAlreadyExistsException
        return e.getCause() instanceof FileAlreadyExistsException;
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test error injection for various operations.
   */
  @Test
  void testErrorInjection() throws Exception {
    // Create file system with high error probability for metadata operations
    SimulationParameters params = new SimulationParameters()
        .setMetadataErrorProbability(1.0); // Always fail
    
    SimulatedFlowFileSystem errorSystem = new SimulatedFlowFileSystem(params);
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      boolean allTestsPassed = true;
      
      // Test error injection in open
      try {
        CompletableFuture<FlowFile> openFuture = errorSystem.open(Paths.get("/file.txt"), OpenOptions.CREATE);
        // Check if future has failed with the expected error
        try {
          Flow.await(openFuture);
          allTestsPassed = false; // Should not reach here
        } catch (Exception e) {
          if (!(e instanceof IOException) || 
              !e.getMessage().contains("Simulated open error")) {
            allTestsPassed = false;
          }
        }
      } catch (Exception e) {
        // Immediate exception is not expected
        allTestsPassed = false;
      }
      
      // Test error injection in delete
      try {
        CompletableFuture<Void> deleteFuture = errorSystem.delete(Paths.get("/file.txt"));
        try {
          Flow.await(deleteFuture);
          allTestsPassed = false; // Should not reach here
        } catch (Exception e) {
          if (!(e instanceof IOException) || 
              !e.getMessage().contains("Simulated delete error")) {
            allTestsPassed = false;
          }
        }
      } catch (Exception e) {
        // Immediate exception is not expected
        allTestsPassed = false;
      }
      
      // Test error injection in exists
      try {
        CompletableFuture<Boolean> existsFuture = errorSystem.exists(Paths.get("/file.txt"));
        try {
          Flow.await(existsFuture);
          allTestsPassed = false; // Should not reach here
        } catch (Exception e) {
          if (!(e instanceof IOException) || 
              !e.getMessage().contains("Simulated exists error")) {
            allTestsPassed = false;
          }
        }
      } catch (Exception e) {
        // Immediate exception is not expected
        allTestsPassed = false;
      }
      
      // Test error injection in createDirectory
      try {
        CompletableFuture<Void> createDirFuture = errorSystem.createDirectory(Paths.get("/dir"));
        try {
          Flow.await(createDirFuture);
          allTestsPassed = false; // Should not reach here
        } catch (Exception e) {
          if (!(e instanceof IOException) || 
              !e.getMessage().contains("Simulated createDirectory error")) {
            allTestsPassed = false;
          }
        }
      } catch (Exception e) {
        // Immediate exception is not expected
        allTestsPassed = false;
      }
      
      // Test error injection in createDirectories
      try {
        CompletableFuture<Void> createDirsFuture = errorSystem.createDirectories(Paths.get("/dir/subdir"));
        try {
          Flow.await(createDirsFuture);
          allTestsPassed = false; // Should not reach here
        } catch (Exception e) {
          if (!(e instanceof IOException) || 
              !e.getMessage().contains("Simulated createDirectories error")) {
            allTestsPassed = false;
          }
        }
      } catch (Exception e) {
        // Immediate exception is not expected
        allTestsPassed = false;
      }
      
      // Test error injection in list
      try {
        CompletableFuture<List<Path>> listFuture = errorSystem.list(Paths.get("/"));
        try {
          Flow.await(listFuture);
          allTestsPassed = false; // Should not reach here
        } catch (Exception e) {
          if (!(e instanceof IOException) || 
              !e.getMessage().contains("Simulated list error")) {
            allTestsPassed = false;
          }
        }
      } catch (Exception e) {
        // Immediate exception is not expected
        allTestsPassed = false;
      }
      
      // Test error injection in move
      try {
        CompletableFuture<Void> moveFuture = errorSystem.move(Paths.get("/src"), Paths.get("/dst"));
        try {
          Flow.await(moveFuture);
          allTestsPassed = false; // Should not reach here
        } catch (Exception e) {
          if (!(e instanceof IOException) || 
              !e.getMessage().contains("Simulated move error")) {
            allTestsPassed = false;
          }
        }
      } catch (Exception e) {
        // Immediate exception is not expected
        allTestsPassed = false;
      }
      
      return allTestsPassed;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test that covers deleting a non-existent file.
   */
  @Test
  void testDeleteNonExistentFile() throws Exception {
    Path nonExistentPath = Paths.get("/doesnotexist.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      try {
        Flow.await(fileSystem.delete(nonExistentPath));
        return false; // Should not reach here
      } catch (Exception e) {
        // Verify the exception type
        return e.getCause() instanceof NoSuchFileException;
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test move with no source parent directory.
   */
  @Test
  void testMoveNoSourceParent() throws Exception {
    Path sourcePath = Paths.get("/noparent/file.txt");
    Path targetPath = Paths.get("/target.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      try {
        // Try to move a file with no parent
        Flow.await(fileSystem.move(sourcePath, targetPath));
        return false; // Should not reach here
      } catch (Exception e) {
        // Verify the exception type
        return e.getCause() instanceof NoSuchFileException;
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test move with no target parent directory.
   */
  @Test
  void testMoveNoTargetParent() throws Exception {
    Path sourcePath = Paths.get("/source.txt");
    Path targetPath = Paths.get("/noparent/target.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create source file
      Flow.await(fileSystem.open(sourcePath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      try {
        // Try to move to a path with no parent
        Flow.await(fileSystem.move(sourcePath, targetPath));
        return false; // Should not reach here
      } catch (Exception e) {
        // Verify it fails with parent not exists
        String message = e.getCause().getMessage();
        return message != null && message.contains("parent directory does not exist");
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test createDirectory with non-existent parent.
   */
  @Test
  void testCreateDirectoryNoParent() throws Exception {
    Path dirPath = Paths.get("/noparent/dir");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      try {
        // Try to create directory with no parent
        Flow.await(fileSystem.createDirectory(dirPath));
        return false; // Should not reach here
      } catch (Exception e) {
        // Verify the exception type
        String message = e.getCause().getMessage();
        return message != null && message.contains("Parent directory does not exist");
      }
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Test truncating an existing file when opening with TRUNCATE_EXISTING.
   */
  @Test
  void testTruncateExisting() throws Exception {
    Path filePath = Paths.get("/file.txt");
    
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create and write to file
      FlowFile file1 = Flow.await(fileSystem.open(filePath, OpenOptions.CREATE, OpenOptions.WRITE));
      ByteBuffer buffer = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5});
      Flow.await(file1.write(0, buffer));
      Flow.await(file1.close());
      
      // Verify file has content
      FlowFile file1Read = Flow.await(fileSystem.open(filePath, OpenOptions.READ));
      long size1 = Flow.await(file1Read.size());
      Flow.await(file1Read.close());
      
      // Open with TRUNCATE_EXISTING
      FlowFile file2 = Flow.await(fileSystem.open(filePath, OpenOptions.READ, OpenOptions.TRUNCATE_EXISTING));
      long size2 = Flow.await(file2.size());
      Flow.await(file2.close());
      
      // File should be empty (size should be 0)
      return size1 > 0 && size2 == 0;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
}