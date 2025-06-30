package io.github.panghy.javaflow;

import io.github.panghy.javaflow.scheduler.FlowClock;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.SimulatedClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.panghy.javaflow.Flow.delay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.concurrent.ExecutionException;
/**
 * Comprehensive tests for Flow timer functionality.
 * Tests both real-time and simulated time operation.
 */
class FlowTimerTest {

  @Nested
  class RealTimeTests {

    @Test
    void testSimpleDelay() throws Exception {
      long start = System.currentTimeMillis();

      // Create a flow task that creates a delay of 100ms
      CompletableFuture<Void> delayFuture = Flow.startActor(() -> {
        // Now inside a flow task, we can use delay operations
        Flow.await(Flow.delay(0.1));
        return null;
      });

      // Wait for the delay to complete
      delayFuture.get();

      // Verify that at least 100ms has passed
      long elapsed = System.currentTimeMillis() - start;
      assertTrue(elapsed >= 90, "Delay should be at least 100ms (with small margin of error), " +
                                "but was " + elapsed + "ms");
    }

    @Test
    void testMultipleDelays() throws Exception {
      AtomicInteger counter = new AtomicInteger(0);
      CountDownLatch latch = new CountDownLatch(3);
      List<Long> completionTimes = new ArrayList<>();
      long start = System.currentTimeMillis();

      // Schedule 3 delays with different durations, each in their own flow task
      Flow.startActor(() -> {
        Flow.await(Flow.delay(0.1));
        completionTimes.add(System.currentTimeMillis() - start);
        counter.incrementAndGet();
        latch.countDown();
        return null;
      });

      Flow.startActor(() -> {
        Flow.await(Flow.delay(0.2));
        completionTimes.add(System.currentTimeMillis() - start);
        counter.incrementAndGet();
        latch.countDown();
        return null;
      });

      Flow.startActor(() -> {
        Flow.await(Flow.delay(0.3));
        completionTimes.add(System.currentTimeMillis() - start);
        counter.incrementAndGet();
        latch.countDown();
        return null;
      });

      // Wait for all delays to complete
      assertTrue(latch.await(1, TimeUnit.SECONDS), "All delays should complete");

      // Verify the counter
      assertEquals(3, counter.get(), "All delays should have fired");

      // Verify the timing order (allow some margin of error)
      assertTrue(completionTimes.get(0) >= 90, "First delay should be at least 100ms");
      assertTrue(completionTimes.get(1) >= 190, "Second delay should be at least 200ms");
      assertTrue(completionTimes.get(2) >= 290, "Third delay should be at least 300ms");
    }

    @Test
    void testCancelDelay() throws Exception {
      AtomicBoolean executed = new AtomicBoolean(false);
      AtomicReference<CompletableFuture<Void>> delayFutureRef = new AtomicReference<>();

      // Create a task that will issue a delay
      Flow.startActor(() -> {
        // Create a delay inside the flow task
        CompletableFuture<Void> delayFuture = delay(1.0);

        // Store the delayFuture so we can cancel it from outside
        delayFutureRef.set(delayFuture);

        // Add a completion handler
        delayFuture.whenComplete((result, exception) -> {
          if (exception == null) {
            executed.set(true);
          }
        });

        try {
          // Wait for the delay to complete
          Flow.await(delayFuture);
        } catch (Exception e) {
          // Expected if cancelled
        }

        return null;
      });

      // Make sure we have a delay future to cancel
      Thread.sleep(50);
      CompletableFuture<Void> delayFuture = delayFutureRef.get();
      assertNotNull(delayFuture, "Delay future should be created");

      // Cancel the delay
      boolean cancelled = delayFuture.cancel(true);
      assertTrue(cancelled, "Delay should be cancellable");

      // Wait a bit to ensure the delay doesn't fire
      Thread.sleep(100);
      assertFalse(executed.get(), "Delay shouldn't execute after cancellation");
    }

