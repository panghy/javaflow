package io.github.panghy.javaflow.simulation;

import io.github.panghy.javaflow.io.network.NetworkSimulationParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SimulationConfiguration.
 */
public class SimulationConfigurationTest {
  
  @Test
  public void testDefaultConfiguration() {
    SimulationConfiguration config = new SimulationConfiguration();
    
    // Should default to deterministic mode
    assertEquals(SimulationConfiguration.Mode.DETERMINISTIC, config.getMode());
    assertEquals(0.0, config.getTaskSelectionProbability());
    assertEquals(0.0, config.getInterTaskDelayMs());
    assertFalse(config.isPriorityRandomization());
    assertFalse(config.isTaskExecutionLogging());
    
    // Network defaults
    assertEquals(0.01, config.getNetworkConnectDelay());
    assertEquals(0.001, config.getNetworkSendDelay());
    assertEquals(10_000_000, config.getNetworkBytesPerSecond());
    assertEquals(0.0, config.getNetworkErrorProbability());
    
    // Disk defaults
    assertEquals(0.001, config.getDiskReadDelay());
    assertEquals(0.005, config.getDiskWriteDelay());
    assertEquals(100_000_000, config.getDiskBytesPerSecond());
    assertEquals(0.0, config.getDiskFailureProbability());
  }
  
  @Test
  public void testDeterministicProfile() {
    SimulationConfiguration config = SimulationConfiguration.deterministic();
    
    assertEquals(SimulationConfiguration.Mode.DETERMINISTIC, config.getMode());
    assertEquals(0.0, config.getTaskSelectionProbability());
    assertEquals(0.0, config.getInterTaskDelayMs());
    assertFalse(config.isPriorityRandomization());
  }
  
  @Test
  public void testChaosProfile() {
    SimulationConfiguration config = SimulationConfiguration.chaos();
    
    assertEquals(SimulationConfiguration.Mode.CHAOS, config.getMode());
    assertEquals(0.5, config.getTaskSelectionProbability());
    assertEquals(1.0, config.getInterTaskDelayMs());
    assertTrue(config.isPriorityRandomization());
    
    // Should have moderate fault injection
    assertEquals(0.01, config.getNetworkErrorProbability());
    assertEquals(0.001, config.getNetworkDisconnectProbability());
    assertEquals(0.005, config.getPacketLossProbability());
    assertEquals(0.01, config.getPacketReorderProbability());
    assertEquals(0.001, config.getDiskFailureProbability());
    assertEquals(0.0001, config.getDiskCorruptionProbability());
    assertEquals(100.0, config.getClockSkewMs());
    assertEquals(0.01, config.getMemoryPressureProbability());
  }
  
  @Test
  public void testStressProfile() {
    SimulationConfiguration config = SimulationConfiguration.stress();
    
    assertEquals(SimulationConfiguration.Mode.STRESS, config.getMode());
    assertEquals(0.9, config.getTaskSelectionProbability());
    assertEquals(10.0, config.getInterTaskDelayMs());
    assertTrue(config.isPriorityRandomization());
    assertTrue(config.isTaskExecutionLogging());
    
    // Should have high fault injection rates
    assertEquals(0.1, config.getNetworkErrorProbability());
    assertEquals(0.05, config.getNetworkDisconnectProbability());
    assertEquals(0.1, config.getPacketLossProbability());
    assertEquals(0.2, config.getPacketReorderProbability());
    assertEquals(0.05, config.getDiskFailureProbability());
    assertEquals(0.01, config.getDiskFullProbability());
    assertEquals(0.01, config.getDiskCorruptionProbability());
    assertEquals(1000.0, config.getClockSkewMs());
    assertEquals(0.1, config.getMemoryPressureProbability());
    assertEquals(0.001, config.getProcessCrashProbability());
  }
  
  @Test
  public void testFluentInterface() {
    SimulationConfiguration config = new SimulationConfiguration()
        .setMode(SimulationConfiguration.Mode.CHAOS)
        .setTaskSelectionProbability(0.7)
        .setInterTaskDelayMs(5.0)
        .setPriorityRandomization(true)
        .setTaskExecutionLogging(true)
        .setNetworkErrorProbability(0.02)
        .setDiskFailureProbability(0.003);
    
    assertEquals(SimulationConfiguration.Mode.CHAOS, config.getMode());
    assertEquals(0.7, config.getTaskSelectionProbability());
    assertEquals(5.0, config.getInterTaskDelayMs());
    assertTrue(config.isPriorityRandomization());
    assertTrue(config.isTaskExecutionLogging());
    assertEquals(0.02, config.getNetworkErrorProbability());
    assertEquals(0.003, config.getDiskFailureProbability());
  }
  
