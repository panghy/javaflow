package io.github.panghy.javaflow.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    assertEquals("test", future.get());
  }

  @Test
  void testCreateAndCompleteFailed() {
    FlowFuture<String> future = new FlowFuture<>();
    Exception testException = new RuntimeException("Test exception");
    
    assertFalse(future.isDone());
    
    future.getPromise().completeExceptionally(testException);
    
    assertTrue(future.isDone());
    Exception e = assertThrows(Exception.class, future::get);
    assertEquals("Test exception", e.getCause().getMessage());
  }

  @Test
  void testCompletedStaticCreator() throws Exception {
    FlowFuture<String> future = FlowFuture.completed("done");
    
    assertTrue(future.isDone());
    assertEquals("done", future.get());
  }

  @Test
  void testFailedStaticCreator() {
    Exception testException = new RuntimeException("Failed");
    FlowFuture<String> future = FlowFuture.failed(testException);
    
    assertTrue(future.isDone());
    Exception e = assertThrows(Exception.class, future::get);
    assertEquals("Failed", e.getCause().getMessage());
  }

  @Test
  void testCancellation() {
    FlowFuture<String> future = new FlowFuture<>();
    
    assertFalse(future.isCancelled());
    
    future.cancel(true);
    
    assertTrue(future.isCancelled());
    assertTrue(future.isDone());
    assertThrows(CancellationException.class, future::get);
  }
  
  @Test
  void testCancellationFail() {
    // Test cancellation when already completed
    FlowFuture<String> future = FlowFuture.completed("done");
    
    assertFalse(future.isCancelled());
    
    // Should return false since future is already completed
    boolean result = future.cancel(true);
    
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
    assertEquals(4, mapped.get());
  }

  @Test
  void testMapWithFailure() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<Integer> mapped = future.map(String::length);
    
    future.getPromise().completeExceptionally(new RuntimeException("Oops"));
    
    assertTrue(mapped.isDone());
    Exception e = assertThrows(Exception.class, mapped::get);
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
    Exception e = assertThrows(Exception.class, mapped::get);
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
    assertEquals("hello world", flatMapped.get());
  }
  
  @Test
  void testFlatMapWithException() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<String> flatMapped = future.flatMap(s -> {
      throw new RuntimeException("Flat map exception");
    });
    
    future.getPromise().complete("test");
    
    assertTrue(flatMapped.isDone());
    Exception e = assertThrows(Exception.class, flatMapped::get);
    assertEquals("Flat map exception", e.getCause().getMessage());
  }
  
  @Test
  void testFlatMapWithSourceException() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowFuture<String> flatMapped = future.flatMap(s -> FlowFuture.completed("never called"));
    
    future.getPromise().completeExceptionally(new RuntimeException("Source exception"));
    
    assertTrue(flatMapped.isDone());
    Exception e = assertThrows(Exception.class, flatMapped::get);
    assertEquals("Source exception", e.getCause().getMessage());
  }
  
  @Test
  void testFlatMapWithTargetException() {
    FlowFuture<String> future = new FlowFuture<>();
    RuntimeException targetException = new RuntimeException("Target exception");
    
    FlowFuture<String> flatMapped = future.flatMap(s -> FlowFuture.failed(targetException));
    
    future.getPromise().complete("test");
    
    assertTrue(flatMapped.isDone());
    Exception e = assertThrows(Exception.class, flatMapped::get);
    assertEquals("Target exception", e.getCause().getMessage());
  }

  @Test
  void testConcurrentCallbacks() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    CountDownLatch latch = new CountDownLatch(2);
    StringBuilder results = new StringBuilder();
    
    future.getPromise().whenComplete((result, ex) -> {
      results.append("A:").append(result);
      latch.countDown();
    });
    
    future.getPromise().whenComplete((result, ex) -> {
      results.append(",B:").append(result);
      latch.countDown();
    });
    
    future.getPromise().complete("done");
    
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(results.toString().contains("A:done"));
    assertTrue(results.toString().contains("B:done"));
  }
  
  @Test
  void testGetWithTimeout() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    
    // Complete the future after a short delay
    Thread.ofVirtual().start(() -> {
      try {
        Thread.sleep(50);
        future.getPromise().complete("delayed");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    
    // Get with timeout should succeed
    assertEquals("delayed", future.get(1, TimeUnit.SECONDS));
  }
  
  @Test
  void testGetWithTimeoutException() {
    FlowFuture<String> future = new FlowFuture<>();
    
    // Future won't complete, so this should timeout
    assertThrows(TimeoutException.class, () -> future.get(50, TimeUnit.MILLISECONDS));
  }
  
  @Test
  void testGetWithInterruptedException() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    
    Thread testThread = Thread.currentThread();
    
    // Start another thread to interrupt this one
    Thread.ofVirtual().start(() -> {
      try {
        Thread.sleep(50);
        testThread.interrupt();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    
    // This should throw InterruptedException
    assertThrows(InterruptedException.class, () -> future.get(5, TimeUnit.SECONDS));
    
    // Clear the interrupted status for other tests
    Thread.interrupted();
  }
}