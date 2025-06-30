package io.github.panghy.javaflow.io;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the internal classes of SimulatedFlowFileSystem.
 * Uses reflection to access private methods and classes.
 */
class SimulatedFlowFileSystemEntryTest extends AbstractFlowTest {
  
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
   * Tests the DirectoryEntry class using direct access since it's now package-private.
   */
  @Test
  void testDirectoryEntry() {
    // Create a directory entry
    SimulatedFlowFileSystem.DirectoryEntry dirEntry = new SimulatedFlowFileSystem.DirectoryEntry("/test");
    
    // Test adding a child
    dirEntry.addChild("/test/child1");
    
    // Test getting children
    List<String> children = dirEntry.getChildren();
    assertEquals(1, children.size());
    assertEquals("/test/child1", children.get(0));
    
    // Test adding the same child again (should not duplicate)
    dirEntry.addChild("/test/child1");
    children = dirEntry.getChildren();
    assertEquals(1, children.size());
    
    // Test adding another child
    dirEntry.addChild("/test/child2");
    children = dirEntry.getChildren();
    assertEquals(2, children.size());
    
    // Test isEmpty
    boolean isEmpty = dirEntry.isEmpty();
    assertFalse(isEmpty);
    
    // Test removing a child
    dirEntry.removeChild("/test/child1");
    children = dirEntry.getChildren();
    assertEquals(1, children.size());
    assertEquals("/test/child2", children.get(0));
    
    // Remove all children
    dirEntry.removeChild("/test/child2");
    isEmpty = dirEntry.isEmpty();
    assertTrue(isEmpty);
    
    // Test setPath
    dirEntry.setPath("/newPath");
    // No direct access to path field, but we can test functionality
    // by adding a child with the new path and verifying it works
    dirEntry.addChild("/newPath/child");
    children = dirEntry.getChildren();
    assertEquals(1, children.size());
    assertEquals("/newPath/child", children.get(0));
  }
  
  /**
   * Tests the FileEntry class using reflection to access private methods.
   */
  @Test
  void testFileEntry() throws Exception {
    // Get the FileEntry class
    Class<?> fileEntryClass = Class.forName(
        "io.github.panghy.javaflow.io.SimulatedFlowFileSystem$FileEntry");
    
    // Create a file entry
    Object fileEntry = fileEntryClass.getDeclaredConstructor(String.class)
        .newInstance("/test/file.txt");
    
    // Test getting data store
    Method getDataStoreMethod = fileEntryClass.getDeclaredMethod("getDataStore");
    FileDataStore dataStore = (FileDataStore) getDataStoreMethod.invoke(fileEntry);
    assertFalse(dataStore.isClosed());
    
    // Test setting path
    Method setPathMethod = fileEntryClass.getDeclaredMethod("setPath", String.class);
    setPathMethod.invoke(fileEntry, "/test/newfile.txt");
    
    // Verify path was updated
    Field pathField = fileEntryClass.getDeclaredField("path");
    pathField.setAccessible(true);
    assertEquals("/test/newfile.txt", pathField.get(fileEntry));
    
    // Verify data store was reopened
    assertFalse(dataStore.isClosed());
  }
  
