# Phase 5: Deterministic Simulation Mode - Implementation Plan

## Overview

This document outlines the step-by-step implementation plan for adding deterministic simulation capabilities to JavaFlow. The implementation will be done incrementally to ensure each component is properly tested before moving to the next.

## Prerequisites

- Phase 1-4 completed (Core Futures, Scheduling, Timers, I/O & RPC)
- Understanding of current Random usage in the codebase
- Test framework setup (JUnit)

## Implementation Steps

### Step 1: Random Infrastructure (Week 1)

#### 1.1 Create Core Random Classes

**Files to create:**
- `src/main/java/io/github/panghy/javaflow/simulation/FlowRandom.java`
- `src/main/java/io/github/panghy/javaflow/simulation/RandomSource.java`
- `src/main/java/io/github/panghy/javaflow/simulation/DeterministicRandomSource.java`
- `src/main/java/io/github/panghy/javaflow/simulation/SimulationContext.java`

**Key implementations:**
```java
// FlowRandom.java
public final class FlowRandom {
    private static final ThreadLocal<RandomSource> randomSource = new ThreadLocal<>();
    
    public static void initialize(RandomSource source) {
        randomSource.set(source);
    }
    
    public static Random current() {
        RandomSource source = randomSource.get();
        if (source == null) {
            // Fallback to system random for non-simulation mode
            source = new SystemRandomSource();
            randomSource.set(source);
        }
        return source.getRandom();
    }
    
    public static long getCurrentSeed() {
        RandomSource source = randomSource.get();
        return source != null ? source.getSeed() : 0;
    }
}
```

#### 1.2 Extract Existing Random Usage

**Files to modify:**
- `src/main/java/io/github/panghy/javaflow/SingleThreadedScheduler.java`
- `src/main/java/io/github/panghy/javaflow/io/network/SimulatedFlowTransport.java`
- `src/main/java/io/github/panghy/javaflow/io/file/SimulatedFlowFileSystem.java`

**Changes:**
- Replace all `new Random()` with `FlowRandom.current()`
- Replace all `Math.random()` with `FlowRandom.current().nextDouble()`
- Ensure thread-safety for random access

#### 1.3 Create Test Base Class

**Files to create:**
- `src/test/java/io/github/panghy/javaflow/test/AbstractFlowSimulationTest.java`
- `src/test/java/io/github/panghy/javaflow/test/RandomSeed.java` (annotation)
- `src/test/java/io/github/panghy/javaflow/test/FixedSeed.java` (annotation)

### Step 2: Scheduler Randomization (Week 1-2)

#### 2.1 Add Randomized Task Selection

**Files to modify:**
- `src/main/java/io/github/panghy/javaflow/SingleThreadedScheduler.java`

**New classes:**
- `src/main/java/io/github/panghy/javaflow/simulation/SimulationSchedulerConfig.java`

**Implementation:**
```java
// In SingleThreadedScheduler
private Task selectNextTask() {
    if (simulationConfig != null && 
        FlowRandom.current().nextDouble() < simulationConfig.getRandomSelectionProbability()) {
        // Random selection from ready tasks
        List<Task> readyTasks = collectReadyTasks();
        if (!readyTasks.isEmpty()) {
            int index = FlowRandom.current().nextInt(readyTasks.size());
            return readyTasks.get(index);
        }
    }
    // Fall back to priority-based selection
    return selectHighestPriorityTask();
}
```

#### 2.2 Add Random Delays

**Implementation:**
- Add configurable delays between task executions
- Use exponential distribution for realistic delay patterns
- Ensure delays don't affect correctness, only timing

### Step 3: Enhanced Network Simulation (Week 2)

#### 3.1 Network Fault Injection

**Files to modify:**
- `src/main/java/io/github/panghy/javaflow/io/network/SimulatedFlowTransport.java`
- `src/main/java/io/github/panghy/javaflow/io/network/SimulatedFlowConnection.java`

**New classes:**
- `src/main/java/io/github/panghy/javaflow/simulation/NetworkFaultInjector.java`
- `src/main/java/io/github/panghy/javaflow/simulation/NetworkSimulationConfig.java`

**Features to add:**
- Packet loss simulation
- Packet reordering
- Connection failures
- Bandwidth limitations
- Latency variations

#### 3.2 Deterministic Message Ordering

**Implementation:**
- Ensure message delivery order is deterministic given same seed
- Add message queuing with deterministic sorting
- Implement timeout variations

### Step 4: File System Simulation Enhancement (Week 2-3)

#### 4.1 I/O Fault Injection

