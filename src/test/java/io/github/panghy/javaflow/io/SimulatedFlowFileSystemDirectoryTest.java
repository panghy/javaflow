package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SimulatedFlowFileSystem class focusing on directory operations.
 */
class SimulatedFlowFileSystemDirectoryTest extends AbstractFlowTest {
  
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
  
  @Test
  void testCreateDirectory() throws Exception {
    Path dir = Paths.get("/testDir");
    
    // Create the directory
    FlowFuture<Boolean> future = Flow.startActor(() -> {
      Flow.await(fileSystem.createDirectory(dir));
      
      // Check it exists
      return Flow.await(fileSystem.exists(dir));
    });
    
    pumpUntilDone(future);
    
    // Verify it exists
    assertTrue(future.getNow());
  }
  
  @Test
  void testCreateDirectoryDuplicate() throws Exception {
    Path dir = Paths.get("/testDir");
    
    // Create the directory
    FlowFuture<Void> createFuture = Flow.startActor(() -> {
      Flow.await(fileSystem.createDirectory(dir));
      return null;
    });
    
    pumpUntilDone(createFuture);
    
    // Try to create the same directory again
    FlowFuture<Class<?>> future = Flow.startActor(() -> {
      try {
        Flow.await(fileSystem.createDirectory(dir));
        return null;
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          return cause.getCause().getClass();
        }
        return cause.getClass();
      }
    });
    
    pumpUntilDone(future);
    
