# Phase 5: Deterministic Simulation Mode - API Reference

## Core Classes

### FlowRandom

Central access point for all random number generation in JavaFlow.

```java
public final class FlowRandom {
    // Initialize the random source for the current thread
    public static void initialize(RandomSource source);
    
    // Get the current Random instance
    public static Random current();
    
    // Get the current seed value
    public static long getCurrentSeed();
    
    // Clear the random source for the current thread
    public static void clear();
}
```

### RandomSource

Interface for providing random number generators.

```java
public interface RandomSource {
    // Get the Random instance
    Random getRandom();
    
    // Get the seed used to initialize this source
    long getSeed();
    
    // Reset with a new seed
    void reset(long seed);
    
    // Create a child source for isolated randomness
    RandomSource createChild(String name);
}
```

### SimulationConfig

Configuration for simulation mode behavior.

```java
public class SimulationConfig {
    private long seed;
    private double randomTaskSelectionProbability = 0.0;
    private boolean enableNetworkFaults = false;
    private boolean enableDiskFaults = false;
    private boolean enableBuggify = false;
    private Map<String, Double> buggifyProbabilities = new HashMap<>();
    
    // Builder pattern
    public static class Builder {
        public Builder withSeed(long seed);
        public Builder withRandomTaskSelection(double probability);
        public Builder withNetworkFaults(boolean enable);
        public Builder withDiskFaults(boolean enable);
        public Builder withBuggify(String bugId, double probability);
        public SimulationConfig build();
    }
}
```

## Test Annotations

### @RandomSeed

Indicates that a test should run with a random seed.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RandomSeed {
    // Number of times to run with different random seeds
    int iterations() default 1;
}
```

### @FixedSeed

Indicates that a test should run with a specific seed.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FixedSeed {
    long value();
}
```

### @SimulationMode

Configures simulation parameters for a test.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SimulationMode {
    double randomTaskSelection() default 0.0;
    double packetLossProbability() default 0.0;
    double diskFailureProbability() default 0.0;
    boolean enableBuggify() default false;
    SeedMode seed() default SeedMode.FIXED;
    
    enum SeedMode {
        FIXED,    // Use seed 0
        RANDOM,   // Use random seed
        CUSTOM    // Use @FixedSeed annotation
    }
}
```

### @ReproduceFailure

Used to reproduce a specific test failure.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReproduceFailure {
    long seed();
    String description() default "";
}
```

## Fault Injection

### Buggify

Fault injection utility for introducing controlled failures.

```java
public final class Buggify {
    // Check if a specific bug should be injected
    public static boolean isEnabled(String bugId);
    
    // Inject fault with given probability
    public static boolean sometimes(double probability);
    
    // Inject fault with specific bug ID and probability
    public static boolean isEnabled(String bugId, double probability);
    
    // Common bug IDs
    public static final String SLOW_TASK = "slow_task";
    public static final String NETWORK_DELAY = "network_delay";
    public static final String PACKET_LOSS = "packet_loss";
    public static final String DISK_FAILURE = "disk_failure";
    public static final String CONNECTION_FAILURE = "connection_failure";
}
```

### NetworkFaultInjector

Controls network-related fault injection.

```java
public class NetworkFaultInjector {
    // Configure packet loss
    public void setPacketLossProbability(double probability);
    
    // Configure packet reordering
    public void setPacketReorderProbability(double probability);
    
    // Configure connection failures
    public void setConnectionFailureProbability(double probability);
    
    // Configure latency
    public void setLatencyRange(double minMs, double maxMs);
    
    // Check if a packet should be dropped
    public boolean shouldDropPacket();
    
    // Get random delay for a packet
    public double getPacketDelay();
    
    // Check if a connection should fail
    public boolean shouldFailConnection();
}
```

## Simulation Control

### FlowSimulation

Main control interface for simulation mode.

```java
public final class FlowSimulation {
    // Start simulation with configuration
    public static void start(SimulationConfig config);
    
    // Stop simulation and cleanup
    public static void stop();
    
    // Check if running in simulation mode
    public static boolean isSimulated();
    
    // Get current simulation configuration
    public static SimulationConfig getConfig();
    
    // Run simulation for specified duration
    public static void runFor(Duration duration);
    
    // Run simulation until condition is met
    public static void runUntil(Supplier<Boolean> condition);
    
    // Advance simulation time
    public static void advanceTime(double seconds);
    
    // Get current simulation metrics
    public static SimulationMetrics getMetrics();
}
```

