package io.github.panghy.javaflow.examples;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.SimulatedClock;
import io.github.panghy.javaflow.scheduler.TaskPriority;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example demonstrating the priority aging mechanism in the JavaFlow scheduler.
 * This example shows how low-priority tasks will eventually execute even when
 * high-priority tasks are continuously added to the queue, preventing starvation.
 */
public class PriorityAgingExample {

  public static void main(String[] args) throws Exception {
    // Create a simulated clock for deterministic testing
    SimulatedClock simulatedClock = new SimulatedClock();
    simulatedClock.setCurrentTime(0); // Start at time 0

    // Create a scheduler - priority aging is enabled by default
    FlowScheduler scheduler = new FlowScheduler(false, simulatedClock);

    // Replace the global scheduler
    Flow.setScheduler(scheduler);

    // Set up counters for different priority tasks
    AtomicInteger highPriorityCount = new AtomicInteger(0);
    AtomicInteger lowPriorityCount = new AtomicInteger(0);

    // Create a high priority task that runs repeatedly
    CompletableFuture<Void> highPriorityTask = Flow.startActor(() -> {
      int count = 0;
      while (count < 100) { // Run 100 iterations
        count++;
        highPriorityCount.incrementAndGet();
        System.out.println("[Time " + scheduler.currentTimeMillis() +
                           "ms] High priority task iteration: " + count);

        // Yield after each iteration to give other tasks a chance
        Flow.await(Flow.yieldF());
      }
      return null;
    }, TaskPriority.HIGH); // HIGH priority

    // Create a low priority task that runs repeatedly
    CompletableFuture<Void> lowPriorityTask = Flow.startActor(() -> {
      int count = 0;
      while (count < 50) { // Run 50 iterations
        count++;
        lowPriorityCount.incrementAndGet();
        System.out.println("[Time " + scheduler.currentTimeMillis() +
                           "ms] Low priority task iteration: " + count);

        // Yield after each iteration
        Flow.await(Flow.yieldF());
      }
      return null;
    }, TaskPriority.LOW); // LOW priority

    // Run the simulation in a loop
    System.out.println("Starting simulation with priority aging enabled...");

    // Run for 20 time steps, advancing time by 500ms each step
    for (int i = 0; i < 20; i++) {
      System.out.println("\n== Time step " + i + " ==");

      // Process all ready tasks
      scheduler.pump();

      // Add a new high priority task to simulate continuous high-priority load
      if (i % 2 == 0) { // Every other step
        Flow.startActor(() -> {
          highPriorityCount.incrementAndGet();
          System.out.println("[Time " + scheduler.currentTimeMillis() +
                             "ms] New high priority one-off task");
          return null;
        }, TaskPriority.HIGH);
      }

      // Show current counts
      System.out.println("Current stats - High priority: " + highPriorityCount.get() +
                         ", Low priority: " + lowPriorityCount.get());

      // Advance simulated time by 500ms
      simulatedClock.advanceTime(500);
      scheduler.pump(); // Process any tasks that became ready
    }

    // Print final results
    System.out.println("\nFinal results:");
    System.out.println("High priority task executions: " + highPriorityCount.get());
    System.out.println("Low priority task executions: " + lowPriorityCount.get());

    // Without priority aging, the low priority tasks would be starved by the continuous
    // high priority tasks. With aging enabled, low priority tasks will eventually
    // get enough priority boost to execute.
  }
}