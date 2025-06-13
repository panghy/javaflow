package io.github.panghy.javaflow.rpc;

/**
 * Configuration for FlowRpcTransportImpl.
 *
 * <p>This class provides configuration options for the RPC transport layer,
 * allowing customization of various parameters such as buffer sizes and timeouts.</p>
 *
 * <p>The configuration includes timeouts for:</p>
 * <ul>
 *   <li>Unary RPC calls - timeout for single-value returning methods (default: 30s)</li>
 *   <li>Stream inactivity - timeout when no data is received on a stream (default: 60s)</li>
 *   <li>Connection establishment - timeout for creating new connections (default: 10s)</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * FlowRpcConfiguration config = FlowRpcConfiguration.builder()
 *     .receiveBufferSize(128 * 1024)  // 128KB receive buffer
 *     .unaryRpcTimeoutMs(60_000)      // 60s RPC timeout
 *     .connectionTimeoutMs(5_000)      // 5s connection timeout
 *     .build();
 *
 * FlowRpcTransportImpl transport = new FlowRpcTransportImpl(networkTransport, config);
 * }</pre>
 */
public class FlowRpcConfiguration {

  /**
   * Default receive buffer size (64KB).
   */
  public static final int DEFAULT_RECEIVE_BUFFER_SIZE = 65536;

  /**
   * Default timeout for unary RPC calls (30 seconds).
   */
  public static final long DEFAULT_UNARY_RPC_TIMEOUT_MS = 30_000;

  /**
   * Default timeout for stream inactivity (60 seconds).
   */
  public static final long DEFAULT_STREAM_INACTIVITY_TIMEOUT_MS = 60_000;

  /**
   * Default connection timeout (10 seconds).
   */
  public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10_000;

  private final int receiveBufferSize;
  private final long unaryRpcTimeoutMs;
  private final long streamInactivityTimeoutMs;
  private final long connectionTimeoutMs;

  private FlowRpcConfiguration(Builder builder) {
    this.receiveBufferSize = builder.receiveBufferSize;
    this.unaryRpcTimeoutMs = builder.unaryRpcTimeoutMs;
    this.streamInactivityTimeoutMs = builder.streamInactivityTimeoutMs;
    this.connectionTimeoutMs = builder.connectionTimeoutMs;
  }

  /**
   * Gets the receive buffer size for incoming messages.
   * This determines the maximum size of a single message that can be received.
   *
   * @return The receive buffer size in bytes
   */
  public int getReceiveBufferSize() {
    return receiveBufferSize;
  }

  /**
   * Gets the timeout for unary RPC calls in milliseconds.
   * This timeout applies to RPC methods that return a single value (not streams).
   * Void methods are not subject to this timeout as they return immediately after sending.
   *
   * @return The unary RPC timeout in milliseconds
   */
  public long getUnaryRpcTimeoutMs() {
    return unaryRpcTimeoutMs;
  }

  /**
   * Gets the timeout for stream inactivity in milliseconds.
   * If no data is received on a stream for this duration, the stream will timeout.
   *
   * @return The stream inactivity timeout in milliseconds
   */
  public long getStreamInactivityTimeoutMs() {
    return streamInactivityTimeoutMs;
  }

  /**
   * Gets the connection timeout in milliseconds.
   * This timeout applies when establishing new network connections.
   *
   * @return The connection timeout in milliseconds
   */
  public long getConnectionTimeoutMs() {
    return connectionTimeoutMs;
  }

  /**
   * Creates a new builder for FlowRpcConfiguration.
   *
   * @return A new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a default configuration with standard settings.
   *
   * @return A default configuration
   */
  public static FlowRpcConfiguration defaultConfig() {
    return builder().build();
  }

  /**
   * Builder for FlowRpcConfiguration.
   */
  public static class Builder {
    private int receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;
    private long unaryRpcTimeoutMs = DEFAULT_UNARY_RPC_TIMEOUT_MS;
    private long streamInactivityTimeoutMs = DEFAULT_STREAM_INACTIVITY_TIMEOUT_MS;
    private long connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;

    private Builder() {
    }

    /**
     * Sets the receive buffer size for incoming messages.
     * This determines the maximum size of a single message that can be received.
     *
     * @param size The buffer size in bytes (must be positive)
     * @return This builder for chaining
     * @throws IllegalArgumentException if size is not positive
     */
    public Builder receiveBufferSize(int size) {
      if (size <= 0) {
        throw new IllegalArgumentException("Receive buffer size must be positive, got: " + size);
      }
      this.receiveBufferSize = size;
      return this;
    }

    /**
     * Sets the timeout for unary RPC calls.
     * This timeout applies to RPC methods that return a single value (not streams).
     * Void methods are not subject to this timeout as they return immediately after sending.
     * A value of 0 disables the timeout.
     *
     * @param timeoutMs The timeout in milliseconds (must be non-negative, 0 to disable)
     * @return This builder for chaining
     * @throws IllegalArgumentException if timeoutMs is negative
     */
    public Builder unaryRpcTimeoutMs(long timeoutMs) {
      if (timeoutMs < 0) {
        throw new IllegalArgumentException("Unary RPC timeout must be non-negative, got: " + timeoutMs);
      }
      this.unaryRpcTimeoutMs = timeoutMs;
      return this;
    }

    /**
     * Sets the timeout for stream inactivity.
     * If no data is received on a stream for this duration, the stream will timeout.
     * A value of 0 disables the timeout.
     *
     * @param timeoutMs The timeout in milliseconds (must be non-negative, 0 to disable)
     * @return This builder for chaining
     * @throws IllegalArgumentException if timeoutMs is negative
     */
    public Builder streamInactivityTimeoutMs(long timeoutMs) {
      if (timeoutMs < 0) {
        throw new IllegalArgumentException("Stream inactivity timeout must be non-negative, got: " + timeoutMs);
      }
      this.streamInactivityTimeoutMs = timeoutMs;
      return this;
    }

    /**
     * Sets the connection timeout.
     * This timeout applies when establishing new network connections.
     * A value of 0 disables the timeout.
     *
     * @param timeoutMs The timeout in milliseconds (must be non-negative, 0 to disable)
     * @return This builder for chaining
     * @throws IllegalArgumentException if timeoutMs is negative
     */
    public Builder connectionTimeoutMs(long timeoutMs) {
      if (timeoutMs < 0) {
        throw new IllegalArgumentException("Connection timeout must be non-negative, got: " + timeoutMs);
      }
      this.connectionTimeoutMs = timeoutMs;
      return this;
    }

    /**
     * Builds the configuration with the specified settings.
     *
     * @return A new FlowRpcConfiguration instance
     */
    public FlowRpcConfiguration build() {
      return new FlowRpcConfiguration(this);
    }
  }
}