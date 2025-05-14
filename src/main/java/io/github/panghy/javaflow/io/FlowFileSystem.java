package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.core.FlowFuture;

import java.nio.file.Path;
import java.util.List;

/**
 * A file system abstraction for JavaFlow that provides asynchronous file operations.
 * All operations return FlowFuture objects that can be awaited within actors.
 * 
 * This interface abstracts file system operations to support both real file I/O
 * and simulated file I/O (for testing).
 */
public interface FlowFileSystem {
  
  /**
   * Opens a file with the specified options.
   *
   * @param path The path of the file to open
   * @param options The options to open the file with
   * @return A future that completes with a FlowFile
   */
  FlowFuture<FlowFile> open(Path path, OpenOptions... options);
  
  /**
   * Deletes a file or directory.
   *
   * @param path The path to delete
   * @return A future that completes when the delete operation is complete
   */
  FlowFuture<Void> delete(Path path);
  
  /**
   * Checks if a file or directory exists.
   *
   * @param path The path to check
   * @return A future that completes with true if the path exists, false otherwise
   */
  FlowFuture<Boolean> exists(Path path);
  
  /**
   * Creates a directory.
   *
   * @param path The directory to create
   * @return A future that completes when the directory is created
   */
  FlowFuture<Void> createDirectory(Path path);
  
  /**
   * Creates a directory and any parent directories that don't exist.
   *
   * @param path The directory path to create
   * @return A future that completes when all directories are created
   */
  FlowFuture<Void> createDirectories(Path path);
  
  /**
   * Lists the contents of a directory.
   *
   * @param directory The directory to list
   * @return A future that completes with a list of paths in the directory
   */
  FlowFuture<List<Path>> list(Path directory);
  
  /**
   * Moves a file from one path to another.
   *
   * @param source The source path
   * @param target The target path
   * @return A future that completes when the move operation is complete
   */
  FlowFuture<Void> move(Path source, Path target);
  
  /**
   * Gets the default instance of FlowFileSystem.
   * In normal mode, this returns a real file system implementation.
   * In simulation mode, this returns a simulated file system.
   *
   * @return The default FlowFileSystem instance
   */
  static FlowFileSystem getDefault() {
    // This will be implemented to return the appropriate file system based on mode
    return FileSystemProvider.getDefaultFileSystem();
  }
}