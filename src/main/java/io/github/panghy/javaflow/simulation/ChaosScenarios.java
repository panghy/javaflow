package io.github.panghy.javaflow.simulation;

/**
 * Predefined chaos testing scenarios for JavaFlow simulations.
 * 
 * <p>This class provides methods to configure common fault injection scenarios
 * that can be used to test system resilience. Each scenario registers a set of
 * related bugs with appropriate probabilities to simulate different failure modes.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Test network reliability
 * ChaosScenarios.networkChaos();
 * 
 * // Test storage reliability
 * ChaosScenarios.storageChaos();
 * 
 * // Full chaos testing
 * ChaosScenarios.fullChaos();
 * 
 * // Test specific failure modes
 * ChaosScenarios.splitBrainScenario();
 * }</pre>
 */
public final class ChaosScenarios {
  
  private ChaosScenarios() {
    // Prevent instantiation
  }
  
  /**
   * Configures network chaos testing scenario.
   * 
   * <p>This scenario simulates various network failures including:
   * <ul>
   *   <li>5% packet loss</li>
   *   <li>3% packet reordering</li>
   *   <li>2% connection timeouts</li>
   *   <li>1% network partitions</li>
   *   <li>1% connection resets</li>
   * </ul>
   */
  public static void networkChaos() {
    BugRegistry.getInstance()
        .register(BugIds.PACKET_LOSS, 0.05)
        .register(BugIds.PACKET_REORDER, 0.03)
        .register(BugIds.CONNECTION_TIMEOUT, 0.02)
        .register(BugIds.NETWORK_PARTITION, 0.01)
        .register(BugIds.CONNECTION_RESET, 0.01);
  }
  
  /**
   * Configures storage chaos testing scenario.
   * 
   * <p>This scenario simulates various storage failures including:
   * <ul>
   *   <li>10% slow disk operations</li>
   *   <li>2% write failures</li>
   *   <li>1% read failures</li>
   *   <li>0.5% disk full errors</li>
   *   <li>0.1% data corruption</li>
   * </ul>
   */
  public static void storageChaos() {
    BugRegistry.getInstance()
        .register(BugIds.DISK_SLOW, 0.10)
        .register(BugIds.WRITE_FAILURE, 0.02)
        .register(BugIds.READ_FAILURE, 0.01)
        .register(BugIds.DISK_FULL, 0.005)
        .register(BugIds.DATA_CORRUPTION, 0.001);
  }
  
  /**
   * Configures process chaos testing scenario.
   * 
   * <p>This scenario simulates various process-level failures including:
   * <ul>
   *   <li>5% memory pressure</li>
   *   <li>3% CPU starvation</li>
   *   <li>1% process crashes</li>
   *   <li>0.5% process hangs</li>
   *   <li>2% clock skew</li>
   * </ul>
   */
  public static void processChaos() {
    BugRegistry.getInstance()
        .register(BugIds.MEMORY_PRESSURE, 0.05)
        .register(BugIds.CPU_STARVATION, 0.03)
        .register(BugIds.PROCESS_CRASH, 0.01)
        .register(BugIds.PROCESS_HANG, 0.005)
        .register(BugIds.CLOCK_SKEW, 0.02);
  }
  
  /**
   * Configures scheduling chaos testing scenario.
   * 
   * <p>This scenario simulates various scheduling anomalies including:
   * <ul>
   *   <li>20% task delays</li>
   *   <li>5% priority inversions</li>
   *   <li>3% unfair scheduling</li>
   *   <li>1% task starvation</li>
   * </ul>
   */
  public static void schedulingChaos() {
    BugRegistry.getInstance()
        .register(BugIds.TASK_DELAY, 0.20)
        .register(BugIds.PRIORITY_INVERSION, 0.05)
        .register(BugIds.UNFAIR_SCHEDULING, 0.03)
        .register(BugIds.TASK_STARVATION, 0.01);
  }
  
