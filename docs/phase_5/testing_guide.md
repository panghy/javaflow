# Phase 5: Deterministic Simulation Mode - Testing Guide

## Overview

This guide explains how to write effective tests using JavaFlow's deterministic simulation mode. Simulation mode allows you to test distributed systems behavior under controlled conditions, including network failures, timing variations, and other fault scenarios.

## Getting Started

### Basic Setup

All simulation tests should extend `AbstractFlowSimulationTest`:

```java
import io.github.panghy.javaflow.test.AbstractFlowSimulationTest;

public class MySimulationTest extends AbstractFlowSimulationTest {
    @Test
    public void testBasicOperation() {
        // Your test code
    }
}
```

### Running with Different Seeds

#### Fixed Seed (Deterministic)

```java
@Test
@FixedSeed(12345)
public void testWithFixedSeed() {
    // This test will always run with seed 12345
    // Useful for regression tests
}
```

#### Random Seed

```java
@Test
@RandomSeed
public void testWithRandomSeed() {
    // This test will run with a random seed
    // Seed will be logged for reproduction
}
```

#### Multiple Iterations

```java
@Test
@RandomSeed(iterations = 100)
public void testMultipleSeeds() {
    // This test will run 100 times with different seeds
    // Useful for finding rare bugs
}
```

## Fault Injection

### Using BUGGIFY

BUGGIFY allows you to inject faults at specific points in your code:

```java
@Test
public void testWithFaultInjection() {
    FlowFuture<String> result = Flow.startActor(() -> {
        // Inject a delay 10% of the time
        if (Buggify.sometimes(0.1)) {
            await(Flow.delay(1.0));
        }
        
        // Inject a failure 5% of the time
        if (Buggify.isEnabled("operation_failure", 0.05)) {
            throw new RuntimeException("Simulated failure");
        }
        
        return "success";
    });
    
    // Handle both success and failure cases
    try {
        assertEquals("success", result.get());
    } catch (Exception e) {
        // Verify failure handling
        assertTrue(e.getMessage().contains("Simulated failure"));
    }
}
```

### Network Fault Injection

```java
@Test
@SimulationMode(
    packetLossProbability = 0.05,  // 5% packet loss
    enableBuggify = true
)
public void testNetworkResilience() {
    // Set up client and server
    FlowFuture<String> serverResult = startServer();
    FlowFuture<String> clientResult = startClient();
    
    // Despite packet loss, operation should eventually succeed
    assertEquals("success", clientResult.get());
}
```

## Common Test Patterns

### 1. Testing Retry Logic

```java
@Test
public void testRetryUnderFailures() {
    AtomicInteger attempts = new AtomicInteger(0);
    
    FlowFuture<String> result = Flow.startActor(() -> {
        while (true) {
            try {
                attempts.incrementAndGet();
                
                // Fail first 2 attempts
                if (attempts.get() <= 2 && Buggify.isEnabled("retry_test")) {
                    throw new RuntimeException("Simulated failure");
                }
                
                return "success after " + attempts.get() + " attempts";
            } catch (Exception e) {
                // Retry with backoff
                await(Flow.delay(0.1 * attempts.get()));
            }
        }
    });
    
    String message = result.get();
    assertTrue(message.contains("success"));
    assertTrue(attempts.get() >= 1);
}
```

### 2. Testing Timeout Behavior

```java
@Test
public void testTimeoutHandling() {
    FlowFuture<String> result = Flow.startActor(() -> {
        FlowFuture<String> operation = startSlowOperation();
        FlowFuture<Void> timeout = Flow.delay(1.0);
        
        // Race between operation and timeout
        return Flow.select(
            operation.map(value -> "completed: " + value),
            timeout.map(v -> "timeout")
        ).get();
    });
    
    String outcome = result.get();
    // Should handle both outcomes gracefully
    assertTrue(outcome.equals("timeout") || outcome.startsWith("completed"));
}
```

### 3. Testing Concurrent Operations

```java
@Test
@SimulationMode(randomTaskSelection = 0.2)  // 20% random scheduling
public void testConcurrentOperations() {
    final int NUM_ACTORS = 10;
    List<FlowFuture<Integer>> futures = new ArrayList<>();
    
    // Start multiple concurrent actors
    for (int i = 0; i < NUM_ACTORS; i++) {
        final int actorId = i;
        futures.add(Flow.startActor(() -> {
            // Simulate work with random delays
            if (Buggify.sometimes(0.3)) {
                await(Flow.delay(FlowRandom.current().nextDouble()));
            }
            
            return performWork(actorId);
        }));
    }
    
    // Wait for all to complete
    List<Integer> results = Flow.waitForAll(futures).get();
    
    // Verify all completed successfully
    assertEquals(NUM_ACTORS, results.size());
    // Verify results despite random scheduling
    verifyResults(results);
}
```

### 4. Testing Distributed Consensus

```java
@Test
@RandomSeed(iterations = 50)
public void testDistributedConsensus() {
    // Create multiple nodes
    List<ConsensusNode> nodes = createNodes(5);
    
    // Inject network partitions
    if (Buggify.sometimes(0.2)) {
        partitionNetwork(nodes, 2, 3);  // Split into 2 and 3 nodes
    }
    
    // Propose a value
    FlowFuture<String> result = nodes.get(0).propose("value");
    
    // Despite partitions, consensus should be reached
    String agreed = result.get();
    
    // Verify all nodes in majority partition agree
    verifyConsensus(nodes, agreed);
}
```

## Chaos Testing

### Comprehensive Chaos Test

