package io.github.panghy.javaflow.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.CompletableFuture.failedFuture;

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

  public static final FlowFuture<Void> COMPLETED_VOID_FUTURE = completed(null);

  private static final Logger LOGGER = Logger.getLogger(FlowFuture.class.getName());

  private final CompletableFuture<T> delegate = new CompletableFuture<>();
  private final FlowPromise<T> promise;

  /**
   * Creates a new FlowFuture with its corresponding FlowPromise.
   */
  public FlowFuture() {
    this.promise = new FlowPromise<>(this);
  }

  /**
   * Completes the associated future with a value. Called by the promise.
   *
   * @param value The value to complete with
   * @return true if this completion changed the future's state, false otherwise
   */
  public boolean complete(T value) {
    return delegate.complete(value);
  }

  /**
   * Completes the associated future with an exception. Called by the promise.
   *
   * @param exception The exception to complete with
   * @return true if this completion changed the future's state, false otherwise
   */
  boolean completeExceptionally(Throwable exception) {
    return delegate.completeExceptionally(exception);
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
   * Returns a new FlowFuture that completes when all the given futures complete.
   * If any of the futures completes exceptionally, the resulting future also completes
   * exceptionally.
   *
   * @param futures The futures to wait for
   * @param <U>     The type of the futures
   * @return A future that completes when all the given futures complete
   */
  public static <U> FlowFuture<List<U>> allOf(Collection<FlowFuture<U>> futures) {
    if (futures.isEmpty()) {
      return completed(new ArrayList<>());
    }

    FlowFuture<List<U>> result = new FlowFuture<>();
    List<U> results = new ArrayList<>(futures.size());

    // We need to count completions to know when all futures are done
    int[] completionCount = new int[1];

    // Create a handler for each future
    for (FlowFuture<U> future : futures) {
      int index = results.size();
      results.add(null); // Placeholder

      future.delegate.whenComplete((value, exception) -> {
        synchronized (completionCount) {
          if (exception != null) {
            // If not already completed, complete with exception
            if (!result.isDone()) {
              result.promise.completeExceptionally(exception);
            }
          } else if (!result.isDone()) {
            // Store the result
            results.set(index, value);
            completionCount[0]++;

            // If all futures are complete, complete the result
            if (completionCount[0] == futures.size()) {
              result.promise.complete(results);
            }
          }
        }
      });
    }

    return result;
  }

  /**
   * Returns a new FlowFuture that completes when any of the given futures completes.
   * If all futures complete exceptionally, the resulting future also completes exceptionally
   * with the exception from the last future to complete.
   *
   * @param futures The futures to wait for
   * @param <U>     The type of the futures
   * @return A future that completes when any of the given futures completes
   */
  public static <U> FlowFuture<U> anyOf(Collection<FlowFuture<U>> futures) {
    if (futures.isEmpty()) {
      throw new IllegalArgumentException("No futures provided");
    }

    FlowFuture<U> result = new FlowFuture<>();

    // We need to count failures to know if all futures failed
    int[] failureCount = new int[1];
    Object lock = new Object(); // Dedicated lock object

    // Create a handler for each future
    for (FlowFuture<U> future : futures) {
      future.delegate.whenComplete((value, exception) -> {
        synchronized (lock) {
          if (exception != null) {
            failureCount[0]++;

            // If all futures failed, complete with the last exception
            if (failureCount[0] == futures.size()) {
              result.promise.completeExceptionally(exception);
            }
          } else if (!result.isDone()) {
            // Complete with the first successful result
            result.promise.complete(value);
            // cancel all other futures
            for (FlowFuture<U> other : futures) {
              if (other != future) {
                other.cancel();
              }
            }
          }
        }
      });
    }
    return result;
  }

  /**
   * Returns a new FlowFuture that completes when either this future or the other future completes.
   * This is useful for implementing timeout patterns, where you race a task against a timer.
   *
   * @param other The other future to race against
   * @return A future that completes when either this future or the other future completes
   */
  public FlowFuture<Void> or(FlowFuture<?> other) {
    FlowFuture<Void> result = new FlowFuture<>();

    // Handle completion of this future
    this.delegate.whenComplete((value, exception) -> {
      if (!result.isDone()) {
        if (exception != null) {
          result.promise.completeExceptionally(exception);
        } else {
          result.promise.complete(null);
          other.cancel();
        }
      }
    });

    // Handle completion of the other future
    other.delegate.whenComplete((value, exception) -> {
      if (!result.isDone()) {
        if (exception != null) {
          result.promise.completeExceptionally(exception);
        } else {
          result.promise.complete(null);
          this.cancel();
        }
      }
    });

    return result;
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
    if (delegate.isCancelled()) {
      return new CancellationException("future was cancelled");
    }
    return delegate.exceptionNow();
  }

  /**
   * Attaches a callback to be invoked when this future completes.
   *
   * @param action The action to invoke when this future completes
   * @return This future
   */
  public FlowFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    delegate.whenComplete((result, exception) -> {
      try {
        action.accept(result, exception);
      } catch (Exception e) {
        // Log the exception and continue
        LOGGER.log(Level.SEVERE, "Exception in whenComplete callback", e);
      }
    });
    return this;
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
        result.cancel();
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
          result.cancel();
          return failedFuture(new CancellationException("Parent future was cancelled"));
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
        return failedFuture(ex);
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
   * @return true if the task was cancelled
   */
  public boolean cancel() {
    boolean result = delegate.cancel(false);
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
   * or throws an exception if it completed exceptionally. This method is
   * intended for use in flow tasks where the future is known to be ready.
   * Use {@link io.github.panghy.javaflow.Flow#await(FlowFuture)} instead
   * if the future may not be ready.
   *
   * @return the value
   * @throws ExecutionException    if the future completed exceptionally
   * @throws IllegalStateException if the future is not done
   */
  public T getNow() throws ExecutionException {
    try {
      return delegate.get();
    } catch (CompletionException ex) {
      throw new ExecutionException(ex.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for future", e);
    }
  }

  /**
   * Converts this FlowFuture to a CompletableFuture. For unit testing and
   * integration with non-flow code.
   *
   * @return The CompletableFuture representation of this FlowFuture
   */
  public CompletableFuture<T> toCompletableFuture() {
    return delegate;
  }
}