package io.github.panghy.javaflow.examples;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.FlowRpcTransport;
import io.github.panghy.javaflow.rpc.FlowRpcTransportImpl;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.delay;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Example demonstrating a race condition bug that only manifests with certain
 * task execution orderings. This shows how deterministic simulation with seeds
 * can help reproduce and diagnose concurrency bugs.
 */
public class RaceConditionBugExample extends AbstractFlowTest {

  // Service interface for a distributed counter
  public interface CounterService {
    FlowFuture<Integer> getValue();

    FlowFuture<Void> increment();

    FlowFuture<Integer> getAndIncrement();

    void reset();
  }

  // Buggy implementation with a race condition
  public static class BuggyCounterService implements CounterService {
    protected int counter = 0;

    @Override
    public FlowFuture<Integer> getValue() {
      FlowFuture<Integer> future = new FlowFuture<>();
      // Simulate some async work
      startActor(() -> {
        // Add a small delay to simulate network/processing
        await(delay(0.001));
        future.getPromise().complete(counter);
        return null;
      });
      return future;
    }

    @Override
    public FlowFuture<Void> increment() {
      FlowFuture<Void> future = new FlowFuture<>();
      // BUG: This implementation has a race condition!
      // It reads, modifies, and writes in separate steps
      startActor(() -> {
        // Read current value
        int currentValue = counter;

        // Simulate some processing time
        await(delay(0.002));

        // Increment and write back - RACE CONDITION HERE!
        counter = currentValue + 1;

        future.getPromise().complete(null);
        return null;
      });
      return future;
    }

    @Override
    public FlowFuture<Integer> getAndIncrement() {
      FlowFuture<Integer> future = new FlowFuture<>();
      startActor(() -> {
        int oldValue = counter;
        await(increment());
        future.getPromise().complete(oldValue);
        return null;
      });
      return future;
    }

    @Override
    public void reset() {
      counter = 0;
    }
  }

  // Fixed implementation without race condition
  public static class FixedCounterService implements CounterService {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public FlowFuture<Integer> getValue() {
      FlowFuture<Integer> future = new FlowFuture<>();
      startActor(() -> {
        await(delay(0.001));
        future.getPromise().complete(counter.get());
        return null;
      });
      return future;
    }

    @Override
    public FlowFuture<Void> increment() {
      FlowFuture<Void> future = new FlowFuture<>();
      startActor(() -> {
        // Atomic increment - no race condition
        await(delay(0.002));
        counter.incrementAndGet();
        future.getPromise().complete(null);
        return null;
      });
      return future;
    }

    @Override
    public FlowFuture<Integer> getAndIncrement() {
      FlowFuture<Integer> future = new FlowFuture<>();
      startActor(() -> {
        await(delay(0.002));
        int oldValue = counter.getAndIncrement();
        future.getPromise().complete(oldValue);
        return null;
      });
      return future;
    }

    @Override
    public void reset() {
      counter.set(0);
    }
  }

  private FlowRpcTransport transport;

  @BeforeEach
  public void setupRpc() {
    transport = new FlowRpcTransportImpl();
  }

  @AfterEach
  public void teardownRpc() {
    if (transport != null) {
      transport.close();
    }
  }