**Files to modify:**
- `src/main/java/io/github/panghy/javaflow/io/file/SimulatedFlowFileSystem.java`
- `src/main/java/io/github/panghy/javaflow/io/file/SimulatedFlowFile.java`

**Features to add:**
- Random I/O delays
- Disk full errors
- Read/write failures
- File corruption simulation

### Step 5: Fault Injection Framework (Week 3)

#### 5.1 BUGGIFY Implementation

**Files to create:**
- `src/main/java/io/github/panghy/javaflow/simulation/Buggify.java`
- `src/main/java/io/github/panghy/javaflow/simulation/BugRegistry.java`
- `src/main/java/io/github/panghy/javaflow/simulation/BugConfiguration.java`

**Implementation:**
```java
public class Buggify {
    public static boolean isEnabled(String bugId) {
        if (!Flow.isSimulated()) {
            return false;
        }
        return BugRegistry.getInstance().shouldInject(bugId);
    }
    
    public static boolean sometimes(double probability) {
        if (!Flow.isSimulated()) {
            return false;
        }
        return FlowRandom.current().nextDouble() < probability;
    }
}
```

#### 5.2 Common Injection Points

**Locations to add BUGGIFY:**
- Network send/receive operations
- File I/O operations
- Timer operations
- Task scheduling
- RPC calls

### Step 6: Test Framework Integration (Week 3-4)

#### 6.1 JUnit Integration

**Files to create:**
- `src/test/java/io/github/panghy/javaflow/test/FlowTestRunner.java`
- `src/test/java/io/github/panghy/javaflow/test/SimulationMode.java` (annotation)
- `src/test/java/io/github/panghy/javaflow/test/ReproduceFailure.java` (annotation)

**Features:**
- Custom JUnit runner for simulation tests
- Automatic seed management
- Failure reproduction support
- Test result reporting with seeds

#### 6.2 Test Utilities

**Files to create:**
- `src/test/java/io/github/panghy/javaflow/test/SimulationTestUtils.java`
- `src/test/java/io/github/panghy/javaflow/test/ChaosTestScenarios.java`

### Step 7: Simulation Control API (Week 4)

#### 7.1 Simulation Configuration

**Files to create:**
- `src/main/java/io/github/panghy/javaflow/simulation/SimulationConfig.java`
- `src/main/java/io/github/panghy/javaflow/simulation/SimulationController.java`
- `src/main/java/io/github/panghy/javaflow/simulation/SimulationMode.java` (enum)

#### 7.2 Runtime Control

**Features:**
- Start/stop simulation
- Advance simulation time
- Query simulation state
- Collect simulation metrics

### Step 8: Testing and Validation (Week 4-5)

#### 8.1 Determinism Tests

**Tests to create:**
- Run same test with same seed multiple times, verify identical results
- Test all random sources are properly controlled
- Verify no hidden sources of non-determinism

#### 8.2 Chaos Tests

**Tests to create:**
- Network partition scenarios
- Node failure scenarios
- High latency scenarios
- Resource exhaustion scenarios

#### 8.3 Performance Tests

**Measurements:**
- Overhead of simulation mode
- Impact of random scheduling
- Memory usage in simulation

### Step 9: Documentation and Examples (Week 5)

#### 9.1 User Documentation

**Documents to create:**
- Simulation mode user guide
- Fault injection guide
- Test writing best practices
- Troubleshooting guide

#### 9.2 Example Tests

**Examples to create:**
- Simple simulation test
- Chaos test with fault injection
- Reproducing a failure
- Custom fault injection

## Testing Strategy

### Unit Tests
- Test each random source independently
- Test fault injection probability
- Test deterministic ordering

### Integration Tests
- Test full simulation scenarios
- Test interaction between components
- Test seed reproduction

### Chaos Tests
- Run extended chaos scenarios
- Verify system resilience
- Find edge cases

## Success Criteria

1. **Determinism**: Same seed produces identical execution
2. **Coverage**: All sources of randomness controlled
3. **Usability**: Easy to write simulation tests
4. **Performance**: Acceptable overhead in simulation mode
5. **Debugging**: Failed tests can be reproduced exactly

## Risks and Mitigations

### Risk: Hidden Non-determinism
**Mitigation**: Comprehensive testing, code review for Random usage

### Risk: Performance Overhead
**Mitigation**: Optimize hot paths, allow disabling features

### Risk: Test Flakiness
**Mitigation**: Careful seed management, comprehensive logging

### Risk: Maintenance Burden
**Mitigation**: Good abstractions, minimal invasion of production code