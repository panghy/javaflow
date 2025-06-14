package io.github.panghy.javaflow.simulation;

import java.util.Random;

/**
 * Central access point for all random number generation in JavaFlow.
 * This class manages thread-local random sources to ensure deterministic
 * behavior in simulation mode while maintaining thread safety.
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // In simulation mode
 * FlowRandom.initialize(new DeterministicRandomSource(12345));
 * 
 * // Generate random numbers
 * int value = FlowRandom.current().nextInt(100);
 * double probability = FlowRandom.current().nextDouble();
 * }</pre>
 * 
 * <p>All code in JavaFlow should use {@code FlowRandom.current()} instead of
 * {@code new Random()} or {@code Math.random()} to ensure deterministic behavior
 * in simulation mode.</p>
 */
public final class FlowRandom {
  
  private static final ThreadLocal<RandomSource> randomSource = new ThreadLocal<>();
  
  // Private constructor to prevent instantiation
  private FlowRandom() { }
  
  /**
   * Initializes the random source for the current thread.
   * This should be called at the beginning of a simulation test or
   * when starting a new simulated process.
   *
   * @param source The random source to use
   * @throws NullPointerException if source is null
   */
  public static void initialize(RandomSource source) {
    if (source == null) {
      throw new NullPointerException("RandomSource cannot be null");
    }
    randomSource.set(source);
  }
  
  /**
   * Gets the current Random instance for this thread.
   * If no random source has been initialized, a system random source
   * is created and used (non-deterministic mode).
   *
   * @return The Random instance to use for random number generation
   */
  public static Random current() {
    RandomSource source = randomSource.get();
    if (source == null) {
      // Fallback to system random for non-simulation mode
      // This ensures the system works even without explicit initialization
      source = new SystemRandomSource();
      randomSource.set(source);
    }
    return source.getRandom();
  }
  
  /**
   * Gets the current seed value for this thread's random source.
   * This is useful for logging test failures with reproducible seeds.
   *
   * @return The seed value, or 0 if using system randomness
   */
  public static long getCurrentSeed() {
    RandomSource source = randomSource.get();
    return source != null ? source.getSeed() : 0;
  }
  
  /**
   * Clears the random source for the current thread.
   * This should be called in test cleanup to prevent thread-local leaks.
   */
  public static void clear() {
    randomSource.remove();
  }
  
  /**
   * Checks if a deterministic random source is currently active.
   * This can be used to determine if we're in simulation mode.
   *
   * @return true if using deterministic randomness, false otherwise
   */
  public static boolean isDeterministic() {
    RandomSource source = randomSource.get();
    return source instanceof DeterministicRandomSource;
  }
  
  /**
   * Creates a child random source with the given name.
   * This is useful for creating independent random streams for different
   * components while maintaining determinism.
   *
   * @param name A unique name for the child source
   * @return A new RandomSource with independent randomness
   * @throws NullPointerException if name is null
   */
  public static RandomSource createChild(String name) {
    if (name == null) {
      throw new NullPointerException("Child name cannot be null");
    }
    RandomSource source = randomSource.get();
    if (source == null) {
      source = new SystemRandomSource();
      randomSource.set(source);
    }
    return source.createChild(name);
  }
}