### SimulationMetrics

Metrics collected during simulation runs.

```java
public class SimulationMetrics {
    // Get total tasks executed
    public long getTasksExecuted();
    
    // Get random tasks selected
    public long getRandomTasksSelected();
    
    // Get packets dropped
    public long getPacketsDropped();
    
    // Get disk failures injected
    public long getDiskFailures();
    
    // Get bugs triggered
    public Map<String, Long> getBugTriggerCounts();
    
    // Get simulation duration
    public Duration getSimulationDuration();
}
```

## Test Base Classes

### AbstractFlowSimulationTest

Base class for tests that use simulation mode.

```java
public abstract class AbstractFlowSimulationTest {
    // Override to provide custom configuration
    protected SimulationConfig getSimulationConfig() {
        return SimulationConfig.builder().build();
    }
    
    // Called before each test with seed info
    protected void onTestStart(long seed) {
        // Override for custom setup
    }
    
    // Called after test with results
    protected void onTestComplete(long seed, TestResult result) {
        // Override for custom cleanup
    }
    
    // Utility to run actor and wait for completion
    protected <T> T runActor(Callable<T> actor) throws Exception;
    
    // Utility to run actor with timeout
    protected <T> T runActor(Callable<T> actor, Duration timeout) throws Exception;
}
```

## Usage Examples

### Basic Simulation Test

```java
public class BasicSimulationTest extends AbstractFlowSimulationTest {
    
    @Test
    @RandomSeed(iterations = 100)
    public void testWithRandomSeeds() {
        FlowFuture<String> result = Flow.startActor(() -> {
            // Simulate network delay
            if (Buggify.sometimes(0.1)) {
                await(Flow.delay(1.0));
            }
            
            // Perform operation
            return performDistributedOperation();
        });
        
        assertEquals("expected", result.get());
    }
}
```

### Chaos Test

```java
public class ChaosTest extends AbstractFlowSimulationTest {
    
    @Override
    protected SimulationConfig getSimulationConfig() {
        return SimulationConfig.builder()
            .withRandomTaskSelection(0.2)
            .withNetworkFaults(true)
            .withBuggify(Buggify.PACKET_LOSS, 0.05)
            .withBuggify(Buggify.CONNECTION_FAILURE, 0.01)
            .build();
    }
    
    @Test
    @SimulationMode(
        randomTaskSelection = 0.3,
        packetLossProbability = 0.1,
        enableBuggify = true
    )
    public void testSystemUnderChaos() {
        // Test implementation
    }
}
```

### Reproducing Failures

```java
public class ReproductionTest extends AbstractFlowSimulationTest {
    
    @Test
    @ReproduceFailure(seed = 1234567890L, 
                      description = "Failed in CI on 2024-01-15")
    public void reproduceProductionFailure() {
        // This will run with exact same randomness that caused the failure
        // Test implementation
    }
}
```

### Custom Fault Injection

```java
public class CustomFaultTest extends AbstractFlowSimulationTest {
    
    @Test
    public void testWithCustomFaults() {
        // Register custom bug
        BugRegistry.getInstance().register("custom_delay", 0.2);
        
        FlowFuture<Void> result = Flow.startActor(() -> {
            // Custom fault injection
            if (Buggify.isEnabled("custom_delay")) {
                double delay = FlowRandom.current().nextDouble() * 5.0;
                await(Flow.delay(delay));
            }
            
            return processData();
        });
        
        result.get();
    }
}
```

## Best Practices

1. **Always log seeds**: When using random seeds, ensure they're logged for reproduction
2. **Start small**: Begin with low fault probabilities and increase gradually
3. **Isolate randomness**: Use `RandomSource.createChild()` for independent random streams
4. **Clean up**: Always ensure simulation is stopped in test cleanup
5. **Monitor metrics**: Use `SimulationMetrics` to understand test behavior
6. **Document failures**: Use `@ReproduceFailure` to document and fix specific failures
7. **Combine modes**: Use both fixed and random seeds for comprehensive testing