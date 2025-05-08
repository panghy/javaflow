package io.github.panghy.javaflow.core;

import io.github.panghy.javaflow.util.ReflectionUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

/**
 * The completion handle for a {@link FlowFuture}.
 * This class is responsible for setting the value or exception that completes a future.
 *
 * @param <T> The type of value this promise can deliver
 */
public class FlowPromise<T> {

  private final FlowFuture<T> future;
  private final CompletableFuture<T> delegate;

  /**
   * Creates a new promise linked to the given future.
   *
   * @param future The future to complete through this promise
   */
  @SuppressWarnings("unchecked")
  FlowPromise(FlowFuture<T> future) {
    this.future = future;
    this.delegate = future.isDone() ? CompletableFuture.completedFuture(null) :
        (CompletableFuture<T>) ReflectionUtil.getField(future, "delegate");
  }

  /**
   * Completes the associated future with a value.
   *
   * @param value The value to complete with
   * @return true if this completion changed the future's state, false otherwise
   */
  public boolean complete(T value) {
    return delegate.complete(value);
  }

  /**
   * Completes the associated future with an exception.
   *
   * @param exception The exception to complete with
   * @return true if this completion changed the future's state, false otherwise
   */
  public boolean completeExceptionally(Throwable exception) {
    return delegate.completeExceptionally(exception);
  }

  /**
   * Adds a callback to be executed when the future is completed.
   *
   * @param action The action to execute
   * @return The completion stage for chaining
   */
  public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return delegate.whenComplete(action);
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