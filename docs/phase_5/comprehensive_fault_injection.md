# Comprehensive Fault Injection Design for JavaFlow

## Overview

Based on extensive research of FoundationDB's Flow framework, this document provides an exhaustive list of fault injection capabilities that JavaFlow should implement to match and extend the original Flow system's testing capabilities.

## Core BUGGIFY Philosophy

The BUGGIFY system in Flow operates on these principles:
1. **Cooperative Testing**: Code explicitly cooperates with the simulator by instrumenting failure points
2. **Deterministic Chaos**: Faults are injected randomly but reproducibly using a seeded PRNG
3. **Probability-Based**: Each BUGGIFY point has a configurable probability of activation
4. **Simulation-Only**: BUGGIFY only activates in simulation mode, never in production
5. **Time-Aware**: After 300 simulated seconds, the system shifts focus from chaos to recovery

## Comprehensive Fault Categories

### 1. Network Faults

```java
public class NetworkFaultInjection {
    // Packet-level faults
    private double packetLossProbability = 0.0;
    private double packetReorderProbability = 0.0;
    private double packetDuplicationProbability = 0.0;
    private double packetCorruptionProbability = 0.0;
    
    // Connection-level faults
    private double connectionFailureProbability = 0.0;
    private double connectionTimeoutProbability = 0.0;
    private double connectionResetProbability = 0.0;
    private double connectionRefusalProbability = 0.0;
    
    // Network partitions
    private boolean enableNetworkPartitions = false;
    private double partitionProbability = 0.0;
    private double healPartitionProbability = 0.0;
    
    // Bandwidth and latency
    private boolean enableBandwidthLimitation = false;
    private double minBandwidth = 1_000_000;  // 1MB/s
    private double maxBandwidth = 100_000_000; // 100MB/s
    
    private boolean enableLatencyVariation = false;
    private double baseLatency = 0.001;  // 1ms
    private double jitterRange = 0.005;  // ±5ms
    
    // Asymmetric failures
    private boolean enableAsymmetricFailures = false;
    private double asymmetricFailureProbability = 0.0;
    
    // DNS failures
    private double dnsResolutionFailureProbability = 0.0;
    private double dnsResolutionDelay = 0.0;
}
```

### 2. Disk/Storage Faults

```java
public class StorageFaultInjection {
    // I/O operation failures
    private double readFailureProbability = 0.0;
    private double writeFailureProbability = 0.0;
    private double syncFailureProbability = 0.0;
    private double metadataFailureProbability = 0.0;
    
    // Data corruption
    private double bitFlipProbability = 0.0;
    private double sectorCorruptionProbability = 0.0;
    private double checksumMismatchProbability = 0.0;
    
    // Storage space issues
    private double diskFullProbability = 0.0;
    private double quotaExceededProbability = 0.0;
    private boolean enableDynamicDiskFilling = false;
    
    // Performance degradation
    private boolean enableSlowDisk = false;
    private double slowDiskProbability = 0.0;
    private double diskDegradationFactor = 10.0;  // 10x slower
    
    // File system errors
    private double fileNotFoundProbability = 0.0;
    private double permissionDeniedProbability = 0.0;
    private double directoryNotEmptyProbability = 0.0;
    
    // Advanced storage faults
    private double partialWriteProbability = 0.0;  // Write only part of data
    private double reorderedWriteProbability = 0.0; // Reorder write operations
    private double lostWriteProbability = 0.0;     // Accept write but don't persist
}
```

### 3. Process/Machine Faults

```java
public class ProcessFaultInjection {
    // Process lifecycle
    private double processCrashProbability = 0.0;
    private double processHangProbability = 0.0;
    private double processRestartProbability = 0.0;
    
    // Resource exhaustion
    private double memoryExhaustionProbability = 0.0;
    private double cpuStarvationProbability = 0.0;
    private double threadExhaustionProbability = 0.0;
    private double fileDescriptorExhaustionProbability = 0.0;
    
    // Performance variations
    private boolean enableVariableCpuSpeed = false;
    private double minCpuSpeedFactor = 0.1;  // 10% of normal
    private double maxCpuSpeedFactor = 1.0;  // 100% of normal
    
    // Clock issues
    private double clockSkewProbability = 0.0;
    private double maxClockSkew = 60.0;  // seconds
    private double clockJumpProbability = 0.0;
    
    // Zombie processes
    private double zombieProcessProbability = 0.0;  // Process appears dead but isn't
    private double splitBrainProbability = 0.0;     // Multiple instances think they're primary
}
```

