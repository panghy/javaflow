package io.github.panghy.javaflow.rpc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Additional tests for FlowRpcConfiguration to improve code coverage.
 */
public class FlowRpcConfigurationCoverageTest {

  @Test
  void testBuilderNegativeTimeouts() {
    // Test negative unary RPC timeout
    assertThatThrownBy(() ->
        FlowRpcConfiguration.builder()
            .unaryRpcTimeoutMs(-1)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unary RPC timeout must be non-negative, got: -1");

    // Test negative stream inactivity timeout
    assertThatThrownBy(() ->
        FlowRpcConfiguration.builder()
            .streamInactivityTimeoutMs(-1)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Stream inactivity timeout must be non-negative, got: -1");

    // Test negative connection timeout
    assertThatThrownBy(() ->
        FlowRpcConfiguration.builder()
            .connectionTimeoutMs(-1)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Connection timeout must be non-negative, got: -1");
  }

  @Test
  void testBuilderZeroTimeouts() {
    // Test zero timeouts (disabled)
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .unaryRpcTimeoutMs(0)
        .streamInactivityTimeoutMs(0)
        .connectionTimeoutMs(0)
        .build();

    assertThat(config.getUnaryRpcTimeoutMs()).isEqualTo(0);
    assertThat(config.getStreamInactivityTimeoutMs()).isEqualTo(0);
    assertThat(config.getConnectionTimeoutMs()).isEqualTo(0);
  }
}