package io.github.panghy.javaflow.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a test should run with a random seed.
 * The seed will be logged so that failures can be reproduced.
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * @Test
 * @RandomSeed
 * public void testWithRandomSeed() {
 *     // This test will run with a random seed
 * }
 * 
 * @Test
 * @RandomSeed(iterations = 100)
 * public void testMultipleRandomSeeds() {
 *     // This test will run 100 times with different random seeds
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RandomSeed {
  
  /**
   * Number of times to run the test with different random seeds.
   * Each iteration will use a different seed.
   *
   * @return The number of iterations (default: 1)
   */
  int iterations() default 1;
}