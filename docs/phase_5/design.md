# JavaFlow Phase 5: Deterministic Simulation Mode Design

## Overview

Phase 5 introduces deterministic simulation capabilities to JavaFlow, enabling reproducible testing of distributed systems. The core idea is to control all sources of non-determinism in the system through a single random seed, allowing perfect reproduction of test failures and systematic exploration of timing-related bugs.

## Goals

1. **Deterministic Execution**: Given the same seed, the system should produce identical execution traces
2. **Controlled Randomness**: All randomness in the system should flow from a single, controllable source
3. **Failure Reproduction**: When tests fail, capture the seed to enable exact reproduction
4. **Fault Injection**: Introduce controlled failures and delays to test system resilience
5. **Seamless Integration**: Simulation mode should work with existing JavaFlow code without modification

## Core Design Principles

### Single Source of Randomness

All randomness in the system must derive from a single `Random` instance that can be seeded deterministically:

```java
public class FlowRandom {
    private static ThreadLocal<Random> random = new ThreadLocal<>();
    
    public static void setSeed(long seed) {
        random.set(new Random(seed));
    }
    
    public static Random current() {
        Random r = random.get();
        if (r == null) {
            // In production, use truly random seed
            r = new Random();
            random.set(r);
        }
        return r;
    }
}
```

### Randomized Task Scheduling

While maintaining the priority-based scheduling system, we introduce controlled randomness to explore different interleavings:

```java
public class SimulationSchedulerConfig {
    // Probability of selecting a random task instead of highest priority
    private double randomSelectionProbability = 0.1;
    
    // Whether to add random delays between task executions
    private boolean randomDelays = true;
    
    // Maximum random delay in simulated milliseconds
    private long maxRandomDelayMs = 10;
}
```

### Deterministic Time

In simulation mode, time is completely controlled by the simulator:

```java
public class SimulatedClock implements FlowClock {
    private double currentTime = 0.0;
    private final TreeSet<ScheduledEvent> scheduledEvents;
    
    public void advanceToNextEvent() {
        ScheduledEvent next = scheduledEvents.pollFirst();
        if (next != null) {
            currentTime = next.time;
            next.task.run();
        }
    }
}
```

## Architecture

### Random Number Management

```java
public interface RandomSource {
    Random getRandom();
    long getSeed();
    void reset(long seed);
    RandomSource createChild(String name);  // For independent random streams
}

public class DeterministicRandomSource implements RandomSource {
    private final long initialSeed;
    private Random random;
    
    public DeterministicRandomSource(long seed) {
        this.initialSeed = seed;
        this.random = new Random(seed);
    }
    
    @Override
    public void reset(long seed) {
        this.random = new Random(seed);
    }
    
    @Override
    public RandomSource createChild(String name) {
        // Create deterministic child source
        long childSeed = initialSeed ^ name.hashCode();
        return new DeterministicRandomSource(childSeed);
    }
}
```

### Unified Simulation Configuration

All simulation parameters are grouped into a unified configuration structure that replaces the separate `SimulationParameters` and `NetworkSimulationParameters` classes. See [Unified Simulation Config](unified_simulation_config.md) for details.

### Integration Points

1. **Scheduler**: Modified to optionally select tasks randomly
2. **Network Layer**: Introduces random delays, packet loss, reordering
3. **File I/O**: Simulates random I/O delays and failures
4. **Clock**: Fully controlled time advancement
5. **Fault Injection**: BUGGIFY-style random failure injection

### Test Framework Integration

```java
public abstract class AbstractFlowTest {
    private Long fixedSeed = null;
    private boolean useRandomSeed = false;
    
    @Before
    public void setupSimulation() {
        long seed;
        if (fixedSeed != null) {
            seed = fixedSeed;
        } else if (useRandomSeed) {
            seed = System.currentTimeMillis();
            System.out.println("Test running with seed: " + seed);
        } else {
            seed = 0; // Default deterministic seed
        }
        
        FlowSimulation.initialize(seed);
    }
    
    @Test
    @RandomSeed // Custom annotation to enable random seeds
    public void testWithRandomSeed() {
        // Test code
    }
    
    @Test
    @FixedSeed(12345) // Run with specific seed
    public void testWithFixedSeed() {
        // Test code
    }
}
```

## Fault Injection Framework

JavaFlow implements a comprehensive fault injection system inspired by FoundationDB's BUGGIFY. The framework supports injection of various fault types including network failures, storage errors, process crashes, and timing variations.

### BUGGIFY-Style Injection

```java
public class BugRegistry {
    private final Map<String, BugConfiguration> bugs = new HashMap<>();
    
    public void register(String bugId, double probability) {
        bugs.put(bugId, new BugConfiguration(bugId, probability));
    }
    
    public boolean shouldInject(String bugId) {
        if (!FlowSimulation.isSimulated()) {
            return false;
        }
        
        BugConfiguration config = bugs.get(bugId);
        if (config == null || !config.enabled) {
            return false;
        }
        
        return FlowRandom.current().nextDouble() < config.probability;
    }
}

// Usage in code
if (Buggify.isEnabled("slow_disk_write")) {
    await(Flow.delay(FlowRandom.current().nextDouble() * 5.0));
}
```

### Comprehensive Fault Types

The fault injection system supports:

