package io.github.panghy.javaflow.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * A future representing an asynchronous value in the JavaFlow actor system.
 * Similar to {@link CompletableFuture} but with specific integration with
 * the Flow runtime for cooperative scheduling.
 *
 * <p>Unlike standard futures, this class does not block when asking for results
 * but instead cooperatively yields to the flow scheduler when called from within
 * a flow task. When called from outside a flow task, get() will block and attempt
 * to retrieve the result.</p>
 *
 * @param <T> The type of value this future holds
 */
public class FlowFuture<T> {

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
   * @param <U>   The type of the value
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
   * @param <U>       The type of the future
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
   * Checks if this future is completed exceptionally.
   *
   * @return true if completed exceptionally, false otherwise
   */
  public boolean isCompletedExceptionally() {
    return delegate.isCompletedExceptionally();
  }

  /**
   * Returns the exception that caused this future to complete exceptionally.
   *
   * @return The exception, or throw IllegalStateException if not completed exceptionally
   */
  public Throwable getException() {
    return delegate.exceptionNow();
  }

  /**
   * Maps the value of this future to another value once it completes.
   *
   * @param mapper The function to apply to the result
   * @param <R>    The type of the resulting future
   * @return A new future that will complete with the mapped value
   */
  public <R> FlowFuture<R> map(Function<? super T, ? extends R> mapper) {
    FlowFuture<R> result = new FlowFuture<>();

    delegate.whenComplete((value, exception) -> {
      if (exception != null) {
        // Propagate exception to the result
        result.promise.completeExceptionally(exception);
      } else if (isCancelled()) {
        // Propagate cancellation from parent to child
        result.cancel(true);
      } else {
        // Map the value
        try {
          R mappedValue = mapper.apply(value);
          result.promise.complete(mappedValue);
        } catch (Throwable ex) {
          result.promise.completeExceptionally(ex);
        }
      }
    });

    return result;
  }

  /**
   * Transforms the value of this future using a function that returns another future.
   *
   * @param mapper A function that takes a T and returns a FlowFuture<R>
   * @param <R>    The type of the resulting future
   * @return A new future that will complete with the result of the mapped future
   */
  public <R> FlowFuture<R> flatMap(Function<? super T, ? extends FlowFuture<R>> mapper) {
    FlowFuture<R> result = new FlowFuture<>();

    delegate.thenCompose(value -> {
      try {
        if (isCancelled()) {
          // If the parent is cancelled, propagate to result
          result.cancel(true);
          return CompletableFuture.failedFuture(
              new CancellationException("Parent future was cancelled"));
        }

        FlowFuture<R> mapped = mapper.apply(value);

        // Link mapped future and result for value/exception propagation
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

  /**
   * Attempts to cancel execution of this task.
   * If the future is already completed, this method has no effect.
   * If the future is not completed, it will be completed exceptionally with a
   * CancellationException.
   * If an actor is awaiting this future, the awaiting actor's task will also be cancelled.
   *
   * @param mayInterruptIfRunning true if the thread executing this task should be interrupted
   * @return true if the task was cancelled
   */
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = delegate.cancel(mayInterruptIfRunning);
    if (result) {
      CancellationException ce = new CancellationException("Future was cancelled");
      promise.completeExceptionally(ce);
    }
    return result;
  }

  /**
   * Returns true if this task was cancelled before it completed normally.
   *
   * @return true if this task was cancelled
   */
  public boolean isCancelled() {
    return delegate.isCancelled();
  }

  /**
   * Returns true if this task completed.
   *
   * @return true if this task completed
   */
  public boolean isDone() {
    return delegate.isDone();
  }

  /**
   * Returns the value of this future if it has already completed,
   * or throws an exception if it completed exceptionally.
   *
   * @return the value
   * @throws ExecutionException    if the future completed exceptionally
   * @throws IllegalStateException if the future is not done
   */
  public T getNow() throws ExecutionException {
    if (isDone()) {
      try {
        return delegate.getNow(null);
      } catch (CompletionException e) {
        throw new ExecutionException(e.getCause());
      }
    }
    throw new IllegalStateException("Future is not done");
  }

  /**
   * Converts this FlowFuture to a CompletableFuture.
   *
   * @return The CompletableFuture representation of this FlowFuture
   */
  public CompletableFuture<T> toCompletableFuture() {
    return delegate;
  }
}