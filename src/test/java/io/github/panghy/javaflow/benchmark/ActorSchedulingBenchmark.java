package io.github.panghy.javaflow.benchmark;

import io.github.panghy.javaflow.Flow;
import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.scheduler.TaskPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced benchmark to measure scheduling fairness and throughput with different actor priorities.
 * This benchmark creates actor groups with different priorities and measures their relative throughput.
 */
public class ActorSchedulingBenchmark {

  // Benchmark configuration
  private static final int TOTAL_ACTORS = 10_000;
  private static final long BENCHMARK_DURATION_MS = 60_000; // 60 seconds
  private static final long REPORTING_INTERVAL_MS = 1_000;  // 1 second

  // Define actor priority groups (key: priority level, value: percentage of actors)
  private static final Map<Integer, Integer> PRIORITY_DISTRIBUTION = Map.of(
      TaskPriority.CRITICAL, 5,   // 5% of actors at CRITICAL priority
      TaskPriority.HIGH, 15,      // 15% of actors at HIGH priority
      TaskPriority.DEFAULT, 60,   // 60% of actors at DEFAULT priority
      TaskPriority.LOW, 15,       // 15% of actors at LOW priority
      TaskPriority.IDLE, 5        // 5% of actors at IDLE priority
  );

  public static void main(String[] args) {

    // Create counters for each priority level
    Map<Integer, AtomicLong> priorityCounters = new ConcurrentHashMap<>();
    AtomicLong totalCounter = new AtomicLong(0);

    // Initialize counters for each priority
    PRIORITY_DISTRIBUTION.keySet().forEach(priority ->
        priorityCounters.put(priority, new AtomicLong(0))
    );

    // Start time tracking immediately
    final long startTime = System.currentTimeMillis();

    // Show the actor distribution
    System.out.println("Starting actors with the following distribution and measuring throughput:");
    PRIORITY_DISTRIBUTION.forEach((priority, percentage) ->
        System.out.printf("- Priority %d: %d%% (%d actors)%n",
            priority, percentage, TOTAL_ACTORS * percentage / 100)
    );

    // Launch actors for each priority level
    List<CompletableFuture<Void>> actors = new ArrayList<>(TOTAL_ACTORS);
    PRIORITY_DISTRIBUTION.forEach((priority, percentage) -> {
      int actorCount = TOTAL_ACTORS * percentage / 100;
      for (int i = 0; i < actorCount; i++) {
        CompletableFuture<Void> actor = Flow.startActor(() -> {
          // No need to signal startup

          // Run until interrupted
          while (true) {
            // Update both the priority-specific counter and the total counter
            priorityCounters.get(priority).incrementAndGet();
            totalCounter.incrementAndGet();

            // Yield to allow other actors to run
            Flow.await(Flow.yieldF());
          }
        }, priority);
        actors.add(actor);
      }
    });

    // Start a reporting thread for continuous monitoring
    Thread reportingThread = new Thread(() -> {
      try {
        // Local variables for this thread
        long localLastReportTime = startTime;
        Map<Integer, Long> localLastCounts = new ConcurrentHashMap<>();
        PRIORITY_DISTRIBUTION.keySet().forEach(priority ->
            localLastCounts.put(priority, 0L)
        );
        long localLastTotalCount = 0;

        while (System.currentTimeMillis() - startTime < BENCHMARK_DURATION_MS) {
          // Sleep for the reporting interval
          Thread.sleep(REPORTING_INTERVAL_MS);

          // Get current time and counts
          long now = System.currentTimeMillis();
          Map<Integer, Long> currentCounts = new ConcurrentHashMap<>();
          PRIORITY_DISTRIBUTION.keySet().forEach(priority ->
              currentCounts.put(priority, priorityCounters.get(priority).get())
          );
          long currentTotalCount = totalCounter.get();

          // Calculate elapsed time
          long intervalMs = now - localLastReportTime;
          long totalElapsedMs = now - startTime;

          // Print header for this report
          System.out.printf("%n=== Throughput Report (%.1f seconds elapsed) ===%n",
              totalElapsedMs / 1000.0);

          // Calculate and report throughput for each priority
          System.out.println("Priority-level throughput:");
          PRIORITY_DISTRIBUTION.keySet().stream().sorted().forEach(priority -> {
            long count = currentCounts.get(priority);
            long lastCount = localLastCounts.get(priority);
            long diff = count - lastCount;

            double opsPerSec = (diff * 1000.0) / intervalMs;
            int actorCount = TOTAL_ACTORS * PRIORITY_DISTRIBUTION.get(priority) / 100;
            double opsPerActorPerSec = opsPerSec / actorCount;

            System.out.printf("- Priority %d: %.2f ops/sec total (%.2f ops/actor/sec)%n",
                priority, opsPerSec, opsPerActorPerSec);

            // Update for next iteration
            localLastCounts.put(priority, count);
          });

          // Calculate and report overall throughput
          long totalDiff = currentTotalCount - localLastTotalCount;
          double totalOpsPerSec = (totalDiff * 1000.0) / intervalMs;
          double avgOpsPerActorPerSec = totalOpsPerSec / TOTAL_ACTORS;
          System.out.printf("Overall: %.2f ops/sec (%.2f ops/actor/sec)%n",
              totalOpsPerSec, avgOpsPerActorPerSec);

          // Update for next iteration
          localLastReportTime = now;
          localLastTotalCount = currentTotalCount;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    // Start the reporting thread
    reportingThread.setDaemon(true);
    reportingThread.start();

    // Wait for benchmark duration
    try {
      Thread.sleep(BENCHMARK_DURATION_MS);
    } catch (InterruptedException e) {
      System.out.println("Benchmark interrupted");
    }

    // Clean up reporting thread
    reportingThread.interrupt();
    try {
      reportingThread.join(1000);
    } catch (InterruptedException e) {
      System.out.println("Interrupted while waiting for reporting thread");
    }
    // Reporting is now handled by the reporting thread

    // Final report
    long endTime = System.currentTimeMillis();
    long totalRuntime = endTime - startTime;
    System.out.printf("%n%n=== Final Results (after %.1f seconds) ===%n",
        totalRuntime / 1000.0);

    // Report total operations by priority
    System.out.println("Total operations by priority:");
    PRIORITY_DISTRIBUTION.keySet().stream().sorted().forEach(priority -> {
      long count = priorityCounters.get(priority).get();
      int actorCount = TOTAL_ACTORS * PRIORITY_DISTRIBUTION.get(priority) / 100;
      double percentage = 100.0 * count / totalCounter.get();

      System.out.printf("- Priority %d: %,d ops (%.2f%% of total, %,d ops/actor)%n",
          priority, count, percentage, count / actorCount);
    });

    // Report overall statistics
    long finalCount = totalCounter.get();
    double overallOpsPerSec = (finalCount * 1000.0) / totalRuntime;
    System.out.printf("%nTotal operations: %,d%n", finalCount);
    System.out.printf("Overall throughput: %.2f ops/sec (%.2f ops/actor/sec)%n",
        overallOpsPerSec, overallOpsPerSec / TOTAL_ACTORS);

    // Shutdown
    System.out.println("Shutting down scheduler...");
    Flow.shutdown();
    System.out.println("Benchmark complete.");
  }
}