1. **Network Faults**: Packet loss, reordering, corruption, partitions, bandwidth limits
2. **Storage Faults**: I/O failures, data corruption, disk full, performance degradation
3. **Process Faults**: Crashes, hangs, resource exhaustion, clock skew
4. **Memory Faults**: Corruption, pressure, cache effects, GC pressure
5. **Timing Faults**: Task delays, priority inversions, unfair scheduling
6. **Byzantine Faults**: Protocol violations, malicious behavior

For a complete list of supported fault types and implementation details, see [Comprehensive Fault Injection Design](comprehensive_fault_injection.md).

## Simulation Modes

### 1. Deterministic Mode (Default)
- Fixed seed for perfect reproduction
- No random task selection
- Fixed network/disk delays
- Used for regression tests

### 2. Controlled Chaos Mode
- Random seed with logging
- Random task selection enabled
- Variable delays and fault injection
- Used for finding new bugs

### 3. Stress Test Mode
- High fault injection rates
- Aggressive random scheduling
- Used for resilience testing

## Implementation Phases

### Phase 5.1: Core Random Infrastructure
- Implement `FlowRandom` and `RandomSource`
- Extract all `java.util.Random` usage
- Add seed management to `AbstractFlowTest`

### Phase 5.2: Scheduler Randomization
- Add random task selection to `SingleThreadedScheduler`
- Implement configurable selection probability
- Add random delays between tasks

### Phase 5.3: Network Simulation Enhancement
- Add packet loss and reordering
- Implement random connection failures
- Add bandwidth/latency simulation

### Phase 5.4: Fault Injection Framework
- Implement BUGGIFY macro equivalent
- Create fault injection registry
- Add common injection points

### Phase 5.5: Testing and Validation
- Create simulation test suite
- Verify determinism with same seeds
- Add chaos testing scenarios

## API Examples

### Running Tests with Simulation

```java
@RunWith(FlowTestRunner.class)
public class DistributedSystemTest extends AbstractFlowTest {
    
    @Test
    @SimulationMode(
        randomTaskSelection = 0.2,
        packetLossProbability = 0.05,
        seed = RandomSeed.RANDOM
    )
    public void testUnderChaos() {
        // Test implementation
    }
    
    @Test
    @ReproduceFailure(seed = 1234567890L)
    public void reproduceSpecificFailure() {
        // This will run with the exact same conditions that caused a failure
    }
}
```

### Manual Simulation Control

```java
public void runSimulation() {
    FlowSimulation.start(new SimulationConfig()
        .withSeed(12345)
        .withRandomScheduling(0.1)
        .withNetworkFaults(true)
        .withDiskFaults(true)
    );
    
    try {
        // Run test scenario
        FlowFuture<Void> result = startActor(this::distributedOperation);
        
        // Advance simulation time
        FlowSimulation.runFor(Duration.ofMinutes(5));
        
        // Check result
        assertTrue(result.isDone());
    } finally {
        FlowSimulation.stop();
    }
}
```

## Benefits

1. **Reproducible Failures**: Any test failure can be exactly reproduced
2. **Systematic Testing**: Explore different execution orderings systematically
3. **Rare Bug Discovery**: Find bugs that only manifest under specific timings
4. **Confidence in Correctness**: Test system behavior under adverse conditions
5. **Debugging Aid**: Deterministic execution makes debugging much easier

## Integration with Existing Components

### Migrating Current Simulation Classes

Existing simulation components will be updated to use the centralized random source:

1. **SimulatedFlowFileSystem**: Replace `Math.random()` with `FlowRandom.current()`
2. **SimulatedFlowTransport**: Use unified configuration instead of `NetworkSimulationParameters`
3. **SimulatedFlowConnection**: Integrate with fault injection framework
4. **SimulatedClock**: Already deterministic, just needs seed management

### Production Code Separation

To ensure simulation code doesn't impact production performance:

```java
// Use static final flags for JIT optimization
if (FlowSimulation.IS_SIMULATED && Buggify.isEnabled("fault")) {
    // Fault injection code - completely eliminated in production by JIT
}
```

## Performance and Debugging

### Performance Considerations

1. **JIT Optimization**: Use static final flags for simulation checks
2. **Lazy Initialization**: Only create simulation objects when needed
3. **Minimal Overhead**: BUGGIFY checks should be first-level if statements
4. **Memory Usage**: Track and limit simulation metadata

### Logging and Debugging

```java
// Automatic seed logging
@Before
public void logTestSeed() {
    long seed = FlowRandom.getCurrentSeed();
    System.out.printf("[TEST] Running with seed: %d (use @FixedSeed(%d) to reproduce)\n", 
                      seed, seed);
}

// Determinism verification
@Test
public void verifyDeterminism() {
    long seed = 12345;
    String result1 = runWithSeed(seed);
    String result2 = runWithSeed(seed);
    assertEquals("Results must be identical with same seed", result1, result2);
}
```

## Test Coverage Guidelines

1. **Critical Paths**: 100% of critical paths should have BUGGIFY points
2. **I/O Operations**: Every I/O operation should be faultable
3. **Network Operations**: All network calls should support fault injection
4. **Resource Allocation**: Memory/thread allocation should be faultable
5. **Timing Sensitive Code**: Add delays and scheduling variations

## Considerations

1. **Performance**: Simulation mode will be slower than production mode
2. **Coverage**: Not all real-world failures can be simulated
3. **Maintenance**: Injection points need to be maintained as code evolves
4. **False Positives**: Over-aggressive fault injection might trigger unrealistic scenarios
5. **Determinism Leaks**: Watch for system calls that break determinism