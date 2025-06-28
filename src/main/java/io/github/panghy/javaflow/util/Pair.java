package io.github.panghy.javaflow.util;

/**
 * A simple container for a pair of values.
 * This class is used throughout the RPC framework to pair requests with
 * their response promises or stream handlers.
 *
 * <p>Pair is immutable and can be safely shared between threads or actors.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create a pair of request and response promise
 * GetUserRequest request = new GetUserRequest(userId);
 * CompletableFuture<UserInfo> promise = future.getPromise();
 * Pair<GetUserRequest, CompletableFuture<UserInfo>> pair =
 *     new Pair<>(request, promise);
 *
 * // Send the pair to a promise stream
 * userService.getUser.send(pair);
 *
 * // Access the values
 * GetUserRequest req = pair.first;
 * CompletableFuture<UserInfo> prom = pair.second;
 * }</pre>
 *
 * @param <T>    The type of the first value
 * @param <U>    The type of the second value
 * @param first  The first value in the pair.
 * @param second The second value in the pair.
 */
public record Pair<T, U>(T first, U second) {

  /**
   * Creates a new pair with the specified values.
   *
   * @param first  The first value
   * @param second The second value
   */
  public Pair {
  }

  /**
   * Factory method to create a new pair.
   *
   * @param <T>    The type of the first value
   * @param <U>    The type of the second value
   * @param first  The first value
   * @param second The second value
   * @return A new pair containing the specified values
   */
  public static <T, U> Pair<T, U> of(T first, U second) {
    return new Pair<>(first, second);
  }

  @Override
  public String toString() {
    return "Pair{" +
           "first=" + first +
           ", second=" + second +
           '}';
  }
}