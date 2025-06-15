package io.github.panghy.javaflow.examples;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.simulation.BugIds;
import io.github.panghy.javaflow.simulation.BugRegistry;
import io.github.panghy.javaflow.simulation.Buggify;
import io.github.panghy.javaflow.simulation.ChaosScenarios;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example demonstrating how to use the BUGGIFY framework for fault injection testing.
 * 
 * This example shows how BUGGIFY can be used to test the resilience of a simple
 * distributed key-value store implementation.
 */
public class BuggifyExample extends AbstractFlowTest {
  
  /**
   * Simple key-value store interface.
   */
  public interface KeyValueStore {
    FlowFuture<Void> put(String key, String value);
    FlowFuture<String> get(String key);
    FlowFuture<Void> replicate();
  }
  
  /**
   * Implementation that uses BUGGIFY to simulate various failure modes.
   */
  public static class BuggifiedKeyValueStore implements KeyValueStore {
    private final java.util.Map<String, String> data = new java.util.HashMap<>();
    private final List<BuggifiedKeyValueStore> replicas = new ArrayList<>();
    private final String nodeId;
    private boolean isDown = false;
    
    public BuggifiedKeyValueStore(String nodeId) {
      this.nodeId = nodeId;
    }
    
    public void addReplica(BuggifiedKeyValueStore replica) {
      replicas.add(replica);
    }
    
    @Override
    public FlowFuture<Void> put(String key, String value) {
      return Flow.startActor(() -> {
        // Simulate node being down
        if (Buggify.isEnabled(BugIds.PROCESS_CRASH)) {
          isDown = true;
          throw new RuntimeException("Node " + nodeId + " crashed");
        }
        
        if (isDown) {
          throw new RuntimeException("Node " + nodeId + " is down");
        }
        
        // Simulate slow disk
        if (Buggify.isEnabled(BugIds.DISK_SLOW)) {
          Flow.await(Flow.delay(Buggify.randomDouble(0.5, 2.0)));
        }
        
        // Simulate disk full
        if (Buggify.isEnabled(BugIds.DISK_FULL)) {
          throw new IOException("Disk full on node " + nodeId);
        }
        
        // Simulate write failure
        if (Buggify.isEnabled(BugIds.WRITE_FAILURE)) {
          throw new IOException("Write failed on node " + nodeId);
        }
        
        // Actually store the data
        data.put(key, value);
        
        // Simulate data corruption
        if (Buggify.isEnabled(BugIds.DATA_CORRUPTION)) {
          // Corrupt the value by flipping a character
          String corrupted = value.substring(0, 1) + "X" + value.substring(2);
          data.put(key, corrupted);
        }
        
        return null;
      });
    }
    
    @Override
    public FlowFuture<String> get(String key) {
      return Flow.startActor(() -> {
        if (isDown) {
          throw new RuntimeException("Node " + nodeId + " is down");
        }
        
        // Simulate read failure
        if (Buggify.isEnabled(BugIds.READ_FAILURE)) {
          throw new IOException("Read failed on node " + nodeId);
        }
        
        // Simulate slow disk
        if (Buggify.isEnabled(BugIds.DISK_SLOW)) {
          Flow.await(Flow.delay(Buggify.randomDouble(0.1, 0.5)));
        }
        
        return data.get(key);
      });
    }
    
    @Override
    public FlowFuture<Void> replicate() {
      return Flow.startActor(() -> {
        if (isDown) {
          return null; // Can't replicate if down
        }
        
        List<FlowFuture<Void>> replicationFutures = new ArrayList<>();
        
        for (BuggifiedKeyValueStore replica : replicas) {
          // Simulate network issues during replication
          if (Buggify.isEnabled(BugIds.NETWORK_PARTITION)) {
            continue; // Skip this replica due to network partition
          }
          
          FlowFuture<Void> replicaFuture = Flow.startActor(() -> {
            // Simulate network delay
            if (Buggify.isEnabled(BugIds.NETWORK_SLOW)) {
              Flow.await(Flow.delay(Buggify.randomDouble(0.1, 1.0)));
            }
            
            // Simulate packet loss
            if (Buggify.isEnabled(BugIds.PACKET_LOSS)) {
              throw new IOException("Packet lost during replication");
            }
            
            // Copy data to replica
            for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
              Flow.await(replica.put(entry.getKey(), entry.getValue()));
            }
            
            return null;
          });
          
          replicationFutures.add(replicaFuture);
        }
        
        // Wait for all replications to complete
        for (FlowFuture<Void> future : replicationFutures) {
          try {
            Flow.await(future);
          } catch (Exception e) {
            // Log replication failure but continue
            System.err.println("Replication failed: " + e.getMessage());
          }
        }
        
        return null;
      });
    }
    