### 4. Memory Faults

```java
public class MemoryFaultInjection {
    // Memory corruption
    private double memoryCorruptionProbability = 0.0;
    private double bufferOverflowProbability = 0.0;
    private double useAfterFreeProbability = 0.0;
    
    // Memory pressure
    private boolean enableMemoryPressure = false;
    private double availableMemoryRatio = 1.0;  // 0.1 = only 10% memory available
    
    // Cache effects
    private double cacheMissProbability = 0.0;
    private double cacheInvalidationDelay = 0.0;
    
    // Garbage collection pressure (for JVM)
    private boolean enableGcPressure = false;
    private double forcedGcProbability = 0.0;
    private double gcPauseDuration = 0.0;
}
```

### 5. Timing and Scheduling Faults

```java
public class SchedulingFaultInjection {
    // Task scheduling
    private double taskDelayProbability = 0.0;
    private double minTaskDelay = 0.0;
    private double maxTaskDelay = 1.0;
    
    // Priority inversions
    private double priorityInversionProbability = 0.0;
    private double priorityBoostProbability = 0.0;
    
    // Scheduling fairness
    private double unfairSchedulingProbability = 0.0;
    private double taskStarvationProbability = 0.0;
    
    // Timer precision
    private double timerInaccuracyProbability = 0.0;
    private double timerJitterRange = 0.0;
    
    // Concurrency issues
    private double raceConditionWindowProbability = 0.0;
    private double lockContentionProbability = 0.0;
}
```

### 6. Configuration and State Faults

```java
public class ConfigurationFaultInjection {
    // Configuration changes
    private double configChangesProbability = 0.0;
    private double invalidConfigProbability = 0.0;
    
    // State corruption
    private double stateCorruptionProbability = 0.0;
    private double stateRollbackProbability = 0.0;
    
    // Version mismatches
    private double versionMismatchProbability = 0.0;
    private double protocolIncompatibilityProbability = 0.0;
}
```

### 7. Byzantine Faults

```java
public class ByzantineFaultInjection {
    // Data corruption
    private double serializationCorruptionProbability = 0.0;
    private double deserializationErrorProbability = 0.0;
    
    // Protocol violations
    private double protocolViolationProbability = 0.0;
    private double invalidMessageProbability = 0.0;
    
    // Malicious behavior
    private double duplicateMessageProbability = 0.0;
    private double outOfOrderMessageProbability = 0.0;
    private double contradictoryMessageProbability = 0.0;
}
```

## BUGGIFY Implementation

### Core BUGGIFY API

```java
public final class Buggify {
    // Basic BUGGIFY
    public static boolean isEnabled(String bugId) {
        if (!FlowSimulation.isSimulated()) {
            return false;
        }
        return BugRegistry.getInstance().shouldInject(bugId);
    }
    
    // Probability-based BUGGIFY
    public static boolean sometimes(double probability) {
        if (!FlowSimulation.isSimulated()) {
            return false;
        }
        return FlowRandom.current().nextDouble() < probability;
    }
    
    // Time-aware BUGGIFY (reduces chaos after 300s)
    public static boolean isEnabledWithRecovery(String bugId) {
        if (!FlowSimulation.isSimulated()) {
            return false;
        }
        if (FlowSimulation.getCurrentTime() > 300.0) {
            // Reduce probability after 300 seconds to allow recovery
            return sometimes(0.01);  // 1% instead of normal probability
        }
        return isEnabled(bugId);
    }
    
    // Conditional BUGGIFY
    public static boolean isEnabledIf(String bugId, boolean condition) {
        return condition && isEnabled(bugId);
    }
}
```

### Common BUGGIFY Patterns

```java
// Pattern 1: Inject delays
if (Buggify.isEnabled("slow_operation")) {
    await(Flow.delay(Buggify.sometimes(0.5) ? 5.0 : 0.1));
}

// Pattern 2: Inject failures
if (Buggify.isEnabled("operation_failure")) {
    throw new SimulatedFailureException("Injected failure");
}

// Pattern 3: Corrupt data
if (Buggify.isEnabled("data_corruption")) {
    data[FlowRandom.current().nextInt(data.length)] ^= 1 << FlowRandom.current().nextInt(8);
}

// Pattern 4: Skip operations
if (!Buggify.isEnabled("skip_validation")) {
    validateData(data);
}

// Pattern 5: Change behavior
int batchSize = Buggify.isEnabled("small_batch") ? 1 : 1000;

// Pattern 6: Inject resource constraints
int maxConnections = Buggify.isEnabled("low_resources") ? 2 : 100;
```

