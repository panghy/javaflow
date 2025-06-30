package io.github.panghy.javaflow.io;
import java.util.concurrent.CompletableFuture;

import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.panghy.javaflow.io.FlowFutureUtil.delayThenRun;

/**
 * A simulated implementation of FlowFileSystem for testing purposes.
 * This implementation maintains an in-memory representation of files and directories
 * with configurable simulation parameters.
 */
public class SimulatedFlowFileSystem implements FlowFileSystem {

  private final SimulationParameters params;

  // Map of file entries by path
  private final Map<String, FileEntry> files = new ConcurrentHashMap<>();

  // Map of directory entries by path
  private final Map<String, DirectoryEntry> directories = new ConcurrentHashMap<>();

  /**
   * Creates a new simulated file system with the specified parameters.
   *
   * @param params Simulation parameters to control behavior
   */
  public SimulatedFlowFileSystem(SimulationParameters params) {
    this.params = params;

    // Initialize with root directory
    directories.put("/", new DirectoryEntry("/"));
  }

  @Override
  public CompletableFuture<FlowFile> open(Path path, OpenOptions... options) {
    // Check if the path is null
    if (path == null) {
      return CompletableFuture.failedFuture(new NullPointerException("Path cannot be null"));
    }

    // Convert to canonical string representation
    final String pathStr = normalize(path);

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated open error"));
    }

    // Check if options include required ones
    final boolean read = hasOption(options, OpenOptions.READ);
    final boolean write = hasOption(options, OpenOptions.WRITE);
    final boolean create = hasOption(options, OpenOptions.CREATE);
    final boolean createNew = hasOption(options, OpenOptions.CREATE_NEW);
    final boolean truncate = hasOption(options, OpenOptions.TRUNCATE_EXISTING);

