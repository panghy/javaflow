package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for FlowRpcConfiguration.
 */
public class FlowRpcConfigurationTest {

  @Test
  public void testDefaultConfiguration() {
    FlowRpcConfiguration config = FlowRpcConfiguration.defaultConfig();
    assertEquals(FlowRpcConfiguration.DEFAULT_RECEIVE_BUFFER_SIZE, config.getReceiveBufferSize());
  }

  @Test
  public void testCustomBufferSize() {
    int customSize = 128 * 1024; // 128KB
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .receiveBufferSize(customSize)
        .build();
    assertEquals(customSize, config.getReceiveBufferSize());
  }

  @Test
  public void testInvalidBufferSize() {
    assertThrows(IllegalArgumentException.class, () ->
        FlowRpcConfiguration.builder().receiveBufferSize(0).build());

    assertThrows(IllegalArgumentException.class, () ->
        FlowRpcConfiguration.builder().receiveBufferSize(-1).build());
  }

  @Test
  public void testTransportConfiguration() {
    // Test that FlowRpcTransportImpl uses the configuration
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .receiveBufferSize(8192)
        .build();

    FlowRpcTransportImpl transport = new FlowRpcTransportImpl(
        new SimulatedFlowTransport(), config);

    assertEquals(config, transport.getConfiguration());
    assertEquals(8192, transport.getConfiguration().getReceiveBufferSize());
  }
}