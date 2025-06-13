package io.github.panghy.javaflow.rpc.util;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.error.RpcTimeoutException;

import java.util.logging.Logger;

/**
 * Utility class for handling RPC timeouts.
 * This class provides methods for creating futures that time out after a specified period.
 */
public class RpcTimeoutUtil {
  
  private static final Logger logger = Logger.getLogger(RpcTimeoutUtil.class.getName());

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
   * exception if the timeout is exceeded
   */
  public static <T> FlowFuture<T> withTimeout(FlowFuture<T> future, EndpointId endpointId,
                                              String methodName, long timeoutMs) {
    // Create a result future
    FlowFuture<T> resultFuture = new FlowFuture<>();

    // Set up handlers for the original future
    future.whenComplete((result, error) -> {
      if (error != null) {
        // Only propagate the error if the result future hasn't already been completed
        // (e.g., by the timeout handler)
        if (!resultFuture.isDone()) {
          logger.fine(() -> "Original future completed with error: " + error);
          resultFuture.getPromise().completeExceptionally(error);
        }
      } else {
        if (!resultFuture.isDone()) {
          resultFuture.getPromise().complete(result);
        }
      }
    });
    
    // Start an actor to handle the timeout
    Flow.startActor(() -> {
      // Create a timer future that completes after the timeout
      FlowFuture<Void> timeoutFuture = Flow.delay(timeoutMs / 1000.0);
      
      // Wait for the timeout
      Flow.await(timeoutFuture);
      
      if (!resultFuture.isDone()) {
        // Timeout occurred before the original future completed
        logger.fine(() -> "Timeout occurred for " + endpointId + "." + methodName + " after " + timeoutMs + "ms");
        RpcTimeoutException timeoutEx = new RpcTimeoutException(endpointId, methodName, timeoutMs);
        resultFuture.getPromise().completeExceptionally(timeoutEx);
        logger.fine(() -> "Completed result future with timeout exception: " + timeoutEx);
        future.cancel();
      }
      return null;
    });

    return resultFuture;
  }
}