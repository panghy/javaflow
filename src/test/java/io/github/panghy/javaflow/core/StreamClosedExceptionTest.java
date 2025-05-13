package io.github.panghy.javaflow.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link StreamClosedException}.
 */
public class StreamClosedExceptionTest {

  @Test
  void testDefaultConstructor() {
    StreamClosedException exception = new StreamClosedException();
    assertEquals("Stream has been closed", exception.getMessage());
    assertNotNull(exception);
  }

  @Test
  void testConstructorWithMessage() {
    String customMessage = "Custom stream closed message";
    StreamClosedException exception = new StreamClosedException(customMessage);
    assertEquals(customMessage, exception.getMessage());
  }

  @Test
  void testConstructorWithCause() {
    RuntimeException cause = new RuntimeException("Original cause");
    StreamClosedException exception = new StreamClosedException(cause);
    assertEquals("Stream has been closed", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void testConstructorWithMessageAndCause() {
    String customMessage = "Custom message with cause";
    RuntimeException cause = new RuntimeException("Original cause");
    StreamClosedException exception = new StreamClosedException(customMessage, cause);
    assertEquals(customMessage, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }
}