package io.github.panghy.javaflow.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFutureTest {

  @Test
  void testCreateAndCompleteSuccessfully() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();

    assertFalse(future.isDone());
    assertFalse(future.isCompleted());

    future.getPromise().complete("test");

    assertTrue(future.isDone());
    assertTrue(future.isCompleted());
    assertEquals("test", future.getNow());
  }

  @Test
  void testCreateAndCompleteFailed() {
    FlowFuture<String> future = new FlowFuture<>();
    Exception testException = new RuntimeException("Test exception");

    assertFalse(future.isDone());

    future.getPromise().completeExceptionally(testException);

    assertTrue(future.isDone());
    Exception e = assertThrows(Exception.class, future::getNow);
    assertEquals("Test exception", e.getCause().getMessage());
  }

  @Test
  void testCompletedStaticCreator() throws Exception {
    FlowFuture<String> future = FlowFuture.completed("done");

    assertTrue(future.isDone());
    assertEquals("done", future.getNow());
  }

  @Test
  void testFailedStaticCreator() {
    Exception testException = new RuntimeException("Failed");
    FlowFuture<String> future = FlowFuture.failed(testException);

    assertTrue(future.isDone());
    Exception e = assertThrows(Exception.class, future::getNow);
    assertEquals("Failed", e.getCause().getMessage());
  }

  @Test
  void testCancellation() {
    FlowFuture<String> future = new FlowFuture<>();

    assertFalse(future.isCancelled());

    future.cancel();

    assertTrue(future.isCancelled());
    assertTrue(future.isDone());
    assertThrows(CancellationException.class, future::getNow);
  }

  @Test
  void testCancellationFail() {
    // Test cancellation when already completed
    FlowFuture<String> future = FlowFuture.completed("done");

    assertFalse(future.isCancelled());

    // Should return false since future is already completed
    boolean result = future.cancel();

    assertFalse(result);
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
  }

  @Test
  void testMap() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<Integer> mapped = future.map(String::length);

    assertFalse(mapped.isDone());

    future.getPromise().complete("test");

    assertTrue(mapped.isDone());
    assertEquals(4, mapped.getNow());
  }

  @Test
  void testMapWithFailure() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<Integer> mapped = future.map(String::length);

    future.getPromise().completeExceptionally(new RuntimeException("Oops"));

    assertTrue(mapped.isDone());
    Exception e = assertThrows(Exception.class, mapped::getNow);
    assertEquals("Oops", e.getCause().getMessage());
  }

  @Test
  void testMapWithMappingFailure() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<Integer> mapped = future.map(s -> {
      throw new IllegalArgumentException("Bad mapping");
    });

    future.getPromise().complete("test");

    assertTrue(mapped.isDone());
    Exception e = assertThrows(Exception.class, mapped::getNow);
    assertTrue(e.getCause() instanceof IllegalArgumentException);
    assertEquals("Bad mapping", e.getCause().getMessage());
  }

  @Test
  void testFlatMap() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<String> flatMapped = future.flatMap(s -> FlowFuture.completed(s + " world"));

    assertFalse(flatMapped.isDone());

    future.getPromise().complete("hello");

    assertTrue(flatMapped.isDone());
    assertEquals("hello world", flatMapped.getNow());
  }

  @Test
  void testFlatMapWithException() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<String> flatMapped = future.flatMap($ -> {
      throw new RuntimeException("Flat map exception");
    });

    future.getPromise().complete("test");

    assertTrue(flatMapped.isDone());
    Exception e = assertThrows(Exception.class, flatMapped::getNow);
    assertEquals("Flat map exception", e.getCause().getMessage());
  }

  @Test
  void testFlatMapWithSourceException() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<String> flatMapped = future.flatMap(s -> FlowFuture.completed("never called"));

    future.getPromise().completeExceptionally(new RuntimeException("Source exception"));

    assertTrue(flatMapped.isDone());
    Exception e = assertThrows(Exception.class, flatMapped::getNow);
    assertEquals("Source exception", e.getCause().getMessage());
  }

  @Test
  void testFlatMapWithTargetException() {
    FlowFuture<String> future = new FlowFuture<>();
    RuntimeException targetException = new RuntimeException("Target exception");

    FlowFuture<String> flatMapped = future.flatMap(s -> FlowFuture.failed(targetException));

    future.getPromise().complete("test");

    assertTrue(flatMapped.isDone());
    Exception e = assertThrows(Exception.class, flatMapped::getNow);
    assertEquals("Target exception", e.getCause().getMessage());
  }

  @Test
  void testConcurrentCallbacks() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
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

    future.getPromise().complete("done");

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(results.toString().contains("A:done"));
    assertTrue(results.toString().contains("B:done"));
  }
  
  @Test
  void testCancellationOfDependentFutures() throws Exception {
    FlowFuture<String> future1 = new FlowFuture<>();
    FlowFuture<String> future2 = future1.map(s -> s + " mapped");
    
    future1.cancel();
    
    assertTrue(future1.isCancelled());
    assertTrue(future2.isCancelled());
    assertThrows(CancellationException.class, future1::getNow);
    assertThrows(CancellationException.class, future2::getNow);
  }
  
  @Test
  void testCancellationOfFlatMappedFutures() throws Exception {
    FlowFuture<String> future1 = new FlowFuture<>();
    FlowFuture<String> future2 = future1.flatMap(s -> {
      FlowFuture<String> nestedFuture = new FlowFuture<>();
      nestedFuture.getPromise().complete(s + " nested");
      return nestedFuture;
    });
    
    future1.cancel();
    
    assertTrue(future1.isCancelled());
    assertTrue(future2.isCompletedExceptionally());
    assertThrows(CancellationException.class, future1::getNow);
    // The exception should be propagated to the dependent future
    assertThrows(Exception.class, future2::getNow);
  }
}