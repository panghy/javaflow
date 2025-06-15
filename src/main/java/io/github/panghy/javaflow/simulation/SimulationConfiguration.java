package io.github.panghy.javaflow.simulation;

import io.github.panghy.javaflow.io.network.NetworkSimulationParameters;

/**
 * Unified configuration for all simulation parameters in JavaFlow.
 * This class consolidates network, scheduler, file system, and fault injection settings
 * into a single configuration object.
 * 
 * <p>SimulationConfiguration supports different simulation profiles:</p>
 * <ul>
 *   <li><b>DETERMINISTIC</b>: Fixed behavior for regression testing</li>
 *   <li><b>CHAOS</b>: Randomized behavior for finding bugs</li>
 *   <li><b>STRESS</b>: High fault rates for resilience testing</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create configuration for chaos testing
 * SimulationConfiguration config = SimulationConfiguration.chaos()
 *     .setTaskSelectionProbability(0.8)
 *     .setNetworkErrorProbability(0.05)
 *     .setDiskFailureProbability(0.01);
 * 
 * // Apply configuration to simulation context
 * SimulationContext context = new SimulationContext(seed, config);
 * }</pre>
 */
public class SimulationConfiguration {
  
  /**
   * Simulation mode that determines the overall behavior profile.
   */
  public enum Mode {
    /**
     * Deterministic mode with fixed behavior for regression testing.
     * No randomization in task scheduling or timing.
     */
    DETERMINISTIC,
    
    /**
     * Chaos mode with controlled randomization for bug discovery.
     * Moderate fault injection and scheduling randomization.
     */
    CHAOS,
    
    /**
     * Stress mode with aggressive fault injection for resilience testing.
     * High error rates and maximum randomization.
     */
    STRESS
  }
  
  // Scheduler configuration
  private double taskSelectionProbability = 0.0;  // Probability of random task selection
  private double interTaskDelayMs = 0.0;          // Delay between task executions
  private boolean priorityRandomization = false;   // Whether to randomize priorities
  private boolean taskExecutionLogging = false;    // Whether to log task execution order
  
  // Network configuration
  private double networkConnectDelay = 0.01;       // Base connection delay (seconds)
  private double networkSendDelay = 0.001;         // Base send delay (seconds)
  private double networkReceiveDelay = 0.001;      // Base receive delay (seconds)
  private double networkBytesPerSecond = 10_000_000; // Network throughput (10MB/s)
  private double networkErrorProbability = 0.0;    // Network operation error probability
  private double networkDisconnectProbability = 0.0; // Random disconnect probability
  private double packetLossProbability = 0.0;      // Packet loss probability
  private double packetReorderProbability = 0.0;   // Packet reorder probability
  
  // File system configuration
  private double diskReadDelay = 0.001;            // Base disk read delay (seconds)
  private double diskWriteDelay = 0.005;           // Base disk write delay (seconds)
  private double diskBytesPerSecond = 100_000_000; // Disk throughput (100MB/s)
  private double diskFailureProbability = 0.0;     // Disk operation failure probability
  private double diskFullProbability = 0.0;        // Disk full error probability
  private double diskCorruptionProbability = 0.0;  // Data corruption probability
  
  // General fault injection
  private double clockSkewMs = 0.0;                // Maximum clock skew in milliseconds
  private double memoryPressureProbability = 0.0;  // Memory pressure simulation
  private double processCrashProbability = 0.0;    // Process crash probability
  
  // Mode
  private Mode mode = Mode.DETERMINISTIC;
  
  /**
   * Creates a default configuration with deterministic behavior.
   */
  public SimulationConfiguration() {
    // Use deterministic defaults
  }
  
  /**
   * Creates a configuration for deterministic testing.
   * No randomization or fault injection.
   *
   * @return A deterministic configuration
   */
  public static SimulationConfiguration deterministic() {
    return new SimulationConfiguration()
        .setMode(Mode.DETERMINISTIC);
  }
  
