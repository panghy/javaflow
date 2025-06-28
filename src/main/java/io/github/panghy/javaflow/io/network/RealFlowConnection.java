package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.PromiseStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A real implementation of FlowConnection using Java NIO's AsynchronousSocketChannel.
 * This implementation performs actual network I/O operations.
 *
 * <p>RealFlowConnection provides the concrete implementation of FlowConnection for
 * real network operations. It integrates Java NIO's asynchronous I/O with JavaFlow's
 * promise/future system to provide non-blocking network operations that work
 * seamlessly with Flow's cooperative multitasking model.</p>
 *
 * <p>Key features of this implementation include:</p>
 * <ul>
 *   <li>Lazy, pull-based reading from the socket only when data is requested</li>
 *   <li>Automatic handling of partial writes for large messages</li>
 *   <li>Automatic connection closure on I/O errors</li>
 *   <li>Bridge between NIO's CompletionHandler API and Flow's Promise/Future API</li>
 * </ul>
 *
 * <p>This class is typically not instantiated directly by user code.
 * Instead, it is created by {@link RealFlowTransport} when establishing
 * new connections. It is exposed to user code through the {@link FlowConnection}
 * interface.</p>
 *
 * <p>The implementation details include:</p>
 * <ul>
 *   <li>Using a dedicated thread pool for asynchronous I/O operations</li>
 *   <li>Buffer management for efficient memory usage</li>
 *   <li>Error handling and resource cleanup</li>
 * </ul>
 *
 * <p>Usage notes:</p>
 * <ul>
 *   <li>The connection is automatically closed when I/O errors occur</li>
 *   <li>Attempting to use a closed connection will result in IOException</li>
 *   <li>The connection reads from the socket only when data is requested by consumers</li>
 * </ul>
 *
 * @see FlowConnection
 * @see RealFlowTransport
 * @see AsynchronousSocketChannel
 */
public class RealFlowConnection implements FlowConnection {

  private final AsynchronousSocketChannel channel;
  private final Endpoint localEndpoint;
  private final Endpoint remoteEndpoint;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final CompletableFuture<Void> closePromise;
  private final PromiseStream<ByteBuffer> receivePromiseStream = new PromiseStream<>();

  // For holding partial buffers when a receive() call doesn't consume an entire buffer
  private ByteBuffer pendingReadBuffer = null;

  // Lock for synchronizing access to the pendingReadBuffer
  private final Object pendingBufferLock = new Object();

  // Default size for read buffers
  private static final int DEFAULT_READ_BUFFER_SIZE = 4096;

  // Flag to track if a read operation is currently in progress
  private final AtomicBoolean readInProgress = new AtomicBoolean(false);

  // Configurable read buffer size
  private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;

  /**
   * Creates a new RealFlowConnection backed by the given AsynchronousSocketChannel.
   *
   * @param channel        The channel to use for network operations
   * @param localEndpoint  The local endpoint of this connection
   * @param remoteEndpoint The remote endpoint of this connection
   */
  public RealFlowConnection(AsynchronousSocketChannel channel, Endpoint localEndpoint, Endpoint remoteEndpoint) {
    this.channel = channel;
    this.localEndpoint = localEndpoint;
    this.remoteEndpoint = remoteEndpoint;

    this.closePromise = new CompletableFuture<>();

    // No automatic reading - only read when requested
  }

