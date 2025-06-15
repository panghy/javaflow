package io.github.panghy.javaflow.simulation;

import io.github.panghy.javaflow.Flow;

/**
 * BUGGIFY-style fault injection framework for JavaFlow.
 * 
 * <p>This class provides methods for injecting faults and unusual conditions into code
 * during simulation runs. BUGGIFY is inspired by FoundationDB's testing methodology
 * where code explicitly cooperates with the simulator to test edge cases and failure
 * scenarios that would be difficult or impossible to reproduce in real systems.
 * 
 * <p>All BUGGIFY methods return false when not in simulation mode, ensuring zero
 * overhead in production.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Inject random delays
 * if (Buggify.isEnabled("slow_disk_io")) {
 *     await(Flow.delay(5.0)); // 5 second delay
 * }
 * 
 * // Inject failures
 * if (Buggify.sometimes(0.01)) { // 1% chance
 *     throw new IOException("Simulated disk failure");
 * }
 * 
 * // Change behavior
 * int batchSize = Buggify.isEnabled("small_batches") ? 1 : 1000;
 * }</pre>
 */
public final class Buggify {
  
  private Buggify() {
    // Prevent instantiation
  }
  
  /**
   * Checks if a specific bug is enabled based on its ID.
   * 
   * <p>This method first checks if we're in simulation mode. If not, it always
   * returns false. In simulation mode, it consults the bug registry to determine
   * if the specified bug should be injected based on its configured probability.
   * 
   * @param bugId The unique identifier for the bug to check
   * @return true if the bug should be injected, false otherwise
   */
  public static boolean isEnabled(String bugId) {
    if (!Flow.isSimulated()) {
      return false;
    }
    return BugRegistry.getInstance().shouldInject(bugId);
  }
  
  /**
   * Randomly returns true with the specified probability.
   * 
   * <p>This is useful for injecting faults without pre-registering them in the
   * bug registry. Uses the deterministic random source in simulation mode.
   * 
   * @param probability The probability of returning true (0.0 to 1.0)
   * @return true with the specified probability, false otherwise
   */
  public static boolean sometimes(double probability) {
    if (!Flow.isSimulated()) {
      return false;
    }
    return FlowRandom.current().nextDouble() < probability;
  }
  
  /**
   * Checks if a bug is enabled with reduced probability after recovery time.
   * 
   * <p>This method implements time-aware fault injection. After 300 seconds of
   * simulation time, the probability of fault injection is greatly reduced to
   * allow the system to demonstrate recovery behavior.
   * 
   * @param bugId The unique identifier for the bug to check
   * @return true if the bug should be injected, false otherwise
   */
  public static boolean isEnabledWithRecovery(String bugId) {
    if (!Flow.isSimulated()) {
      return false;
    }
    
    SimulationContext context = SimulationContext.current();
    if (context != null && context.getCurrentTimeSeconds() > 300.0) {
      // After 300 seconds, reduce fault injection to 1% to test recovery
      return sometimes(0.01);
    }
    
    return isEnabled(bugId);
  }
  
  /**
   * Conditionally checks if a bug is enabled based on a condition.
   * 
   * <p>This is useful for context-dependent fault injection where bugs should
   * only be activated under certain conditions.
   * 
   * @param bugId The unique identifier for the bug to check
   * @param condition The condition that must be true for the bug to be checked
   * @return true if both the condition is true and the bug is enabled, false otherwise
   */
  public static boolean isEnabledIf(String bugId, boolean condition) {
    return condition && isEnabled(bugId);
  }
  
  /**
   * Injects a delay with the specified probability.
   * 
   * <p>This is a convenience method for injecting random delays, one of the most
   * common BUGGIFY patterns.
   * 
   * @param probability The probability of injecting the delay
   * @param delaySeconds The delay duration in seconds
   * @return true if the delay was injected, false otherwise
   */
  public static boolean injectDelay(double probability, double delaySeconds) {
    if (sometimes(probability)) {
      Flow.delay(delaySeconds);
      return true;
    }
    return false;
  }
  
  /**
   * Returns a value chosen randomly between two options.
   * 
   * <p>This is useful for randomly selecting between different configurations
   * or behaviors during simulation.
   * 
   * @param <T> The type of the values
   * @param probability The probability of returning the first value
   * @param ifTrue The value to return with the specified probability
   * @param ifFalse The value to return otherwise
   * @return One of the two values based on random selection
   */
  public static <T> T choose(double probability, T ifTrue, T ifFalse) {
    if (!Flow.isSimulated()) {
      return ifFalse;
    }
    return sometimes(probability) ? ifTrue : ifFalse;
  }
  
  /**
   * Returns a random integer within the specified range.
   * 
   * <p>Useful for varying parameters like batch sizes, retry counts, etc.
   * 
   * @param min The minimum value (inclusive)
   * @param max The maximum value (exclusive)
   * @return A random integer in the range [min, max)
   */
  public static int randomInt(int min, int max) {
    if (!Flow.isSimulated() || min >= max) {
      return min;
    }
    return min + FlowRandom.current().nextInt(max - min);
  }
  
  /**
   * Returns a random double within the specified range.
   * 
   * <p>Useful for varying parameters like timeouts, delays, probabilities, etc.
   * 
   * @param min The minimum value (inclusive)
   * @param max The maximum value (exclusive)
   * @return A random double in the range [min, max)
   */
  public static double randomDouble(double min, double max) {
    if (!Flow.isSimulated() || min >= max) {
      return min;
    }
    return min + FlowRandom.current().nextDouble() * (max - min);
  }
}