  /**
   * Configures full chaos testing scenario.
   * 
   * <p>This combines all chaos scenarios for comprehensive testing.
   * Use with caution as this creates a very hostile environment.
   */
  public static void fullChaos() {
    networkChaos();
    storageChaos();
    processChaos();
    schedulingChaos();
  }
  
  /**
   * Configures a split-brain scenario.
   * 
   * <p>This scenario creates conditions that can lead to split-brain
   * situations in distributed systems:
   * <ul>
   *   <li>50% network partitions</li>
   *   <li>30% clock skew</li>
   *   <li>10% connection timeouts</li>
   * </ul>
   */
  public static void splitBrainScenario() {
    BugRegistry.getInstance()
        .register(BugIds.NETWORK_PARTITION, 0.50)
        .register(BugIds.CLOCK_SKEW, 0.30)
        .register(BugIds.CONNECTION_TIMEOUT, 0.10);
  }
  
  /**
   * Configures a cascading failure scenario.
   * 
   * <p>This scenario creates conditions that can lead to cascading
   * failures across the system:
   * <ul>
   *   <li>70% memory pressure</li>
   *   <li>50% connection timeouts</li>
   *   <li>30% process crashes</li>
   *   <li>20% CPU starvation</li>
   * </ul>
   */
  public static void cascadingFailureScenario() {
    BugRegistry.getInstance()
        .register(BugIds.MEMORY_PRESSURE, 0.70)
        .register(BugIds.CONNECTION_TIMEOUT, 0.50)
        .register(BugIds.PROCESS_CRASH, 0.30)
        .register(BugIds.CPU_STARVATION, 0.20);
  }
  
  /**
   * Configures a data corruption scenario.
   * 
   * <p>This scenario focuses on data integrity issues:
   * <ul>
   *   <li>10% data corruption</li>
   *   <li>5% partial writes</li>
   *   <li>5% write failures</li>
   *   <li>2% disk full errors</li>
   * </ul>
   */
  public static void dataCorruptionScenario() {
    BugRegistry.getInstance()
        .register(BugIds.DATA_CORRUPTION, 0.10)
        .register(BugIds.PARTIAL_WRITE, 0.05)
        .register(BugIds.WRITE_FAILURE, 0.05)
        .register(BugIds.DISK_FULL, 0.02);
  }
  
  /**
   * Configures a latency-sensitive scenario.
   * 
   * <p>This scenario tests behavior under high latency conditions:
   * <ul>
   *   <li>30% network slowness</li>
   *   <li>20% disk slowness</li>
   *   <li>40% task delays</li>
   *   <li>10% packet reordering</li>
   * </ul>
   */
  public static void highLatencyScenario() {
    BugRegistry.getInstance()
        .register(BugIds.NETWORK_SLOW, 0.30)
        .register(BugIds.DISK_SLOW, 0.20)
        .register(BugIds.TASK_DELAY, 0.40)
        .register(BugIds.PACKET_REORDER, 0.10);
  }
  
  /**
   * Configures a resource constraint scenario.
   * 
   * <p>This scenario simulates resource-constrained environments:
   * <ul>
   *   <li>80% low resources flag</li>
   *   <li>50% memory pressure</li>
   *   <li>30% thread exhaustion</li>
   *   <li>20% disk full errors</li>
   * </ul>
   */
  public static void resourceConstraintScenario() {
    BugRegistry.getInstance()
        .register(BugIds.LOW_RESOURCES, 0.80)
        .register(BugIds.MEMORY_PRESSURE, 0.50)
        .register(BugIds.THREAD_EXHAUSTION, 0.30)
        .register(BugIds.DISK_FULL, 0.20);
  }
  
  /**
   * Clears all registered bugs.
   * 
   * <p>Use this to reset the bug registry to a clean state.
   */
  public static void clearAll() {
    BugRegistry.getInstance().clear();
  }
}