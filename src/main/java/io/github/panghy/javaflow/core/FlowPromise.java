package io.github.panghy.javaflow.core;

/**
 * The completion handle for a {@link FlowFuture}.
 * This class is responsible for setting the value or exception that completes a future.
 *
 * @param <T> The type of value this promise can deliver
 */
public class FlowPromise<T> {

  private final FlowFuture<T> future;

  /**
   * Creates a new promise linked to the given future.
   *
   * @param future The future to complete through this promise
   */
  FlowPromise(FlowFuture<T> future) {
    this.future = future;
  }

  /**
   * Completes the associated future with a value.
   *
   * @param value The value to complete with
   * @return true if this completion changed the future's state, false otherwise
   */
  public boolean complete(T value) {
    return future.complete(value);
  }

  /**
   * Completes the associated future with an exception.
   *
   * @param exception The exception to complete with
   * @return true if this completion changed the future's state, false otherwise
   */
  public boolean completeExceptionally(Throwable exception) {
    return future.completeExceptionally(exception);
  }

  /**
   * Returns the associated future.
   *
   * @return The future this promise completes
   */
  public FlowFuture<T> getFuture() {
    return future;
  }

  /**
   * Checks if the associated future is already completed.
   *
   * @return true if completed, false otherwise
   */
  public boolean isCompleted() {
    return future.isCompleted();
  }
}