package io.github.panghy.javaflow.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for FlowCancellationException.
 */
class FlowCancellationExceptionTest {

  @Test
  void testExceptionWithMessage() {
    String message = "Operation was cancelled";
    FlowCancellationException exception = new FlowCancellationException(message);
    
    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void testExceptionWithMessageAndCause() {
    String message = "Operation was cancelled due to underlying error";
    Throwable cause = new RuntimeException("Underlying cause");
    FlowCancellationException exception = new FlowCancellationException(message, cause);
    
    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void testExceptionIsRuntimeException() {
    FlowCancellationException exception = new FlowCancellationException("test");
    
    // Verify it extends RuntimeException
    assertTrue(exception instanceof RuntimeException);
    
    // Verify it can be thrown without declaration - expect the exception
    assertThrows(FlowCancellationException.class, () -> {
      throwCancellation();
    });
  }

  @Test
  void testExceptionStackTrace() {
    FlowCancellationException exception = new FlowCancellationException("test");
    
    // Verify stack trace is populated
    assertNotNull(exception.getStackTrace());
    assertTrue(exception.getStackTrace().length > 0);
  }

  @Test
  void testExceptionWithNullMessage() {
    FlowCancellationException exception = new FlowCancellationException(null);
    assertNull(exception.getMessage());
  }

  @Test
  void testExceptionWithNullCause() {
    FlowCancellationException exception = new FlowCancellationException("message", null);
    assertEquals("message", exception.getMessage());
    assertNull(exception.getCause());
  }

  // Helper method to verify runtime exception behavior
  private void throwCancellation() {
    throw new FlowCancellationException("test");
  }
}