## Predefined Bug IDs

```java
public class BugIds {
    // Network bugs
    public static final String NETWORK_SLOW = "network_slow";
    public static final String NETWORK_PARTITION = "network_partition";
    public static final String PACKET_LOSS = "packet_loss";
    public static final String CONNECTION_TIMEOUT = "connection_timeout";
    
    // Storage bugs
    public static final String DISK_SLOW = "disk_slow";
    public static final String DISK_FULL = "disk_full";
    public static final String WRITE_FAILURE = "write_failure";
    public static final String DATA_CORRUPTION = "data_corruption";
    
    // Process bugs
    public static final String PROCESS_CRASH = "process_crash";
    public static final String MEMORY_PRESSURE = "memory_pressure";
    public static final String CPU_STARVATION = "cpu_starvation";
    public static final String CLOCK_SKEW = "clock_skew";
    
    // Scheduling bugs
    public static final String TASK_DELAY = "task_delay";
    public static final String PRIORITY_INVERSION = "priority_inversion";
    public static final String UNFAIR_SCHEDULING = "unfair_scheduling";
    
    // Application-specific bugs
    public static final String TRANSACTION_CONFLICT = "transaction_conflict";
    public static final String LEADER_ELECTION_DELAY = "leader_election_delay";
    public static final String REPLICATION_LAG = "replication_lag";
}
```

## Testing Scenarios

### 1. Chaos Monkey Scenarios
```java
public class ChaosScenarios {
    public static void networkChaos() {
        BugRegistry.getInstance()
            .register(BugIds.PACKET_LOSS, 0.05)
            .register(BugIds.CONNECTION_TIMEOUT, 0.02)
            .register(BugIds.NETWORK_PARTITION, 0.01);
    }
    
    public static void storageChaos() {
        BugRegistry.getInstance()
            .register(BugIds.DISK_SLOW, 0.1)
            .register(BugIds.WRITE_FAILURE, 0.02)
            .register(BugIds.DATA_CORRUPTION, 0.001);
    }
    
    public static void fullChaos() {
        networkChaos();
        storageChaos();
        BugRegistry.getInstance()
            .register(BugIds.PROCESS_CRASH, 0.01)
            .register(BugIds.MEMORY_PRESSURE, 0.05)
            .register(BugIds.TASK_DELAY, 0.2);
    }
}
```

### 2. Specific Failure Scenarios
```java
public class FailureScenarios {
    // Split brain scenario
    public static void splitBrainScenario() {
        BugRegistry.getInstance()
            .register(BugIds.NETWORK_PARTITION, 0.5)
            .register(BugIds.CLOCK_SKEW, 0.3);
    }
    
    // Cascading failure scenario
    public static void cascadingFailureScenario() {
        BugRegistry.getInstance()
            .register(BugIds.MEMORY_PRESSURE, 0.7)
            .register(BugIds.PROCESS_CRASH, 0.3)
            .register(BugIds.CONNECTION_TIMEOUT, 0.5);
    }
    
    // Data corruption scenario
    public static void dataCorruptionScenario() {
        BugRegistry.getInstance()
            .register(BugIds.DATA_CORRUPTION, 0.1)
            .register(BugIds.WRITE_FAILURE, 0.05)
            .register(BugIds.DISK_FULL, 0.02);
    }
}
```

## Implementation Guidelines

1. **Instrumentation Points**: Place BUGGIFY calls at critical points in the code
2. **Probability Tuning**: Start with low probabilities (0.01-0.05) and increase as needed
3. **Recovery Testing**: Use time-aware BUGGIFY to test both chaos and recovery
4. **Determinism**: Always use FlowRandom for any random decisions
5. **Documentation**: Document each BUGGIFY point with its purpose and expected behavior
6. **Performance**: BUGGIFY checks should be cheap when disabled (simulation mode check first)

## Benefits

This comprehensive fault injection framework enables:
- Testing of rare edge cases that are nearly impossible to reproduce naturally
- Validation of system behavior under extreme conditions
- Discovery of bugs before they occur in production
- Confidence in system resilience and recovery mechanisms
- Deterministic reproduction of complex failure scenarios