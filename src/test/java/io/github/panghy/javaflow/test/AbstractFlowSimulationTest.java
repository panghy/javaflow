package io.github.panghy.javaflow.test;

import io.github.panghy.javaflow.simulation.DeterministicRandomSource;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.lang.reflect.Method;

/**
 * Base class for tests that use JavaFlow's simulation mode.
 * This class handles seed management, simulation context setup,
 * and proper cleanup after tests.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Automatic seed management based on annotations</li>
 *   <li>Seed logging for reproducibility</li>
 *   <li>Simulation context setup and cleanup</li>
 *   <li>Thread-local state management</li>
 * </ul>
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * public class MySimulationTest extends AbstractFlowSimulationTest {
 *     
 *     @Test
 *     @RandomSeed
 *     public void testWithRandomSeed() {
 *         // Test runs with random seed
 *     }
 *     
 *     @Test
 *     @FixedSeed(12345)
 *     public void testWithFixedSeed() {
 *         // Test runs with seed 12345
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractFlowSimulationTest {
  
  // TestInfo will be injected by JUnit 5
  
  private long currentSeed;
  private boolean isSimulated = true;
  private SimulationConfiguration configuration;
  
  /**
   * Sets up the simulation context before each test.
   * This method determines the seed based on test annotations
   * and initializes the simulation context.
   */
  @BeforeEach
  public void setupSimulation(TestInfo testInfo) throws Exception {
    // Get the test method
    String methodName = testInfo.getTestMethod().map(Method::getName).orElseThrow();
    Method testMethod = testInfo.getTestMethod().orElseThrow();
    
    // Determine the seed
    currentSeed = determineSeed(testMethod);
    
    // Log the seed for reproducibility
    System.out.printf("[TEST] %s.%s running with seed: %d (use @FixedSeed(%d) to reproduce)%n",
        getClass().getSimpleName(), methodName, currentSeed, currentSeed);
    
    // Determine configuration
    configuration = determineConfiguration(testMethod);
    
    // Set up simulation context
    SimulationContext context = new SimulationContext(currentSeed, isSimulated, configuration);
    SimulationContext.setCurrent(context);
    
    // Initialize FlowRandom
    FlowRandom.initialize(new DeterministicRandomSource(currentSeed));
    
    // Call subclass setup
    onSetup();
  }
  
  /**
   * Cleans up the simulation context after each test.
   * This prevents thread-local leaks and ensures clean state.
   */
  @AfterEach
  public void tearDownSimulation() {
    try {
      // Call subclass cleanup
      onTearDown();
    } finally {
      // Clear simulation context
      SimulationContext.clear();
      
      // Clear FlowRandom
      FlowRandom.clear();
    }
  }
  
  /**
   * Determines the seed to use based on test annotations.
   * Priority order:
   * 1. @FixedSeed on method
   * 2. @RandomSeed on method  
   * 3. @FixedSeed on class
   * 4. @RandomSeed on class
   * 5. Default to 0
   *
   * @param testMethod The test method being run
   * @return The seed to use
   */
  private long determineSeed(Method testMethod) {
    // Check method-level annotations first
    FixedSeed methodFixed = testMethod.getAnnotation(FixedSeed.class);
    if (methodFixed != null) {
      return methodFixed.value();
    }
    
    RandomSeed methodRandom = testMethod.getAnnotation(RandomSeed.class);
    if (methodRandom != null) {
      return System.currentTimeMillis() ^ testMethod.getName().hashCode();
    }
    
    // Check class-level annotations
    FixedSeed classFixed = getClass().getAnnotation(FixedSeed.class);
    if (classFixed != null) {
      return classFixed.value();
    }
    
    RandomSeed classRandom = getClass().getAnnotation(RandomSeed.class);
    if (classRandom != null) {
      return System.currentTimeMillis() ^ getClass().getName().hashCode();
    }
    
    // Default to deterministic seed 0
    return 0L;
  }
  
  /**
   * Gets the current seed being used for this test.
   *
   * @return The current seed
   */
  protected long getCurrentSeed() {
    return currentSeed;
  }
  
  /**
   * Sets whether this test should run in simulated mode.
   * Must be called before the test starts (e.g., in constructor).
   *
   * @param simulated true for simulation mode, false for production mode
   */
  protected void setSimulated(boolean simulated) {
    this.isSimulated = simulated;
  }
  
  /**
   * Called after simulation setup but before the test runs.
   * Subclasses can override to perform additional setup.
   */
  protected void onSetup() {
    // Default: no additional setup
  }
  
  /**
   * Called before simulation teardown after the test completes.
   * Subclasses can override to perform additional cleanup.
   */
  protected void onTearDown() {
    // Default: no additional cleanup
  }
  
  /**
   * Determines the simulation configuration based on test annotations.
   * Subclasses can override to provide custom configuration logic.
   *
   * @param testMethod The test method being run
   * @return The configuration to use
   */
  protected SimulationConfiguration determineConfiguration(Method testMethod) {
    // Default to deterministic configuration
    // Subclasses can override to check for configuration annotations
    return SimulationConfiguration.deterministic();
  }
  
  /**
   * Gets the current simulation configuration.
   *
   * @return The simulation configuration
   */
  protected SimulationConfiguration getConfiguration() {
    return configuration;
  }
}