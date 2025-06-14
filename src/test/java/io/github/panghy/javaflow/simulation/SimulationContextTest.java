package io.github.panghy.javaflow.simulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for enhanced SimulationContext with configuration support.
 */
public class SimulationContextTest {
  
  @AfterEach
  public void cleanup() {
    SimulationContext.clear();
    FlowRandom.clear();
  }
  
  @Test
  public void testContextWithDefaultConfiguration() {
    SimulationContext context = new SimulationContext(12345, true);
    
    assertEquals(12345, context.getSeed());
    assertTrue(context.isSimulatedContext());
    assertNotNull(context.getConfiguration());
    assertEquals(SimulationConfiguration.Mode.DETERMINISTIC, 
        context.getConfiguration().getMode());
  }
  
  @Test
  public void testContextWithCustomConfiguration() {
    SimulationConfiguration config = SimulationConfiguration.chaos()
        .setTaskSelectionProbability(0.75);
    SimulationContext context = new SimulationContext(99999, true, config);
    
    assertEquals(99999, context.getSeed());
    assertTrue(context.isSimulatedContext());
    assertEquals(config, context.getConfiguration());
    assertEquals(0.75, context.getConfiguration().getTaskSelectionProbability());
  }
  
  @Test
  public void testContextWithNullConfiguration() {
    // Should default to deterministic when null is passed
    SimulationContext context = new SimulationContext(11111, true, null);
    
    assertNotNull(context.getConfiguration());
    assertEquals(SimulationConfiguration.Mode.DETERMINISTIC, 
        context.getConfiguration().getMode());
  }
  
  @Test
  public void testCurrentConfiguration() {
    // No context set
    assertNull(SimulationContext.currentConfiguration());
    
    // Set context with configuration
    SimulationConfiguration config = SimulationConfiguration.stress();
    SimulationContext context = new SimulationContext(77777, true, config);
    SimulationContext.setCurrent(context);
    
    assertNotNull(SimulationContext.currentConfiguration());
    assertEquals(config, SimulationContext.currentConfiguration());
    assertEquals(SimulationConfiguration.Mode.STRESS, 
        SimulationContext.currentConfiguration().getMode());
    
    // Clear context
    SimulationContext.clear();
    assertNull(SimulationContext.currentConfiguration());
  }
  
  @Test
  public void testThreadLocalIsolation() throws InterruptedException {
    // Set context in main thread
    SimulationConfiguration mainConfig = SimulationConfiguration.chaos();
    SimulationContext mainContext = new SimulationContext(11111, true, mainConfig);
    SimulationContext.setCurrent(mainContext);
    
    // Verify in another thread
    Thread otherThread = new Thread(() -> {
      // Should not see main thread's context
      assertNull(SimulationContext.current());
      assertNull(SimulationContext.currentConfiguration());
      
      // Set different context in this thread
      SimulationConfiguration otherConfig = SimulationConfiguration.stress();
      SimulationContext otherContext = new SimulationContext(22222, true, otherConfig);
      SimulationContext.setCurrent(otherContext);
      
      // Verify it's set
      assertEquals(otherContext, SimulationContext.current());
      assertEquals(otherConfig, SimulationContext.currentConfiguration());
    });
    
    otherThread.start();
    otherThread.join();
    
    // Main thread's context should be unchanged
    assertEquals(mainContext, SimulationContext.current());
    assertEquals(mainConfig, SimulationContext.currentConfiguration());
  }
  
  @Test
  public void testNonSimulatedContext() {
    // Create non-simulated context
    SimulationContext context = new SimulationContext(12345, false);
    
    assertFalse(context.isSimulatedContext());
    assertFalse(SimulationContext.isSimulated()); // Static method should return false
    
    // Should use SystemRandomSource
    assertTrue(context.getRandomSource() instanceof SystemRandomSource);
  }
  
  @Test
  public void testFlowRandomIntegration() {
    // Setting context should initialize FlowRandom
    SimulationContext context = new SimulationContext(55555, true);
    SimulationContext.setCurrent(context);
    
    // FlowRandom should be initialized with the context's random source
    assertEquals(55555, FlowRandom.getCurrentSeed());
    assertTrue(FlowRandom.isDeterministic());
    
    // Clearing context should clear FlowRandom
    SimulationContext.clear();
    assertFalse(FlowRandom.isDeterministic());
  }
}