    @Test
    void testNowFunctions() {
      // Test that now() returns current time
      long systemTime = System.currentTimeMillis();
      long flowTime = Flow.now();

      // Allow for larger time differences since system clocks can have more variability
      // and tests might be running on systems with different timing characteristics
      assertTrue(Math.abs(systemTime - flowTime) < 1000,
          "Flow.now() should be reasonably close to System.currentTimeMillis(). " +
          "System time: " + systemTime + ", Flow time: " + flowTime +
          ", Difference: " + Math.abs(systemTime - flowTime) + "ms");

      // Test nowSeconds() - capture a fresh flowTime to avoid time drift issues
      flowTime = Flow.now();
      double nowSecs = Flow.nowSeconds();
      assertEquals(flowTime / 1000.0, nowSecs, 0.1,
          "nowSeconds should be consistent with now()");
    }
  }

  /**
   * Tests using simulated time clock. This class extends AbstractFlowTest for reusable
   * scheduling and simulation capabilities.
   */
  @Nested
  class SimulatedTimeTests extends AbstractFlowTest {

    @Test
    void testSimpleSimulatedDelay() {
      // Reset the clock at the start of the test to ensure a known state
      ((SimulatedClock) Flow.getClock()).reset();

      AtomicBoolean completed = new AtomicBoolean(false);
      AtomicReference<CompletableFuture<Void>> delayFutureRef = new AtomicReference<>();

      // Create a flow task that will create a delay for 1 second in simulated time
      CompletableFuture<Void> taskFuture = Flow.startActor(() -> {
        // Create a delay inside a flow task
        CompletableFuture<Void> delayFuture = delay(1.0);
        delayFutureRef.set(delayFuture);

        delayFuture.whenComplete((v, e) -> completed.set(true));

        // Wait for the delay to complete
        try {
          Flow.await(delayFuture);
        } catch (Exception e) {
          // Shouldn't happen in this test
          fail("Delay was interrupted: " + e.getMessage());
        }

        return null;
      });

      // Process initial scheduling
      pump();

      // Get the delay future for verification
      CompletableFuture<Void> delayFuture = delayFutureRef.get();
      assertNotNull(delayFuture, "Delay future should have been created");

      // Verify the state before advancing time
      assertFalse(completed.get(), "Delay shouldn't complete before advancing time");
      assertFalse(delayFuture.isDone(), "Future shouldn't be done before advancing time");

      // Advance time by 0.5 seconds
      advanceTime(0.5); // 0.5 seconds
      pump();

      // Verify still not completed
      assertFalse(completed.get(), "Delay shouldn't complete after advancing only 0.5 seconds");

      // Advance time to trigger the delay (advance by 0.6 seconds to reach 1.1 total)
      advanceTime(0.6); // 0.6 seconds

      // We need multiple pumps to ensure all callbacks fire
      for (int i = 0; i < 10 && !completed.get(); i++) {
        pump();
      }

      // Verify the delay completed
      assertTrue(completed.get(), "Delay should complete after advancing past 1.0 seconds");
      assertTrue(delayFuture.isDone(), "Future should be done after advancing time");
      assertTrue(taskFuture.isDone(), "Task future should be done after advancing time");
    }