  @Test
  @Timeout(10)
  public void demonstrateRaceConditionBug() throws Exception {
    // Test with multiple seeds to find one that triggers the bug
    List<Long> problematicSeeds = new ArrayList<>();

    for (long seed = 1; seed <= 100; seed++) {
      // Reset simulation with new seed
      SimulationConfiguration config = new SimulationConfiguration()
          .setPriorityRandomization(true)  // Enable priority randomization
          .setTaskSelectionProbability(0.5) // 50% chance of random task selection
          // Disable other fault injections to focus on ordering
          .setNetworkErrorProbability(0.0)
          .setPacketLossProbability(0.0)
          .setDiskFailureProbability(0.0);

      SimulationContext context = new SimulationContext(seed, true, config);
      SimulationContext.setCurrent(context);

      // Register buggy service with unique ID for each iteration
      BuggyCounterService buggyService = new BuggyCounterService();
      EndpointId serviceId = new EndpointId("counter-service-" + seed);
      LocalEndpoint endpoint = LocalEndpoint.localhost(8080 + (int) seed);

      transport.registerServiceAndListen(serviceId, buggyService, CounterService.class, endpoint);

      // Create multiple client stubs
      CounterService client1 = transport.getRpcStub(serviceId, CounterService.class);
      CounterService client2 = transport.getRpcStub(serviceId, CounterService.class);
      CounterService client3 = transport.getRpcStub(serviceId, CounterService.class);

      // Run concurrent increments
      FlowFuture<Void> client1Future = startActor(() -> {
        for (int i = 0; i < 5; i++) {
          await(client1.increment());
        }
        return null;
      });

      FlowFuture<Void> client2Future = startActor(() -> {
        for (int i = 0; i < 5; i++) {
          await(client2.increment());
        }
        return null;
      });

      FlowFuture<Void> client3Future = startActor(() -> {
        for (int i = 0; i < 5; i++) {
          await(client3.increment());
        }
        return null;
      });

      // Wait for all clients to complete
      pumpAndAdvanceTimeUntilDone(client1Future, client2Future, client3Future);

      // Check final value
      FlowFuture<Integer> finalValueFuture = client1.getValue();
      pumpAndAdvanceTimeUntilDone(finalValueFuture);
      int finalValue = finalValueFuture.getNow();

      // Should be 15 (3 clients * 5 increments each)
      if (finalValue != 15) {
        problematicSeeds.add(seed);
        System.out.println("Found race condition bug with seed " + seed +
                           ": expected 15, got " + finalValue);
      }

      // Clean up for next iteration
      buggyService.reset();
    }

    // We should find at least one seed that triggers the bug
    assertNotEquals(0, problematicSeeds.size(),
        "No seeds found that trigger the race condition - adjust parameters");

    // Now demonstrate that we can reproduce the bug with a specific seed
    Long bugSeed = problematicSeeds.getFirst();
    System.out.println("\nReproducing bug with seed: " + bugSeed);

    // Reset and reproduce
    SimulationConfiguration bugConfig = new SimulationConfiguration()
        .setPriorityRandomization(true)
        .setTaskSelectionProbability(0.5)
        .setNetworkErrorProbability(0.0)
        .setPacketLossProbability(0.0)
        .setDiskFailureProbability(0.0)
        .setTaskExecutionLogging(true); // Enable logging to see execution order

    SimulationContext bugContext = new SimulationContext(bugSeed, true, bugConfig);
    SimulationContext.setCurrent(bugContext);

    // Register service again
    BuggyCounterService buggyService = new BuggyCounterService();
    EndpointId serviceId = new EndpointId("counter-service-reproduce");
    LocalEndpoint endpoint = LocalEndpoint.localhost(9999);

    transport.registerServiceAndListen(serviceId, buggyService, CounterService.class, endpoint);

    CounterService client = transport.getRpcStub(serviceId, CounterService.class);

    // Run the same scenario
    List<FlowFuture<Void>> futures = new ArrayList<>();
    for (int clientNum = 1; clientNum <= 3; clientNum++) {
      final int num = clientNum;
      futures.add(startActor(() -> {
        System.out.println("Client " + num + " starting increments");
        for (int i = 0; i < 5; i++) {
          System.out.println("Client " + num + " increment " + (i + 1));
          await(client.increment());
        }
        System.out.println("Client " + num + " finished");
        return null;
      }));
    }

    pumpAndAdvanceTimeUntilDone(futures.toArray(new FlowFuture[0]));

    FlowFuture<Integer> finalValueFuture = client.getValue();
    pumpAndAdvanceTimeUntilDone(finalValueFuture);
    int finalValue = finalValueFuture.getNow();

    // Bug should reproduce with same result
    assertNotEquals(15, finalValue,
        "Bug should reproduce with seed " + bugSeed);
    System.out.println("Bug reproduced: expected 15, got " + finalValue);
  }

