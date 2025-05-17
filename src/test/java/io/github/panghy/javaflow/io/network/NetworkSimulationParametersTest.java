package io.github.panghy.javaflow.io.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the NetworkSimulationParameters class.
 */
public class NetworkSimulationParametersTest {

  @Test
  void testDefaultConstructor() {
    // Create parameters with default constructor
    NetworkSimulationParameters params = new NetworkSimulationParameters();
    
    // Verify default values
    assertEquals(0.01, params.getConnectDelay(), 0.001);
    assertEquals(0.001, params.getSendDelay(), 0.001);
    assertEquals(0.001, params.getReceiveDelay(), 0.001);
    assertEquals(0.0, params.getConnectErrorProbability());
    assertEquals(0.0, params.getSendErrorProbability());
    assertEquals(0.0, params.getReceiveErrorProbability());
    assertEquals(0.0, params.getDisconnectProbability());
    assertTrue(params.getSendBytesPerSecond() > 0);
    assertTrue(params.getReceiveBytesPerSecond() > 0);
  }
  
  @Test
  void testFluidChainedBuilder() {
    // Create parameters with fluent API
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setConnectDelay(0.1)
        .setSendDelay(0.05)
        .setReceiveDelay(0.05)
        .setSendBytesPerSecond(5_000_000)
        .setReceiveBytesPerSecond(5_000_000)
        .setConnectErrorProbability(0.05)
        .setSendErrorProbability(0.05)
        .setReceiveErrorProbability(0.05)
        .setDisconnectProbability(0.01);
    
    // Verify set values
    assertEquals(0.1, params.getConnectDelay());
    assertEquals(0.05, params.getSendDelay());
    assertEquals(0.05, params.getReceiveDelay());
    assertEquals(5_000_000, params.getSendBytesPerSecond());
    assertEquals(5_000_000, params.getReceiveBytesPerSecond());
    assertEquals(0.05, params.getConnectErrorProbability());
    assertEquals(0.05, params.getSendErrorProbability());
    assertEquals(0.05, params.getReceiveErrorProbability());
    assertEquals(0.01, params.getDisconnectProbability());
  }
  
  @Test
  void testCalculateSendDelay() {
    // Create parameters with specific settings
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setSendDelay(0.05)
        .setSendBytesPerSecond(1_048_576); // 1MB/s
    
    // Calculate delay for a 1MB message
    double delay = params.calculateSendDelay(1_048_576);
    
    // Should be base delay (0.05) plus data transfer time (1s for 1MB at 1MB/s)
    assertEquals(1.05, delay, 0.001);
    
    // Calculate delay for a 100KB message
    double smallDelay = params.calculateSendDelay(100 * 1024);
    
    // Should be base delay (0.05) plus data transfer time (100KB/1MB/s)
    double expectedDelay = 0.05 + ((100 * 1024) / (double) 1_048_576);
    assertEquals(expectedDelay, smallDelay, 0.001);
  }
  
  @Test
  void testCalculateReceiveDelay() {
    // Create parameters with specific settings
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setReceiveDelay(0.05)
        .setReceiveBytesPerSecond(1_048_576); // 1MB/s
    
    // Calculate delay for a 1MB message
    double delay = params.calculateReceiveDelay(1_048_576);
    
    // Should be base delay (0.05) plus data transfer time (1s for 1MB at 1MB/s)
    assertEquals(1.05, delay, 0.001);
  }
}