package io.github.panghy.javaflow.io;
import java.util.concurrent.CompletableFuture;


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
  public static CompletableFuture<FlowFile> open(Path path, OpenOptions... options) {
    CompletableFuture<FlowFile> result = new CompletableFuture<>();

    try {
      // Convert to StandardOpenOption
      Set<StandardOpenOption> standardOptions = OpenOptions.toStandardOptions(options);

      // Open the channel - convert Set to array for varargs
      StandardOpenOption[] optionsArray = standardOptions.toArray(new StandardOpenOption[0]);
      AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, optionsArray);

      // Create the file
      RealFlowFile file = new RealFlowFile(channel, path);

      // Complete the promise
      result.complete(file);
    } catch (IOException e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<ByteBuffer> read(long position, int length) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    CompletableFuture<ByteBuffer> result = new CompletableFuture<>();

    // Allocate a buffer for the read
    ByteBuffer buffer = ByteBuffer.allocate(length);

    // Perform the read
    channel.read(buffer, position, buffer, new CompletionHandler<>() {
      @Override
      public void completed(Integer bytesRead, ByteBuffer attachment) {
        if (bytesRead < 0) {
          // End of file
          attachment.flip();
          result.complete(attachment);
        } else if (bytesRead < length) {
          // Partial read, adjust the buffer size
          attachment.flip();
          ByteBuffer partialResult = ByteBuffer.allocate(bytesRead);
          partialResult.put(attachment);
          partialResult.flip();
          result.complete(partialResult);
        } else {
          // Full read
          attachment.flip();
          result.complete(attachment);
        }
      }

      @Override
      public void failed(Throwable exc, ByteBuffer attachment) {
        result.completeExceptionally(exc);
      }
    });

    return result;
  }

  @Override
  public CompletableFuture<Void> write(long position, ByteBuffer data) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    CompletableFuture<Void> result = new CompletableFuture<>();

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
          result.complete(null);
        }
      }

      @Override
      public void failed(Throwable exc, Void attachment) {
        result.completeExceptionally(exc);
      }
    });

    return result;
  }

  @Override
  public CompletableFuture<Void> sync() {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    CompletableFuture<Void> result = new CompletableFuture<>();

    try {
      // Force all updates to be written to the file
      channel.force(true);

      // Complete the promise
      result.complete(null);
    } catch (Exception e) {
      // Complete the promise exceptionally
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<Void> truncate(long size) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    CompletableFuture<Void> result = new CompletableFuture<>();

    try {
      // Truncate the file
      channel.truncate(size);

      // Complete the promise
      result.complete(null);
    } catch (Exception e) {
      // Complete the promise exceptionally
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<Void> close() {
    CompletableFuture<Void> result = new CompletableFuture<>();

    // Only close once
    if (closed.compareAndSet(false, true)) {
      try {
        // Close the channel
        channel.close();

        // Complete the promise
        result.complete(null);
      } catch (Exception e) {
        // Complete the promise exceptionally
        result.completeExceptionally(e);
      }
    } else {
      // Already closed
      result.complete(null);
    }

    return result;
  }

  @Override
  public CompletableFuture<Long> size() {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("File is closed"));
    }

    CompletableFuture<Long> result = new CompletableFuture<>();

    try {
      // Get the file size
      long size = channel.size();

      // Complete the promise
      result.complete(size);
    } catch (Exception e) {
      // Complete the promise exceptionally
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public Path getPath() {
    return path;
  }
}