package io.github.panghy.javaflow.core;

import io.github.panghy.javaflow.Flow;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.panghy.javaflow.Flow.startActor;

/**
 * A promise-based implementation of a stream in the JavaFlow actor system.
 * This class allows values to be sent to a stream and consumed asynchronously.
 *
 * <p>The PromiseStream acts as the producer side of a stream, allowing values to be
 * sent into the stream using {@link #send(Object)}. Consumers can access these values
 * through the {@link FutureStream} interface returned by {@link #getFutureStream()}.</p>
 *
 * @param <T> The type of value flowing through this stream
 */
public class PromiseStream<T> {

  private final Queue<T> buffer = new ConcurrentLinkedQueue<>();
  private final Queue<CompletableFuture<T>> nextFutures = new ConcurrentLinkedQueue<>();
  private final Queue<CompletableFuture<Boolean>> hasNextFutures = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicReference<Throwable> closeException = new AtomicReference<>();
  private final FutureStream<T> futureStream;
  private final Object lock = new Object(); // Lock for synchronizing buffer and promise operations
  private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

  /**
   * Creates a new PromiseStream.
   */
  public PromiseStream() {
    this.futureStream = new FutureStreamImpl<>(this);
  }

  /**
   * Gets the FutureStream interface for consuming values from this stream.
   *
   * @return The FutureStream for this stream
   */
  public FutureStream<T> getFutureStream() {
    return futureStream;
  }

  /**
   * Sends a value to this stream, making it available to consumers.
   * If there are pending next promises, the first one will be completed with this value.
   * Otherwise, the value is stored in the buffer for future consumption.
   *
   * @param value The value to send
   * @return true if the stream accepted the value, false if the stream is closed
   */
  public boolean send(T value) {
    if (closed.get()) {
      return false;
    }

    synchronized (lock) {
      CompletableFuture<T> future = nextFutures.poll();
      if (future != null) {
        // There's a waiting consumer, deliver directly
        future.complete(value);
      } else {
        // No waiting consumer, buffer the value
        buffer.add(value);
      }

      // Complete any pending hasNext futures with true
      CompletableFuture<Boolean> hasNextFuture;
      while ((hasNextFuture = hasNextFutures.poll()) != null) {
        hasNextFuture.complete(true);
      }
    }

    return true;
  }

  /**
   * Closes this stream, preventing any new values from being sent.
   * Pending hasNext promises will be completed with false, and pending next promises
   * will be completed exceptionally with a StreamClosedException.
   *
   * @return true if this call closed the stream, false if it was already closed
   */
  public boolean close() {
    return closeExceptionally(new StreamClosedException());
  }

  /**
   * Closes this stream with an exception, preventing any new values from being sent.
   * Pending hasNext promises will be completed with false, and pending next promises
   * will be completed exceptionally with the given exception.
   *
   * @param exception The exception to complete pending promises with
   * @return true if this call closed the stream, false if it was already closed
   */
  public boolean closeExceptionally(Throwable exception) {
    if (!closed.compareAndSet(false, true)) {
      return false;
    }

    closeException.set(exception);

    synchronized (lock) {
      // Complete all pending next futures with the exception
      CompletableFuture<T> nextFuture;
      while ((nextFuture = nextFutures.poll()) != null) {
        nextFuture.completeExceptionally(exception);
      }

      // Complete all pending hasNext futures with false
      CompletableFuture<Boolean> hasNextFuture;
      while ((hasNextFuture = hasNextFutures.poll()) != null) {
        hasNextFuture.complete(false);
      }
    }

    // Complete the close future
    if (exception instanceof StreamClosedException) {
      closeFuture.complete(null);
    } else {
      closeFuture.completeExceptionally(exception);
    }

    return true;
  }

  /**
   * Checks if this stream is closed.
   *
   * @return true if closed, false otherwise
   */
  public boolean isClosed() {
    return closed.get();
  }

  /**
   * Gets the exception that caused this stream to close, if any.
   *
   * @return the exception that closed this stream, or null if closed normally
   */
  public Throwable getCloseException() {
    return closeException.get();
  }

  /**
   * Checks if there are any pending next futures waiting for data.
   * This is used internally to determine if more data should be read.
   *
   * @return true if there are no pending next futures, false otherwise
   */
  public boolean nextFuturesEmpty() {
    return nextFutures.isEmpty();
  }

  /**
   * Implementation of the FutureStream interface for this PromiseStream.
   *
   * @param <E> The type of value provided by the stream
   */
  private static class FutureStreamImpl<E> implements FutureStream<E> {
    private final PromiseStream<E> parent;

    FutureStreamImpl(PromiseStream<E> parent) {
      this.parent = parent;
    }

