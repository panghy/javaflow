package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A real implementation of FlowFile that uses Java NIO's AsynchronousFileChannel.
 * This implementation performs actual I/O operations on the filesystem.
 */
public class RealFlowFile implements FlowFile {

  private final AsynchronousFileChannel channel;
  private final Path path;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * Creates a new RealFlowFile backed by the given AsynchronousFileChannel.
   *
   * @param channel The channel to use for file operations
   * @param path    The path of the file
   */
  public RealFlowFile(AsynchronousFileChannel channel, Path path) {
    this.channel = channel;
    this.path = path;
  }

  /**
   * Opens a file at the given path with the specified options.
   *
   * @param path    The path of the file to open
   * @param options The options to open the file with
   * @return A future that completes with a RealFlowFile
   */
  public static FlowFuture<FlowFile> open(Path path, OpenOptions... options) {
    FlowFuture<FlowFile> result = new FlowFuture<>();
    FlowPromise<FlowFile> promise = result.getPromise();

    try {
      // Convert to StandardOpenOption
      Set<StandardOpenOption> standardOptions = OpenOptions.toStandardOptions(options);

      // Open the channel - convert Set to array for varargs
      StandardOpenOption[] optionsArray = standardOptions.toArray(new StandardOpenOption[0]);
      AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, optionsArray);

      // Create the file
      RealFlowFile file = new RealFlowFile(channel, path);

      // Complete the promise
      promise.complete(file);
    } catch (IOException e) {
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<ByteBuffer> read(long position, int length) {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("File is closed"));
    }

    FlowFuture<ByteBuffer> result = new FlowFuture<>();
    FlowPromise<ByteBuffer> promise = result.getPromise();

    // Allocate a buffer for the read
    ByteBuffer buffer = ByteBuffer.allocate(length);

    // Perform the read
    channel.read(buffer, position, buffer, new CompletionHandler<>() {
      @Override
      public void completed(Integer bytesRead, ByteBuffer attachment) {
        if (bytesRead < 0) {
          // End of file
          attachment.flip();
          promise.complete(attachment);
        } else if (bytesRead < length) {
          // Partial read, adjust the buffer size
          attachment.flip();
          ByteBuffer result = ByteBuffer.allocate(bytesRead);
          result.put(attachment);
          result.flip();
          promise.complete(result);
        } else {
          // Full read
          attachment.flip();
          promise.complete(attachment);
        }
      }

      @Override
      public void failed(Throwable exc, ByteBuffer attachment) {
        promise.completeExceptionally(exc);
      }
    });

    return result;
  }

  @Override
  public FlowFuture<Void> write(long position, ByteBuffer data) {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("File is closed"));
    }

    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    // Get a duplicate of the buffer to avoid position changes affecting the caller
    ByteBuffer bufferToWrite = data.duplicate();

    // Perform the write
    channel.write(bufferToWrite, position, null, new CompletionHandler<Integer, Void>() {
      @Override
      public void completed(Integer bytesWritten, Void attachment) {
        if (bufferToWrite.hasRemaining()) {
          // Not all bytes were written, continue writing
          channel.write(bufferToWrite, position + bytesWritten, null, this);
        } else {
          // All bytes written
          promise.complete(null);
        }
      }

      @Override
      public void failed(Throwable exc, Void attachment) {
        promise.completeExceptionally(exc);
      }
    });

    return result;
  }

  @Override
  public FlowFuture<Void> sync() {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("File is closed"));
    }

    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    try {
      // Force all updates to be written to the file
      channel.force(true);

      // Complete the promise
      promise.complete(null);
    } catch (Exception e) {
      // Complete the promise exceptionally
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<Void> truncate(long size) {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("File is closed"));
    }

    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    try {
      // Truncate the file
      channel.truncate(size);

      // Complete the promise
      promise.complete(null);
    } catch (Exception e) {
      // Complete the promise exceptionally
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<Void> close() {
    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    // Only close once
    if (closed.compareAndSet(false, true)) {
      try {
        // Close the channel
        channel.close();

        // Complete the promise
        promise.complete(null);
      } catch (Exception e) {
        // Complete the promise exceptionally
        promise.completeExceptionally(e);
      }
    } else {
      // Already closed
      promise.complete(null);
    }

    return result;
  }

  @Override
  public FlowFuture<Long> size() {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("File is closed"));
    }

    FlowFuture<Long> result = new FlowFuture<>();
    FlowPromise<Long> promise = result.getPromise();

    try {
      // Get the file size
      long size = channel.size();

      // Complete the promise
      promise.complete(size);
    } catch (Exception e) {
      // Complete the promise exceptionally
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public Path getPath() {
    return path;
  }
}