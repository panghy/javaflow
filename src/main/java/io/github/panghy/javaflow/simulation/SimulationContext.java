package io.github.panghy.javaflow.simulation;

/**
 * Context for simulation mode that tracks simulation state and configuration.
 * This class manages the simulation environment including random sources,
 * configuration, and simulation state.
 * 
 * <p>SimulationContext is thread-local, allowing different threads to have
 * independent simulation contexts. This is essential for deterministic
 * multi-threaded testing.</p>
 */
public class SimulationContext {
  
  private static final ThreadLocal<SimulationContext> contextThreadLocal = new ThreadLocal<>();
  
  private final long seed;
  private final RandomSource randomSource;
  private final boolean isSimulated;
  private final SimulationConfiguration configuration;
  private double currentTimeSeconds = 0.0;
  
  /**
   * Creates a new simulation context with the given seed and default configuration.
   *
   * @param seed The seed for deterministic randomness
   * @param isSimulated Whether this is a simulated context
   */
  public SimulationContext(long seed, boolean isSimulated) {
    this(seed, isSimulated, SimulationConfiguration.deterministic());
  }
  
  /**
   * Creates a new simulation context with the given seed and configuration.
   *
   * @param seed The seed for deterministic randomness
   * @param isSimulated Whether this is a simulated context
   * @param configuration The simulation configuration to use
   */
  public SimulationContext(long seed, boolean isSimulated, SimulationConfiguration configuration) {
    this.seed = seed;
    this.isSimulated = isSimulated;
    this.configuration = configuration != null ? configuration : SimulationConfiguration.deterministic();
    this.randomSource = isSimulated 
        ? new DeterministicRandomSource(seed) 
        : new SystemRandomSource();
  }
  
  /**
   * Gets the current simulation context for this thread.
   *
   * @return The current context, or null if not in simulation
   */
  public static SimulationContext current() {
    return contextThreadLocal.get();
  }
  
  /**
   * Sets the simulation context for the current thread.
   *
   * @param context The context to set
   */
  public static void setCurrent(SimulationContext context) {
    contextThreadLocal.set(context);
    // Also initialize FlowRandom with the context's random source
    if (context != null) {
      FlowRandom.initialize(context.getRandomSource());
    }
  }
  
  /**
   * Clears the simulation context for the current thread.
   */
  public static void clear() {
    contextThreadLocal.remove();
    FlowRandom.clear();
  }
  
  /**
   * Checks if we're currently in simulation mode.
   *
   * @return true if in simulation mode, false otherwise
   */
  public static boolean isSimulated() {
    SimulationContext context = current();
    return context != null && context.isSimulated;
  }
  
  /**
   * Gets the seed used for this simulation context.
   *
   * @return The seed value
   */
  public long getSeed() {
    return seed;
  }
  
  /**
   * Gets the random source for this context.
   *
   * @return The random source
   */
  public RandomSource getRandomSource() {
    return randomSource;
  }
  
  /**
   * Checks if this is a simulated context.
   *
   * @return true if simulated, false otherwise
   */
  public boolean isSimulatedContext() {
    return isSimulated;
  }
  
  /**
   * Gets the simulation configuration for this context.
   *
   * @return The simulation configuration
   */
  public SimulationConfiguration getConfiguration() {
    return configuration;
  }
  
  /**
   * Gets the current simulation configuration from the thread-local context.
   *
   * @return The configuration or null if no context is set
   */
  public static SimulationConfiguration currentConfiguration() {
    SimulationContext context = current();
    return context != null ? context.getConfiguration() : null;
  }
  
  /**
   * Gets the current simulation time in seconds.
   *
   * @return The current time in seconds
   */
  public double getCurrentTimeSeconds() {
    return currentTimeSeconds;
  }
  
  /**
   * Advances the simulation time by the specified amount.
   * This is typically called by the scheduler during simulation.
   *
   * @param seconds The number of seconds to advance
   */
  public void advanceTime(double seconds) {
    currentTimeSeconds += seconds;
  }
  
  /**
   * Clears the current simulation context for the current thread.
   * This is an alias for clear() to match the naming convention.
   */
  public static void clearCurrent() {
    clear();
  }
}