  /**
   * Creates a configuration for chaos testing.
   * Moderate randomization and fault injection.
   *
   * @return A chaos configuration
   */
  public static SimulationConfiguration chaos() {
    return new SimulationConfiguration()
        .setMode(Mode.CHAOS)
        .setTaskSelectionProbability(0.5)
        .setInterTaskDelayMs(1.0)
        .setPriorityRandomization(true)
        .setNetworkErrorProbability(0.01)
        .setNetworkDisconnectProbability(0.001)
        .setPacketLossProbability(0.005)
        .setPacketReorderProbability(0.01)
        .setDiskFailureProbability(0.001)
        .setDiskCorruptionProbability(0.0001)
        .setClockSkewMs(100)
        .setMemoryPressureProbability(0.01);
  }
  
  /**
   * Creates a configuration for stress testing.
   * High fault injection rates and maximum randomization.
   *
   * @return A stress configuration
   */
  public static SimulationConfiguration stress() {
    return new SimulationConfiguration()
        .setMode(Mode.STRESS)
        .setTaskSelectionProbability(0.9)
        .setInterTaskDelayMs(10.0)
        .setPriorityRandomization(true)
        .setTaskExecutionLogging(true)
        .setNetworkErrorProbability(0.1)
        .setNetworkDisconnectProbability(0.05)
        .setPacketLossProbability(0.1)
        .setPacketReorderProbability(0.2)
        .setDiskFailureProbability(0.05)
        .setDiskFullProbability(0.01)
        .setDiskCorruptionProbability(0.01)
        .setClockSkewMs(1000)
        .setMemoryPressureProbability(0.1)
        .setProcessCrashProbability(0.001);
  }
  
  // Getters and setters with fluent interface
  
  /**
   * Gets the current simulation mode.
   *
   * @return The simulation mode (DETERMINISTIC, CHAOS, or STRESS)
   */
  public Mode getMode() {
    return mode;
  }
  
  /**
   * Sets the simulation mode.
   *
   * @param mode The simulation mode to set
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setMode(Mode mode) {
    this.mode = mode;
    return this;
  }
  
  /**
   * Gets the probability of random task selection in the scheduler.
   * When non-zero, the scheduler may randomly select tasks instead of
   * always choosing the highest priority task.
   *
   * @return The task selection probability (0.0 to 1.0)
   */
  public double getTaskSelectionProbability() {
    return taskSelectionProbability;
  }
  
  /**
   * Sets the probability of random task selection in the scheduler.
   *
   * @param probability The probability (0.0 to 1.0) of selecting a random task
   *                    instead of the highest priority task
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setTaskSelectionProbability(double probability) {
    this.taskSelectionProbability = probability;
    return this;
  }
  
  /**
   * Gets the delay between task executions in milliseconds.
   * This simulates scheduling overhead or CPU contention.
   *
   * @return The inter-task delay in milliseconds
   */
  public double getInterTaskDelayMs() {
    return interTaskDelayMs;
  }
  
  /**
   * Sets the delay between task executions in milliseconds.
   *
   * @param delayMs The delay in milliseconds to add between task executions
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setInterTaskDelayMs(double delayMs) {
    this.interTaskDelayMs = delayMs;
    return this;
  }
  
  /**
   * Checks if priority randomization is enabled.
   * When enabled, task priorities may be randomly adjusted.
   *
   * @return true if priority randomization is enabled, false otherwise
   */
  public boolean isPriorityRandomization() {
    return priorityRandomization;
  }
  
  /**
   * Sets whether to enable priority randomization.
   *
   * @param randomize true to enable priority randomization, false to disable
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setPriorityRandomization(boolean randomize) {
    this.priorityRandomization = randomize;
    return this;
  }
  
  /**
   * Checks if task execution logging is enabled.
   * When enabled, the scheduler logs task selection decisions.
   *
   * @return true if task execution logging is enabled, false otherwise
   */
  public boolean isTaskExecutionLogging() {
    return taskExecutionLogging;
  }
  
  /**
   * Sets whether to enable task execution logging.
   *
   * @param log true to enable logging, false to disable
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setTaskExecutionLogging(boolean log) {
    this.taskExecutionLogging = log;
    return this;
  }
  
  /**
   * Gets the base network connection delay in seconds.
   * This simulates network latency for connection establishment.
   *
   * @return The connection delay in seconds
   */
  public double getNetworkConnectDelay() {
    return networkConnectDelay;
  }
  
