package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.Flow;
import java.util.concurrent.CompletableFuture;

import java.util.function.Function;

import static io.github.panghy.javaflow.Flow.await;

/**
 * Utility methods for working with CompletableFuture objects, especially in the context
 * of file I/O operations.
 */
public final class FlowFutureUtil {

  private FlowFutureUtil() {
    // Utility class should not be instantiated
  }

  /**
   * Transforms the result of a future using the provided function.
   * This is similar to {@code CompletableFuture.thenApply()}.
   *
   * @param future The source future
   * @param mapper The function to apply to the result
   * @param <T>    The input type
   * @param <R>    The result type
   * @return A new future that will be completed with the transformed result
   */
  public static <T, R> CompletableFuture<R> thenApply(CompletableFuture<T> future, Function<T, R> mapper) {
    return future.thenApply(mapper);
  }

  /**
   * Applies the given function to the result of the future after the specified delay.
   * This is useful for simulating operations that take time.
   *
   * @param future       The source future
   * @param delaySeconds The delay in seconds before applying the function
   * @param mapper       The function to apply to the result
   * @param <T>          The input type
   * @param <R>          The result type
   * @return A new future that will be completed with the transformed result after the delay
   */
  public static <T, R> CompletableFuture<R> delayThenApply(
      CompletableFuture<T> future, double delaySeconds, IOFunction<T, R> mapper) {
    return Flow.startActor(() -> {
      T value = await(future);
      await(Flow.delay(delaySeconds));
      return mapper.apply(value);
    });
  }

  /**
   * Delays the execution of the given supplier by the specified amount.
   * This is useful for simulating operations that take time.
   *
   * @param delaySeconds The delay in seconds
   * @param supplier     The supplier to run after the delay
   * @param <T>          The result type
   * @return A new future that completes with the result of the supplier after the delay
   */
  public static <T> CompletableFuture<T> delayThenRun(double delaySeconds, IOSupplier<T> supplier) {
    return Flow.startActor(() -> {
      await(Flow.delay(delaySeconds));
      return supplier.get();
    });
  }

  /**
   * Delays completion of the given future by the specified amount.
   * This is useful for simulating operations that take time.
   *
   * @param future       The source future
   * @param delaySeconds The delay in seconds
   * @param <T>          The future value type
   * @return A new future that completes delaySeconds after the source future
   */
  public static <T> CompletableFuture<T> delay(CompletableFuture<T> future, double delaySeconds) {
    return delayThenApply(future, delaySeconds, value -> value);
  }
}