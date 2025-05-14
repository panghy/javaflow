package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.core.FlowFuture;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Represents an asynchronous file in the JavaFlow actor system.
 * All operations return FlowFuture objects that can be awaited using Flow.await().
 * This allows file I/O to integrate seamlessly with the actor model.
 */
public interface FlowFile {
  
  /**
   * Reads data from the file at the specified position.
   *
   * @param position The position in the file to start reading from
   * @param length The number of bytes to read
   * @return A future that completes with a ByteBuffer containing the read data
   */
  FlowFuture<ByteBuffer> read(long position, int length);
  
  /**
   * Writes data to the file at the specified position.
   *
   * @param position The position in the file to write at
   * @param data The data to write
   * @return A future that completes when the write operation is finished
   */
  FlowFuture<Void> write(long position, ByteBuffer data);
  
  /**
   * Flushes any pending changes to the file to the storage device.
   *
   * @return A future that completes when the sync operation is complete
   */
  FlowFuture<Void> sync();
  
  /**
   * Truncates the file to the specified size.
   *
   * @param size The new size of the file
   * @return A future that completes when the truncate operation is complete
   */
  FlowFuture<Void> truncate(long size);
  
  /**
   * Closes the file, releasing any resources held.
   *
   * @return A future that completes when the file is closed
   */
  FlowFuture<Void> close();
  
  /**
   * Gets the current size of the file.
   *
   * @return A future that completes with the size of the file in bytes
   */
  FlowFuture<Long> size();
  
  /**
   * Gets the path of this file.
   *
   * @return The path of this file
   */
  Path getPath();
}