    @Override
    public CompletableFuture<E> nextAsync() {
      synchronized (parent.lock) {
        if (parent.closed.get() && parent.buffer.isEmpty()) {
          // Stream is closed and empty, return a failed future
          Throwable exception = parent.closeException.get();
          if (exception == null) {
            exception = new StreamClosedException();
          }
          return CompletableFuture.failedFuture(exception);
        }

        // Check for buffered value
        E value = parent.buffer.poll();
        if (value != null) {
          return CompletableFuture.completedFuture(value);
        }

        // Check if closed after checking buffer
        if (parent.closed.get()) {
          Throwable exception = parent.closeException.get();
          if (exception == null) {
            exception = new StreamClosedException();
          }
          return CompletableFuture.failedFuture(exception);
        }

        // No buffered value and not closed, create a future and register it
        CompletableFuture<E> future = new CompletableFuture<>();
        parent.nextFutures.add(future);
        return future;
      }
    }

    @Override
    public CompletableFuture<Boolean> hasNextAsync() {
      synchronized (parent.lock) {
        if (!parent.buffer.isEmpty()) {
          // There is a buffered value
          return CompletableFuture.completedFuture(true);
        }

        if (parent.closed.get()) {
          // Stream is closed and empty - check if it was closed exceptionally
          Throwable closeException = parent.getCloseException();
          if (closeException != null && !(closeException instanceof StreamClosedException)) {
            // Stream was closed exceptionally, return a failed future
            return CompletableFuture.failedFuture(closeException);
          }
          // Stream was closed normally
          return CompletableFuture.completedFuture(false);
        }

        // No buffered value and not closed, create a future and register it
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        parent.hasNextFutures.add(future);
        return future;
      }
    }

    @Override
    public CompletableFuture<Void> closeExceptionally(Throwable exception) {
      parent.closeExceptionally(exception);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> close() {
      parent.close();
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isClosed() {
      return parent.isClosed();
    }

    @Override
    public CompletableFuture<Void> onClose() {
      return parent.closeFuture;
    }

    @Override
    public <R> FlowStream<R> map(Function<? super E, ? extends R> mapper) {
      PromiseStream<R> result = new PromiseStream<>();

      // Start an actor that pulls from this stream and pushes to the result stream
      startActor(() -> {
        try {
          while (true) {
            // Check if we should stop
            if (result.isClosed()) {
              return null;
            }

            // Check if there's more to process
            boolean hasNext = Flow.await(hasNextAsync());
            if (!hasNext) {
              result.close();
              return null;
            }

            // Get the next value and map it
            E value = Flow.await(nextAsync());
            R mappedValue = mapper.apply(value);
            result.send(mappedValue);
          }
        } catch (Exception e) {
          // Propagate exception to result stream
          result.closeExceptionally(e);
          return null;
        }
      });

      return result.getFutureStream();
    }

    @Override
    public FlowStream<E> filter(Predicate<? super E> predicate) {
      PromiseStream<E> result = new PromiseStream<>();

      // Start an actor that pulls from this stream, filters, and pushes to the result stream
      startActor(() -> {
        try {
          while (true) {
            // Check if we should stop
            if (result.isClosed()) {
              return null;
            }

            // Check if there's more to process
            boolean hasNext = Flow.await(hasNextAsync());
            if (!hasNext) {
              result.close();
              return null;
            }

            // Get the next value and filter it
            E value = Flow.await(nextAsync());
            if (predicate.test(value)) {
              result.send(value);
            }
          }
        } catch (Exception e) {
          // Propagate exception to result stream
          result.closeExceptionally(e);
          return null;
        }
      });

      return result.getFutureStream();
    }

    @Override
    public CompletableFuture<Void> forEach(Consumer<? super E> action) {
      CompletableFuture<Void> result = new CompletableFuture<>();

      // If the stream is already closed with a real exception (not StreamClosedException), fail immediately
      if (parent.closed.get() && parent.closeException.get() != null 
          && !(parent.closeException.get() instanceof StreamClosedException)) {
        Throwable exception = parent.closeException.get();
        result.completeExceptionally(exception);
        return result;
      }

      // Process any values already in the buffer first
      E bufferedValue;
      while (!parent.buffer.isEmpty() && (bufferedValue = parent.buffer.poll()) != null) {
        try {
          action.accept(bufferedValue);
        } catch (Exception e) {
          result.completeExceptionally(e);
          return result;
        }
      }

      // If the stream is already closed and the buffer is empty, complete
      if (parent.closed.get() && parent.buffer.isEmpty()) {
        result.complete(null);
        return result;
      }

      // Start an actor that processes all remaining elements in the stream
      startActor(() -> {
        try {
          while (true) {
            CompletableFuture<Boolean> hasNextFuture = hasNextAsync();
            boolean hasNext = Flow.await(hasNextFuture);
            if (!hasNext) {
              result.complete(null);
              return null;
            }

            CompletableFuture<E> nextFuture = nextAsync();
            E value = Flow.await(nextFuture);
            action.accept(value);

            // Add a yield to ensure the test can pump and see progress
            Flow.await(Flow.yield());
          }
        } catch (Exception e) {
          result.completeExceptionally(e);
          return null;
        }
      });

      return result;
    }
  }
}