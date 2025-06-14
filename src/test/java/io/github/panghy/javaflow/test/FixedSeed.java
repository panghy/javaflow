package io.github.panghy.javaflow.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a test should run with a specific fixed seed.
 * This is useful for reproducing specific test failures or
 * ensuring deterministic behavior in certain tests.
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * @Test
 * @FixedSeed(12345)
 * public void testWithFixedSeed() {
 *     // This test will always run with seed 12345
 * }
 * 
 * @Test
 * @FixedSeed(0)
 * public void testWithZeroSeed() {
 *     // This test will always run with seed 0
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FixedSeed {
  
  /**
   * The seed value to use for this test.
   *
   * @return The fixed seed value
   */
  long value();
}