  @Test
  @Timeout(10)
  public void verifyFixedImplementationWorks() throws Exception {
    // Use the same problematic seeds to verify the fix works
    long[] testSeeds = {42, 87, 123}; // Use specific seeds that might trigger issues

    for (long seed : testSeeds) {
      SimulationConfiguration config = new SimulationConfiguration()
          .setPriorityRandomization(true)
          .setTaskSelectionProbability(0.5)
          .setNetworkErrorProbability(0.0)
          .setPacketLossProbability(0.0)
          .setDiskFailureProbability(0.0);

      SimulationContext context = new SimulationContext(seed, true, config);
      SimulationContext.setCurrent(context);

      // Register fixed service with unique ID for each iteration
      FixedCounterService fixedService = new FixedCounterService();
      EndpointId serviceId = new EndpointId("fixed-counter-service-" + seed);
      LocalEndpoint endpoint = LocalEndpoint.localhost(7000 + (int) seed);

      transport.registerServiceAndListen(serviceId, fixedService, CounterService.class, endpoint);

      // Create clients
      CounterService client1 = transport.getRpcStub(serviceId, CounterService.class);
      CounterService client2 = transport.getRpcStub(serviceId, CounterService.class);
      CounterService client3 = transport.getRpcStub(serviceId, CounterService.class);

      // Run concurrent increments
      List<FlowFuture<Void>> futures = new ArrayList<>();
      futures.add(startActor(() -> {
        for (int i = 0; i < 5; i++) {
          await(client1.increment());
        }
        return null;
      }));

      futures.add(startActor(() -> {
        for (int i = 0; i < 5; i++) {
          await(client2.increment());
        }
        return null;
      }));

      futures.add(startActor(() -> {
        for (int i = 0; i < 5; i++) {
          await(client3.increment());
        }
        return null;
      }));

      pumpAndAdvanceTimeUntilDone(futures.toArray(new FlowFuture[0]));

      // Check final value
      FlowFuture<Integer> finalValueFuture = client1.getValue();
      pumpAndAdvanceTimeUntilDone(finalValueFuture);
      int finalValue = finalValueFuture.getNow();

      // Should always be 15 with the fixed implementation
      assertEquals(15, finalValue,
          "Fixed implementation should always produce correct result with seed " + seed);

      // Clean up
      fixedService.reset();
    }
  }

  @Test
  @Timeout(10)
  public void demonstrateDebuggingWithSeed() throws Exception {
    // Use a known problematic seed to debug the issue
    long problematicSeed = 13; // This seed often triggers the race condition

    SimulationConfiguration config = new SimulationConfiguration()
        .setPriorityRandomization(true)
        .setTaskSelectionProbability(0.5)
        .setTaskExecutionLogging(true); // Enable detailed logging

    SimulationContext context = new SimulationContext(problematicSeed, true, config);
    SimulationContext.setCurrent(context);

    // Create a custom buggy service that logs operations
    class DebuggingCounterService extends BuggyCounterService {
      @Override
      public FlowFuture<Void> increment() {
        FlowFuture<Void> future = new FlowFuture<>();
        startActor(() -> {
          System.out.println(Thread.currentThread().getName() +
                             " - Reading counter value: " + counter);
          int currentValue = counter;

          System.out.println(Thread.currentThread().getName() +
                             " - Current value read: " + currentValue);

          // Simulate processing
          await(delay(0.002));

          // This is where the race condition occurs
          int newValue = currentValue + 1;
          System.out.println(Thread.currentThread().getName() +
                             " - Writing new value: " + newValue +
                             " (was: " + counter + ")");
          counter = newValue;

          future.getPromise().complete(null);
          return null;
        });
        return future;
      }
    }

    // Register debugging service
    DebuggingCounterService debugService = new DebuggingCounterService();
    EndpointId serviceId = new EndpointId("debug-counter");
    LocalEndpoint endpoint = LocalEndpoint.localhost(5555);

    transport.registerServiceAndListen(serviceId, debugService, CounterService.class, endpoint);

    CounterService client = transport.getRpcStub(serviceId, CounterService.class);

    // Run just two concurrent increments to see the race condition clearly
    System.out.println("\n=== Starting debug scenario with seed " + problematicSeed + " ===");

    FlowFuture<Void> increment1 = startActor(() -> {
      System.out.println("Actor 1: Starting increment");
      await(client.increment());
      System.out.println("Actor 1: Increment complete");
      return null;
    });

    FlowFuture<Void> increment2 = startActor(() -> {
      System.out.println("Actor 2: Starting increment");
      await(client.increment());
      System.out.println("Actor 2: Increment complete");
      return null;
    });

    pumpAndAdvanceTimeUntilDone(increment1, increment2);

    FlowFuture<Integer> finalValueFuture = client.getValue();
    pumpAndAdvanceTimeUntilDone(finalValueFuture);
    int finalValue = finalValueFuture.getNow();

    System.out.println("\nFinal counter value: " + finalValue);
    System.out.println("Expected: 2, Actual: " + finalValue);

    if (finalValue != 2) {
      System.out.println("\n*** RACE CONDITION DETECTED ***");
      System.out.println("The bug occurs when both actors read the same initial value");
      System.out.println("before either writes back their incremented value.");
      System.out.println("This seed (" + problematicSeed + ") can be used to");
      System.out.println("consistently reproduce this race condition for debugging.");
    }
  }
}