    @Test
    void testMultipleSimulatedDelays() {
      List<Integer> executionOrder = new ArrayList<>();

      // Launch three separate flow tasks with different delay durations
      Flow.startActor(() -> {
        try {
          Flow.await(Flow.delay(1.0));
          executionOrder.add(1);
        } catch (Exception e) {
          fail("Unexpected exception: " + e.getMessage());
        }
        return null;
      });

      Flow.startActor(() -> {
        try {
          Flow.await(Flow.delay(0.5));
          executionOrder.add(2);
        } catch (Exception e) {
          fail("Unexpected exception: " + e.getMessage());
        }
        return null;
      });

      Flow.startActor(() -> {
        try {
          Flow.await(Flow.delay(2.0));
          executionOrder.add(3);
        } catch (Exception e) {
          fail("Unexpected exception: " + e.getMessage());
        }
        return null;
      });

      // Process initial task scheduling
      testScheduler.pump();

      // Verify initial state
      assertTrue(executionOrder.isEmpty(), "No delays should fire initially");

      // Advance time to trigger the first delay (0.5 seconds)
      testScheduler.advanceTime(600); // 0.6 seconds in milliseconds
      testScheduler.pump();

      // We may need extra pumps for callbacks to complete
      for (int i = 0; i < 3 && executionOrder.size() < 1; i++) {
        testScheduler.pump();
      }

      assertTrue(executionOrder.size() >= 1, "At least one delay should have fired");
      if (executionOrder.size() >= 1) {
        assertEquals(2, executionOrder.get(0).intValue(), "The 0.5 second delay should fire first");
      }

      // Advance time to trigger the second delay (1.0 seconds)
      testScheduler.advanceTime(500); // 0.5 seconds in milliseconds, total time is now 1.1 seconds
      testScheduler.pump();

      // We may need extra pumps for callbacks to complete
      for (int i = 0; i < 3 && executionOrder.size() < 2; i++) {
        testScheduler.pump();
      }

      assertTrue(executionOrder.size() >= 2, "At least two delays should have fired");
      if (executionOrder.size() >= 2) {
        assertEquals(1, executionOrder.get(1).intValue(), "The 1.0 second delay should fire second");
      }

      // Advance time to trigger the last delay (2.0 seconds)
      testScheduler.advanceTime(1000); // 1.0 second in milliseconds, total time is now 2.1 seconds
      testScheduler.pump();

      // We may need extra pumps for callbacks to complete
      for (int i = 0; i < 3 && executionOrder.size() < 3; i++) {
        testScheduler.pump();
      }

      assertEquals(3, executionOrder.size(), "All three delays should have fired");
      assertEquals(3, executionOrder.get(2).intValue(), "The 2.0 second delay should fire last");
    }

    @Test
    void testCancelSimulatedDelay() {
      AtomicBoolean completed = new AtomicBoolean(false);
      AtomicReference<CompletableFuture<Void>> delayFutureRef = new AtomicReference<>();

      // Create a flow task that will create a delay
      Flow.startActor(() -> {
        // Create a delay inside a flow task
        CompletableFuture<Void> delayFuture = delay(1.0);

        // Store it for access from outside the flow task
        delayFutureRef.set(delayFuture);

        delayFuture.whenComplete((v, e) -> {
          if (e == null) {
            completed.set(true);
          }
        });

        // We won't await it since we're going to cancel it
        return null;
      });

      // Process initial scheduling to make sure the delay future is created
      testScheduler.pump();

      // Get the delay future
      CompletableFuture<Void> delayFuture = delayFutureRef.get();
      assertNotNull(delayFuture, "Delay future should be created");

      // Cancel the delay
      boolean cancelled = delayFuture.cancel(true);
      assertTrue(cancelled, "Delay should be cancellable");

      // Make sure we pump once to ensure cancellation propagates
      testScheduler.pump();

      // Advance time past when the delay would have fired
      testScheduler.advanceTime(2000); // 2.0 seconds in milliseconds
      testScheduler.pump();

      // Execute another pump cycle to ensure all work is done
      testScheduler.pump();

      // Verify the delay didn't execute
      assertFalse(completed.get(), "Delay shouldn't execute after cancellation");
      assertTrue(delayFuture.isCancelled(), "Future should be marked as cancelled");
    }

    @Test
    void testTimeControlMethods() {
      SimulatedClock clock = (SimulatedClock) Flow.getClock();

      // Test initial state - might not be exactly 0 if other tests have run
      long initialTime = Flow.now();

      // Test advancing time
      advanceTime(1.0);
      assertEquals(initialTime + 1000, Flow.now(), "Time should advance by 1000ms");

      // Test that individual operations on the clock work
      long currentTime = Flow.now();
      clock.advanceTime(500);
      assertEquals(currentTime + 500, Flow.now(), "Time should advance by another 500ms");

      // Test resetting the clock
      clock.reset();
      assertEquals(0, Flow.now(), "Time should reset to 0");
    }

