package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowTest {

  @Test
  void testStartCallable() throws Exception {
    FlowFuture<Integer> future = Flow.start(() -> 42);
    
    assertEquals(42, future.get(1, TimeUnit.SECONDS));
  }
  
  @Test
  void testStartCallableWithPriority() throws Exception {
    // Test the start method with priority parameter
    FlowFuture<Integer> future = Flow.start(() -> 42, 5);
    
    assertEquals(42, future.get(1, TimeUnit.SECONDS));
  }

  @Test
  void testStartRunnable() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    
    FlowFuture<Void> future = Flow.start(latch::countDown);
    
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    future.get(1, TimeUnit.SECONDS); // Should not throw
  }
  
  @Test
  void testStartRunnableWithPriority() throws Exception {
    // Test the start method with priority parameter for Runnable
    CountDownLatch latch = new CountDownLatch(1);
    
    FlowFuture<Void> future = Flow.start(latch::countDown, 5);
    
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    future.get(1, TimeUnit.SECONDS); // Should not throw
  }

  @Test
  void testDelay() throws Exception {
    long start = System.currentTimeMillis();
    
    FlowFuture<Void> future = Flow.delay(0.1); // 100ms delay
    
    future.get(1, TimeUnit.SECONDS);
    
    long duration = System.currentTimeMillis() - start;
    assertTrue(duration >= 100, "Delay should be at least 100ms but was " + duration + "ms");
  }

  @Test
  void testYield() throws Exception {
    // For now, just verify it returns a completed future
    FlowFuture<Void> future = Flow.yield();
    
    assertNotNull(future);
    future.get(1, TimeUnit.SECONDS); // Should not throw
  }

  @Test
  void testSchedulerAccess() {
    assertNotNull(Flow.scheduler());
  }
  
  @Test
  void testAwaitSuccess() throws Exception {
    // Test await method with a successful future
    FlowFuture<String> future = FlowFuture.completed("success");
    
    String result = Flow.await(future);
    assertEquals("success", result);
  }
  
  @Test
  void testAwaitWithException() {
    // Test await method with a future that completes exceptionally
    Exception testException = new RuntimeException("test error");
    FlowFuture<String> future = FlowFuture.failed(testException);
    
    Exception thrown = assertThrows(RuntimeException.class, () -> Flow.await(future));
    assertEquals("test error", thrown.getMessage());
  }
  
  @Test
  void testAwaitWithNonStandardException() {
    // Test await with an exception that's not an Exception subclass
    // This tests the else branch in await() where cause is not an Exception
    Throwable testThrowable = new Error("test error");
    FlowFuture<String> future = new FlowFuture<>();
    future.getPromise().completeExceptionally(testThrowable);
    
    ExecutionException thrown = assertThrows(ExecutionException.class, () -> Flow.await(future));
    assertEquals("test error", thrown.getCause().getMessage());
  }
  
  @Test
  void testStartCallableException() throws Exception {
    // Test starting a callable that throws an exception
    RuntimeException testException = new RuntimeException("test failure");
    FlowFuture<Integer> future = Flow.start(() -> {
      throw testException;
    });
    
    ExecutionException thrown = 
        assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
    assertEquals(testException, thrown.getCause());
  }
  
  @Test
  void testStartRunnableException() throws Exception {
    // Test starting a runnable that throws an exception
    RuntimeException testException = new RuntimeException("test failure");
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    
    FlowFuture<Void> future = Flow.start(() -> {
      exceptionThrown.set(true);
      throw testException;
    });
    
    ExecutionException thrown = 
        assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
    assertEquals(testException, thrown.getCause());
    assertTrue(exceptionThrown.get(), "Runnable should have executed and thrown exception");
  }

  // This is a simple integration test that uses multiple flow components together
  @Test
  void testIntegration() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(3);
    
    // Start three actors that increment the counter
    Flow.start(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });
    
    Flow.start(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });
    
    Flow.start(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });
    
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals(3, counter.get());
  }
  
  @Test
  void testComplexIntegration() throws Exception {
    // Test a more complex flow with chained operations
    AtomicReference<String> result = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    
    // Start a flow that does multiple operations
    Flow.start(() -> {
      // First part returns a string
      return "step1";
    }).map(str -> {
      // Map to concatenate a value
      return str + "-step2";
    }).map(str -> {
      // Store the result and signal completion
      result.set(str);
      latch.countDown();
      return null;
    });
    
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals("step1-step2", result.get());
  }
}