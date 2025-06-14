package io.github.panghy.javaflow.simulation;

import java.util.Random;

/**
 * A deterministic implementation of {@link RandomSource} that uses a seeded Random.
 * This ensures reproducible behavior when the same seed is used.
 * 
 * <p>This class is the foundation of JavaFlow's deterministic simulation mode.
 * By using the same seed, tests can reproduce exact sequences of random events,
 * making debugging and regression testing much more effective.</p>
 */
public class DeterministicRandomSource implements RandomSource {
  
  private final long initialSeed;
  private Random random;
  
  /**
   * Creates a new deterministic random source with the given seed.
   *
   * @param seed The seed for the random number generator
   */
  public DeterministicRandomSource(long seed) {
    this.initialSeed = seed;
    this.random = new Random(seed);
  }
  
  @Override
  public Random getRandom() {
    return random;
  }
  
  @Override
  public long getSeed() {
    return initialSeed;
  }
  
  @Override
  public void reset(long seed) {
    this.random = new Random(seed);
  }
  
  @Override
  public RandomSource createChild(String name) {
    // Create a deterministic child seed based on parent seed and name
    // This ensures children are reproducible but independent
    long childSeed = initialSeed;
    for (char c : name.toCharArray()) {
      childSeed = childSeed * 31 + c;
    }
    return new DeterministicRandomSource(childSeed);
  }
  
  @Override
  public String toString() {
    return "DeterministicRandomSource{seed=" + initialSeed + "}";
  }
}