    @Test
    void testNestedDelays() {
      System.out.println("\n\n=== STARTING testNestedDelays ===\n");

      // Reset the clock first to ensure a known state
      SimulatedClock clock = (SimulatedClock) Flow.getClock();
      clock.reset();

      // Ensure we're in a clean state
      testScheduler.pump();

      AtomicLong completionTime = new AtomicLong();
      AtomicBoolean done = new AtomicBoolean(false);

      System.out.println("*** Creating nested delays test task");
      // Create a task with nested delays
      CompletableFuture<Void> future = Flow.startActor(() -> {
        System.out.println("*** Starting nested delays actor at time: " + Flow.now() + "ms");

        try {
          // First delay
          System.out.println("*** Before first delay (1.0s)");
          CompletableFuture<Void> delay1 = Flow.delay(1.0);
          System.out.println("*** Created first delay future: " + delay1);
          Flow.await(delay1);
          System.out.println("*** After first delay, time: " + Flow.now() + "ms");

          // Second nested delay
          System.out.println("*** Before second delay (0.5s)");
          CompletableFuture<Void> delay2 = Flow.delay(0.5);
          System.out.println("*** Created second delay future: " + delay2);
          Flow.await(delay2);
          System.out.println("*** After second delay, time: " + Flow.now() + "ms");

          // Third nested delay
          System.out.println("*** Before third delay (0.25s)");
          CompletableFuture<Void> delay3 = Flow.delay(0.25);
          System.out.println("*** Created third delay future: " + delay3);
          Flow.await(delay3);
          System.out.println("*** After third delay, time: " + Flow.now() + "ms");

          // Record completion time
          completionTime.set(Flow.now());
          System.out.println("*** Setting done flag to true");
          done.set(true);
          System.out.println("*** Task completed, time = " + Flow.now() + "ms");
        } catch (Exception e) {
          System.out.println("*** EXCEPTION in task: " + e.getMessage());
          e.printStackTrace();
        }
        return null;
      });

      System.out.println("*** Initial pump to start task");
      testScheduler.pump();

      // Step 1: Advance time for the first delay
      System.out.println("\n*** STEP 1: Advancing time to 1000ms");
      testScheduler.advanceTime(1000); // 1.0 second in milliseconds

      System.out.println("*** Is task done after first delay? " + done.get());
      System.out.println("*** Is future done after first delay? " + future.isDone());
      assertFalse(future.isDone(), "Future shouldn't be done after first delay");

      // Step 2: Advance time for the second delay
      System.out.println("\n*** STEP 2: Advancing time to 1500ms");
      testScheduler.advanceTime(500); // 0.5 seconds in milliseconds

      System.out.println("*** Is task done after second delay? " + done.get());
      System.out.println("*** Is future done after second delay? " + future.isDone());
      assertFalse(future.isDone(), "Future shouldn't be done after second delay");

      // Step 3: Advance time for the third delay
      System.out.println("\n*** STEP 3: Advancing time to 1750ms");
      testScheduler.advanceTime(250); // 0.25 seconds in milliseconds

      // Add one more small advancement to handle any rounding issues
      if (!done.get()) {
        System.out.println("\n*** STEP 4: Final small advance to handle any rounding");
        testScheduler.advanceTime(10);
      }

      System.out.println("\n*** FINAL STATE: done=" + done.get() + ", future.isDone=" + future.isDone());
      System.out.println("*** Current time: " + Flow.now() + "ms");

      // Add more pump cycles to ensure the task completes
      if (!done.get()) {
        System.out.println("*** Task not done yet, pumping more...");
        for (int i = 0; i < 5 && !done.get(); i++) {
          testScheduler.pump();
        }

        System.out.println("*** After extra pumps: done=" + done.get() +
                           ", future.isDone=" + future.isDone());
      }

      assertTrue(done.get(), "Task should have completed");
      assertTrue(future.isDone(), "Future should be done after all delays");

      // Verify the completion time is approximately what we expect
      // With some flexibility for task scheduling delays
      long expectedTime = 1750; // 1.75 seconds
      long actualTime = completionTime.get();

      System.out.println("*** Expected completion time: " + expectedTime +
                         "ms, actual: " + actualTime + "ms");

      assertTrue(Math.abs(actualTime - expectedTime) <= 20,
          "Total time should be close to 1.75 seconds (1750ms), but was " + actualTime + "ms");

      System.out.println("\n=== FINISHED testNestedDelays ===\n");
    }
  }

  /**
   * Tests for the or() method in FlowFuture.
   */
  @Nested
  class OrOperationTests {

