package io.github.panghy.javaflow.simulation;

/**
 * Context for simulation mode that tracks simulation state and configuration.
 * This class will be expanded in future phases to include fault injection,
 * metrics collection, and other simulation features.
 * 
 * <p>For now, it primarily manages the random source and simulation mode flag.</p>
 */
public class SimulationContext {
  
  private static final ThreadLocal<SimulationContext> contextThreadLocal = new ThreadLocal<>();
  
  private final long seed;
  private final RandomSource randomSource;
  private final boolean isSimulated;
  
  /**
   * Creates a new simulation context with the given seed.
   *
   * @param seed The seed for deterministic randomness
   * @param isSimulated Whether this is a simulated context
   */
  public SimulationContext(long seed, boolean isSimulated) {
    this.seed = seed;
    this.isSimulated = isSimulated;
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
}