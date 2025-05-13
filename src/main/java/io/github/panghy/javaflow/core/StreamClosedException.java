package io.github.panghy.javaflow.core;

/**
 * Exception thrown when an operation is attempted on a closed stream.
 */
public class StreamClosedException extends RuntimeException {

  /**
   * Creates a new StreamClosedException with a default message.
   */
  public StreamClosedException() {
    super("Stream has been closed");
  }

  /**
   * Creates a new StreamClosedException with a custom message.
   *
   * @param message The exception message
   */
  public StreamClosedException(String message) {
    super(message);
  }

  /**
   * Creates a new StreamClosedException with a cause.
   *
   * @param cause The underlying cause of this exception
   */
  public StreamClosedException(Throwable cause) {
    super("Stream has been closed", cause);
  }

  /**
   * Creates a new StreamClosedException with a custom message and cause.
   *
   * @param message The exception message
   * @param cause The underlying cause of this exception
   */
  public StreamClosedException(String message, Throwable cause) {
    super(message, cause);
  }
}