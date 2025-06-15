package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SimulationParameters class.
 */
class SimulationParametersTest {
  
  @Test
  void testDefaultValues() {
    SimulationParameters params = new SimulationParameters();
    
    // Default delays
    assertEquals(0.001, params.getReadDelay());
    assertEquals(0.002, params.getWriteDelay());
    assertEquals(0.0005, params.getMetadataDelay());
    
    // Default throughputs
    assertEquals(100_000_000, params.getReadBytesPerSecond());
    assertEquals(50_000_000, params.getWriteBytesPerSecond());
    
    // Default error probabilities
    assertEquals(0.0, params.getReadErrorProbability());
    assertEquals(0.0, params.getWriteErrorProbability());
    assertEquals(0.0, params.getMetadataErrorProbability());
  }
  
  @Test
  void testSetReadDelay() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setReadDelay(0.5);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(0.5, params.getReadDelay());
  }
  
  @Test
  void testSetWriteDelay() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setWriteDelay(0.5);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(0.5, params.getWriteDelay());
  }
  
  @Test
  void testSetMetadataDelay() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setMetadataDelay(0.5);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(0.5, params.getMetadataDelay());
  }
  
  @Test
  void testSetReadBytesPerSecond() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setReadBytesPerSecond(1_000_000);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(1_000_000, params.getReadBytesPerSecond());
  }
  
  @Test
  void testSetWriteBytesPerSecond() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setWriteBytesPerSecond(1_000_000);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(1_000_000, params.getWriteBytesPerSecond());
  }
  
  @Test
  void testSetReadErrorProbability() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setReadErrorProbability(0.5);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(0.5, params.getReadErrorProbability());
  }
  
  @Test
  void testSetWriteErrorProbability() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setWriteErrorProbability(0.5);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(0.5, params.getWriteErrorProbability());
  }
  
  @Test
  void testSetMetadataErrorProbability() {
    SimulationParameters params = new SimulationParameters();
    
    // Test method chaining and value setting
    SimulationParameters result = params.setMetadataErrorProbability(0.5);
    
    // Should return same instance
    assertTrue(result == params);
    
    // Value should be updated
    assertEquals(0.5, params.getMetadataErrorProbability());
  }
  
  @Test
  void testCalculateReadDelay() {
    SimulationParameters params = new SimulationParameters();
    
    // Set test values
    params.setReadDelay(0.1);
    params.setReadBytesPerSecond(1000); // 1KB/s
    
    // Calculate delay for 500 bytes (should be base delay + 0.5 seconds)
    double delay = params.calculateReadDelay(500);
    assertEquals(0.6, delay);
    
    // Calculate delay for 0 bytes (should be just base delay)
    delay = params.calculateReadDelay(0);
    assertEquals(0.1, delay);
  }
  
  @Test
  void testCalculateWriteDelay() {
    SimulationParameters params = new SimulationParameters();
    
    // Set test values
    params.setWriteDelay(0.1);
    params.setWriteBytesPerSecond(1000); // 1KB/s
    
    // Calculate delay for 500 bytes (should be base delay + 0.5 seconds)
    double delay = params.calculateWriteDelay(500);
    assertEquals(0.6, delay);
    
    // Calculate delay for 0 bytes (should be just base delay)
    delay = params.calculateWriteDelay(0);
    assertEquals(0.1, delay);
  }
  
  @Test
  void testFluentInterface() {
    // Test the fluent interface by chaining multiple calls
    SimulationParameters params = new SimulationParameters()
        .setReadDelay(0.1)
        .setWriteDelay(0.2)
        .setMetadataDelay(0.3)
        .setReadBytesPerSecond(1_000_000)
        .setWriteBytesPerSecond(2_000_000)
        .setReadErrorProbability(0.1)
        .setWriteErrorProbability(0.2)
        .setMetadataErrorProbability(0.3);
    
    // Verify all values are set correctly
    assertEquals(0.1, params.getReadDelay());
    assertEquals(0.2, params.getWriteDelay());
    assertEquals(0.3, params.getMetadataDelay());
    assertEquals(1_000_000, params.getReadBytesPerSecond());
    assertEquals(2_000_000, params.getWriteBytesPerSecond());
    assertEquals(0.1, params.getReadErrorProbability());
    assertEquals(0.2, params.getWriteErrorProbability());
    assertEquals(0.3, params.getMetadataErrorProbability());
  }
  
  @Test
  void testDiskFullProbability() {
    SimulationParameters params = new SimulationParameters();
    
    // Test default value
    assertEquals(0.0, params.getDiskFullProbability());
    
    // Test setting and getting
    SimulationParameters result = params.setDiskFullProbability(0.25);
    assertTrue(result == params);
    assertEquals(0.25, params.getDiskFullProbability());
  }
  
  @Test
  void testCorruptionProbability() {
    SimulationParameters params = new SimulationParameters();
    
    // Test default value
    assertEquals(0.0, params.getCorruptionProbability());
    
    // Test setting and getting
    SimulationParameters result = params.setCorruptionProbability(0.15);
    assertTrue(result == params);
    assertEquals(0.15, params.getCorruptionProbability());
  }
  
  @Test
  void testFromSimulationConfigWithNullConfig() {
    // Test with null config - should return params with default values
    SimulationParameters params = SimulationParameters.fromSimulationConfig(null);
    
    // Verify default values are used
    assertEquals(0.001, params.getReadDelay());
    assertEquals(0.002, params.getWriteDelay());
    assertEquals(0.0005, params.getMetadataDelay());
    assertEquals(100_000_000, params.getReadBytesPerSecond());
    assertEquals(50_000_000, params.getWriteBytesPerSecond());
    assertEquals(0.0, params.getReadErrorProbability());
    assertEquals(0.0, params.getWriteErrorProbability());
    assertEquals(0.0, params.getMetadataErrorProbability());
    assertEquals(0.0, params.getDiskFullProbability());
    assertEquals(0.0, params.getCorruptionProbability());
  }
  
  @Test
  void testFromSimulationConfigWithConfig() {
    // Create a config with custom values
    SimulationConfiguration config = new SimulationConfiguration();
    config.setDiskReadDelay(0.5);
    config.setDiskWriteDelay(0.6);
    config.setDiskBytesPerSecond(1_000_000);
    config.setDiskFailureProbability(0.1);
    config.setDiskFullProbability(0.2);
    config.setDiskCorruptionProbability(0.3);
    
    // Create params from config
    SimulationParameters params = SimulationParameters.fromSimulationConfig(config);
    
    // Verify values from config are applied
    assertEquals(0.5, params.getReadDelay());
    assertEquals(0.6, params.getWriteDelay());
    assertEquals(0.25, params.getMetadataDelay()); // 0.5 * 0.5
    assertEquals(1_000_000, params.getReadBytesPerSecond());
    assertEquals(500_000, params.getWriteBytesPerSecond()); // 1_000_000 * 0.5
    assertEquals(0.1, params.getReadErrorProbability());
    assertEquals(0.1, params.getWriteErrorProbability());
    assertEquals(0.05, params.getMetadataErrorProbability()); // 0.1 * 0.5
    assertEquals(0.2, params.getDiskFullProbability());
    assertEquals(0.3, params.getCorruptionProbability());
  }
}