  /**
   * Sets the base network connection delay in seconds.
   *
   * @param delay The connection delay in seconds
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setNetworkConnectDelay(double delay) {
    this.networkConnectDelay = delay;
    return this;
  }
  
  /**
   * Gets the base network send delay in seconds.
   * This simulates network latency for sending data.
   *
   * @return The send delay in seconds
   */
  public double getNetworkSendDelay() {
    return networkSendDelay;
  }
  
  /**
   * Sets the base network send delay in seconds.
   *
   * @param delay The send delay in seconds
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setNetworkSendDelay(double delay) {
    this.networkSendDelay = delay;
    return this;
  }
  
  /**
   * Gets the base network receive delay in seconds.
   * This simulates network latency for receiving data.
   *
   * @return The receive delay in seconds
   */
  public double getNetworkReceiveDelay() {
    return networkReceiveDelay;
  }
  
  /**
   * Sets the base network receive delay in seconds.
   *
   * @param delay The receive delay in seconds
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setNetworkReceiveDelay(double delay) {
    this.networkReceiveDelay = delay;
    return this;
  }
  
  /**
   * Gets the network throughput in bytes per second.
   * This simulates bandwidth limitations.
   *
   * @return The network throughput in bytes per second
   */
  public double getNetworkBytesPerSecond() {
    return networkBytesPerSecond;
  }
  
  /**
   * Sets the network throughput in bytes per second.
   *
   * @param bytesPerSecond The network throughput in bytes per second
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setNetworkBytesPerSecond(double bytesPerSecond) {
    this.networkBytesPerSecond = bytesPerSecond;
    return this;
  }
  
  /**
   * Gets the probability of network operation errors.
   * This simulates network failures and errors.
   *
   * @return The error probability (0.0 to 1.0)
   */
  public double getNetworkErrorProbability() {
    return networkErrorProbability;
  }
  
  /**
   * Sets the probability of network operation errors.
   *
   * @param probability The error probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setNetworkErrorProbability(double probability) {
    this.networkErrorProbability = probability;
    return this;
  }
  
  /**
   * Gets the probability of random network disconnections.
   * This simulates unexpected connection drops.
   *
   * @return The disconnect probability (0.0 to 1.0)
   */
  public double getNetworkDisconnectProbability() {
    return networkDisconnectProbability;
  }
  
  /**
   * Sets the probability of random network disconnections.
   *
   * @param probability The disconnect probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setNetworkDisconnectProbability(double probability) {
    this.networkDisconnectProbability = probability;
    return this;
  }
  
  /**
   * Gets the probability of packet loss.
   * This simulates unreliable network conditions.
   *
   * @return The packet loss probability (0.0 to 1.0)
   */
  public double getPacketLossProbability() {
    return packetLossProbability;
  }
  
  /**
   * Sets the probability of packet loss.
   *
   * @param probability The packet loss probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setPacketLossProbability(double probability) {
    this.packetLossProbability = probability;
    return this;
  }
  
  /**
   * Gets the probability of packet reordering.
   * This simulates out-of-order packet delivery.
   *
   * @return The packet reorder probability (0.0 to 1.0)
   */
  public double getPacketReorderProbability() {
    return packetReorderProbability;
  }
  
  /**
   * Sets the probability of packet reordering.
   *
   * @param probability The packet reorder probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setPacketReorderProbability(double probability) {
    this.packetReorderProbability = probability;
    return this;
  }
  
  /**
   * Gets the base disk read delay in seconds.
   * This simulates I/O latency for read operations.
   *
   * @return The disk read delay in seconds
   */
  public double getDiskReadDelay() {
    return diskReadDelay;
  }
  
  /**
   * Sets the base disk read delay in seconds.
   *
   * @param delay The disk read delay in seconds
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setDiskReadDelay(double delay) {
    this.diskReadDelay = delay;
    return this;
  }
  
  /**
   * Gets the base disk write delay in seconds.
   * This simulates I/O latency for write operations.
   *
   * @return The disk write delay in seconds
   */
  public double getDiskWriteDelay() {
    return diskWriteDelay;
  }
  
