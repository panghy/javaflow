package io.github.panghy.javaflow.rpc.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Additional tests for RpcTimeoutException to improve code coverage.
 */
public class RpcTimeoutExceptionCoverageTest {

  @Test
  void testConstructorWithCause() {
    // Test the constructor that takes a cause (currently not covered)
    Throwable cause = new RuntimeException("Underlying cause");
    RpcTimeoutException exception = new RpcTimeoutException(
        RpcTimeoutException.TimeoutType.CONNECTION,
        5000,
        "Connection timed out",
        cause);

    assertThat(exception.getTimeoutType()).isEqualTo(RpcTimeoutException.TimeoutType.CONNECTION);
    assertThat(exception.getTimeoutMs()).isEqualTo(5000);
    assertThat(exception.getMessage()).isEqualTo("Connection timed out");
    assertThat(exception.getCause()).isEqualTo(cause);
    assertThat(exception.getEndpointId()).isNull();
    assertThat(exception.getMethodName()).isNull();
  }

  @Test
  void testToStringWithoutEndpointAndMethod() {
    // Test toString when endpointId and methodName are null
    RpcTimeoutException exception = new RpcTimeoutException(
        RpcTimeoutException.TimeoutType.STREAM_INACTIVITY,
        10000,
        "Stream timed out");

    String str = exception.toString();
    assertThat(str).contains("RpcTimeoutException");
    assertThat(str).contains("type=STREAM_INACTIVITY");
    assertThat(str).contains("timeoutMs=10000");
    assertThat(str).contains("Stream timed out");
    assertThat(str).doesNotContain("endpoint=");
    assertThat(str).doesNotContain("method=");
  }
}