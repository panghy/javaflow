package io.github.panghy.javaflow.core;

import io.github.panghy.javaflow.Flow;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CompletableFuture integration with Flow's cooperative scheduling.
 */
class CompletableFutureFlowTest {

  @Test
  void testCreateAndCompleteSuccessfully() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();

    assertFalse(future.isDone());
    assertFalse(future.isCompletedExceptionally());

    future.complete("test");

    assertTrue(future.isDone());
    assertFalse(future.isCompletedExceptionally());
    assertEquals("test", future.getNow(null));
  }

  @Test
  void testCreateAndCompleteFailed() {
    CompletableFuture<String> future = new CompletableFuture<>();
    Exception testException = new RuntimeException("Test exception");

    assertFalse(future.isDone());

    future.completeExceptionally(testException);

    assertTrue(future.isDone());
    assertTrue(future.isCompletedExceptionally());
    ExecutionException e = assertThrows(ExecutionException.class, () -> future.get());
    assertEquals("Test exception", e.getCause().getMessage());
  }

  @Test
  void testCompletedStaticCreator() throws Exception {
    CompletableFuture<String> future = CompletableFuture.completedFuture("done");

    assertTrue(future.isDone());
    assertEquals("done", future.getNow(null));
  }

  @Test
  void testFailedStaticCreator() {
    Exception testException = new RuntimeException("Failed");
    CompletableFuture<String> future = CompletableFuture.failedFuture(testException);

    assertTrue(future.isDone());
    ExecutionException e = assertThrows(ExecutionException.class, () -> future.get());
    assertEquals("Failed", e.getCause().getMessage());
  }

  @Test
  void testCancellation() {
    CompletableFuture<String> future = new CompletableFuture<>();

    assertFalse(future.isCancelled());

    future.cancel(true);

    assertTrue(future.isCancelled());
    assertTrue(future.isDone());
    assertThrows(CancellationException.class, () -> future.getNow(null));
  }

  @Test
  void testCancellationFail() {
    // Test cancellation when already completed
    CompletableFuture<String> future = CompletableFuture.completedFuture("done");

    assertFalse(future.isCancelled());

    // Should return false since future is already completed
    boolean result = future.cancel(true);

    assertFalse(result);
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
  }

  @Test
  void testThenApply() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<Integer> mapped = future.thenApply(String::length);

    assertFalse(mapped.isDone());

    future.complete("test");

    assertTrue(mapped.isDone());
    assertEquals(4, mapped.getNow(null));
  }

  @Test
  void testThenApplyWithFailure() {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<Integer> mapped = future.thenApply(String::length);

    future.completeExceptionally(new RuntimeException("Oops"));

    assertTrue(mapped.isDone());
    ExecutionException e = assertThrows(ExecutionException.class, () -> mapped.get());
    assertEquals("Oops", e.getCause().getMessage());
  }

  @Test
  void testThenApplyWithMappingFailure() {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<Integer> mapped = future.thenApply(s -> {
      throw new IllegalArgumentException("Bad mapping");
    });

    future.complete("test");

    assertTrue(mapped.isDone());
    ExecutionException e = assertThrows(ExecutionException.class, () -> mapped.get());
    assertTrue(e.getCause() instanceof IllegalArgumentException);
    assertEquals("Bad mapping", e.getCause().getMessage());
  }

  @Test
  void testThenCompose() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<String> flatMapped = future.thenCompose(s -> CompletableFuture.completedFuture(s + " world"));

    assertFalse(flatMapped.isDone());

    future.complete("hello");

    assertTrue(flatMapped.isDone());
    assertEquals("hello world", flatMapped.getNow(null));
  }

  @Test
  void testThenComposeWithException() {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<String> flatMapped = future.thenCompose($ -> {
      throw new RuntimeException("Flat map exception");
    });

    future.complete("test");

    assertTrue(flatMapped.isDone());
    ExecutionException e = assertThrows(ExecutionException.class, () -> flatMapped.get());
    assertEquals("Flat map exception", e.getCause().getMessage());
  }

  @Test
  void testThenComposeWithSourceException() {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<String> flatMapped = future.thenCompose(s -> CompletableFuture.completedFuture("never called"));

    future.completeExceptionally(new RuntimeException("Source exception"));

    assertTrue(flatMapped.isDone());
    ExecutionException e = assertThrows(ExecutionException.class, () -> flatMapped.get());
    assertEquals("Source exception", e.getCause().getMessage());
  }

  @Test
  void testThenComposeWithTargetException() {
    CompletableFuture<String> future = new CompletableFuture<>();
    RuntimeException targetException = new RuntimeException("Target exception");

    CompletableFuture<String> flatMapped = future.thenCompose(s -> CompletableFuture.failedFuture(targetException));

    future.complete("test");

    assertTrue(flatMapped.isDone());
    ExecutionException e = assertThrows(ExecutionException.class, () -> flatMapped.get());
    assertEquals("Target exception", e.getCause().getMessage());
  }

  @Test
  void testConcurrentCallbacks() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    CountDownLatch latch = new CountDownLatch(2);
    StringBuilder results = new StringBuilder();

    future.whenComplete((result, $) -> {
      results.append("A:").append(result);
      latch.countDown();
    });

    future.whenComplete((result, $) -> {
      results.append(",B:").append(result);
      latch.countDown();
    });

    future.complete("done");

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(results.toString().contains("A:done"));
    assertTrue(results.toString().contains("B:done"));
  }
  
  @Test
  void testCancellationOfDependentFutures() throws Exception {
    CompletableFuture<String> future1 = new CompletableFuture<>();
    CompletableFuture<String> future2 = future1.thenApply(s -> s + " mapped");
    
    future1.cancel(true);
    
    assertTrue(future1.isCancelled());
    assertTrue(future2.isCancelled());
    assertThrows(CancellationException.class, () -> future1.getNow(null));
    assertThrows(CancellationException.class, () -> future2.getNow(null));
  }
  
  @Test
  void testCancellationOfComposedFutures() throws Exception {
    CompletableFuture<String> future1 = new CompletableFuture<>();
    CompletableFuture<String> future2 = future1.thenCompose(s -> {
      CompletableFuture<String> nestedFuture = new CompletableFuture<>();
      nestedFuture.complete(s + " nested");
      return nestedFuture;
    });
    
    future1.cancel(true);
    
    assertTrue(future1.isCancelled());
    assertTrue(future2.isCompletedExceptionally());
    assertThrows(CancellationException.class, () -> future1.getNow(null));
    // The exception should be propagated to the dependent future
    assertThrows(CancellationException.class, () -> future2.getNow(null));
  }

  @Test
  void testFlowIntegration() throws Exception {
    // Test that CompletableFuture works with Flow.await()
    CompletableFuture<String> result = Flow.startActor(() -> {
      CompletableFuture<String> future = CompletableFuture.completedFuture("Hello");
      String value = Flow.await(future);
      return value + " World";
    });

    assertEquals("Hello World", result.get(1, TimeUnit.SECONDS));
  }

  @Test
  void testFlowAwaitWithDelay() throws Exception {
    // Test Flow.await() with delayed CompletableFuture
    CompletableFuture<Integer> result = Flow.startActor(() -> {
      CompletableFuture<Integer> delayed = CompletableFuture.supplyAsync(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        return 42;
      });
      
      return Flow.await(delayed);
    });

    assertEquals(42, result.get(1, TimeUnit.SECONDS));
  }
}