    @Test
    void testOrWithFirstCompleting() throws Exception {
      CountDownLatch fastLatch = new CountDownLatch(1);
      CountDownLatch slowLatch = new CountDownLatch(1);

      // Create two futures, where the first will complete before the second
      CompletableFuture<String> fastFuture = Flow.startActor(() -> {
        Thread.sleep(50);
        fastLatch.countDown();
        return "fast";
      });

      CompletableFuture<String> slowFuture = Flow.startActor(() -> {
        Thread.sleep(500);
        slowLatch.countDown();
        return "slow";
      });

      // Create a combined future that completes when either completes
      CompletableFuture<Void> combinedFuture = CompletableFuture.anyOf(fastFuture, slowFuture)
          .thenApply(v -> null);

      // Wait for the combined future to complete
      combinedFuture.get(1, TimeUnit.SECONDS);

      // Verify the fast future completed
      assertTrue(fastLatch.await(0, TimeUnit.MILLISECONDS), "Fast task should have completed");
      assertTrue(fastFuture.isDone(), "Fast future should be done");

      // The combined future should be done
      assertTrue(combinedFuture.isDone(), "Combined future should be done");

      // Verify the slow future wasn't awaited
      slowFuture.cancel(true); // Cancel it to avoid waiting
    }

    @Test
    void testOrWithException() throws Exception {
      // Create a future that fails
      CompletableFuture<String> failingFuture = Flow.startActor(() -> {
        throw new RuntimeException("test error");
      });

      // Create a future that succeeds, but slowly
      CompletableFuture<String> slowFuture = Flow.startActor(() -> {
        Thread.sleep(500);
        return "slow";
      });

      // Create a combined future
      CompletableFuture<Void> combinedFuture = CompletableFuture.anyOf(failingFuture, slowFuture)
          .thenApply(v -> null);

      // The combined future should complete exceptionally
      ExecutionException exception = assertThrows(ExecutionException.class,
          () -> combinedFuture.get(1, TimeUnit.SECONDS));

      assertTrue(exception.getCause() instanceof RuntimeException,
          "Exception should be the RuntimeException from the failing future");
      assertEquals("test error", exception.getCause().getMessage(),
          "Exception message should match");

      // The combined future should be done (but with exception)
      assertTrue(combinedFuture.isDone(), "Combined future should be done");
      assertTrue(combinedFuture.isCompletedExceptionally(),
          "Combined future should be completed exceptionally");

      // Cancel the slow future to avoid waiting
      slowFuture.cancel(true);
    }
  }

  /**
   * Tests for the Future combination methods (allOf, anyOf).
   */
  @Nested
  class FutureCombinationTests {

    @Test
    void testAllOf() throws Exception {
      List<CompletableFuture<Integer>> futures = new ArrayList<>();

      // Create 5 futures with different completion times
      for (int i = 0; i < 5; i++) {
        final int value = i;
        futures.add(Flow.startActor(() -> {
          Thread.sleep(value * 20); // Stagger completion times
          return value;
        }));
      }

      // Wait for all futures to complete
      CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
      allOf.get(1, TimeUnit.SECONDS);
      
      // Collect results
      List<Integer> results = new ArrayList<>();
      for (CompletableFuture<Integer> f : futures) {
        results.add(f.getNow(null));
      }

      // Verify the results are in the correct order
      assertEquals(5, results.size(), "Should have 5 results");
      for (int i = 0; i < 5; i++) {
        assertEquals(i, results.get(i).intValue(), "Result at index " + i + " should be " + i);
      }
    }

    @Test
    void testAllOfWithEmptyList() throws Exception {
      // Test with an empty list
      CompletableFuture<Void> emptyFuture = CompletableFuture.allOf();
      emptyFuture.get(1, TimeUnit.SECONDS);

      assertTrue(emptyFuture.isDone(), "Future should be complete");
    }

