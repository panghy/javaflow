package io.github.panghy.javaflow.rpc.util;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.core.StreamClosedException;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.error.RpcTimeoutException;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.Flow.startActor;

/**
 * Utility class for handling RPC stream timeouts.
 * This class provides methods for creating streams that time out after a period of inactivity.
 */
public class RpcStreamTimeoutUtil {

  private static final Logger logger = Logger.getLogger(RpcStreamTimeoutUtil.class.getName());

  /**
   * Wraps a stream with an inactivity timeout.
   * If no data is received within the specified time, the stream will be closed with a timeout exception.
   *
   * @param <T>                 The type of elements in the stream
   * @param stream              The stream to wrap
   * @param endpointId          The endpoint ID for error reporting
   * @param methodName          The method name for error reporting
   * @param inactivityTimeoutMs The inactivity timeout in milliseconds
   * @return A new stream that will timeout on inactivity
   */
  public static <T> PromiseStream<T> withInactivityTimeout(PromiseStream<T> stream,
                                                           EndpointId endpointId,
                                                           String methodName,
                                                           long inactivityTimeoutMs) {
    // Create a new stream to return
    PromiseStream<T> timeoutStream = new PromiseStream<>();

    // Track the current timeout future
    AtomicReference<FlowFuture<Void>> currentTimeout = new AtomicReference<>();

    // Function to reset the timeout
    Runnable resetTimeout = () -> {
      // Cancel the previous timeout if any
      FlowFuture<Void> previous = currentTimeout.get();
      if (previous != null && !previous.isDone()) {
        previous.cancel();
      }

      // Start a new timeout
      FlowFuture<Void> newTimeout = Flow.delay(inactivityTimeoutMs / 1000.0);
      currentTimeout.set(newTimeout);

      // When timeout expires, close the stream with timeout exception
      newTimeout.whenComplete((v, error) -> {
        if (error == null && currentTimeout.compareAndSet(newTimeout, null)) {
          // Timeout occurred
          logger.fine(() -> "Stream inactivity timeout for " + endpointId + "." + methodName
                            + " after " + inactivityTimeoutMs + "ms");

          String message = String.format("Stream %s.%s timed out after %dms of inactivity",
              endpointId != null ? endpointId : "unknown", methodName, inactivityTimeoutMs);
          RpcTimeoutException timeoutEx = new RpcTimeoutException(
              RpcTimeoutException.TimeoutType.STREAM_INACTIVITY,
              inactivityTimeoutMs,
              message);

          logger.fine(() -> "Closing timeout stream with exception: " + timeoutEx);
          boolean closed = timeoutStream.closeExceptionally(timeoutEx);
          logger.fine(() -> "timeoutStream.closeExceptionally returned: " + closed);
        }
      });
    };

    // Forward values from original stream to timeout stream
    startActor(() -> {
      try {
        // Start the initial timeout
        resetTimeout.run();

        // Use a loop instead of forEach to have better control over timeout handling
        while (true) {
          // Check if the timeout stream is already closed due to timeout
          if (timeoutStream.isClosed()) {
            // If the timeout stream was closed, we should stop processing and check if it's due to timeout
            Throwable closeException = timeoutStream.getCloseException();
            logger.fine(() -> "Timeout stream is closed, stopping forwarding. Exception: " + closeException);
            break;
          }

          // Check if there's a next value
          FlowFuture<Boolean> hasNextFuture = stream.getFutureStream().hasNextAsync();
          boolean hasNext;
          try {
            hasNext = Flow.await(hasNextFuture);
          } catch (Exception e) {
            // If hasNext fails and timeout stream is closed with timeout exception, propagate the timeout exception
            if (timeoutStream.isClosed()) {
              Throwable timeoutException = timeoutStream.getCloseException();
              if (timeoutException != null && !(timeoutException instanceof StreamClosedException)) {
                logger.fine(() -> "hasNext failed but timeout occurred, propagating timeout exception: " +
                                  timeoutException);
                rethrow(timeoutException);
              }
            }
            // Otherwise, propagate the original exception
            throw e;
          }

          if (!hasNext) {
            // Check if timeout stream was closed while we were checking hasNext
            if (timeoutStream.isClosed()) {
              Throwable timeoutException = timeoutStream.getCloseException();
              if (timeoutException != null && !(timeoutException instanceof StreamClosedException)) {
                logger.fine(() -> "Original stream ended but timeout occurred, propagating timeout exception: " +
                                  timeoutException);
                rethrow(timeoutException);
              }
            }

            // Original stream ended normally
            logger.fine(() -> "Original stream ended normally");
            if (!timeoutStream.isClosed()) {
              timeoutStream.close();
            }
            break;
          }

          // Check again if timeout stream is closed (timeout might have occurred during hasNext)
          if (timeoutStream.isClosed()) {
            Throwable timeoutException = timeoutStream.getCloseException();
            if (timeoutException != null && !(timeoutException instanceof StreamClosedException)) {
              logger.fine(() -> "Timeout occurred during hasNext, propagating timeout exception: " + timeoutException);
              rethrow(timeoutException);
            }
            logger.fine(() -> "Timeout stream closed during hasNext, stopping");
            break;
          }

          // Get the next value
          FlowFuture<T> nextFuture = stream.getFutureStream().nextAsync();
          T value = Flow.await(nextFuture);

          // Reset timeout on each value received
          resetTimeout.run();

          // Forward the value to timeout stream if it's still open
          if (!timeoutStream.isClosed()) {
            timeoutStream.send(value);
          }
        }
      } catch (Exception e) {
        // The original stream failed or was closed exceptionally
        logger.fine(() -> "Original stream processing failed with exception: " + e);
        if (!timeoutStream.isClosed()) {
          timeoutStream.closeExceptionally(e);
        } else {
          // If timeout stream is already closed, check if it's due to timeout
          Throwable timeoutException = timeoutStream.getCloseException();
          if (timeoutException != null && !(timeoutException instanceof StreamClosedException)) {
            // Re-throw the timeout exception instead of the connection exception
            logger.fine(() -> "Rethrowing timeout exception instead of: " + e);
            rethrow(timeoutException);
          }
        }
      } finally {
        // Cancel any pending timeout
        FlowFuture<Void> timeout = currentTimeout.getAndSet(null);
        if (timeout != null) {
          timeout.cancel();
        }
      }

      return null;
    });

    return timeoutStream;
  }

