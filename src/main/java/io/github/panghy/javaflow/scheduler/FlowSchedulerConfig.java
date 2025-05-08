package io.github.panghy.javaflow.scheduler;

/**
 * Configuration options for the {@link FlowScheduler}.
 */
public class FlowSchedulerConfig {
  
  /** The default number of carrier threads to use (1 for true single-threading). */
  private static final int DEFAULT_CARRIER_THREAD_COUNT = 1;
  
  /** The number of carrier threads to use. */
  private final int carrierThreadCount;
  
  /** Whether to enforce strict priority ordering. */
  private final boolean enforcePriorities;
  
  /** Whether to enable debug logging. */
  private final boolean debugLogging;
  
  /** The default scheduler configuration. */
  public static final FlowSchedulerConfig DEFAULT = new FlowSchedulerConfig(
      DEFAULT_CARRIER_THREAD_COUNT, true, true);
  
  /** A configuration that uses true single-threading. */
  public static final FlowSchedulerConfig SINGLE_THREADED = new FlowSchedulerConfig(
      1, true, false);
  
  /**
   * Creates a new scheduler configuration.
   *
   * @param carrierThreadCount The number of carrier threads to use
   * @param enforcePriorities Whether to enforce strict priority ordering
   * @param debugLogging Whether to enable debug logging
   */
  public FlowSchedulerConfig(int carrierThreadCount, boolean enforcePriorities, 
      boolean debugLogging) {
    if (carrierThreadCount < 1) {
      throw new IllegalArgumentException("Carrier thread count must be at least 1");
    }
    this.carrierThreadCount = carrierThreadCount;
    this.enforcePriorities = enforcePriorities;
    this.debugLogging = debugLogging;
  }
  
  /**
   * Gets the number of carrier threads to use.
   *
   * @return The number of carrier threads
   */
  public int getCarrierThreadCount() {
    return carrierThreadCount;
  }
  
  /**
   * Gets whether to enforce strict priority ordering.
   *
   * @return True if priorities should be enforced, false otherwise
   */
  public boolean isEnforcePriorities() {
    return enforcePriorities;
  }
  
  /**
   * Gets whether debug logging is enabled.
   *
   * @return True if debug logging is enabled, false otherwise
   */
  public boolean isDebugLogging() {
    return debugLogging;
  }
  
  /**
   * Creates a new builder for scheduler configuration.
   *
   * @return A new builder
   */
  public static Builder builder() {
    return new Builder();
  }
  
  /**
   * Builder for scheduler configuration.
   */
  public static class Builder {
    private int carrierThreadCount = DEFAULT_CARRIER_THREAD_COUNT;
    private boolean enforcePriorities = true;
    private boolean debugLogging = false;
    
    /**
     * Sets the number of carrier threads to use.
     *
     * @param carrierThreadCount The number of carrier threads
     * @return This builder
     */
    public Builder carrierThreadCount(int carrierThreadCount) {
      this.carrierThreadCount = carrierThreadCount;
      return this;
    }
    
    /**
     * Sets whether to enforce strict priority ordering.
     *
     * @param enforcePriorities True if priorities should be enforced
     * @return This builder
     */
    public Builder enforcePriorities(boolean enforcePriorities) {
      this.enforcePriorities = enforcePriorities;
      return this;
    }
    
    /**
     * Sets whether to enable debug logging.
     *
     * @param debugLogging True if debug logging should be enabled
     * @return This builder
     */
    public Builder debugLogging(boolean debugLogging) {
      this.debugLogging = debugLogging;
      return this;
    }
    
    /**
     * Builds a new scheduler configuration.
     *
     * @return A new configuration
     */
    public FlowSchedulerConfig build() {
      return new FlowSchedulerConfig(carrierThreadCount, enforcePriorities, debugLogging);
    }
  }
}