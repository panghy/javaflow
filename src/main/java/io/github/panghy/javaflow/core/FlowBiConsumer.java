package io.github.panghy.javaflow.core;

/**
 * Functional interface for a consumer that takes two arguments.
 * This is similar to {@link java.util.function.BiConsumer} but can throw exceptions.
 *
 * @param <T> The type of the first argument
 * @param <U> The type of the second argument
 */
@FunctionalInterface
public interface FlowBiConsumer<T, U> {
  
  /**
   * Performs this operation on the given arguments.
   *
   * @param t The first argument
   * @param u The second argument
   * @throws Exception If an error occurs during the operation
   */
  void accept(T t, U u) throws Exception;
}