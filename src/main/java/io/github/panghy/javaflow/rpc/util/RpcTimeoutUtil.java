package io.github.panghy.javaflow.rpc.util;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.error.RpcTimeoutException;

/**
 * Utility class for handling RPC timeouts.
 * This class provides methods for creating futures that time out after a specified period.
 */
public class RpcTimeoutUtil {

  /**
   * Adds a timeout to an RPC future.
   * If the future doesn't complete within the specified time, it will be cancelled
   * and the returned future will complete exceptionally with a {@link RpcTimeoutException}.
   *
   * @param <T>        The type of value in the future
   * @param future     The future to add a timeout to
   * @param endpointId The endpoint being called
   * @param methodName The method being called
   * @param timeoutMs  The timeout in milliseconds
   * @return A new future that will complete like the original future, or throw an
   *         exception if the timeout is exceeded
   */
  public static <T> FlowFuture<T> withTimeout(FlowFuture<T> future, EndpointId endpointId,
                                              String methodName, long timeoutMs) {
    // Create a timer future that completes after the timeout
    FlowFuture<Void> timeoutFuture = Flow.delay(timeoutMs / 1000.0);
    
    // Create a result future
    FlowFuture<T> resultFuture = new FlowFuture<>();
    
    // Set up handlers for the original future
    future.whenComplete((result, error) -> {
      if (error != null) {
        resultFuture.getPromise().completeExceptionally(error);
      } else {
        resultFuture.getPromise().complete(result);
      }
    });
    
    // Set up a handler for the timeout future
    timeoutFuture.whenComplete((v, error) -> {
      if (!resultFuture.isDone()) {
        // Timeout occurred before the original future completed
        future.cancel();
        resultFuture.getPromise().completeExceptionally(
            new RpcTimeoutException(endpointId, methodName, timeoutMs));
      }
    });
    
    return resultFuture;
  }
  
  /**
   * Adds a timeout to an RPC future.
   * If the future doesn't complete within the specified time, it will be cancelled
   * and the returned future will complete exceptionally with a {@link RpcTimeoutException}.
   *
   * @param <T>       The type of value in the future
   * @param future    The future to add a timeout to
   * @param timeoutMs The timeout in milliseconds
   * @return A new future that will complete like the original future, or throw an
   *         exception if the timeout is exceeded
   */
  public static <T> FlowFuture<T> withTimeout(FlowFuture<T> future, long timeoutMs) {
    return withTimeout(future, null, "unknown", timeoutMs);
  }
}