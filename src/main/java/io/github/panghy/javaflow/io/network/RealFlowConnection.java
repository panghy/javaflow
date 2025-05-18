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
        // If there's a connection error, close the connection
        if (!closed.get()) {
          close();
        }
      }
    });

    return result;
  }

  @Override
  public FlowFuture<ByteBuffer> receive(int maxBytes) {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("Connection is closed"));
    }

    // We're using the continuous reading approach, so just get from the stream
    return receivePromiseStream.getFutureStream().nextAsync();
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

        // Complete the close promise
        closePromise.complete(null);
      } catch (IOException e) {
        closePromise.completeExceptionally(e);
      }
    }

    return closePromise.getFuture();
  }

  /**
   * Starts the continuous reading process from the channel.
   * This method sets up an asynchronous read loop that feeds data into the receive stream.
   */
  private void startReading() {
    if (closed.get()) {
      return;
    }

    // Create a ReadHandler that properly handles each read operation
    continueReading(ByteBuffer.allocate(8192));
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

          // Create a properly sized result buffer and safely copy the data
          byte[] tmp = new byte[bytesRead];
          // Only read what's available and don't go beyond buffer limit
          for (int i = 0; i < bytesRead && readBuffer.hasRemaining(); i++) {
            tmp[i] = readBuffer.get();
          }

          ByteBuffer result = ByteBuffer.wrap(tmp);

          // Send to the stream
          receivePromiseStream.send(result);

          // Continue reading with a fresh buffer 
          if (!closed.get()) {
            continueReading(ByteBuffer.allocate(8192));
          }
        } else if (bytesRead < 0) {
          // End of stream reached, close the connection
          if (!closed.get()) {
            close();
          }
        } else {
          // Zero bytes read, try again with same buffer
          readBuffer.clear();
          if (!closed.get()) {
            continueReading(readBuffer);
          }
        }
      }

      @Override
      public void failed(Throwable exc, ByteBuffer readBuffer) {
        // Close the connection on error
        if (!closed.get()) {
          close();
        }
      }
    });
  }
}