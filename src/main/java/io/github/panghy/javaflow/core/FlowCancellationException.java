package io.github.panghy.javaflow.core;

/**
 * Exception thrown when a Flow operation is cancelled.
 * This exception should generally not be caught by user code,
 * as it indicates the operation should be aborted immediately.
 */
public class FlowCancellationException extends RuntimeException {
  /**
   * Creates a new cancellation exception with the specified message.
   *
   * @param message The detail message explaining the cancellation
   */
  public FlowCancellationException(String message) {
    super(message);
  }
  
  /**
   * Creates a new cancellation exception with the specified message and cause.
   *
   * @param message The detail message explaining the cancellation
   * @param cause The underlying cause of the cancellation
   */
  public FlowCancellationException(String message, Throwable cause) {
    super(message, cause);
  }
}