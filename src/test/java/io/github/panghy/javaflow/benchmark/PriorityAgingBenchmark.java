package io.github.panghy.javaflow.benchmark;

import io.github.panghy.javaflow.Flow;
import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.TaskPriority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark to measure the effectiveness of the priority aging mechanism.
 * This benchmark creates a continuous stream of high-priority tasks and a set of
 * low-priority tasks, then measures whether the low-priority tasks are able to make
 * progress due to priority aging.
 */
public class PriorityAgingBenchmark {

  // Benchmark configuration
  private static final int LOW_PRIORITY_ACTORS = 100;
  private static final int HIGH_PRIORITY_ACTOR_BURSTS = 200;
  private static final int HIGH_PRIORITY_ACTORS_PER_BURST = 50;
  private static final long BURST_INTERVAL_MS = 50;  // Create new high-priority actors every 50ms
  private static final long BENCHMARK_DURATION_MS = 15_000;  // 15 seconds

  public static void main(String[] args) throws Exception {
    // Create a scheduler with a carrier thread
    FlowScheduler scheduler = new FlowScheduler(true);
    Flow.setScheduler(scheduler);

    // Counters for operations
    AtomicLong highPriorityOps = new AtomicLong(0);
    AtomicLong lowPriorityOps = new AtomicLong(0);

    // Map of actors to their start times
    Map<Long, Long> lowPriorityActorFirstOp = new HashMap<>();

    // No need for a startup latch since we're reporting continuously

    // Start time
    long startTime = System.currentTimeMillis();

    // Start a reporting thread immediately
    Thread reportingThread = new Thread(() -> {
      try {
        long localLastReportTime = startTime;
        long localLastHighOps = 0;
        long localLastLowOps = 0;

        while (System.currentTimeMillis() - startTime < BENCHMARK_DURATION_MS) {
          // Sleep for reporting interval
          Thread.sleep(1000);

          // Get current stats
          long now = System.currentTimeMillis();
          long currentHighOps = highPriorityOps.get();
          long currentLowOps = lowPriorityOps.get();

          // Calculate interval statistics
          long intervalMs = now - localLastReportTime;
          double highOpsPerSec = (currentHighOps - localLastHighOps) * 1000.0 / intervalMs;
          double lowOpsPerSec = (currentLowOps - localLastLowOps) * 1000.0 / intervalMs;

          // Count how many low-priority actors have executed at least once
          long activeCount = lowPriorityActorFirstOp.size();

          // Report
          System.out.printf("%n=== Progress Report (%.1f seconds elapsed) ===%n",
              (now - startTime) / 1000.0);
          System.out.printf("High-priority operations: %,d (%.2f ops/sec)%n",
              currentHighOps, highOpsPerSec);
          System.out.printf("Low-priority operations: %,d (%.2f ops/sec)%n",
              currentLowOps, lowOpsPerSec);
          System.out.printf("Low-priority actors started execution: %d of %d (%.1f%%)%n",
              activeCount, LOW_PRIORITY_ACTORS,
              100.0 * activeCount / LOW_PRIORITY_ACTORS);

          // Update for next interval
          localLastReportTime = now;
          localLastHighOps = currentHighOps;
          localLastLowOps = currentLowOps;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    // Start the reporting thread as daemon
    reportingThread.setDaemon(true);
    reportingThread.start();

    // Start low-priority actors
    System.out.println("Starting " + LOW_PRIORITY_ACTORS + " low-priority actors and measuring throughput...");
    List<CompletableFuture<Void>> lowPriorityActors = new ArrayList<>();

    for (int i = 0; i < LOW_PRIORITY_ACTORS; i++) {
      final long actorId = i;
      CompletableFuture<Void> actor = Flow.startActor(() -> {
        // Record the first successful operation
        boolean firstOp = true;

        // Run until interrupted
        while (true) {
          long now = System.currentTimeMillis();

          // Record first operation time
          if (firstOp) {
            lowPriorityActorFirstOp.put(actorId, now - startTime);
            firstOp = false;
          }

          lowPriorityOps.incrementAndGet();
          Flow.await(Flow.yieldF());
        }
      }, TaskPriority.LOW);

      lowPriorityActors.add(actor);
    }

    // Start high-priority actor bursts
    System.out.println("Starting high-priority actor bursts...");
    Thread highPriorityBurstThread = new Thread(() -> {
      try {
        for (int burst = 0; burst < HIGH_PRIORITY_ACTOR_BURSTS; burst++) {
          // Create a burst of high-priority actors
          for (int i = 0; i < HIGH_PRIORITY_ACTORS_PER_BURST; i++) {
            Flow.startActor(() -> {
              // Do some work and then exit
              for (int j = 0; j < 100; j++) {
                highPriorityOps.incrementAndGet();
                Flow.await(Flow.yieldF());
              }
              return null;
            }, TaskPriority.HIGH);
          }

          // Wait before next burst
          Thread.sleep(BURST_INTERVAL_MS);

          // Exit if benchmark duration exceeded
          if (System.currentTimeMillis() - startTime > BENCHMARK_DURATION_MS) {
            break;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    highPriorityBurstThread.start();

    // Let the benchmark run for the specified duration
    try {
      Thread.sleep(BENCHMARK_DURATION_MS);
    } catch (InterruptedException e) {
      System.out.println("Benchmark interrupted");
    }

    // Shut down the reporting thread
    reportingThread.interrupt();
    try {
      reportingThread.join(1000);
    } catch (InterruptedException e) {
      System.out.println("Interrupted while waiting for reporting thread");
    }

    // Final report
    System.out.println("\n\n=== Final Results ===");
    long totalTime = System.currentTimeMillis() - startTime;

    // Report total operations
    long finalHighOps = highPriorityOps.get();
    long finalLowOps = lowPriorityOps.get();
    System.out.printf("Benchmark duration: %.1f seconds%n", totalTime / 1000.0);
    System.out.printf("High-priority operations: %,d (%.2f ops/sec)%n",
        finalHighOps, finalHighOps * 1000.0 / totalTime);
    System.out.printf("Low-priority operations: %,d (%.2f ops/sec)%n",
        finalLowOps, finalLowOps * 1000.0 / totalTime);

    // Report activation times statistics
    long activeCount = lowPriorityActorFirstOp.size();
    System.out.printf("Low-priority actors that executed: %d of %d (%.1f%%)%n",
        activeCount, LOW_PRIORITY_ACTORS,
        100.0 * activeCount / LOW_PRIORITY_ACTORS);

    if (!lowPriorityActorFirstOp.isEmpty()) {
      // Calculate statistics on when low-priority actors first executed
      long minTime = Long.MAX_VALUE;
      long maxTime = 0;
      long sumTime = 0;

      for (Long time : lowPriorityActorFirstOp.values()) {
        minTime = Math.min(minTime, time);
        maxTime = Math.max(maxTime, time);
        sumTime += time;
      }

      double avgTime = (double) sumTime / lowPriorityActorFirstOp.size();

      System.out.println("\nLow-priority actor activation times (ms from start):");
      System.out.printf("  Minimum: %d ms%n", minTime);
      System.out.printf("  Maximum: %d ms%n", maxTime);
      System.out.printf("  Average: %.1f ms%n", avgTime);
    }

    // Shutdown
    System.out.println("Shutting down scheduler...");
    highPriorityBurstThread.interrupt();
    scheduler.shutdown();
    System.out.println("Benchmark complete.");
  }
}