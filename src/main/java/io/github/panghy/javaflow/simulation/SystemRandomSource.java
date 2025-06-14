package io.github.panghy.javaflow.simulation;

import java.util.Random;

/**
 * A non-deterministic implementation of {@link RandomSource} that uses system randomness.
 * This is used in production mode where true randomness is desired.
 * 
 * <p>Unlike {@link DeterministicRandomSource}, this class creates Random instances
 * without explicit seeds, relying on system entropy for randomness.</p>
 */
public class SystemRandomSource implements RandomSource {
  
  private final Random random;
  private final long creationTime;
  
  /**
   * Creates a new system random source with true randomness.
   */
  public SystemRandomSource() {
    this.random = new Random();
    this.creationTime = System.currentTimeMillis();
  }
  
  @Override
  public Random getRandom() {
    return random;
  }
  
  @Override
  public long getSeed() {
    // Return creation time as a pseudo-seed for logging purposes
    return creationTime;
  }
  
  @Override
  public void reset(long seed) {
    // For system random, we ignore reset requests to maintain true randomness
    // This is intentional - production code should not be deterministic
  }
  
  @Override
  public RandomSource createChild(String name) {
    // Each child gets its own independent system random
    return new SystemRandomSource();
  }
  
  @Override
  public String toString() {
    return "SystemRandomSource{creationTime=" + creationTime + "}";
  }
}