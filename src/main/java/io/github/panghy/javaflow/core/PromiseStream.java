package io.github.panghy.javaflow.core;

import io.github.panghy.javaflow.Flow;

import java.util.Queue;
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
  private final Queue<FlowPromise<T>> nextPromises = new ConcurrentLinkedQueue<>();
  private final Queue<FlowPromise<Boolean>> hasNextPromises = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicReference<Throwable> closeException = new AtomicReference<>();
  private final FutureStream<T> futureStream;
  private final Object lock = new Object(); // Lock for synchronizing buffer and promise operations

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
      FlowPromise<T> promise = nextPromises.poll();
      if (promise != null) {
        // There's a waiting consumer, deliver directly
        promise.complete(value);
      } else {
        // No waiting consumer, buffer the value
        buffer.add(value);
      }

      // Complete any pending hasNext promises with true
      FlowPromise<Boolean> hasNextPromise;
      while ((hasNextPromise = hasNextPromises.poll()) != null) {
        hasNextPromise.complete(true);
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
      // Complete all pending next promises with the exception
      FlowPromise<T> nextPromise;
      while ((nextPromise = nextPromises.poll()) != null) {
        nextPromise.completeExceptionally(exception);
      }

      // Complete all pending hasNext promises with false
      FlowPromise<Boolean> hasNextPromise;
      while ((hasNextPromise = hasNextPromises.poll()) != null) {
        hasNextPromise.complete(false);
      }
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
   * Checks if there are any pending next promises waiting for data.
   * This is used internally to determine if more data should be read.
   *
   * @return true if there are no pending next promises, false otherwise
   */
  public boolean nextPromisesEmpty() {
    return nextPromises.isEmpty();
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
    public FlowFuture<E> nextAsync() {
      synchronized (parent.lock) {
        if (parent.closed.get() && parent.buffer.isEmpty()) {
          // Stream is closed and empty, return a failed future
          Throwable exception = parent.closeException.get();
          if (exception == null) {
            exception = new StreamClosedException();
          }
          return FlowFuture.failed(exception);
        }

        // Check for buffered value
        E value = parent.buffer.poll();
        if (value != null) {
          return FlowFuture.completed(value);
        }

        // Check if closed after checking buffer
        if (parent.closed.get()) {
          Throwable exception = parent.closeException.get();
          if (exception == null) {
            exception = new StreamClosedException();
          }
          return FlowFuture.failed(exception);
        }

        // No buffered value and not closed, create a promise and register it
        FlowFuture<E> future = new FlowFuture<>();
        FlowPromise<E> promise = future.getPromise();
        parent.nextPromises.add(promise);
        return future;
      }
    }

    @Override
    public FlowFuture<Boolean> hasNextAsync() {
      synchronized (parent.lock) {
        if (!parent.buffer.isEmpty()) {
          // There is a buffered value
          return FlowFuture.completed(true);
        }

        if (parent.closed.get()) {
          // Stream is closed and empty
          return FlowFuture.completed(false);
        }

        // No buffered value and not closed, create a promise and register it
        FlowFuture<Boolean> future = new FlowFuture<>();
        FlowPromise<Boolean> promise = future.getPromise();
        parent.hasNextPromises.add(promise);
        return future;
      }
    }

    @Override
    public FlowFuture<Void> closeExceptionally(Throwable exception) {
      parent.closeExceptionally(exception);
      return FlowFuture.completed(null);
    }

    @Override
    public FlowFuture<Void> close() {
      parent.close();
      return FlowFuture.completed(null);
    }

    @Override
    public boolean isClosed() {
      return parent.isClosed();
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
    public FlowFuture<Void> forEach(Consumer<? super E> action) {
      FlowFuture<Void> result = new FlowFuture<>();
      FlowPromise<Void> promise = result.getPromise();

      // If the stream is already closed with an exception, fail immediately
      if (parent.closed.get() && parent.closeException.get() != null) {
        Throwable exception = parent.closeException.get();
        promise.completeExceptionally(exception);
        return result;
      }

      // Process any values already in the buffer first
      E bufferedValue;
      while (!parent.buffer.isEmpty() && (bufferedValue = parent.buffer.poll()) != null) {
        try {
          action.accept(bufferedValue);
        } catch (Exception e) {
          promise.completeExceptionally(e);
          return result;
        }
      }

      // If the stream is already closed and the buffer is empty, complete
      if (parent.closed.get() && parent.buffer.isEmpty()) {
        promise.complete(null);
        return result;
      }

      // Start an actor that processes all remaining elements in the stream
      startActor(() -> {
        try {
          while (true) {
            FlowFuture<Boolean> hasNextFuture = hasNextAsync();
            boolean hasNext = Flow.await(hasNextFuture);
            if (!hasNext) {
              promise.complete(null);
              return null;
            }

            FlowFuture<E> nextFuture = nextAsync();
            E value = Flow.await(nextFuture);
            action.accept(value);

            // Add a yield to ensure the test can pump and see progress
            Flow.await(Flow.yieldF());
          }
        } catch (Exception e) {
          promise.completeExceptionally(e);
          return null;
        }
      });

      return result;
    }
  }
}