    // Verify exception type
    assertEquals(FileAlreadyExistsException.class, future.getNow());
  }
  
  @Test
  void testCreateDirectories() throws Exception {
    Path deep = Paths.get("/a/b/c/d");
    
    // Create nested directories and check they all exist
    FlowFuture<Boolean> future = Flow.startActor(() -> {
      Flow.await(fileSystem.createDirectories(deep));
      
      // Check all directories exist
      boolean aExists = Flow.await(fileSystem.exists(Paths.get("/a")));
      boolean bExists = Flow.await(fileSystem.exists(Paths.get("/a/b")));
      boolean cExists = Flow.await(fileSystem.exists(Paths.get("/a/b/c")));
      boolean dExists = Flow.await(fileSystem.exists(Paths.get("/a/b/c/d")));
      
      return aExists && bExists && cExists && dExists;
    });
    
    pumpUntilDone(future);
    
    // Verify all directories exist
    assertTrue(future.getNow());
  }
  
  @Test
  void testList() throws Exception {
    // Create a directory structure
    Path parent = Paths.get("/parent");
    Path child1 = Paths.get("/parent/child1");
    Path child2 = Paths.get("/parent/child2");
    Path file1 = Paths.get("/parent/file1");
    
    FlowFuture<List<Path>> future = Flow.startActor(() -> {
      // Create parent directory
      Flow.await(fileSystem.createDirectory(parent));
      
      // Create child directories
      Flow.await(fileSystem.createDirectory(child1));
      Flow.await(fileSystem.createDirectory(child2));
      
      // Create a file
      Flow.await(fileSystem.open(file1, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // List the parent directory
      return Flow.await(fileSystem.list(parent));
    });
    
    pumpUntilDone(future);
    
    // Verify results
    List<Path> children = future.getNow();
    assertEquals(3, children.size());
    assertTrue(children.contains(Paths.get("/parent/child1")));
    assertTrue(children.contains(Paths.get("/parent/child2")));
    assertTrue(children.contains(Paths.get("/parent/file1")));
  }
  
  @Test
  void testListNonExistentDirectory() throws Exception {
    Path nonExistent = Paths.get("/doesNotExist");
    
    // Try to list a non-existent directory
    FlowFuture<Class<?>> future = Flow.startActor(() -> {
      try {
        Flow.await(fileSystem.list(nonExistent));
        return null;
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          return cause.getCause().getClass();
        }
        return cause.getClass();
      }
    });
    
    pumpUntilDone(future);
    
    // Verify exception type
    assertEquals(NoSuchFileException.class, future.getNow());
  }
  
  @Test
  void testDeleteDirectory() throws Exception {
    // Create a directory
    Path dir = Paths.get("/testDir");
    
    FlowFuture<Boolean> future = Flow.startActor(() -> {
      // Create the directory
      Flow.await(fileSystem.createDirectory(dir));
      
      // Verify it exists
      boolean exists = Flow.await(fileSystem.exists(dir));
      if (!exists) {
        throw new AssertionError("Directory should exist after creation");
      }
      
      // Delete the directory
      Flow.await(fileSystem.delete(dir));
      
      // Check it no longer exists
      return !Flow.await(fileSystem.exists(dir));
    });
    
    pumpUntilDone(future);
    
    // Verify the directory was deleted
    assertTrue(future.getNow());
  }
  
  @Test
  void testDeleteNonEmptyDirectory() throws Exception {
    // Create a directory with contents and try to delete it
    Path parent = Paths.get("/parent");
    Path child = Paths.get("/parent/child");
    
    FlowFuture<Class<?>> future = Flow.startActor(() -> {
      // Create parent and child directories
      Flow.await(fileSystem.createDirectory(parent));
      Flow.await(fileSystem.createDirectory(child));
      
      try {
        // Try to delete the non-empty directory
        Flow.await(fileSystem.delete(parent));
        return null;
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          return cause.getCause().getClass();
        }
        return cause.getClass();
      }
    });
    
    pumpUntilDone(future);
    
    // Verify the exception class is IOException
    assertEquals(IOException.class, future.getNow());
  }
  
  @Test
  void testMoveDirectory() throws Exception {
    // Create directories
    Path source = Paths.get("/source");
    Path target = Paths.get("/target");
    Path file = Paths.get("/source/file");
    
    FlowFuture<Boolean> future = Flow.startActor(() -> {
      // Create source directory
      Flow.await(fileSystem.createDirectory(source));
      
      // Create a file in the source directory
      Flow.await(fileSystem.open(file, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Move the directory
      Flow.await(fileSystem.move(source, target));
      
      // Verify results
      boolean sourceExists = Flow.await(fileSystem.exists(source));
      boolean targetExists = Flow.await(fileSystem.exists(target));
      boolean fileExists = Flow.await(fileSystem.exists(Paths.get("/target/file")));
      
      // Source should not exist, target and moved file should exist
      return !sourceExists && targetExists && fileExists;
    });
    
    pumpUntilDone(future);
    
    // Verify all checks pass
    assertTrue(future.getNow());
  }
  
  @Test
  void testMoveToExistingTarget() throws Exception {
    // Create directories and try to move one to another that already exists
    Path source = Paths.get("/source");
    Path target = Paths.get("/target");
    
    FlowFuture<Class<?>> future = Flow.startActor(() -> {
      // Create source and target directories
      Flow.await(fileSystem.createDirectory(source));
      Flow.await(fileSystem.createDirectory(target));
      
      try {
        // Try to move to an existing directory
        Flow.await(fileSystem.move(source, target));
        return null;
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          return cause.getCause().getClass();
        }
        return cause.getClass();
      }
    });
    
    pumpUntilDone(future);
    
    // Verify exception type
    assertEquals(FileAlreadyExistsException.class, future.getNow());
  }
  
  @Test
  void testDeepDirectoryStructure() throws Exception {
    // Create and verify a deep directory structure
    Path path = Paths.get("/level1/level2/level3/level4/level5");
    
    FlowFuture<Boolean> future = Flow.startActor(() -> {
      // Create the deep directory structure
      Flow.await(fileSystem.createDirectories(path));
      
      // Create files at each level
      Path file1Path = Paths.get("/level1/file1");
      Path file2Path = Paths.get("/level1/level2/file2");
      Path file3Path = Paths.get("/level1/level2/level3/file3");
      
      Flow.await(fileSystem.open(file1Path, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      Flow.await(fileSystem.open(file2Path, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      Flow.await(fileSystem.open(file3Path, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      Path file4Path = Paths.get("/level1/level2/level3/level4/file4");
      Path file5Path = Paths.get("/level1/level2/level3/level4/level5/file5");
      
      Flow.await(fileSystem.open(file4Path, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      Flow.await(fileSystem.open(file5Path, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // List each level and verify contents
      // Level 1
      List<Path> level1Files = Flow.await(fileSystem.list(Paths.get("/level1")));
      if (level1Files.size() != 2 || 
          !level1Files.contains(Paths.get("/level1/file1")) ||
          !level1Files.contains(Paths.get("/level1/level2"))) {
        return false;
      }
      
      // Level 2
      List<Path> level2Files = Flow.await(fileSystem.list(Paths.get("/level1/level2")));
      if (level2Files.size() != 2 || 
          !level2Files.contains(Paths.get("/level1/level2/file2")) ||
          !level2Files.contains(Paths.get("/level1/level2/level3"))) {
        return false;
      }
      
      // Move a deep subdirectory to a new location
      Path sourceDir = Paths.get("/level1/level2/level3");
      Path targetDir = Paths.get("/level1/newlevel3");
      Flow.await(fileSystem.move(sourceDir, targetDir));
      
      // Verify the move succeeded
      boolean targetExists = Flow.await(fileSystem.exists(targetDir));
      boolean file3Exists = Flow.await(fileSystem.exists(Paths.get("/level1/newlevel3/file3")));
      boolean level4Exists = Flow.await(fileSystem.exists(Paths.get("/level1/newlevel3/level4")));
      boolean file4Exists = Flow.await(fileSystem.exists(Paths.get("/level1/newlevel3/level4/file4")));
      boolean level5Exists = Flow.await(fileSystem.exists(Paths.get("/level1/newlevel3/level4/level5")));
      boolean file5Exists = Flow.await(fileSystem.exists(Paths.get("/level1/newlevel3/level4/level5/file5")));
      
      // Original structure should be gone
      boolean sourceGone = !Flow.await(fileSystem.exists(sourceDir));
      
      // All verifications should pass
      return targetExists && file3Exists && level4Exists && file4Exists && 
             level5Exists && file5Exists && sourceGone;
    });
    
    pumpUntilDone(future);
    
    assertTrue(future.getNow());
  }
  
  @Test
  void testOpenOptionsValidation() throws Exception {
    Path filePath = Paths.get("/testfile");
    
    // Test file opening with various option combinations
    FlowFuture<Class<?>[]> future = Flow.startActor(() -> {
      Class<?>[] results = new Class<?>[2];
      
      // Try to open a non-existent file without CREATE/CREATE_NEW
      try {
        Flow.await(fileSystem.open(filePath, OpenOptions.READ));
        results[0] = null; // Should not reach here
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          results[0] = cause.getCause().getClass();
        } else {
          results[0] = cause.getClass();
        }
      }
      
      // Create the file
      Flow.await(fileSystem.open(filePath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Try to open with CREATE_NEW (should fail since file exists)
      try {
        Flow.await(fileSystem.open(filePath, OpenOptions.CREATE_NEW, OpenOptions.WRITE));
        results[1] = null; // Should not reach here
      } catch (ExecutionException e) {
        // Flow.await wraps exceptions in ExecutionException
        Throwable cause = e.getCause();
        // SimulatedFlowFileSystem wraps exceptions in RuntimeException
        if (cause instanceof RuntimeException && cause.getCause() != null) {
          results[1] = cause.getCause().getClass();
        } else {
          results[1] = cause.getClass();
        }
      }
      
      return results;
    });
    
    pumpUntilDone(future);
    
    Class<?>[] results = future.getNow();
    assertEquals(NoSuchFileException.class, results[0]);
    assertEquals(FileAlreadyExistsException.class, results[1]);
  }
  
  @Test
  void testNormalizePath() throws Exception {
    // Test paths with/without leading slash
    Path path1 = Paths.get("/dir1");
    Path path2 = Paths.get("dir2");
    
    FlowFuture<Boolean> future = Flow.startActor(() -> {
      // Both paths should be created and accessible
      Flow.await(fileSystem.createDirectory(path1));
      Flow.await(fileSystem.createDirectory(path2));
      
      // Check they exist
      boolean path1Exists = Flow.await(fileSystem.exists(path1));
      boolean path2Exists = Flow.await(fileSystem.exists(path2));
      boolean path2WithSlashExists = Flow.await(fileSystem.exists(Paths.get("/dir2"))); // Should normalize to /dir2
      
      return path1Exists && path2Exists && path2WithSlashExists;
    });
    
    pumpUntilDone(future);
    
    assertTrue(future.getNow());
  }
}