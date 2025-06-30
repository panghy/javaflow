package io.github.panghy.javaflow.examples;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.scheduler.TaskPriority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.yieldF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example test that demonstrates the single-threaded scheduler with priorities
 * and cooperative yield working together in a more complex scenario.
 */
public class SingleThreadedActorExample {

  /**
   * This test simulates actors that need to cooperate to process work.
   * We have:
   * - Work generator actor (high priority) that creates work items
   * - Multiple worker actors (medium priority) that process items
   * - Logger actor (low priority) that logs progress
   */
  @Test
  void testWorkloadWithCooperativeActors() throws Exception {
    // Number of work items to process
    final int workItemCount = 20;

    // Number of worker actors
    final int workerCount = 3;

    // Track completion
    CountDownLatch workFinishedLatch = new CountDownLatch(workItemCount);
    CountDownLatch loggerFinishedLatch = new CountDownLatch(1);

    // Work items queue - in a real system you might use a proper queue
    // but this is fine for testing
    List<Integer> workQueue = new ArrayList<>();

    // Track which worker processed each item
    List<String> processingLog = new ArrayList<>();

    // Track total processed work
    AtomicInteger processedCount = new AtomicInteger(0);

    // Generator actor - creates work and adds it to the queue
    CompletableFuture<Void> generator = Flow.startActor(() -> {
      try {
        System.out.println("Generator starting");

        // Create workItemCount work items
        for (int i = 0; i < workItemCount; i++) {
          // Add a work item to the queue
          workQueue.add(i);
          System.out.println("Generated work item " + i);

          // Yield after each item to allow workers to process
          await(yieldF());
        }

        System.out.println("Generator finished");
      } catch (Exception e) {
        System.err.println("Generator error: " + e.getMessage());
      }
      return null;
    });

    // Worker actors - process items from the queue
    List<CompletableFuture<Void>> workers = new ArrayList<>();
    for (int w = 0; w < workerCount; w++) {
      final int workerId = w;
      CompletableFuture<Void> worker = Flow.startActor(() -> {
        try {
          System.out.println("Worker " + workerId + " starting");

          while (processedCount.get() < workItemCount) {
            Integer workItem = null;

            // Try to get a work item from the queue
            if (!workQueue.isEmpty()) {
              workItem = workQueue.remove(0);
            }

            if (workItem != null) {
              // Process the work item
              String workerName = "Worker-" + workerId;
              System.out.println(workerName + " processing item " + workItem);

              // Simulate work with a yield
              await(yieldF());

              // Record which worker processed this item
              processingLog.add(workerName + ":" + workItem);

              // Mark as processed
              processedCount.incrementAndGet();
              workFinishedLatch.countDown();

              // Yield to give other workers a chance
              await(yieldF());
            } else {
              // No work available, yield and check again
              await(yieldF());
            }
          }

          System.out.println("Worker " + workerId + " finished");
        } catch (Exception e) {
          System.err.println("Worker " + workerId + " error: " + e.getMessage());
        }
        return null;
      });
      workers.add(worker);
    }

    // Logger actor - periodically logs progress
    CompletableFuture<Void> logger = Flow.startActor(() -> {
      try {
        System.out.println("Logger starting");

        while (processedCount.get() < workItemCount) {
          // Log progress
          int current = processedCount.get();
          System.out.println("Progress: " + current + "/" + workItemCount +
                             " (" + (current * 100 / workItemCount) + "%)");

          // Do a longer yield equivalent to a delay
          for (int i = 0; i < 3; i++) {
            await(yieldF());
          }
        }

        // Log final status
        System.out.println("Final progress: " + processedCount.get() + "/" + workItemCount +
                           " (100%)");

        loggerFinishedLatch.countDown();
        System.out.println("Logger finished");
      } catch (Exception e) {
        System.err.println("Logger error: " + e.getMessage());
      }
      return null;
    }, TaskPriority.LOW); // Logger has low priority

    // Wait for all work to be processed
    assertTrue(workFinishedLatch.await(5, TimeUnit.SECONDS),
        "Not all work completed in time");

    // Wait for logger to finish
    assertTrue(loggerFinishedLatch.await(1, TimeUnit.SECONDS),
        "Logger did not finish in time");

    // Verify all work was processed
    assertEquals(workItemCount, processedCount.get(),
        "All work items should have been processed");

    // Verify we have logs for all items
    assertEquals(workItemCount, processingLog.size(),
        "Processing log should contain all items");

    // Print out which worker handled each item
    System.out.println("Processing distribution:");
    for (String entry : processingLog) {
      System.out.println("  " + entry);
    }
  }
}