```java
public class ChaosTest extends AbstractFlowSimulationTest {
    
    @Override
    protected SimulationConfig getSimulationConfig() {
        return SimulationConfig.builder()
            .withRandomTaskSelection(0.3)  // High randomness
            .withNetworkFaults(true)
            .withDiskFaults(true)
            .withBuggify("slow_network", 0.2)
            .withBuggify("disk_full", 0.05)
            .withBuggify("connection_reset", 0.1)
            .build();
    }
    
    @Test
    @RandomSeed(iterations = 1000)
    public void testSystemUnderChaos() {
        // Initialize system
        DistributedSystem system = new DistributedSystem();
        
        // Run workload
        FlowFuture<Void> workload = runWorkload(system);
        
        // Inject failures during execution
        FlowFuture<Void> chaosMonkey = Flow.startActor(() -> {
            while (!workload.isDone()) {
                await(Flow.delay(FlowRandom.current().nextDouble() * 2.0));
                
                if (Buggify.sometimes(0.1)) {
                    system.killRandomNode();
                    await(Flow.delay(1.0));
                    system.restartKilledNodes();
                }
            }
        });
        
        // System should remain consistent
        workload.get();
        verifySystemConsistency(system);
    }
}
```

## Reproducing Failures

When a test fails with a random seed, you can reproduce it exactly:

```java
// Original test that failed
@Test
@RandomSeed
public void testThatFailed() {
    // Test failed with seed: 1234567890
    // Check logs for the seed value
}

// Reproduce the exact failure
@Test
@ReproduceFailure(seed = 1234567890L,
                   description = "Failed in CI build #456")
public void reproduceFailure() {
    // Exact same test code
    // Will run with same seed and same random decisions
}
```

## Performance Testing

### Testing Under Load

```java
@Test
public void testPerformanceUnderLoad() {
    SimulationMetrics metrics = FlowSimulation.getMetrics();
    long startTasks = metrics.getTasksExecuted();
    
    // Run high-load scenario
    List<FlowFuture<Void>> load = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        load.add(Flow.startActor(this::performOperation));
    }
    
    // Wait for completion
    Flow.waitForAll(load).get();
    
    // Check performance metrics
    long totalTasks = metrics.getTasksExecuted() - startTasks;
    long randomSelections = metrics.getRandomTasksSelected();
    
    System.out.println("Total tasks: " + totalTasks);
    System.out.println("Random selections: " + randomSelections);
    System.out.println("Packets dropped: " + metrics.getPacketsDropped());
    
    // Verify performance requirements
    assertTrue(totalTasks > 1000);  // At least one task per operation
}
```

## Best Practices

### 1. Start Simple

Begin with deterministic tests before adding randomness:

```java
@Test
@FixedSeed(0)
public void testDeterministic() {
    // Get basic functionality working first
}

@Test
@RandomSeed
public void testWithRandomness() {
    // Then add randomness to find edge cases
}
```

### 2. Use Appropriate Fault Probabilities

- **Development**: Low probabilities (0.01 - 0.05)
- **Integration Testing**: Medium probabilities (0.05 - 0.20)
- **Chaos Testing**: High probabilities (0.20 - 0.50)

### 3. Log Important Events

```java
@Test
public void testWithLogging() {
    Flow.startActor(() -> {
        long seed = FlowRandom.getCurrentSeed();
        System.out.println("Running with seed: " + seed);
        
        if (Buggify.sometimes(0.1)) {
            System.out.println("Injecting delay at seed: " + seed);
            await(Flow.delay(1.0));
        }
        
        return performOperation();
    });
}
```

### 4. Handle Both Success and Failure

```java
@Test
public void testBothOutcomes() {
    FlowFuture<String> result = performOperationWithFaults();
    
    try {
        String value = result.get();
        // Verify successful outcome
        assertNotNull(value);
    } catch (Exception e) {
        // Verify failure is handled gracefully
        assertTrue(e instanceof ExpectedException);
        verifySystemRecovered();
    }
}
```

### 5. Use Timeouts Appropriately

```java
@Test(timeout = 30000)  // 30 second timeout
@SimulationMode
public void testWithTimeout() {
    // Simulation might explore many paths
    // Ensure reasonable timeout for CI
}
```

## Debugging Failed Tests

### 1. Capture Seed from Logs

```
Test failed with seed: 1234567890
```

### 2. Reproduce Locally

```java
@Test
@FixedSeed(1234567890L)
public void debugFailure() {
    // Add debugging output
    FlowSimulation.getConfig().setDebugMode(true);
    
    // Run same test
}
```

### 3. Analyze Metrics

```java
@After
public void printMetrics() {
    SimulationMetrics metrics = FlowSimulation.getMetrics();
    System.out.println("Bug triggers: " + metrics.getBugTriggerCounts());
    System.out.println("Random tasks: " + metrics.getRandomTasksSelected());
}
```

## Advanced Techniques

### Custom Random Streams

```java
@Test
public void testWithIndependentRandomness() {
    RandomSource mainRandom = FlowRandom.current();
    
    // Create independent random stream for specific component
    RandomSource componentRandom = mainRandom.createChild("component");
    
    // Use separate randomness
    if (componentRandom.getRandom().nextDouble() < 0.1) {
        // Component-specific random behavior
    }
}
```

### Simulation Checkpoints

```java
@Test
public void testWithCheckpoints() {
    // Take snapshot of system state
    SystemState checkpoint = captureState();
    
    // Run simulation
    FlowSimulation.runFor(Duration.ofSeconds(10));
    
    // Verify invariants maintained
    verifyInvariants(checkpoint, captureState());
}
```

## Conclusion

Deterministic simulation testing is a powerful technique for finding bugs in distributed systems. By controlling randomness and injecting faults systematically, you can discover edge cases that would be nearly impossible to find in traditional testing. The key is to start simple, gradually increase complexity, and always ensure failures can be reproduced.