package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
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
    FlowFuture<String> future = scheduler.schedule(() -> "hello");
    
    assertEquals("hello", future.get());
  }

  @Test
  void testScheduleTaskWithPriority() throws Exception {
    FlowFuture<Integer> future = scheduler.schedule(() -> 42, TaskPriority.HIGH);
    
    assertEquals(42, future.get());
  }

  @Test
  void testScheduleDelay() throws Exception {
    long start = System.currentTimeMillis();
    
    FlowFuture<Void> future = scheduler.scheduleDelay(0.1); // 100ms
    
    future.get();
    
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
    FlowFuture<Void> firstTask = scheduler.schedule(() -> {
      try {
        // First increment from task 1
        int val1 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val1);
        }
        
        // Yield to allow the second task to run
        scheduler.yield().get();
        
        // Second increment from task 1 (should be after task 2's first increment)
        int val2 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val2);
        }
        
        // Yield again
        scheduler.yield().get();
        
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
    FlowFuture<Void> secondTask = scheduler.schedule(() -> {
      try {
        // First increment from task 2 (should be after task 1's first increment)
        int val1 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val1);
        }
        
        // Yield to allow first task to run again
        scheduler.yield().get();
        
        // Second increment from task 2 (should be after task 1's second increment)
        int val2 = counter.incrementAndGet();
        synchronized (lock) {
          incrementOrder.add(val2);
        }
        
        // Yield again
        scheduler.yield().get();
        
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
    firstTask.get();
    secondTask.get();
    
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
}