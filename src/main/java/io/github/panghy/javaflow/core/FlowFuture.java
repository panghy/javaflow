package io.github.panghy.javaflow.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A future representing an asynchronous value in the JavaFlow actor system.
 * Similar to {@link CompletableFuture} but with specific integration with
 * the Flow runtime for cooperative scheduling.
 *
 * @param <T> The type of value this future holds
 */
public class FlowFuture<T> implements Future<T> {

  private final CompletableFuture<T> delegate = new CompletableFuture<>();
  private final FlowPromise<T> promise;

  /**
   * Creates a new FlowFuture with its corresponding FlowPromise.
   */
  public FlowFuture() {
    this.promise = new FlowPromise<>(this);
  }

  /**
   * Creates a new FlowFuture that's already completed with the given value.
   *
   * @param value The value to complete the future with
   * @param <U> The type of the value
   * @return A completed FlowFuture
   */
  public static <U> FlowFuture<U> completed(U value) {
    FlowFuture<U> future = new FlowFuture<>();
    future.promise.complete(value);
    return future;
  }

  /**
   * Creates a new FlowFuture that's already completed exceptionally.
   *
   * @param exception The exception to complete the future with
   * @param <U> The type of the future
   * @return A failed FlowFuture
   */
  public static <U> FlowFuture<U> failed(Throwable exception) {
    FlowFuture<U> future = new FlowFuture<>();
    future.promise.completeExceptionally(exception);
    return future;
  }

  /**
   * Returns the promise associated with this future.
   *
   * @return The promise that can complete this future
   */
  public FlowPromise<T> getPromise() {
    return promise;
  }

  /**
   * Checks if this future is completed (either successfully or with an exception).
   *
   * @return true if completed, false otherwise
   */
  public boolean isCompleted() {
    return delegate.isDone();
  }

  /**
   * Maps the value of this future to another value once it completes.
   *
   * @param mapper The function to apply to the result
   * @param <R> The type of the resulting future
   * @return A new future that will complete with the mapped value
   */
  public <R> FlowFuture<R> map(Function<? super T, ? extends R> mapper) {
    FlowFuture<R> result = new FlowFuture<>();
    delegate.thenApply(mapper).whenComplete((value, exception) -> {
      if (exception != null) {
        result.promise.completeExceptionally(exception);
      } else {
        result.promise.complete(value);
      }
    });
    return result;
  }

  /**
   * Transforms the value of this future using a function that returns another future.
   *
   * @param mapper A function that takes a T and returns a FlowFuture<R>
   * @param <R> The type of the resulting future
   * @return A new future that will complete with the result of the mapped future
   */
  public <R> FlowFuture<R> flatMap(Function<? super T, ? extends FlowFuture<R>> mapper) {
    FlowFuture<R> result = new FlowFuture<>();
    delegate.thenCompose(value -> {
      try {
        FlowFuture<R> mapped = mapper.apply(value);
        mapped.delegate.whenComplete((mappedValue, mappedException) -> {
          if (mappedException != null) {
            result.promise.completeExceptionally(mappedException);
          } else {
            result.promise.complete(mappedValue);
          }
        });
        return mapped.delegate;
      } catch (Throwable ex) {
        result.promise.completeExceptionally(ex);
        return CompletableFuture.failedFuture(ex);
      }
    }).exceptionally(ex -> {
      result.promise.completeExceptionally(ex);
      return null;
    });
    return result;
  }

  // Future implementation methods

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = delegate.cancel(mayInterruptIfRunning);
    if (result) {
      promise.completeExceptionally(new CancellationException("Future was cancelled"));
    }
    return result;
  }

  @Override
  public boolean isCancelled() {
    return delegate.isCancelled();
  }

  @Override
  public boolean isDone() {
    return delegate.isDone();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    return delegate.get();
  }

  @Override
  public T get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.get(timeout, unit);
  }
}