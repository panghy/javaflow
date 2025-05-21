package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.PromiseStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *   <li>Continuous reading from the socket to feed a stream of data packets</li>
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
 *   <li>The connection maintains its own continuous read loop to populate the receive stream</li>
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
  private final FlowPromise<Void> closePromise;
  private final PromiseStream<ByteBuffer> receivePromiseStream = new PromiseStream<>();

  // For holding partial buffers when a receive() call doesn't consume an entire buffer
  private ByteBuffer pendingReadBuffer = null;

  // Lock for synchronizing access to the pendingReadBuffer
  private final Object pendingBufferLock = new Object();

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

    FlowFuture<Void> closeFuture = new FlowFuture<>();
    this.closePromise = closeFuture.getPromise();

    // Start reading from the channel continuously
    startReading();
  }

  @Override
  public FlowFuture<Void> send(ByteBuffer data) {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("Connection is closed"));
    }

    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    // Get a duplicate of the buffer to avoid position changes affecting the caller
    ByteBuffer bufferToWrite = data.duplicate();

    // Perform the write
    channel.write(bufferToWrite, null, new CompletionHandler<Integer, Void>() {
      @Override
      public void completed(Integer bytesWritten, Void attachment) {
        if (bufferToWrite.hasRemaining()) {
          // Not all bytes were written, continue writing
          channel.write(bufferToWrite, null, this);
        } else {
          // All bytes written
          promise.complete(null);
        }
      }

      @Override
      public void failed(Throwable exc, Void attachment) {
        promise.completeExceptionally(exc);
        close();
      }
    });

    return result;
  }

  @Override
  public FlowFuture<ByteBuffer> receive(int maxBytes) {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("Connection is closed"));
    }

    // Make sure maxBytes is a positive value
    if (maxBytes <= 0) {
      return FlowFuture.failed(new IllegalArgumentException("maxBytes must be positive"));
    }

    // Create the result future
    FlowFuture<ByteBuffer> resultFuture = new FlowFuture<>();
    FlowPromise<ByteBuffer> resultPromise = resultFuture.getPromise();

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

          try {
            // Set temporary limit to only read maxBytes
            pendingReadBuffer.limit(pendingReadBuffer.position() + maxBytes);
            slice.put(pendingReadBuffer);
            slice.flip();

            // Restore the original limit
            pendingReadBuffer.limit(originalLimit);

            // Use this slice as our result
            result = slice;
          } catch (Exception e) {
            pendingReadBuffer.limit(originalLimit);
            resultPromise.completeExceptionally(e);
            return resultFuture;
          }
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
        }
      }
    }

    // If we have pending data, complete with the result
    if (hasPendingData) {
      resultPromise.complete(result);
      return resultFuture;
    }

    // No pending buffer, get from the stream
    FlowFuture<ByteBuffer> nextBufferFuture = receivePromiseStream.getFutureStream().nextAsync();
    nextBufferFuture.whenComplete((buffer, ex) -> {
      if (ex != null) {
        resultPromise.completeExceptionally(ex);
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

          resultPromise.complete(limitedBuffer);
        } catch (Exception e) {
          // Restore the buffer's limit if there was an exception
          buffer.limit(originalLimit);
          resultPromise.completeExceptionally(e);
        }
      } else {
        // If buffer is smaller than or equal to maxBytes, return it as is
        resultPromise.complete(buffer);
      }
    });

    return resultFuture;
  }

  @Override
  public FlowStream<ByteBuffer> receiveStream() {
    return receivePromiseStream.getFutureStream();
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
  public FlowFuture<Void> closeFuture() {
    FlowFuture<Void> result = new FlowFuture<>();
    closePromise.getFuture().whenComplete((v, ex) -> {
      if (ex != null) {
        result.getPromise().completeExceptionally(ex);
      } else {
        result.getPromise().complete(null);
      }
    });
    return result;
  }

  @Override
  public FlowFuture<Void> close() {
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

    return closePromise.getFuture();
  }

  // Default size for read buffers (4KB is a reasonable size for many network operations)
  private static final int DEFAULT_READ_BUFFER_SIZE = 4096;

  /**
   * Starts the continuous reading process from the channel.
   * This method sets up an asynchronous read loop that feeds data into the receive stream.
   */
  private void startReading() {
    if (closed.get()) {
      return;
    }

    // Create a ReadHandler that properly handles each read operation
    continueReading(ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE));
  }

  /**
   * Continues the reading process with a fresh buffer.
   * This method ensures each read operation gets its own buffer and handler.
   *
   * @param buffer The buffer to read into
   */
  private void continueReading(ByteBuffer buffer) {
    if (closed.get()) {
      return;
    }

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

          // Continue reading with a fresh buffer
          continueReading(ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE));
        } else if (bytesRead < 0) {
          // End of stream reached, close the connection
          close();
        } else {
          // Zero bytes read, try again with same buffer
          readBuffer.clear();
          continueReading(readBuffer);
        }
      }

      @Override
      public void failed(Throwable exc, ByteBuffer readBuffer) {
        close();
      }
    });
  }
}