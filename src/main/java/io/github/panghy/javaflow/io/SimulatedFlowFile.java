package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.simulation.FlowRandom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import static io.github.panghy.javaflow.io.FlowFutureUtil.delayThenRun;

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
  public CompletableFuture<ByteBuffer> read(long position, int length) {
    // Check if file is closed
    if (closed) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    // Check if position is valid
    if (position < 0) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Position must be non-negative"));
    }

    // Check if length is valid
    if (length <= 0) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Length must be positive"));
    }

    // Check for injected errors
    if (params.getReadErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getReadErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated read error"));
    }

    // Calculate a realistic delay based on the simulation parameters
    double delay = params.calculateReadDelay(length);

    // Perform the read after the delay
    return delayThenRun(delay, () -> {
      ByteBuffer result = dataStore.read(position, length);
      
      // Check for data corruption
      if (params.getCorruptionProbability() > 0.0 &&
          FlowRandom.current().nextDouble() < params.getCorruptionProbability()) {
        // Simulate data corruption by flipping random bits
        if (result != null && result.hasRemaining()) {
          int corruptionCount = 1 + FlowRandom.current().nextInt(Math.min(10, result.remaining()));
          for (int i = 0; i < corruptionCount; i++) {
            int pos = result.position() + FlowRandom.current().nextInt(result.remaining());
            byte original = result.get(pos);
            int bitToFlip = FlowRandom.current().nextInt(8);
            byte corrupted = (byte) (original ^ (1 << bitToFlip));
            result.put(pos, corrupted);
          }
        }
      }
      
      return result;
    });
  }

  @Override
  public CompletableFuture<Void> write(long position, ByteBuffer data) {
    // Check if file is closed
    if (closed) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    // Check if position is valid
    if (position < 0) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Position must be non-negative"));
    }

    // Check for injected errors
    if (params.getWriteErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getWriteErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated write error"));
    }
    
    // Check for disk full errors
    if (params.getDiskFullProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getDiskFullProbability()) {
      return CompletableFuture.failedFuture(new IOException("No space left on device"));
    }

    // Calculate a realistic delay based on the simulation parameters
    double delay = params.calculateWriteDelay(data.remaining());

    // Perform the write after the delay
    return delayThenRun(delay, () -> {
      dataStore.write(position, data);
      return null;
    });
  }

  @Override
  public CompletableFuture<Void> sync() {
    // Check if file is closed
    if (closed) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    // For simulation, sync is just a delay
    return delayThenRun(params.getMetadataDelay(), () -> null);
  }

  @Override
  public CompletableFuture<Void> truncate(long size) {
    // Check if file is closed
    if (closed) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    // Check if size is valid
    if (size < 0) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Size must be non-negative"));
    }

    // Check for injected errors
    if (params.getMetadataErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getMetadataErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated truncate error"));
    }

    // Simulate delay
    return delayThenRun(params.getMetadataDelay(), () -> {
      dataStore.truncate(size);
      return null;
    });
  }

  @Override
  public CompletableFuture<Void> close() {
    System.out.println("Closing SimulatedFlowFile " + id + " at path " + path);

    // If already closed, still return success (idempotent)
    if (closed) {
      return CompletableFuture.completedFuture(null);
    }

    // Mark the file as closed
    closed = true;

    // Simulate delay for close operation
    return delayThenRun(params.getMetadataDelay(), () -> null);
  }

  @Override
  public CompletableFuture<Long> size() {
    // Check if file is closed
    if (closed) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    // Simulate delay
    return delayThenRun(
        params.getMetadataDelay(),
        () -> {
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