package io.github.panghy.javaflow.core;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A stream of asynchronous values in the JavaFlow actor system.
 * Represents a stream of values that can be consumed using a future-based API.
 *
 * @param <T> The type of value provided by this stream
 */
public interface FlowStream<T> {

  /**
   * Returns a future that completes with the next value in the stream,
   * or completes exceptionally if the stream is closed with an error or
   * there are no more values.
   *
   * @return A future that completes with the next value
   */
  FlowFuture<T> nextAsync();

  /**
   * Returns a future that completes with true if this stream has more values,
   * or false if the stream is closed and has no more values.
   *
   * @return A future that completes with true if more values are available
   */
  FlowFuture<Boolean> hasNextAsync();

  /**
   * Closes this stream, making any pending nextAsync() calls complete exceptionally
   * with the given exception.
   *
   * @param exception The exception to complete pending futures with
   * @return A future that completes when the stream is closed
   */
  FlowFuture<Void> closeExceptionally(Throwable exception);

  /**
   * Closes this stream, making any pending nextAsync() calls complete exceptionally
   * with a default StreamClosedException.
   *
   * @return A future that completes when the stream is closed
   */
  FlowFuture<Void> close();

  /**
   * Returns whether this stream is closed.
   *
   * @return true if the stream is closed, false otherwise
   */
  boolean isClosed();

  /**
   * Returns a future that completes when this stream is closed.
   * This can be used to be notified when the stream is closed.
   *
   * @return A future that completes when the stream is closed
   */
  default FlowFuture<Void> onClose() {
    // Default implementation creates a new future and returns it
    // Implementations should override this to provide proper behavior
    return new FlowFuture<>();
  }

  /**
   * Maps the values in this stream to another type.
   *
   * @param <R> The result type
   * @param mapper A function to transform the values
   * @return A new stream of the mapped values
   */
  <R> FlowStream<R> map(Function<? super T, ? extends R> mapper);

  /**
   * Filters the values in this stream.
   *
   * @param predicate A function that returns true for values to keep
   * @return A new stream containing only values that pass the predicate
   */
  FlowStream<T> filter(Predicate<? super T> predicate);

  /**
   * Executes the given action for each element of the stream, in sequence.
   *
   * @param action The action to execute for each element
   * @return A future that completes when all elements have been processed
   */
  FlowFuture<Void> forEach(Consumer<? super T> action);
}