    // Simulate delay
    return delayThenRun(
        params.getMetadataDelay(),
        () -> {
          // Check if the directory exists
          String parentPath = getParentPath(pathStr);
          if (!directories.containsKey(parentPath)) {
            throw new IOException("Parent directory does not exist: " + parentPath);
          }

          // Check if file exists
          boolean fileExists = files.containsKey(pathStr);

          // Handle CREATE_NEW option
          if (createNew && fileExists) {
            throw new FileAlreadyExistsException(pathStr);
          }

          // Handle READ option when file doesn't exist
          if (read && !write && !create && !fileExists) {
            throw new NoSuchFileException(pathStr);
          }

          // Create the file entry if needed
          if (!fileExists) {
            if (!create && !createNew) {
              throw new NoSuchFileException(pathStr);
            }

            // Create the file entry
            files.put(pathStr, new FileEntry(pathStr));

            // Add to parent directory's children
            DirectoryEntry parent = directories.get(parentPath);
            if (parent != null) {
              parent.addChild(pathStr);
            }
          }

          // Get the file entry
          FileEntry entry = files.get(pathStr);

          // Handle TRUNCATE_EXISTING option
          if (truncate) {
            entry.getDataStore().clear();
          }

          // Create and return the file with the shared data store
          return new SimulatedFlowFile(path, params, entry.getDataStore());
        });
  }

  @Override
  public CompletableFuture<Void> delete(Path path) {
    // Check if the path is null
    if (path == null) {
      return CompletableFuture.failedFuture(new NullPointerException("Path cannot be null"));
    }

    // Convert to canonical string representation
    final String pathStr = normalize(path);

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated delete error"));
    }

    // Simulate delay
    return delayThenRun(
        params.getMetadataDelay(),
        () -> {
          // Check if the path exists
          boolean isFile = files.containsKey(pathStr);
          boolean isDirectory = directories.containsKey(pathStr);

          if (!isFile && !isDirectory) {
            throw new NoSuchFileException(pathStr);
          }

          // If it's a directory, check if it's empty
          if (isDirectory) {
            DirectoryEntry dir = directories.get(pathStr);
            if (!dir.isEmpty()) {
              throw new IOException("Directory not empty: " + pathStr);
            }

            // Remove from parent directory's children
            String parentPath = getParentPath(pathStr);
            if (!parentPath.equals(pathStr)) { // Skip for root
              DirectoryEntry parent = directories.get(parentPath);
              if (parent != null) {
                parent.removeChild(pathStr);
              }
            }

            // Remove the directory
            directories.remove(pathStr);
          } else {
            // It's a file, remove it
            files.remove(pathStr);

            // Remove from parent directory's children
            String parentPath = getParentPath(pathStr);
            DirectoryEntry parent = directories.get(parentPath);
            if (parent != null) {
              parent.removeChild(pathStr);
            }
          }

          return null;
        });
  }

  @Override
  public CompletableFuture<Boolean> exists(Path path) {
    // Check if the path is null
    if (path == null) {
      return CompletableFuture.failedFuture(new NullPointerException("Path cannot be null"));
    }

    // Convert to canonical string representation
    final String pathStr = normalize(path);

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated exists error"));
    }

    // Simulate delay
    return delayThenRun(
        params.getMetadataDelay(),
        () -> files.containsKey(pathStr) || directories.containsKey(pathStr));
  }

  @Override
  public CompletableFuture<Void> createDirectory(Path path) {
    // Check if the path is null
    if (path == null) {
      return CompletableFuture.failedFuture(new NullPointerException("Path cannot be null"));
    }

    // Convert to canonical string representation
    final String pathStr = normalize(path);

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated createDirectory error"));
    }

    // Simulate delay
    return delayThenRun(
        params.getMetadataDelay(),
        () -> {
          // Check if the directory already exists
          if (directories.containsKey(pathStr)) {
            throw new FileAlreadyExistsException(pathStr);
          }

          // Check if a file exists with this name
          if (files.containsKey(pathStr)) {
            throw new FileAlreadyExistsException(pathStr);
          }

          // Check if the parent directory exists
          String parentPath = getParentPath(pathStr);
          if (!directories.containsKey(parentPath)) {
            throw new IOException("Parent directory does not exist: " + parentPath);
          }

          // Create the directory
          DirectoryEntry newDir = new DirectoryEntry(pathStr);
          directories.put(pathStr, newDir);

          // Add to parent directory's children
          DirectoryEntry parent = directories.get(parentPath);
          parent.addChild(pathStr);

          return null;
        });
  }

  @Override
  public CompletableFuture<Void> createDirectories(Path path) {
    // Check if the path is null
    if (path == null) {
      return CompletableFuture.failedFuture(new NullPointerException("Path cannot be null"));
    }

    // Convert to canonical string representation
    final String pathStr = normalize(path);

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated createDirectories error"));
    }

    // If the directory already exists, nothing to do
    if (directories.containsKey(pathStr)) {
      return CompletableFuture.completedFuture(null);
    }

    // If a file exists with this name, throw an exception
    if (files.containsKey(pathStr)) {
      return CompletableFuture.failedFuture(new FileAlreadyExistsException(pathStr));
    }

    // Get parent path
    String parentPath = getParentPath(pathStr);

    // Create parent directories if needed, then create this directory
    if (!parentPath.equals(pathStr) && !directories.containsKey(parentPath)) {
      // First create parent directories, then create this directory
      return createDirectories(Paths.get(parentPath))
          .thenApply(v -> {
            // Create the directory
            DirectoryEntry newDir = new DirectoryEntry(pathStr);
            directories.put(pathStr, newDir);

            // Add to parent directory's children
            DirectoryEntry parent = directories.get(parentPath);
            if (parent != null) {
              parent.addChild(pathStr);
            }

            return null;
          });
    } else {
      // Parent exists, so just create this directory with a delay
      return delayThenRun(
          params.getMetadataDelay(),
          () -> {
            // Create the directory
            DirectoryEntry newDir = new DirectoryEntry(pathStr);
            directories.put(pathStr, newDir);

            // Add to parent directory's children
            DirectoryEntry parent = directories.get(parentPath);
            if (parent != null) {
              parent.addChild(pathStr);
            }

            return null;
          });
    }
  }

  @Override
  public CompletableFuture<List<Path>> list(Path directory) {
    // Check if the path is null
    if (directory == null) {
      return CompletableFuture.failedFuture(new NullPointerException("Path cannot be null"));
    }

    // Convert to canonical string representation
    final String pathStr = normalize(directory);

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated list error"));
    }

    // Simulate delay
    return delayThenRun(
        params.getMetadataDelay(),
        () -> {
          // Check if the directory exists
          if (!directories.containsKey(pathStr)) {
            throw new NoSuchFileException(pathStr);
          }

          // Get the directory entry
          DirectoryEntry dir = directories.get(pathStr);

          // Convert child paths to Path objects
          List<Path> result = new ArrayList<>();
          for (String child : dir.getChildren()) {
            result.add(Paths.get(child));
          }

          return result;
        });
  }

  @Override
  public CompletableFuture<Void> move(Path source, Path target) {
    // Check if the paths are null
    if (source == null || target == null) {
      return CompletableFuture.failedFuture(new NullPointerException("Paths cannot be null"));
    }

    // Convert to canonical string representation
    final String sourceStr = normalize(source);
    final String targetStr = normalize(target);

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated move error"));
    }

    // Simulate delay
    return delayThenRun(
        params.getMetadataDelay(),
        () -> {
          // Check if the source exists
          boolean isFile = files.containsKey(sourceStr);
          boolean isDirectory = directories.containsKey(sourceStr);

          if (!isFile && !isDirectory) {
            throw new NoSuchFileException(sourceStr);
          }

          // Check if the target parent directory exists
          String targetParent = getParentPath(targetStr);
          if (!directories.containsKey(targetParent)) {
            throw new IOException("Target parent directory does not exist: " + targetParent);
          }

          // Check if the target already exists
          if (files.containsKey(targetStr) || directories.containsKey(targetStr)) {
            throw new FileAlreadyExistsException(targetStr);
          }

          if (isFile) {
            // Move file
            FileEntry entry = files.remove(sourceStr);
            files.put(targetStr, entry);
            entry.setPath(targetStr);

            // Reopen the data store to allow access by the target file
            entry.getDataStore().reopen();

            // Update parent directories
            String sourceParent = getParentPath(sourceStr);
            DirectoryEntry sourceDir = directories.get(sourceParent);
            if (sourceDir != null) {
              sourceDir.removeChild(sourceStr);
            }

            DirectoryEntry targetDir = directories.get(targetParent);
            if (targetDir != null) {
              targetDir.addChild(targetStr);
            }
          } else {
            // Move directory
            DirectoryEntry entry = directories.remove(sourceStr);
            directories.put(targetStr, entry);
            entry.setPath(targetStr);

            // Update parent directories
            String sourceParent = getParentPath(sourceStr);
            DirectoryEntry sourceDir = directories.get(sourceParent);
            if (sourceDir != null) {
              sourceDir.removeChild(sourceStr);
            }

            DirectoryEntry targetDir = directories.get(targetParent);
            if (targetDir != null) {
              targetDir.addChild(targetStr);
            }

            // Update paths of all children
            updateChildPaths(entry, sourceStr, targetStr);
          }

          return null;
        });
  }


  /**
   * Checks if the given options array contains the specified option.
   *
   * @param options The options array
   * @param option  The option to check for
   * @return true if the option is present, false otherwise
   */
  private boolean hasOption(OpenOptions[] options, OpenOptions option) {
    for (OpenOptions op : options) {
      if (op == option) {
        return true;
      }
    }
    return false;
  }

  /**
   * Updates the paths of all children of a directory after it has been moved.
   *
   * @param dir       The directory that was moved
   * @param oldPrefix The old path prefix
   * @param newPrefix The new path prefix
   */
  private void updateChildPaths(DirectoryEntry dir, String oldPrefix, String newPrefix) {
    // Get a copy of the children to avoid concurrent modification
    List<String> children = new ArrayList<>(dir.getChildren());

    for (String childPath : children) {
      String newChildPath = childPath.replace(oldPrefix, newPrefix);

      if (files.containsKey(childPath)) {
        // Update file path
        FileEntry file = files.remove(childPath);
        files.put(newChildPath, file);
        file.setPath(newChildPath);

        // Update parent's children
        dir.removeChild(childPath);
        dir.addChild(newChildPath);
      } else if (directories.containsKey(childPath)) {
        // Update directory path
        DirectoryEntry childDir = directories.remove(childPath);
        directories.put(newChildPath, childDir);
        childDir.setPath(newChildPath);

        // Update parent's children
        dir.removeChild(childPath);
        dir.addChild(newChildPath);

        // Recursively update children
        updateChildPaths(childDir, childPath, newChildPath);
      }
    }
  }

  /**
   * Gets the parent path of the specified path.
   *
   * @param path The path
   * @return The parent path
   */
  private String getParentPath(String path) {
    if ("/".equals(path)) {
      return path; // Root has itself as parent
    }

    int lastSlash = path.lastIndexOf('/');
    if (lastSlash <= 0) {
      return "/"; // Parent is root
    }

    return path.substring(0, lastSlash);
  }

  /**
   * Normalizes a path to a canonical string representation.
   *
   * @param path The path to normalize
   * @return The normalized path string
   */
  private String normalize(Path path) {
    String pathStr = path.toString();

    // Ensure path starts with /
    if (!pathStr.startsWith("/")) {
      pathStr = "/" + pathStr;
    }

    // Remove trailing slash (except for root)
    if (pathStr.length() > 1 && pathStr.endsWith("/")) {
      pathStr = pathStr.substring(0, pathStr.length() - 1);
    }

    return pathStr;
  }

  /**
   * Represents a file in the simulated file system.
   */
  private static class FileEntry {
    private String path;
    private final FileDataStore dataStore;

    FileEntry(String path) {
      this.path = path;
      this.dataStore = new FileDataStore();
    }

    void setPath(String path) {
      this.path = path;
      // Reset the closed state of the data store when the path changes
      this.dataStore.reopen();
    }

    FileDataStore getDataStore() {
      return dataStore;
    }
  }

  /**
   * Represents a directory in the simulated file system.
   */
  static class DirectoryEntry {
    private String path;
    private final List<String> children = Collections.synchronizedList(new ArrayList<>());

    DirectoryEntry(String path) {
      this.path = path;
    }

    void setPath(String path) {
      this.path = path;
    }

    void addChild(String childPath) {
      if (!children.contains(childPath)) {
        children.add(childPath);
      }
    }

    void removeChild(String childPath) {
      children.remove(childPath);
    }

    List<String> getChildren() {
      return new ArrayList<>(children);
    }

    boolean isEmpty() {
      return children.isEmpty();
    }
  }
  
  /**
   * Creates a SimulatedFlowFileSystem from the current SimulationConfiguration.
   * This factory method integrates file system simulation with the unified configuration.
   *
   * @return A SimulatedFlowFileSystem configured from the current SimulationConfiguration
   */
  public static SimulatedFlowFileSystem fromSimulationConfig() {
    SimulationConfiguration config = SimulationContext.currentConfiguration();
    SimulationParameters params = SimulationParameters.fromSimulationConfig(config);
    return new SimulatedFlowFileSystem(params);
  }
}