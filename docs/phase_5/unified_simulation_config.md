# Unified Simulation Configuration Design

## Overview

This document proposes a unified simulation configuration structure that consolidates all simulation parameters into a cohesive hierarchy. This design replaces the current separate `SimulationParameters` and `NetworkSimulationParameters` classes with a unified approach.

## Current State

Currently, simulation parameters are split across multiple classes:
- `SimulationParameters` - File system simulation settings
- `NetworkSimulationParameters` - Network simulation settings
- No centralized random number management
- Direct use of `Math.random()` throughout the code

## Proposed Design

### Core Configuration Hierarchy

```java
public class UnifiedSimulationConfig {
    // Global simulation settings
    private long seed = System.currentTimeMillis();
    private boolean deterministic = true;
    private double timeAcceleration = 1.0;  // Speed up/slow down time
    
    // Subsystem configurations
    private final SchedulerSimulationConfig scheduler = new SchedulerSimulationConfig();
    private final FileSystemSimulationConfig fileSystem = new FileSystemSimulationConfig();
    private final NetworkSimulationConfig network = new NetworkSimulationConfig();
    private final FaultInjectionConfig faultInjection = new FaultInjectionConfig();
    
    // Random source management
    private transient RandomSource randomSource;
    
    public RandomSource getRandomSource() {
        if (randomSource == null) {
            randomSource = new DeterministicRandomSource(seed);
        }
        return randomSource;
    }
    
    // Builder pattern for easy configuration
    public static Builder builder() {
        return new Builder();
    }
}
```

### Scheduler Configuration

```java
public class SchedulerSimulationConfig {
    // Task scheduling randomness
    private double randomTaskSelectionProbability = 0.0;
    private double taskDelayProbability = 0.0;
    private double minTaskDelay = 0.0;
    private double maxTaskDelay = 0.01;
    
    // Priority manipulation
    private boolean enablePriorityInversion = false;
    private double priorityInversionProbability = 0.0;
}
```

### File System Configuration

```java
public class FileSystemSimulationConfig {
    // Operation delays
    private double readDelay = 0.001;      // 1ms
    private double writeDelay = 0.002;     // 2ms
    private double metadataDelay = 0.0005; // 0.5ms
    
    // Throughput limits
    private double readBytesPerSecond = 100_000_000;   // 100MB/s
    private double writeBytesPerSecond = 50_000_000;   // 50MB/s
    
    // Error injection
    private double readErrorProbability = 0.0;
    private double writeErrorProbability = 0.0;
    private double metadataErrorProbability = 0.0;
    
    // Advanced fault scenarios
    private double diskFullProbability = 0.0;
    private double fileCorruptionProbability = 0.0;
}
```

### Network Configuration

```java
public class NetworkSimulationConfig {
    // Connection settings
    private double connectDelay = 0.01;      // 10ms
    private double connectErrorProbability = 0.0;
    
    // Data transfer settings
    private double sendDelay = 0.001;        // 1ms
    private double receiveDelay = 0.001;     // 1ms
    private double sendBytesPerSecond = 10_000_000;    // 10MB/s
    private double receiveBytesPerSecond = 10_000_000; // 10MB/s
    
    // Error injection
    private double sendErrorProbability = 0.0;
    private double receiveErrorProbability = 0.0;
    private double disconnectProbability = 0.0;
    
    // Advanced network simulation
    private double packetLossProbability = 0.0;
    private double packetReorderProbability = 0.0;
    private double packetDuplicationProbability = 0.0;
    
    // Latency variation
    private boolean enableJitter = false;
    private double jitterRange = 0.0;  // ± milliseconds
}
```

### Fault Injection Configuration

```java
public class FaultInjectionConfig {
    private boolean enableBuggify = false;
    private final Map<String, BugConfig> bugs = new HashMap<>();
    
    public static class BugConfig {
        private final String id;
        private double probability;
        private boolean enabled = true;
        
        public BugConfig(String id, double probability) {
            this.id = id;
            this.probability = probability;
        }
    }
    
    // Predefined bug categories
    public FaultInjectionConfig withSlowTasks(double probability) {
        bugs.put("slow_task", new BugConfig("slow_task", probability));
        return this;
    }
    
    public FaultInjectionConfig withMemoryPressure(double probability) {
        bugs.put("memory_pressure", new BugConfig("memory_pressure", probability));
        return this;
    }
}
```

## Migration Strategy

### Step 1: Create Adapter Classes

```java
// Adapter to maintain backward compatibility
public class SimulationParameters {
    private final FileSystemSimulationConfig config;
    private final RandomSource randomSource;
    
    public SimulationParameters() {
        this(new FileSystemSimulationConfig(), 
             new SystemRandomSource());
    }
    
    // Wrap new config with old API
    public double getReadDelay() {
        return config.getReadDelay();
    }
    
    // Use centralized random source
    public boolean shouldInjectReadError() {
        return randomSource.getRandom().nextDouble() < config.getReadErrorProbability();
    }
}
```

### Step 2: Update Components Gradually

1. Replace `Math.random()` with `randomSource.getRandom().nextDouble()`
2. Pass `RandomSource` to simulation components
3. Consolidate configuration objects
4. Remove old parameter classes

## Usage Examples

### Basic Configuration

```java
// Simple deterministic configuration
UnifiedSimulationConfig config = UnifiedSimulationConfig.builder()
    .withSeed(12345)
    .build();
```

### Chaos Testing Configuration

```java
// High chaos configuration
UnifiedSimulationConfig config = UnifiedSimulationConfig.builder()
    .withSeed(System.currentTimeMillis())
    .withScheduler(scheduler -> scheduler
        .withRandomTaskSelection(0.2)
        .withTaskDelays(0.1, 0.001, 0.01))
    .withNetwork(network -> network
        .withPacketLoss(0.05)
        .withDisconnects(0.01)
        .withJitter(true, 5.0))
    .withFileSystem(fs -> fs
        .withReadErrors(0.01)
        .withWriteErrors(0.02))
    .withFaultInjection(faults -> faults
        .enableBuggify()
        .withSlowTasks(0.1)
        .withMemoryPressure(0.05))
    .build();
```

### Specific Scenario Configuration

```java
// Network partition scenario
UnifiedSimulationConfig config = UnifiedSimulationConfig.builder()
    .withNetwork(network -> network
        .withConnectErrors(0.5)  // 50% connection failures
        .withDisconnects(0.2))   // 20% random disconnects
    .build();
```

## Benefits

1. **Centralized Configuration**: All simulation settings in one place
2. **Consistent Random Source**: Single seed controls all randomness
3. **Hierarchical Organization**: Logical grouping of related settings
4. **Builder Pattern**: Easy-to-use fluent API
5. **Backward Compatibility**: Adapter classes ease migration
6. **Extensibility**: Easy to add new simulation parameters

## Implementation Phases

### Phase 1: Core Infrastructure
- Create `UnifiedSimulationConfig` and sub-configs
- Implement `RandomSource` abstraction
- Create adapters for existing parameter classes

### Phase 2: Random Source Integration
- Replace all `Math.random()` usage
- Thread `RandomSource` through components
- Add seed logging and management

### Phase 3: Configuration Migration
- Update all simulation components to use new config
- Deprecate old parameter classes
- Update tests to use unified configuration

### Phase 4: Advanced Features
- Add configuration presets (e.g., "slow network", "unreliable disk")
- Implement configuration validation
- Add runtime configuration changes support