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
  void testStartActorCallable() throws Exception {
    FlowFuture<Integer> future = Flow.startActor(() -> 42);

    Flow.startActor(() -> Flow.await(future)).toCompletableFuture().get();
    assertEquals(42, future.toCompletableFuture().get());
  }

  @Test
  void testStartActorCallableWithPriority() throws Exception {
    // Test the start method with priority parameter
    FlowFuture<Integer> future = Flow.startActor(() -> 42, 5);

    assertEquals(42, future.toCompletableFuture().get());
  }

  @Test
  void testStartActorRunnable() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    FlowFuture<Void> future = Flow.startActor(latch::countDown);

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    future.toCompletableFuture().get(); // Should not throw
  }

  @Test
  void testStartActorRunnableWithPriority() throws Exception {
    // Test the start method with priority parameter for Runnable
    CountDownLatch latch = new CountDownLatch(1);

    FlowFuture<Void> future = Flow.startActor(latch::countDown, 5);

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    future.toCompletableFuture().get(); // Should not throw
  }

  @Test
  void testDelay() throws Exception {
    long start = System.currentTimeMillis();

    // Wrap in Flow.start() to create a flow task context
    FlowFuture<Void> future = Flow.startActor(() -> {
      // Now we're in a flow task context, so delay is allowed
      Flow.await(Flow.delay(0.1)); // 100ms delay
      return null;
    });

    future.toCompletableFuture().get();

    long duration = System.currentTimeMillis() - start;
    assertTrue(duration >= 100, "Delay should be at least 100ms but was " + duration + "ms");
  }

  @Test
  void testYieldF() throws Exception {
    // For now, just verify it returns a completed future
    FlowFuture<Void> future = Flow.startActor(() -> {
      Flow.yieldF();
    });

    assertNotNull(future);
    future.toCompletableFuture().get(); // Should not throw
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
  void testStartActorCallableException() {
    // Test starting a callable that throws an exception
    RuntimeException testException = new RuntimeException("test failure");
    FlowFuture<Integer> future = Flow.startActor(() -> {
      throw testException;
    });

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.toCompletableFuture().get());
    assertEquals(testException, thrown.getCause());
  }

  @Test
  void testStartActorRunnableException() {
    // Test starting a runnable that throws an exception
    RuntimeException testException = new RuntimeException("test failure");
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);

    FlowFuture<Void> future = Flow.startActor(() -> {
      exceptionThrown.set(true);
      throw testException;
    });

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.toCompletableFuture().get());
    assertEquals(testException, thrown.getCause());
    assertTrue(exceptionThrown.get(), "Runnable should have executed and thrown exception");
  }

  // This is a simple integration test that uses multiple flow components together
  @Test
  void testIntegration() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(3);

    // Start three actors that increment the counter
    Flow.startActor(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });

    Flow.startActor(() -> {
      counter.incrementAndGet();
      latch.countDown();
      return null;
    });

    Flow.startActor(() -> {
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
    Flow.startActor(() -> {
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

  @Test
  void testCancellationPropagation() throws Exception {
    // Simplified test that just verifies that cancellation works for dependent futures
    FlowFuture<String> future1 = new FlowFuture<>();
    FlowFuture<String> future2 = future1.map(s -> s + " mapped");

    // When we cancel the first future
    future1.cancel();

    // Check that it was marked as cancelled
    assertTrue(future1.isCancelled());

    // Wait a bit for propagation
    Thread.sleep(100);

    // Check that the dependent future is completed exceptionally
    assertTrue(future2.isCompletedExceptionally() || future2.isCancelled());
  }
}