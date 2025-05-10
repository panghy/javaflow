package io.github.panghy.javaflow.benchmark;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.TaskPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark to measure throughput of a large number of actors.
 * This benchmark spawns 10,000 actors that continuously yield and loop,
 * tracking the total number of operations per second across all actors.
 */
public class ActorThroughputBenchmark {

  private static final int ACTOR_COUNT = 10_000;
  private static final long BENCHMARK_DURATION_MS = 60_000; // 60 seconds

  public static void main(String[] args) throws Exception {
    // Create a scheduler with a carrier thread
    FlowScheduler scheduler = new FlowScheduler(true);
    Flow.setScheduler(scheduler);

    // Counter for tracking total operations
    AtomicLong totalOperations = new AtomicLong(0);

    // Start time tracking (using final for variables referenced in lambdas)
    final long startTime = System.currentTimeMillis();

    // Start time tracking immediately to capture initial throughput
    System.out.println("Starting " + ACTOR_COUNT + " actors and measuring throughput...");

    // Start a separate thread for reporting metrics while actors are being created
    Thread reportingThread = new Thread(() -> {
      try {
        // Local variables for the reporting thread
        long localLastReportTime = startTime;
        long localLastReportCount = 0;
        
        while (System.currentTimeMillis() - startTime < BENCHMARK_DURATION_MS) {
          // Sleep for a reporting interval
          Thread.sleep(1000);
          
          long now = System.currentTimeMillis();
          long currentCount = totalOperations.get();
          
          // Calculate operations per second since last report
          long timeElapsed = now - localLastReportTime;
          long opsCount = currentCount - localLastReportCount;
          double opsPerSec = (opsCount * 1000.0) / timeElapsed;
          
          // Calculate overall operations per second
          long totalElapsed = now - startTime;
          double overallOpsPerSec = (currentCount * 1000.0) / totalElapsed;
          
          System.out.printf("Throughput: %.2f ops/sec (Current) | %.2f ops/sec (Average) | %d total ops%n",
              opsPerSec, overallOpsPerSec, currentCount);
          
          // Update for next iteration
          localLastReportTime = now;
          localLastReportCount = currentCount;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    
    // Start the reporting thread as daemon
    reportingThread.setDaemon(true);
    reportingThread.start();
    
    // Create actors with different priorities to better demonstrate aging effects
    List<FlowFuture<Void>> actors = new ArrayList<>(ACTOR_COUNT);
    
    // Create actors with different priorities
    for (int i = 0; i < ACTOR_COUNT; i++) {
      // Assign different priorities to demonstrate aging
      int priority;
      if (i < ACTOR_COUNT * 0.05) { // 5% critical priority
        priority = TaskPriority.CRITICAL;
      } else if (i < ACTOR_COUNT * 0.20) { // 15% high priority
        priority = TaskPriority.HIGH;
      } else if (i < ACTOR_COUNT * 0.80) { // 60% default priority
        priority = TaskPriority.DEFAULT;
      } else if (i < ACTOR_COUNT * 0.95) { // 15% low priority
        priority = TaskPriority.LOW;
      } else { // 5% idle priority
        priority = TaskPriority.IDLE;
      }
      
      // Each actor increments the counter and yields in a loop
      FlowFuture<Void> actor = Flow.startActor(() -> {
        while (true) {
          totalOperations.incrementAndGet();
          Flow.await(Flow.yieldF());
        }
      }, priority);
      
      actors.add(actor);
    }

    // Wait for the benchmark duration to complete
    // The reporting is handled by the reporting thread
    try {
      Thread.sleep(BENCHMARK_DURATION_MS);
    } catch (InterruptedException e) {
      System.out.println("Benchmark interrupted");
    }
    
    // Interrupt and wait for the reporting thread to finish
    reportingThread.interrupt();
    try {
      reportingThread.join(1000);
    } catch (InterruptedException e) {
      System.out.println("Interrupted while waiting for reporting thread");
    }

    // End of benchmark
    long endTime = System.currentTimeMillis();
    long finalCount = totalOperations.get();
    double avgOpsPerSec = (finalCount * 1000.0) / (endTime - startTime);

    System.out.println("\nBenchmark completed:");
    System.out.println("Duration: " + TimeUnit.MILLISECONDS.toSeconds(endTime - startTime) + " seconds");
    System.out.println("Total operations: " + finalCount);
    System.out.printf("Average throughput: %.2f ops/sec%n", avgOpsPerSec);
    System.out.printf("Average ops per actor per second: %.2f%n", avgOpsPerSec / ACTOR_COUNT);

    // Shutdown
    System.out.println("Shutting down scheduler...");
    scheduler.shutdown();
    System.out.println("Benchmark complete.");
  }
}