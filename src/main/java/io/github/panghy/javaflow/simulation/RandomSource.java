package io.github.panghy.javaflow.simulation;

import java.util.Random;

/**
 * Interface for providing random number generators in JavaFlow.
 * This allows for both deterministic (seeded) and non-deterministic random sources.
 * 
 * <p>In simulation mode, a {@link DeterministicRandomSource} is used to ensure
 * reproducible test runs. In production mode, a {@link SystemRandomSource} is used
 * for true randomness.</p>
 */
public interface RandomSource {
  
  /**
   * Gets the Random instance associated with this source.
   * The returned Random should be used for all random number generation.
   *
   * @return The Random instance
   */
  Random getRandom();
  
  /**
   * Gets the seed used to initialize this random source.
   * For deterministic sources, this returns the actual seed.
   * For non-deterministic sources, this may return 0 or a placeholder value.
   *
   * @return The seed value
   */
  long getSeed();
  
  /**
   * Resets this random source with a new seed.
   * For deterministic sources, this creates a new Random with the given seed.
   * For non-deterministic sources, this may be a no-op.
   *
   * @param seed The new seed value
   */
  void reset(long seed);
  
  /**
   * Creates a child random source with independent randomness.
   * This is useful for creating isolated random streams for different components.
   * 
   * <p>The child source should be deterministically derived from the parent
   * when in deterministic mode, ensuring reproducibility.</p>
   *
   * @param name A unique name for the child source
   * @return A new RandomSource with independent randomness
   */
  RandomSource createChild(String name);
}