  /**
   * Rethrows the given exception, wrapping it in a RuntimeException if necessary.
   *
   * @param timeoutException The exception to rethrow.
   */
  private static void rethrow(Throwable timeoutException) {
    if (timeoutException instanceof RuntimeException) {
      throw (RuntimeException) timeoutException;
    } else {
      throw new RuntimeException(timeoutException);
    }
  }

  /**
   * Wraps a future stream with an inactivity timeout.
   * If no data is received within the specified time, the stream will be closed with a timeout exception.
   *
   * @param <T>                 The type of elements in the stream
   * @param futureStream        The future stream to wrap
   * @param endpointId          The endpoint ID for error reporting
   * @param methodName          The method name for error reporting
   * @param inactivityTimeoutMs The inactivity timeout in milliseconds
   * @return A new future stream that will timeout on inactivity
   */
  public static <T> FutureStream<T> withInactivityTimeout(FutureStream<T> futureStream,
                                                          EndpointId endpointId,
                                                          String methodName,
                                                          long inactivityTimeoutMs) {
    // Create a promise stream with timeout
    PromiseStream<T> promiseStream = new PromiseStream<>();
    PromiseStream<T> timeoutStream = withInactivityTimeout(promiseStream, endpointId, methodName, inactivityTimeoutMs);

    // Forward values from future stream to promise stream
    startActor(() -> {
      futureStream.forEach(promiseStream::send);

      // Forward close event
      futureStream.onClose().whenComplete((v, error) -> {
        if (error != null) {
          promiseStream.closeExceptionally(error);
        } else {
          promiseStream.close();
        }
      });

      return null;
    });

    return timeoutStream.getFutureStream();
  }
}