  /**
   * Sets the base disk write delay in seconds.
   *
   * @param delay The disk write delay in seconds
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setDiskWriteDelay(double delay) {
    this.diskWriteDelay = delay;
    return this;
  }
  
  /**
   * Gets the disk throughput in bytes per second.
   * This simulates disk bandwidth limitations.
   *
   * @return The disk throughput in bytes per second
   */
  public double getDiskBytesPerSecond() {
    return diskBytesPerSecond;
  }
  
  /**
   * Sets the disk throughput in bytes per second.
   *
   * @param bytesPerSecond The disk throughput in bytes per second
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setDiskBytesPerSecond(double bytesPerSecond) {
    this.diskBytesPerSecond = bytesPerSecond;
    return this;
  }
  
  /**
   * Gets the probability of disk operation failures.
   * This simulates I/O errors and disk failures.
   *
   * @return The disk failure probability (0.0 to 1.0)
   */
  public double getDiskFailureProbability() {
    return diskFailureProbability;
  }
  
  /**
   * Sets the probability of disk operation failures.
   *
   * @param probability The disk failure probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setDiskFailureProbability(double probability) {
    this.diskFailureProbability = probability;
    return this;
  }
  
  /**
   * Gets the probability of disk full errors.
   * This simulates running out of disk space.
   *
   * @return The disk full probability (0.0 to 1.0)
   */
  public double getDiskFullProbability() {
    return diskFullProbability;
  }
  
  /**
   * Sets the probability of disk full errors.
   *
   * @param probability The disk full probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setDiskFullProbability(double probability) {
    this.diskFullProbability = probability;
    return this;
  }
  
  /**
   * Gets the probability of data corruption.
   * This simulates data integrity issues.
   *
   * @return The data corruption probability (0.0 to 1.0)
   */
  public double getDiskCorruptionProbability() {
    return diskCorruptionProbability;
  }
  
  /**
   * Sets the probability of data corruption.
   *
   * @param probability The data corruption probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setDiskCorruptionProbability(double probability) {
    this.diskCorruptionProbability = probability;
    return this;
  }
  
  /**
   * Gets the maximum clock skew in milliseconds.
   * This simulates clock drift between processes.
   *
   * @return The maximum clock skew in milliseconds
   */
  public double getClockSkewMs() {
    return clockSkewMs;
  }
  
  /**
   * Sets the maximum clock skew in milliseconds.
   *
   * @param skewMs The maximum clock skew in milliseconds
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setClockSkewMs(double skewMs) {
    this.clockSkewMs = skewMs;
    return this;
  }
  
  /**
   * Gets the probability of memory pressure events.
   * This simulates low memory conditions.
   *
   * @return The memory pressure probability (0.0 to 1.0)
   */
  public double getMemoryPressureProbability() {
    return memoryPressureProbability;
  }
  
  /**
   * Sets the probability of memory pressure events.
   *
   * @param probability The memory pressure probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setMemoryPressureProbability(double probability) {
    this.memoryPressureProbability = probability;
    return this;
  }
  
  /**
   * Gets the probability of process crashes.
   * This simulates unexpected process termination.
   *
   * @return The process crash probability (0.0 to 1.0)
   */
  public double getProcessCrashProbability() {
    return processCrashProbability;
  }
  
  /**
   * Sets the probability of process crashes.
   *
   * @param probability The process crash probability (0.0 to 1.0)
   * @return This configuration instance for method chaining
   */
  public SimulationConfiguration setProcessCrashProbability(double probability) {
    this.processCrashProbability = probability;
    return this;
  }
  
  /**
   * Creates a NetworkSimulationParameters object from this configuration.
   * This provides backward compatibility with existing code.
   *
   * @return NetworkSimulationParameters with values from this configuration
   */
  public NetworkSimulationParameters toNetworkParameters() {
    return new NetworkSimulationParameters()
        .setConnectDelay(networkConnectDelay)
        .setSendDelay(networkSendDelay)
        .setReceiveDelay(networkReceiveDelay)
        .setSendBytesPerSecond(networkBytesPerSecond)
        .setReceiveBytesPerSecond(networkBytesPerSecond)
        .setConnectErrorProbability(networkErrorProbability)
        .setSendErrorProbability(networkErrorProbability)
        .setReceiveErrorProbability(networkErrorProbability)
        .setDisconnectProbability(networkDisconnectProbability);
  }
}