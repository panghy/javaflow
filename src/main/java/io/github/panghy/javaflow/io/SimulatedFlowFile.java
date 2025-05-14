package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.core.FlowFuture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static io.github.panghy.javaflow.core.FlowFuture.COMPLETED_VOID_FUTURE;
import static io.github.panghy.javaflow.core.FlowFuture.failed;
import static io.github.panghy.javaflow.io.FlowFutureUtil.delay;
import static io.github.panghy.javaflow.io.FlowFutureUtil.delayThenApply;

/**
 * A simulated implementation of FlowFile for testing purposes.
 * This implementation stores data in memory and simulates file operations
 * with configurable delays and error conditions.
 */
public class SimulatedFlowFile implements FlowFile {

  private final Path path;
  private final SimulationParameters params;
  private final FileDataStore dataStore;
  private final String id;
  private volatile boolean closed = false;

  /**
   * Creates a new simulated file with its own data store.
   * This constructor is used for standalone files that aren't part of a file system.
   *
   * @param path   The path of the simulated file
   * @param params Simulation parameters to control behavior
   */
  public SimulatedFlowFile(Path path, SimulationParameters params) {
    this.path = path;
    this.params = params;
    this.dataStore = new FileDataStore();
    this.id = path.toString() + "-" + System.nanoTime();
    System.out.println("Created SimulatedFlowFile with id " + id + " at path " + path);
  }

  /**
   * Creates a new simulated file that uses a shared data store.
   * This constructor is used by SimulatedFlowFileSystem to create files that share data.
   *
   * @param path      The path of the simulated file
   * @param params    Simulation parameters to control behavior
   * @param dataStore The shared data store for this file
   */
  public SimulatedFlowFile(Path path, SimulationParameters params, FileDataStore dataStore) {
    this.path = path;
    this.params = params;
    this.dataStore = dataStore;
    this.id = path.toString() + "-" + System.nanoTime();
    System.out.println("Created SimulatedFlowFile with id " + id + " at path " + path);
  }

  @Override
  public FlowFuture<ByteBuffer> read(long position, int length) {
    // Check if file is closed
    if (closed) {
      return failed(new IOException("File is closed"));
    }

    // Check if position is valid
    if (position < 0) {
      return failed(new IllegalArgumentException("Position must be non-negative"));
    }

    // Check if length is valid
    if (length <= 0) {
      return failed(new IllegalArgumentException("Length must be positive"));
    }

    // Check for injected errors
    if (params.getReadErrorProbability() > 0.0 &&
        Math.random() < params.getReadErrorProbability()) {
      return failed(new IOException("Simulated read error"));
    }

    // Calculate a realistic delay based on the simulation parameters
    double delay = params.calculateReadDelay(length);

    // Perform the read after the delay
    return delayThenApply(COMPLETED_VOID_FUTURE, delay, v -> dataStore.read(position, length));
  }

  @Override
  public FlowFuture<Void> write(long position, ByteBuffer data) {
    // Check if file is closed
    if (closed) {
      return failed(new IOException("File is closed"));
    }

    // Check if position is valid
    if (position < 0) {
      return failed(new IllegalArgumentException("Position must be non-negative"));
    }

    // Check for injected errors
    if (params.getWriteErrorProbability() > 0.0 &&
        Math.random() < params.getWriteErrorProbability()) {
      return failed(new IOException("Simulated write error"));
    }

    // Calculate a realistic delay based on the simulation parameters
    double delay = params.calculateWriteDelay(data.remaining());

    // Perform the write after the delay
    return delayThenApply(COMPLETED_VOID_FUTURE, delay, v -> {
      dataStore.write(position, data);
      return null;
    });
  }

  @Override
  public FlowFuture<Void> sync() {
    // Check if file is closed
    if (closed) {
      return failed(new IOException("File is closed"));
    }

    // For simulation, sync is just a delay
    return delayThenApply(COMPLETED_VOID_FUTURE, params.getMetadataDelay(), v -> null);
  }

  @Override
  public FlowFuture<Void> truncate(long size) {
    // Check if file is closed
    if (closed) {
      return failed(new IOException("File is closed"));
    }

    // Check if size is valid
    if (size < 0) {
      return failed(new IllegalArgumentException("Size must be non-negative"));
    }

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        Math.random() < params.getMetadataErrorProbability()) {
      return failed(new IOException("Simulated truncate error"));
    }

    // Simulate delay
    return delayThenApply(COMPLETED_VOID_FUTURE, params.getMetadataDelay(), v -> {
      dataStore.truncate(size);
      return null;
    });
  }

  @Override
  public FlowFuture<Void> close() {
    System.out.println("Closing SimulatedFlowFile " + id + " at path " + path);

    // If already closed, still return success (idempotent)
    if (closed) {
      return FlowFuture.completed(null);
    }

    // Mark the file as closed
    closed = true;

    // Simulate delay for close operation
    return delay(COMPLETED_VOID_FUTURE, params.getMetadataDelay());
  }

  @Override
  public FlowFuture<Long> size() {
    // Check if file is closed
    if (closed) {
      return failed(new IOException("File is closed"));
    }

    // Simulate delay
    return delayThenApply(
        COMPLETED_VOID_FUTURE,
        params.getMetadataDelay(),
        v -> {
          // Check if file was closed during the delay
          if (closed) {
            throw new IOException("File is closed");
          }
          return dataStore.getFileSize();
        });
  }

  @Override
  public Path getPath() {
    return path;
  }

  /**
   * Checks if this file is closed.
   *
   * @return true if the file is closed, false otherwise
   */
  public boolean isClosed() {
    return closed;
  }

  /**
   * Gets the file size.
   * This method is for internal use and testing.
   *
   * @return The current file size
   */
  long getFileSize() {
    return dataStore.getFileSize();
  }

  /**
   * Gets the data store used by this file.
   * This method is for internal use and testing.
   *
   * @return The file's data store
   */
  FileDataStore getDataStore() {
    return dataStore;
  }
}