  @Test
  public void testNetworkParametersConversion() {
    SimulationConfiguration config = new SimulationConfiguration()
        .setNetworkConnectDelay(0.05)
        .setNetworkSendDelay(0.002)
        .setNetworkReceiveDelay(0.003)
        .setNetworkBytesPerSecond(5_000_000)
        .setNetworkErrorProbability(0.02)
        .setNetworkDisconnectProbability(0.01);
    
    NetworkSimulationParameters netParams = config.toNetworkParameters();
    assertNotNull(netParams);
    
    assertEquals(0.05, netParams.getConnectDelay());
    assertEquals(0.002, netParams.getSendDelay());
    assertEquals(0.003, netParams.getReceiveDelay());
    assertEquals(5_000_000, netParams.getSendBytesPerSecond());
    assertEquals(5_000_000, netParams.getReceiveBytesPerSecond());
    assertEquals(0.02, netParams.getConnectErrorProbability());
    assertEquals(0.02, netParams.getSendErrorProbability());
    assertEquals(0.02, netParams.getReceiveErrorProbability());
    assertEquals(0.01, netParams.getDisconnectProbability());
  }
  
  @Test
  public void testAllSettersAndGetters() {
    SimulationConfiguration config = new SimulationConfiguration();
    
    // Test all setters return the instance for chaining
    SimulationConfiguration returned = config
        .setMode(SimulationConfiguration.Mode.STRESS)
        .setTaskSelectionProbability(0.8)
        .setInterTaskDelayMs(2.5)
        .setPriorityRandomization(true)
        .setTaskExecutionLogging(true)
        .setNetworkConnectDelay(0.02)
        .setNetworkSendDelay(0.003)
        .setNetworkReceiveDelay(0.004)
        .setNetworkBytesPerSecond(20_000_000)
        .setNetworkErrorProbability(0.03)
        .setNetworkDisconnectProbability(0.02)
        .setPacketLossProbability(0.01)
        .setPacketReorderProbability(0.015)
        .setDiskReadDelay(0.002)
        .setDiskWriteDelay(0.01)
        .setDiskBytesPerSecond(50_000_000)
        .setDiskFailureProbability(0.004)
        .setDiskFullProbability(0.002)
        .setDiskCorruptionProbability(0.0005)
        .setClockSkewMs(200)
        .setMemoryPressureProbability(0.05)
        .setProcessCrashProbability(0.0001);
    
    // Verify chaining works
    assertEquals(config, returned);
    
    // Verify all values were set
    assertEquals(SimulationConfiguration.Mode.STRESS, config.getMode());
    assertEquals(0.8, config.getTaskSelectionProbability());
    assertEquals(2.5, config.getInterTaskDelayMs());
    assertTrue(config.isPriorityRandomization());
    assertTrue(config.isTaskExecutionLogging());
    assertEquals(0.02, config.getNetworkConnectDelay());
    assertEquals(0.003, config.getNetworkSendDelay());
    assertEquals(0.004, config.getNetworkReceiveDelay());
    assertEquals(20_000_000, config.getNetworkBytesPerSecond());
    assertEquals(0.03, config.getNetworkErrorProbability());
    assertEquals(0.02, config.getNetworkDisconnectProbability());
    assertEquals(0.01, config.getPacketLossProbability());
    assertEquals(0.015, config.getPacketReorderProbability());
    assertEquals(0.002, config.getDiskReadDelay());
    assertEquals(0.01, config.getDiskWriteDelay());
    assertEquals(50_000_000, config.getDiskBytesPerSecond());
    assertEquals(0.004, config.getDiskFailureProbability());
    assertEquals(0.002, config.getDiskFullProbability());
    assertEquals(0.0005, config.getDiskCorruptionProbability());
    assertEquals(200, config.getClockSkewMs());
    assertEquals(0.05, config.getMemoryPressureProbability());
    assertEquals(0.0001, config.getProcessCrashProbability());
  }
}