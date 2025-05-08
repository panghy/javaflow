package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowSchedulerConfigTest {

  @Test
  void testDefaultConfig() {
    FlowSchedulerConfig config = FlowSchedulerConfig.DEFAULT;
    
    assertTrue(config.isEnforcePriorities());
    assertTrue(config.isDebugLogging());
    assertEquals(1, config.getCarrierThreadCount());
  }
  
  @Test
  void testSingleThreadedConfig() {
    FlowSchedulerConfig config = FlowSchedulerConfig.SINGLE_THREADED;
    
    assertTrue(config.isEnforcePriorities());
    assertFalse(config.isDebugLogging());
    assertEquals(1, config.getCarrierThreadCount());
  }
  
  @Test
  void testCustomConfig() {
    FlowSchedulerConfig config = new FlowSchedulerConfig(4, false, true);
    
    assertFalse(config.isEnforcePriorities());
    assertTrue(config.isDebugLogging());
    assertEquals(4, config.getCarrierThreadCount());
  }
  
  @Test
  void testInvalidCarrierThreadCount() {
    // Test negative thread count
    assertThrows(IllegalArgumentException.class, () -> 
        new FlowSchedulerConfig(0, true, true));
    
    assertThrows(IllegalArgumentException.class, () -> 
        new FlowSchedulerConfig(-1, true, true));
  }
  
  @Test
  void testBuilderPattern() {
    // Test the builder pattern
    FlowSchedulerConfig config = FlowSchedulerConfig.builder()
        .carrierThreadCount(8)
        .enforcePriorities(false)
        .debugLogging(true)
        .build();
    
    assertEquals(8, config.getCarrierThreadCount());
    assertFalse(config.isEnforcePriorities());
    assertTrue(config.isDebugLogging());
  }
  
  @Test
  void testBuilderPatternWithDefaults() {
    // Test the builder with default values
    FlowSchedulerConfig config = FlowSchedulerConfig.builder().build();
    
    // Should use the default values
    assertEquals(1, config.getCarrierThreadCount());
    assertTrue(config.isEnforcePriorities());
    assertFalse(config.isDebugLogging());
  }
  
  @Test
  void testBuilderChaining() {
    // Test builder method chaining and overrides
    FlowSchedulerConfig config = FlowSchedulerConfig.builder()
        .carrierThreadCount(2)
        .carrierThreadCount(4) // should override previous value
        .enforcePriorities(false)
        .enforcePriorities(true) // should override previous value
        .debugLogging(false)
        .debugLogging(true) // should override previous value
        .build();
    
    assertEquals(4, config.getCarrierThreadCount());
    assertTrue(config.isEnforcePriorities());
    assertTrue(config.isDebugLogging());
  }
}