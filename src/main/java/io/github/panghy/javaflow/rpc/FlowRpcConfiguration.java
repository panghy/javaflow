package io.github.panghy.javaflow.rpc;

/**
 * Configuration for FlowRpcTransportImpl.
 *
 * <p>This class provides configuration options for the RPC transport layer,
 * allowing customization of various parameters such as buffer sizes and timeouts.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * FlowRpcConfiguration config = FlowRpcConfiguration.builder()
 *     .receiveBufferSize(128 * 1024)  // 128KB receive buffer
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

  private final int receiveBufferSize;

  private FlowRpcConfiguration(Builder builder) {
    this.receiveBufferSize = builder.receiveBufferSize;
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
     * Builds the configuration with the specified settings.
     *
     * @return A new FlowRpcConfiguration instance
     */
    public FlowRpcConfiguration build() {
      return new FlowRpcConfiguration(this);
    }
  }
}