  /**
   * Tests the behavior of the directories map in SimulatedFlowFileSystem.
   */
  @Test
  void testDirectoriesMap() throws Exception {
    // Create a directory structure
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directories
      Flow.await(fileSystem.createDirectory(Paths.get("/dir1")));
      Flow.await(fileSystem.createDirectory(Paths.get("/dir1/subdir")));
      
      // Access the directories map using reflection
      Field directoriesField = SimulatedFlowFileSystem.class.getDeclaredField("directories");
      directoriesField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Object> directories = (Map<String, Object>) directoriesField.get(fileSystem);
      
      // Verify entries exist
      boolean rootExists = directories.containsKey("/");
      boolean dir1Exists = directories.containsKey("/dir1");
      boolean subdirExists = directories.containsKey("/dir1/subdir");
      
      // Test directory entries
      Class<?> directoryEntryClass = Class.forName(
          "io.github.panghy.javaflow.io.SimulatedFlowFileSystem$DirectoryEntry");
      Object dir1Entry = directories.get("/dir1");
      Method getChildrenMethod = directoryEntryClass.getDeclaredMethod("getChildren");
      @SuppressWarnings("unchecked")
      List<String> children = (List<String>) getChildrenMethod.invoke(dir1Entry);
      
      // Verify children
      boolean hasSubdir = children.contains("/dir1/subdir");
      
      return rootExists && dir1Exists && subdirExists && hasSubdir;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Tests the normalize method using the public API.
   */
  @Test
  void testNormalize() throws Exception {
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directories with different path formats
      Path path1 = Paths.get("/dir1");
      Path path2 = Paths.get("dir2");     // No leading slash
      Path path3 = Paths.get("/dir3/");   // Trailing slash
      
      // Create all directories
      Flow.await(fileSystem.createDirectory(path1));
      Flow.await(fileSystem.createDirectory(path2));
      Flow.await(fileSystem.createDirectory(path3));
      
      // Check they all exist
      boolean path1Exists = Flow.await(fileSystem.exists(Paths.get("/dir1")));
      boolean path2Exists = Flow.await(fileSystem.exists(Paths.get("/dir2")));  // Should normalize
      boolean path3Exists = Flow.await(fileSystem.exists(Paths.get("/dir3")));  // Should normalize
      
      // Verify they're also visible when listed from root
      List<Path> rootEntries = Flow.await(fileSystem.list(Paths.get("/")));
      boolean path1Listed = rootEntries.contains(Paths.get("/dir1"));
      boolean path2Listed = rootEntries.contains(Paths.get("/dir2"));
      boolean path3Listed = rootEntries.contains(Paths.get("/dir3"));
      
      return path1Exists && path2Exists && path3Exists && 
             path1Listed && path2Listed && path3Listed;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Tests the parent path calculation logic.
   */
  @Test
  void testParentPathLogic() throws Exception {
    // Test adding a file to a directory
    CompletableFuture<Boolean> future = Flow.startActor(() -> {
      // Create directory
      Flow.await(fileSystem.createDirectory(Paths.get("/parent")));
      
      // Create file in directory
      Path filePath = Paths.get("/parent/file.txt");
      Flow.await(fileSystem.open(filePath, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Create nested structure
      Path deepPath = Paths.get("/a/b/c/d/e/f");
      Flow.await(fileSystem.createDirectories(deepPath));
      
      // Create file at root level
      Path rootFile = Paths.get("/rootFile.txt");
      Flow.await(fileSystem.open(rootFile, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Check all files exist
      boolean parentExists = Flow.await(fileSystem.exists(Paths.get("/parent")));
      boolean fileExists = Flow.await(fileSystem.exists(filePath));
      boolean deepExists = Flow.await(fileSystem.exists(deepPath));
      boolean rootFileExists = Flow.await(fileSystem.exists(rootFile));
      
      // Verify parent relationships
      List<Path> rootEntries = Flow.await(fileSystem.list(Paths.get("/")));
      List<Path> parentEntries = Flow.await(fileSystem.list(Paths.get("/parent")));
      
      boolean rootHasParent = rootEntries.contains(Paths.get("/parent"));
      boolean rootHasFile = rootEntries.contains(rootFile);
      boolean parentHasFile = parentEntries.contains(filePath);
      
      return parentExists && fileExists && deepExists && rootFileExists &&
             rootHasParent && rootHasFile && parentHasFile;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    assertTrue(future.getNow(null));
  }
  
  /**
   * Tests the behavior when trying to create a directory with a parent that doesn't exist.
   */
  @Test
  void testCreateDirectoryWithoutParent() throws Exception {
    // Try to create directory without parent
    CompletableFuture<Void> future = Flow.startActor(() -> {
      Path path = Paths.get("/nonexistent/dir");
      Flow.await(fileSystem.createDirectory(path));
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    // The future should complete exceptionally
    assertTrue(future.isCompletedExceptionally());
    
    // Verify the exception type
    try {
      future.getNow(null);
      fail("Expected CompletionException");
    } catch (CompletionException e) {
      assertTrue(e.getCause() instanceof java.io.IOException,
          "Expected IOException as cause, but got " + e.getCause().getClass());
    }
  }
  
  /**
   * Tests that a file and directory cannot have the same name.
   */
  @Test
  void testFileDirectoryNameCollision() throws Exception {
    // Create a file and then try to create a directory with the same name
    CompletableFuture<Void> future = Flow.startActor(() -> {
      Path path = Paths.get("/collision");
      
      // Create file first
      Flow.await(fileSystem.open(path, OpenOptions.CREATE, OpenOptions.WRITE)).close();
      
      // Try to create directory with the same name - should fail
      Flow.await(fileSystem.createDirectory(path));
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(future);
    
    // The future should complete exceptionally
    assertTrue(future.isCompletedExceptionally());
    
    // Verify the exception type
    try {
      future.getNow(null);
      fail("Expected CompletionException");
    } catch (CompletionException e) {
      Throwable cause = e.getCause();
      assertTrue(cause instanceof FileAlreadyExistsException ||
                 cause instanceof java.io.IOException,
          "Expected FileAlreadyExistsException or IOException, but got " + cause.getClass());
    }
  }
}