    public void recover() {
      isDown = false;
    }
  }
  
  @Test
  public void testKeyValueStoreUnderChaos() {
    // Configure chaos scenario
    ChaosScenarios.clearAll();
    BugRegistry.getInstance()
        .register(BugIds.DISK_SLOW, 0.1)          // 10% slow disk
        .register(BugIds.WRITE_FAILURE, 0.05)     // 5% write failures
        .register(BugIds.READ_FAILURE, 0.02)      // 2% read failures
        .register(BugIds.DATA_CORRUPTION, 0.01)   // 1% data corruption
        .register(BugIds.NETWORK_SLOW, 0.1)       // 10% slow network
        .register(BugIds.PACKET_LOSS, 0.05);      // 5% packet loss
    
    // Create a cluster of 3 nodes
    BuggifiedKeyValueStore primary = new BuggifiedKeyValueStore("primary");
    BuggifiedKeyValueStore replica1 = new BuggifiedKeyValueStore("replica1");
    BuggifiedKeyValueStore replica2 = new BuggifiedKeyValueStore("replica2");
    
    primary.addReplica(replica1);
    primary.addReplica(replica2);
    
    AtomicInteger successfulWrites = new AtomicInteger(0);
    AtomicInteger failedWrites = new AtomicInteger(0);
    AtomicInteger successfulReads = new AtomicInteger(0);
    AtomicInteger failedReads = new AtomicInteger(0);
    
    // Perform many operations
    List<FlowFuture<Void>> operations = new ArrayList<>();
    
    for (int i = 0; i < 100; i++) {
      final int index = i;
      FlowFuture<Void> op = Flow.startActor(() -> {
        try {
          // Write to primary
          Flow.await(primary.put("key" + index, "value" + index));
          successfulWrites.incrementAndGet();
          
          // Replicate
          Flow.await(primary.replicate());
          
          // Read back
          String value = Flow.await(primary.get("key" + index));
          if (value != null && value.startsWith("value")) {
            successfulReads.incrementAndGet();
          }
        } catch (Exception e) {
          if (e.getMessage().contains("Write failed")) {
            failedWrites.incrementAndGet();
          } else if (e.getMessage().contains("Read failed")) {
            failedReads.incrementAndGet();
          }
        }
        return null;
      });
      operations.add(op);
    }
    
    // Wait for all operations
    for (FlowFuture<Void> future : operations) {
      pumpAndAdvanceTimeUntilDone(future);
    }
    
    // Verify that we experienced some failures
    System.out.println("Test Results:");
    System.out.println("Successful writes: " + successfulWrites.get());
    System.out.println("Failed writes: " + failedWrites.get());
    System.out.println("Successful reads: " + successfulReads.get());
    System.out.println("Failed reads: " + failedReads.get());
    
    // With the configured probabilities, we should see some failures
    assertTrue(failedWrites.get() > 0, "Should have some write failures");
    assertTrue(successfulWrites.get() > 50, "Should have many successful writes");
  }
  
  @Test
  public void testDeterministicChaos() {
    // Use a specific seed for deterministic behavior
    long seed = 42L;
    SimulationContext context = new SimulationContext(seed, true, new SimulationConfiguration());
    SimulationContext.setCurrent(context);
    
    try {
      // Configure specific bugs
      BugRegistry.getInstance()
          .register("deterministic_bug", 0.5);
      
      // Collect results
      List<Boolean> results = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        results.add(Buggify.isEnabled("deterministic_bug"));
      }
      
      // Reset and run again with same seed
      SimulationContext.clearCurrent();
      context = new SimulationContext(seed, true, new SimulationConfiguration());
      SimulationContext.setCurrent(context);
      
      // Results should be identical
      for (int i = 0; i < 20; i++) {
        assertEquals(results.get(i), Buggify.isEnabled("deterministic_bug"),
            "Same seed should produce same bug injection pattern");
      }
    } finally {
      SimulationContext.clearCurrent();
    }
  }
  
  @Test
  public void testTimeAwareRecovery() {
    // Create a simulation context
    SimulationContext context = new SimulationContext(123L, true, new SimulationConfiguration());
    SimulationContext.setCurrent(context);
    
    try {
      BugRegistry.getInstance()
          .register("recovery_test_bug", 0.5);
      
      // Count failures before and after recovery time
      int failuresBeforeRecovery = 0;
      int failuresAfterRecovery = 0;
      
      // Test before 300 seconds
      for (int i = 0; i < 100; i++) {
        if (Buggify.isEnabledWithRecovery("recovery_test_bug")) {
          failuresBeforeRecovery++;
        }
      }
      
      // Advance time past 300 seconds
      context.advanceTime(301.0);
      
      // Test after 300 seconds
      for (int i = 0; i < 1000; i++) {
        if (Buggify.isEnabledWithRecovery("recovery_test_bug")) {
          failuresAfterRecovery++;
        }
      }
      
      System.out.println("Failures before recovery (100 attempts): " + failuresBeforeRecovery);
      System.out.println("Failures after recovery (1000 attempts): " + failuresAfterRecovery);
      
      // After recovery time, failure rate should be much lower
      assertTrue(failuresBeforeRecovery > 30, "Should have many failures before recovery");
      assertTrue(failuresAfterRecovery < 30, "Should have few failures after recovery");
    } finally {
      SimulationContext.clearCurrent();
    }
  }
}