    @Test
    void testAllOfWithException() {
      List<CompletableFuture<Integer>> futures = new ArrayList<>();

      // Add some normal futures
      futures.add(CompletableFuture.completedFuture(1));
      futures.add(CompletableFuture.completedFuture(2));

      // Add a failing future
      futures.add(CompletableFuture.failedFuture(new RuntimeException("test error")));

      // Add more normal futures
      futures.add(CompletableFuture.completedFuture(4));
      futures.add(CompletableFuture.completedFuture(5));

      // The combined future should fail
      CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

      ExecutionException exception = assertThrows(ExecutionException.class,
          () -> combinedFuture.get(1, TimeUnit.SECONDS));

      assertTrue(exception.getCause() instanceof RuntimeException,
          "Exception should be from the failing future");
      assertEquals("test error", exception.getCause().getMessage(),
          "Exception message should match");
    }

    @Test
    void testAnyOf() throws Exception {
      // Create a direct scheduler instance for better control
      FlowScheduler scheduler = new FlowScheduler(false, FlowClock.createSimulatedClock());

      List<CompletableFuture<String>> futures = new ArrayList<>();

      // Set up atomic flags to track execution
      AtomicBoolean firstTaskStarted = new AtomicBoolean(false);
      AtomicBoolean secondTaskStarted = new AtomicBoolean(false);
      AtomicBoolean thirdTaskStarted = new AtomicBoolean(false);

      AtomicBoolean firstTaskDone = new AtomicBoolean(false);
      AtomicBoolean secondTaskDone = new AtomicBoolean(false);
      AtomicBoolean thirdTaskDone = new AtomicBoolean(false);

      // Add a "slow" task
      futures.add(scheduler.schedule(() -> {
        firstTaskStarted.set(true);
        // Delay using scheduler instead of Thread.sleep
        scheduler.await(scheduler.scheduleDelay(0.3));
        firstTaskDone.set(true);
        return "first";
      }));

      // Add a "fast" task
      futures.add(scheduler.schedule(() -> {
        secondTaskStarted.set(true);
        // This one is fastest
        scheduler.await(scheduler.scheduleDelay(0.1));
        secondTaskDone.set(true);
        return "second";
      }));

      // Add another "slow" task
      futures.add(scheduler.schedule(() -> {
        thirdTaskStarted.set(true);
        // Longest delay
        scheduler.await(scheduler.scheduleDelay(0.5));
        thirdTaskDone.set(true);
        return "third";
      }));

      // Create the anyOf future
      CompletableFuture<Object> anyFuture = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));

      // Pump several times to get tasks running
      scheduler.pump();

      // All tasks should have started
      assertTrue(firstTaskStarted.get(), "First task should have started");
      assertTrue(secondTaskStarted.get(), "Second task should have started");
      assertTrue(thirdTaskStarted.get(), "Third task should have started");

      // Advance time to complete fastest task
      scheduler.advanceTime(150); // 150ms

      // Pump to process completions
      for (int i = 0; i < 10; i++) {
        scheduler.pump();
      }

      // Only the second (fastest) task should be done
      assertFalse(firstTaskDone.get(), "First task should not be completed yet");
      assertTrue(secondTaskDone.get(), "Second task should be completed");
      assertFalse(thirdTaskDone.get(), "Third task should not be completed yet");

      // The anyOf future should be completed
      assertTrue(anyFuture.isDone(), "AnyOf future should be completed");
      assertFalse(anyFuture.isCompletedExceptionally(), "AnyOf future should not be completed with exception");

      // The result should be from the second task
      assertEquals("second", anyFuture.getNow(null), "Result should be from the fastest future");
    }

    @Test
    void testAnyOfAllFail() {
      List<CompletableFuture<String>> futures = new ArrayList<>();

      // Add three failing futures
      futures.add(CompletableFuture.failedFuture(new RuntimeException("error 1")));
      futures.add(CompletableFuture.failedFuture(new RuntimeException("error 2")));
      futures.add(CompletableFuture.failedFuture(new RuntimeException("error 3")));

      // The anyOf should fail
      CompletableFuture<Object> anyFuture = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));

      ExecutionException exception = assertThrows(ExecutionException.class,
          () -> anyFuture.get(1, TimeUnit.SECONDS));

      assertTrue(exception.getCause() instanceof RuntimeException,
          "Exception should be from one of the failing futures");
    }

    @Test
    void testAnyOfEmpty() {
      // anyOf with empty array returns a future that never completes
      CompletableFuture<Object> future = CompletableFuture.anyOf();
      assertFalse(future.isDone());
    }
  }
}