package io.github.panghy.javaflow.io;

/**
 * Functional interface for I/O operations that can throw exceptions.
 */
public interface IOFunction<T, R> {

  /**
   * Applies this function to the given argument.
   *
   * @param input The input argument
   * @return The result of the function
   * @throws Exception If an error occurs during the operation
   */
  R apply(T input) throws Exception;
}
