package io.github.panghy.javaflow.simulation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for managing BUGGIFY fault injection configurations.
 * 
 * <p>This class maintains a registry of bug IDs and their associated probabilities,
 * allowing centralized configuration of fault injection behavior. It uses the
 * deterministic random source to ensure reproducible fault injection patterns.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Register bugs with probabilities
 * BugRegistry.getInstance()
 *     .register("network_partition", 0.01)  // 1% chance
 *     .register("disk_slow", 0.05)          // 5% chance
 *     .register("process_crash", 0.001);    // 0.1% chance
 * 
 * // Clear all registrations
 * BugRegistry.getInstance().clear();
 * }</pre>
 */
public class BugRegistry {
  
  private static final BugRegistry INSTANCE = new BugRegistry();
  
  private final ConcurrentMap<String, Double> bugProbabilities = new ConcurrentHashMap<>();
  
  private BugRegistry() {
    // Singleton
  }
  
  /**
   * Gets the singleton instance of the bug registry.
   * 
   * @return The bug registry instance
   */
  public static BugRegistry getInstance() {
    return INSTANCE;
  }
  
  /**
   * Registers a bug ID with its probability of activation.
   * 
   * @param bugId The unique identifier for the bug
   * @param probability The probability of activation (0.0 to 1.0)
   * @return This registry instance for method chaining
   * @throws IllegalArgumentException if probability is not in range [0.0, 1.0]
   */
  public BugRegistry register(String bugId, double probability) {
    if (probability < 0.0 || probability > 1.0) {
      throw new IllegalArgumentException("Probability must be between 0.0 and 1.0");
    }
    bugProbabilities.put(bugId, probability);
    return this;
  }
  
  /**
   * Unregisters a bug ID from the registry.
   * 
   * @param bugId The bug ID to unregister
   * @return This registry instance for method chaining
   */
  public BugRegistry unregister(String bugId) {
    bugProbabilities.remove(bugId);
    return this;
  }
  
  /**
   * Clears all registered bugs from the registry.
   * 
   * @return This registry instance for method chaining
   */
  public BugRegistry clear() {
    bugProbabilities.clear();
    return this;
  }
  
  /**
   * Determines if a bug should be injected based on its registered probability.
   * 
   * <p>If the bug is not registered, this method returns false. Otherwise, it
   * uses the deterministic random source to decide based on the registered
   * probability.
   * 
   * @param bugId The bug ID to check
   * @return true if the bug should be injected, false otherwise
   */
  public boolean shouldInject(String bugId) {
    Double probability = bugProbabilities.get(bugId);
    if (probability == null) {
      return false;
    }
    return FlowRandom.current().nextDouble() < probability;
  }
  
  /**
   * Gets the registered probability for a bug ID.
   * 
   * @param bugId The bug ID to query
   * @return The registered probability, or null if not registered
   */
  public Double getProbability(String bugId) {
    return bugProbabilities.get(bugId);
  }
  
  /**
   * Checks if a bug ID is registered.
   * 
   * @param bugId The bug ID to check
   * @return true if the bug is registered, false otherwise
   */
  public boolean isRegistered(String bugId) {
    return bugProbabilities.containsKey(bugId);
  }
  
  /**
   * Gets the number of registered bugs.
   * 
   * @return The count of registered bugs
   */
  public int size() {
    return bugProbabilities.size();
  }
}