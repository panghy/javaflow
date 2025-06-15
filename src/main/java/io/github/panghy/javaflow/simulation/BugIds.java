package io.github.panghy.javaflow.simulation;

/**
 * Predefined bug identifiers for common fault injection scenarios.
 * 
 * <p>This class provides standardized bug IDs that can be used with the BUGGIFY
 * framework. Using these predefined constants helps maintain consistency across
 * the codebase and makes it easier to configure fault injection scenarios.
 * 
 * <p>Example usage:
 * <pre>{@code
 * if (Buggify.isEnabled(BugIds.NETWORK_SLOW)) {
 *     await(Flow.delay(5.0));
 * }
 * 
 * if (Buggify.isEnabled(BugIds.DISK_FULL)) {
 *     throw new IOException("No space left on device");
 * }
 * }</pre>
 */
public final class BugIds {
  
  private BugIds() {
    // Prevent instantiation
  }
  
  // Network-related bugs
  
  /** Simulates slow network conditions with increased latency. */
  public static final String NETWORK_SLOW = "network_slow";
  
  /** Simulates network partitions between nodes. */
  public static final String NETWORK_PARTITION = "network_partition";
  
  /** Simulates packet loss in network communication. */
  public static final String PACKET_LOSS = "packet_loss";
  
  /** Simulates packet reordering in network communication. */
  public static final String PACKET_REORDER = "packet_reorder";
  
  /** Simulates packet duplication in network communication. */
  public static final String PACKET_DUPLICATE = "packet_duplicate";
  
  /** Simulates connection timeout failures. */
  public static final String CONNECTION_TIMEOUT = "connection_timeout";
  
  /** Simulates connection reset errors. */
  public static final String CONNECTION_RESET = "connection_reset";
  
  /** Simulates connection refused errors. */
  public static final String CONNECTION_REFUSED = "connection_refused";
  
  // Storage-related bugs
  
  /** Simulates slow disk I/O operations. */
  public static final String DISK_SLOW = "disk_slow";
  
  /** Simulates disk full errors. */
  public static final String DISK_FULL = "disk_full";
  
  /** Simulates disk read failures. */
  public static final String READ_FAILURE = "read_failure";
  
  /** Simulates disk write failures. */
  public static final String WRITE_FAILURE = "write_failure";
  
  /** Simulates disk sync/flush failures. */
  public static final String SYNC_FAILURE = "sync_failure";
  
  /** Simulates data corruption on disk. */
  public static final String DATA_CORRUPTION = "data_corruption";
  
  /** Simulates partial write scenarios. */
  public static final String PARTIAL_WRITE = "partial_write";
  
  // Process-related bugs
  
  /** Simulates process crashes. */
  public static final String PROCESS_CRASH = "process_crash";
  
  /** Simulates process hangs/freezes. */
  public static final String PROCESS_HANG = "process_hang";
  
  /** Simulates memory pressure conditions. */
  public static final String MEMORY_PRESSURE = "memory_pressure";
  
  /** Simulates CPU starvation. */
  public static final String CPU_STARVATION = "cpu_starvation";
  
  /** Simulates clock skew between processes. */
  public static final String CLOCK_SKEW = "clock_skew";
  
  /** Simulates thread pool exhaustion. */
  public static final String THREAD_EXHAUSTION = "thread_exhaustion";
  
  // Scheduling-related bugs
  
  /** Simulates random task delays. */
  public static final String TASK_DELAY = "task_delay";
  
  /** Simulates priority inversion scenarios. */
  public static final String PRIORITY_INVERSION = "priority_inversion";
  
  /** Simulates unfair task scheduling. */
  public static final String UNFAIR_SCHEDULING = "unfair_scheduling";
  
  /** Simulates task starvation. */
  public static final String TASK_STARVATION = "task_starvation";
  
  /** Simulates race condition windows. */
  public static final String RACE_CONDITION_WINDOW = "race_condition_window";
  
  // Application-specific bugs
  
  /** Simulates transaction conflicts in database operations. */
  public static final String TRANSACTION_CONFLICT = "transaction_conflict";
  
  /** Simulates delays in leader election. */
  public static final String LEADER_ELECTION_DELAY = "leader_election_delay";
  
  /** Simulates replication lag in distributed systems. */
  public static final String REPLICATION_LAG = "replication_lag";
  
  /** Simulates serialization/deserialization errors. */
  public static final String SERIALIZATION_ERROR = "serialization_error";
  
  /** Simulates protocol version mismatches. */
  public static final String VERSION_MISMATCH = "version_mismatch";
  
  // Configuration-related bugs
  
  /** Simulates configuration changes during runtime. */
  public static final String CONFIG_CHANGE = "config_change";
  
  /** Simulates invalid configuration values. */
  public static final String INVALID_CONFIG = "invalid_config";
  
  // Generic behavior modifiers
  
  /** Forces small batch sizes for operations. */
  public static final String SMALL_BATCHES = "small_batches";
  
  /** Forces large batch sizes for operations. */
  public static final String LARGE_BATCHES = "large_batches";
  
  /** Skips validation checks. */
  public static final String SKIP_VALIDATION = "skip_validation";
  
  /** Forces immediate operations instead of batched. */
  public static final String FORCE_IMMEDIATE = "force_immediate";
  
  /** Simulates resource constraints. */
  public static final String LOW_RESOURCES = "low_resources";
}