  @Override
  public CompletableFuture<Void> send(ByteBuffer data) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("Connection is closed"));
    }

    CompletableFuture<Void> result = new CompletableFuture<>();

    // Large direct buffer - write in chunks to avoid excessive direct memory usage
    writeInChunks(data, result);

    return result;
  }

  /**
   * Writes a large buffer in chunks to avoid exhausting direct buffer memory.
   */
  private void writeInChunks(ByteBuffer data, CompletableFuture<Void> promise) {
    // Save the original position and limit
    int originalPosition = data.position();
    int originalLimit = data.limit();

    // Create a chunk buffer that we'll reuse
    final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks
    ByteBuffer chunk = ByteBuffer.allocateDirect(Math.min(CHUNK_SIZE, data.remaining()));

    CompletionHandler<Integer, Void> handler = new CompletionHandler<>() {
      @Override
      public void completed(Integer bytesWritten, Void attachment) {
        try {
          if (chunk.hasRemaining()) {
            // Continue writing the current chunk
            channel.write(chunk, null, this);
          } else if (data.hasRemaining()) {
            // Current chunk is done, prepare the next chunk
            chunk.clear();
            int bytesToCopy = Math.min(chunk.capacity(), data.remaining());

            // Save data's limit and temporarily adjust it
            int savedLimit = data.limit();
            data.limit(data.position() + bytesToCopy);

            // Copy data to chunk
            chunk.put(data);
            chunk.flip();

            // Restore data's limit
            data.limit(savedLimit);

            // Write the next chunk
            channel.write(chunk, null, this);
          } else {
            // All data written successfully
            // Restore original position and limit
            data.position(originalPosition);
            data.limit(originalLimit);
            promise.complete(null);
          }
        } catch (Exception e) {
          // Restore original position and limit on error
          data.position(originalPosition);
          data.limit(originalLimit);
          promise.completeExceptionally(e);
          close();
        }
      }

      @Override
      public void failed(Throwable exc, Void attachment) {
        // Restore original position and limit on error
        data.position(originalPosition);
        data.limit(originalLimit);
        promise.completeExceptionally(exc);
        close();
      }
    };

    // Start writing the first chunk
    try {
      int bytesToCopy = Math.min(chunk.capacity(), data.remaining());

      // Save data's limit and temporarily adjust it
      int savedLimit = data.limit();
      data.limit(data.position() + bytesToCopy);

      // Copy data to chunk
      chunk.put(data);
      chunk.flip();

      // Restore data's limit
      data.limit(savedLimit);

      // Start the write
      channel.write(chunk, null, handler);
    } catch (Exception e) {
      // Restore original position and limit on error
      data.position(originalPosition);
      data.limit(originalLimit);
      promise.completeExceptionally(e);
      close();
    }
  }

  @Override
  public CompletableFuture<ByteBuffer> receive(int maxBytes) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("Connection is closed"));
    }

    // Make sure maxBytes is a positive value
    if (maxBytes <= 0) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("maxBytes must be positive"));
    }

    // Create the result future
    CompletableFuture<ByteBuffer> resultFuture = new CompletableFuture<>();

    // First, check if we have a pending buffer from a previous read
    boolean hasPendingData = false;
    ByteBuffer result = null;

    synchronized (pendingBufferLock) {
      if (pendingReadBuffer != null && pendingReadBuffer.hasRemaining()) {
        hasPendingData = true;

        if (pendingReadBuffer.remaining() > maxBytes) {
          // The pending buffer has more data than requested
          // Create a slice with maxBytes
          ByteBuffer slice = ByteBuffer.allocate(maxBytes);

          // Save the original limit
          int originalLimit = pendingReadBuffer.limit();

          // Set temporary limit to only read maxBytes
          pendingReadBuffer.limit(pendingReadBuffer.position() + maxBytes);
          slice.put(pendingReadBuffer);
          slice.flip();

          // Restore the original limit
          pendingReadBuffer.limit(originalLimit);

          // Use this slice as our result
          result = slice;
        } else {
          // The pending buffer is smaller than or equal to maxBytes
          // Use it entirely
          ByteBuffer completeBuffer = ByteBuffer.allocate(pendingReadBuffer.remaining());
          completeBuffer.put(pendingReadBuffer);
          completeBuffer.flip();

          // Clear the pending buffer since we've used it all
          pendingReadBuffer = null;

          // Use the complete buffer as our result
          result = completeBuffer;

          // Since we've consumed all pending data, trigger a new read if not already in progress
          if (readInProgress.compareAndSet(false, true)) {
            performRead();
          }
        }
      }
    }

    // If we have pending data, complete with the result
    if (hasPendingData) {
      resultFuture.complete(result);
      return resultFuture;
    }

    // Wait for data from the stream
    CompletableFuture<ByteBuffer> nextBufferFuture = receivePromiseStream.getFutureStream().nextAsync();
    nextBufferFuture.whenComplete((buffer, ex) -> {
      if (ex != null) {
        resultFuture.completeExceptionally(ex);
        return;
      }

      // Check if the received buffer is larger than the requested maxBytes
      if (buffer.remaining() > maxBytes) {
        // Create a new buffer with exactly maxBytes from the original
        ByteBuffer limitedBuffer = ByteBuffer.allocate(maxBytes);

        // Save original limit for later
        int originalLimit = buffer.limit();

        try {
          // Set a new limit that respects maxBytes
          buffer.limit(buffer.position() + maxBytes);
          limitedBuffer.put(buffer);
          limitedBuffer.flip();

          // Store the remaining data as pending for the next read
          buffer.limit(originalLimit);

          if (buffer.hasRemaining()) {
            synchronized (pendingBufferLock) {
              // Create a copy of the remaining data as our pending buffer
              ByteBuffer remainingBuffer = ByteBuffer.allocate(buffer.remaining());
              remainingBuffer.put(buffer);
              remainingBuffer.flip();
              pendingReadBuffer = remainingBuffer;
            }
          }

          resultFuture.complete(limitedBuffer);
        } catch (Exception e) {
          // Restore the buffer's limit if there was an exception
          buffer.limit(originalLimit);
          resultFuture.completeExceptionally(e);
        }
      } else {
        // If buffer is smaller than or equal to maxBytes, return it as is
        resultFuture.complete(buffer);
      }
    });

    // Try to start a read if not already in progress
    if (readInProgress.compareAndSet(false, true)) {
      try {
        performRead();
      } catch (Exception e) {
        resultFuture.completeExceptionally(e);
        readInProgress.set(false);
        close();
      }
    }

    return resultFuture;
  }

  @Override
  public FlowStream<ByteBuffer> receiveStream() {
    // Return a wrapper that triggers reads when nextAsync is called
    return new FlowStream<>() {
      private final FlowStream<ByteBuffer> delegate = receivePromiseStream.getFutureStream();

      @Override
      public CompletableFuture<ByteBuffer> nextAsync() {
        // Try to start a read if not already in progress
        if (readInProgress.compareAndSet(false, true)) {
          performRead();
        }
        return delegate.nextAsync();
      }

      @Override
      public CompletableFuture<Boolean> hasNextAsync() {
        return delegate.hasNextAsync();
      }

      @Override
      public CompletableFuture<Void> closeExceptionally(Throwable exception) {
        return delegate.closeExceptionally(exception);
      }

      @Override
      public boolean isClosed() {
        return delegate.isClosed();
      }

      @Override
      public <R> FlowStream<R> map(Function<? super ByteBuffer, ? extends R> mapper) {
        return delegate.map(mapper);
      }

      @Override
      public FlowStream<ByteBuffer> filter(Predicate<? super ByteBuffer> predicate) {
        return delegate.filter(predicate);
      }

      @Override
      public CompletableFuture<Void> forEach(Consumer<? super ByteBuffer> action) {
        return delegate.forEach(action);
      }

      @Override
      public CompletableFuture<Void> close() {
        return delegate.close();
      }

      @Override
      public CompletableFuture<Void> onClose() {
        return delegate.onClose();
      }
    };
  }

  @Override
  public Endpoint getLocalEndpoint() {
    return localEndpoint;
  }

  @Override
  public Endpoint getRemoteEndpoint() {
    return remoteEndpoint;
  }

  @Override
  public boolean isOpen() {
    return !closed.get() && channel.isOpen();
  }

  @Override
  public CompletableFuture<Void> closeFuture() {
    return closePromise;
  }

  @Override
  public CompletableFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      try {
        // Close the channel
        channel.close();

        // Close the receive stream
        receivePromiseStream.close();

        // Clear any pending read buffer
        synchronized (pendingBufferLock) {
          pendingReadBuffer = null;
        }

        // Complete the close promise
        closePromise.complete(null);
      } catch (IOException e) {
        closePromise.completeExceptionally(e);
      }
    }

    return closePromise;
  }

  /**
   * Performs a single asynchronous read operation from the channel.
   * This method will be called whenever data is requested and there's no active read operation.
   */
  private void performRead() {
    if (closed.get()) {
      readInProgress.set(false);
      return;
    }

    ByteBuffer buffer = ByteBuffer.allocate(readBufferSize);
    channel.read(buffer, buffer, new CompletionHandler<>() {
      @Override
      public void completed(Integer bytesRead, ByteBuffer readBuffer) {
        if (bytesRead > 0) {
          // Prepare buffer for reading
          readBuffer.flip();

          ByteBuffer result = ByteBuffer.allocate(bytesRead);
          result.put(readBuffer);
          result.flip();

          // Send to the stream
          receivePromiseStream.send(result);

          // Mark that read operation is complete
          readInProgress.set(false);

          // If someone is waiting on the receiveStream or has called receive(), 
          // automatically start another read
          if (!receivePromiseStream.getFutureStream().isClosed() &&
              !receivePromiseStream.nextFuturesEmpty()) {
            if (readInProgress.compareAndSet(false, true)) {
              performRead();
            }
          }
        } else if (bytesRead < 0) {
          // End of stream reached, close the connection
          readInProgress.set(false);
          close();
        } else {
          // Zero bytes read, try again
          readBuffer.clear();
          performRead();
        }
      }

      @Override
      public void failed(Throwable exc, ByteBuffer readBuffer) {
        readInProgress.set(false);
        close();
      }
    });
  }

  /**
   * Sets the read buffer size to use for subsequent read operations.
   * This method can be called to customize the read buffer size.
   *
   * @param readSize The size in bytes to use for read buffers (must be positive)
   * @throws IllegalArgumentException if readSize is not positive
   */
  public void setReadBufferSize(int readSize) {
    if (readSize <= 0) {
      throw new IllegalArgumentException("readSize must be positive");
    }
    this.readBufferSize = readSize;
  }
}