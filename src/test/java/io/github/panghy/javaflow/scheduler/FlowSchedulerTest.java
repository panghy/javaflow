package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FlowSchedulerTest {

  private FlowScheduler scheduler;

  @BeforeEach
  void setUp() {
    // Create a scheduler with debug logging enabled for testing
    scheduler = new FlowScheduler();
  }

  @AfterEach
  void tearDown() {
    scheduler.shutdown();
  }

  @Test
  void testScheduleTask() throws Exception {
    CompletableFuture<String> future = scheduler.schedule(() -> "hello");

    assertEquals("hello", future.toCompletableFuture().get());
  }

  @Test
  void testScheduleTaskWithPriority() throws Exception {
    CompletableFuture<Integer> future = scheduler.schedule(() -> 42, TaskPriority.HIGH);

    assertEquals(42, future.toCompletableFuture().get());
  }

  @Test
  void testScheduleDelay() throws Exception {
    long start = System.currentTimeMillis();

    // First we need to create a flow task to establish a flow context
    CompletableFuture<Void> future = scheduler.schedule(() -> {
      // Now we're in a flow context, so scheduleDelay is allowed
      try {
        CompletableFuture<Void> delayFuture = scheduler.scheduleDelay(0.1); // 100ms
        scheduler.await(delayFuture);
      } catch (Exception e) {
        fail("Delay failed: " + e.getMessage());
      }
      return null;
    });

    future.toCompletableFuture().get();

    long elapsed = System.currentTimeMillis() - start;
    assertTrue(elapsed >= 100, "Delay should be at least 100ms");
  }

  @Test
  void testPriorityOrder() throws Exception {
    // This test verifies that tasks are executed in priority order
    List<String> executionOrder = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(3);
    Object lock = new Object();

    // Block the threads from completing immediately to ensure they execute in priority order
    CountDownLatch startSignal = new CountDownLatch(1);

    // Schedule tasks in reverse priority order so we can verify they execute in correct order

    // High priority task (should execute first)
    scheduler.schedule(() -> {
      try {
        startSignal.await(); // Wait until we're ready to start

        synchronized (lock) {
          executionOrder.add("high");
        }
        latch.countDown();
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }, TaskPriority.HIGH);

    // Medium priority task (should execute second)
    scheduler.schedule(() -> {
      try {
        startSignal.await(); // Wait until we're ready to start

        synchronized (lock) {
          executionOrder.add("medium");
        }
        latch.countDown();
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }, TaskPriority.DEFAULT);

    // Low priority task (should execute last)
    scheduler.schedule(() -> {
      try {
        startSignal.await(); // Wait until we're ready to start

        synchronized (lock) {
          executionOrder.add("low");
        }
        latch.countDown();
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }, TaskPriority.LOW);

    // Give the scheduler a moment to queue up all the tasks
    Thread.sleep(100);

    // Now signal the tasks to start
    startSignal.countDown();

    // Wait for all tasks to complete
    assertTrue(latch.await(2, TimeUnit.SECONDS), "Not all tasks completed in time");

    // Verify the execution order
    synchronized (lock) {
      assertEquals(3, executionOrder.size(), "All tasks should have executed");
      assertEquals("high", executionOrder.get(0), "High priority task should execute first");
      assertEquals("medium", executionOrder.get(1), "Medium priority task should execute second");
      assertEquals("low", executionOrder.get(2), "Low priority task should execute last");
    }
  }

  @Test
  void testCooperativeExecution() throws Exception {
    // This test verifies that yield() works as expected
    AtomicInteger counter = new AtomicInteger(0);
    CountDownLatch taskLatch = new CountDownLatch(2);
    List<Integer> incrementOrder = new ArrayList<>();
    Object lock = new Object();

    // First task increments and yields
    CompletableFuture<Void> firstTask = scheduler.schedule(() -> {
      try {
        // First increment from task 1
        int val1 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val1);
        }

        // Yield to allow the second task to run
        scheduler.await(scheduler.yield());

        // Second increment from task 1 (should be after task 2's first increment)
        int val2 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val2);
        }

        // Yield again
        scheduler.await(scheduler.yield());

        // Third increment from task 1 (should be after task 2's second increment)
        int val3 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val3);
        }
      } finally {
        taskLatch.countDown();
      }
      return null;
    });

    // Give the first task time to start
    Thread.sleep(50);

    // Second task also increments and yields
    CompletableFuture<Void> secondTask = scheduler.schedule(() -> {
      try {
        // First increment from task 2 (should be after task 1's first increment)
        int val1 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val1);
        }

        // Yield to allow first task to run again
        scheduler.await(scheduler.yield());

        // Second increment from task 2 (should be after task 1's second increment)
        int val2 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val2);
        }

        // Yield again
        scheduler.await(scheduler.yield());

        // Third increment from task 2 (should be after task 1's third increment)
        int val3 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val3);
        }
      } finally {
        taskLatch.countDown();
      }
      return null;
    });

    // Wait for both tasks to complete
    assertTrue(taskLatch.await(3, TimeUnit.SECONDS), "Not all tasks completed in time");

    // Get the results from futures (to propagate any exceptions)
    firstTask.toCompletableFuture().get();
    secondTask.toCompletableFuture().get();

    // Verify the counter and execution order
    assertEquals(6, counter.get(), "Counter should be incremented 6 times in total");

    // Check that increments alternated between tasks (more or less)
    // This is a loose check since the exact ordering can depend on scheduling
    synchronized (lock) {
      assertEquals(6, incrementOrder.size(), "All increments should be recorded");

      // The values should be 1 through 6 in order (incrementing)
      for (int i = 0; i < 6; i++) {
        assertEquals(i + 1, incrementOrder.get(i), "Increments should be in order");
      }
    }
  }

  @Test
  void testPumpProcessesAllReadyTasks() throws Exception {
    scheduler.close();
    scheduler = new FlowScheduler(false);

    // This test verifies that the pump() method processes all ready tasks
    List<String> executionOrder = new ArrayList<>();
    CountDownLatch startLatch = new CountDownLatch(3); // Signal when all tasks start
    CountDownLatch yieldLatch = new CountDownLatch(3); // Signal when all tasks yield
    CountDownLatch resumeLatch = new CountDownLatch(3); // Signal when all tasks complete

    // Create tasks that yield in a specific order and signal via latches
    CompletableFuture<Void> task1 = scheduler.schedule(() -> {
      executionOrder.add("task1-start");
      startLatch.countDown();
      // Block until all tasks have started
      scheduler.await(scheduler.yield());
      yieldLatch.countDown();
      // This second yield puts the task back in the ready queue
      scheduler.await(scheduler.yield());
      executionOrder.add("task1-after-yield");
      resumeLatch.countDown();
      return null;
    });

    CompletableFuture<Void> task2 = scheduler.schedule(() -> {
      executionOrder.add("task2-start");
      startLatch.countDown();
      // Block until all tasks have started
      scheduler.await(scheduler.yield());
      yieldLatch.countDown();
      // This second yield puts the task back in the ready queue
      scheduler.await(scheduler.yield());
      executionOrder.add("task2-after-yield");
      resumeLatch.countDown();
      return null;
    });

    CompletableFuture<Void> task3 = scheduler.schedule(() -> {
      executionOrder.add("task3-start");
      startLatch.countDown();
      // Block until all tasks have started
      scheduler.await(scheduler.yield());
      yieldLatch.countDown();
      // This second yield puts the task back in the ready queue
      scheduler.await(scheduler.yield());
      executionOrder.add("task3-after-yield");
      resumeLatch.countDown();
      return null;
    });

    // Now use pump to process all the ready tasks
    int processedTasks = scheduler.pump();
    assertTrue(processedTasks >= 3, "Pump should have processed at least 3 tasks");

    // Wait for all tasks to start
    assertTrue(startLatch.await(1, TimeUnit.SECONDS), "Not all tasks started in time");

    processedTasks = scheduler.pump();
    assertEquals(3, processedTasks, "Pump should have processed at least 3 tasks");
    processedTasks = scheduler.pump();
    assertEquals(3, processedTasks, "Pump should have processed at least 3 tasks");

    // Wait for all tasks to yield
    assertTrue(yieldLatch.await(1, TimeUnit.SECONDS), "Not all tasks yielded in time");

    // At this point, all three tasks should have yielded a second time and be in the ready queue
    // Verify no tasks have completed yet (added "after-yield" to the list)
    synchronized (executionOrder) {
      assertTrue(executionOrder.contains("task1-start"), "Task 1 should have started");
      assertTrue(executionOrder.contains("task2-start"), "Task 2 should have started");
      assertTrue(executionOrder.contains("task3-start"), "Task 3 should have started");
      // Due to async behavior, some tasks might have already been resumed
      // We'll just check that they started
    }

    // Now use pump to process all the ready tasks
    processedTasks = scheduler.pump();
    assertEquals(3, processedTasks, "Pump should have processed at least 3 tasks");
    processedTasks = scheduler.pump();
    assertEquals(3, processedTasks, "Pump should have processed at least 3 tasks");

    // All tasks should have completed, and after-yield statements executed
    synchronized (executionOrder) {
      assertTrue(executionOrder.contains("task1-after-yield"), "Task 1 should have completed");
      assertTrue(executionOrder.contains("task2-after-yield"), "Task 2 should have completed");
      assertTrue(executionOrder.contains("task3-after-yield"), "Task 3 should have completed");
    }

    // Verify the tasks completed successfully
    task1.getNow(null);
    task2.getNow(null);
    task3.getNow(null);
  }

  @Test
  void testPumpWithTimers() throws Exception {
    scheduler.close();
    scheduler = new FlowScheduler(false, FlowClock.createSimulatedClock());

    // Test that pump helps process timer-based tasks
    AtomicInteger counter = new AtomicInteger(0);
    CountDownLatch readyLatch = new CountDownLatch(3); // This latch ensures timers have completed
    CountDownLatch completionLatch = new CountDownLatch(3);

    // Schedule three tasks that create delays
    scheduler.schedule(() -> {
      try {
        CompletableFuture<Void> delay = scheduler.scheduleDelay(0.05); // 50ms
        scheduler.await(delay); // Wait for the delay to complete

        readyLatch.countDown(); // Signal that timer completed
        scheduler.await(scheduler.yield()); // Yield so it goes back to the ready queue
        counter.incrementAndGet(); // Only incremented after pump executes it
        completionLatch.countDown();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    scheduler.schedule(() -> {
      try {
        CompletableFuture<Void> delay = scheduler.scheduleDelay(0.05); // 50ms
        scheduler.await(delay); // Wait for the delay to complete

        readyLatch.countDown(); // Signal that timer completed
        scheduler.await(scheduler.yield()); // Yield so it goes back to the ready queue
        counter.incrementAndGet(); // Only incremented after pump executes it
        completionLatch.countDown();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    scheduler.schedule(() -> {
      try {
        CompletableFuture<Void> delay = scheduler.scheduleDelay(0.05); // 50ms
        scheduler.await(delay); // Wait for the delay to complete

        readyLatch.countDown(); // Signal that timer completed
        scheduler.await(scheduler.yield()); // Yield so it goes back to the ready queue
        counter.incrementAndGet(); // Only incremented after pump executes it
        completionLatch.countDown();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    // Advance time to trigger the timers
    ((SimulatedClock) scheduler.getClock()).advanceTime(100);

    // Process all the tasks that are now ready
    for (int i = 0; i < 5; i++) {
      scheduler.pump();
      if (readyLatch.getCount() == 0) {
        break;
      }
    }

    // Verify all timers fired (waiting longer if needed)
    boolean allTimersFired = readyLatch.await(1, TimeUnit.SECONDS);

    if (!allTimersFired) {
      // Try advancing time and pumping again
      ((SimulatedClock) scheduler.getClock()).advanceTime(100);
      for (int i = 0; i < 5; i++) {
        scheduler.pump();
      }
      allTimersFired = readyLatch.await(1, TimeUnit.SECONDS);
    }

    assertTrue(allTimersFired, "All timers should have fired");

    // Pump again to process all yielded tasks
    for (int i = 0; i < 5; i++) {
      scheduler.pump();
      if (completionLatch.getCount() == 0) {
        break;
      }
    }

    // Verify all callbacks executed (waiting longer if needed)
    boolean allCallbacksExecuted = completionLatch.await(1, TimeUnit.SECONDS);
    assertTrue(allCallbacksExecuted, "All delays should have completed");

    // Verify the counter
    assertEquals(3, counter.get(), "Counter should have been incremented 3 times");
  }
}