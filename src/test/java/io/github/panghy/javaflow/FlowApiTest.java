package io.github.panghy.javaflow;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Flow API, particularly the static helper methods
 * and configuration options.
 */
class FlowApiTest {

  @AfterEach
  void afterEach() {
    Flow.shutdown();
    Flow.setScheduler(new FlowScheduler());
  }
  
  @Test
  void testFlowYieldF() throws Exception {
    // Create a counter
    AtomicInteger counter = new AtomicInteger(0);
    
    // Start a task that will increment twice with a yield in between
    CompletableFuture<Integer> future = Flow.startActor(() -> {
      // First increment
      counter.incrementAndGet();
      
      // Yield back to scheduler
      Flow.await(Flow.yieldF());
      
      // Second increment
      return counter.incrementAndGet();
    });
    
    // Get the result
    assertEquals(2, future.toCompletableFuture().get(), "The final value should be 2");
    assertEquals(2, counter.get(), "Counter should have been incremented twice");
  }
  
  @Test
  void testCompletableFutureReadyOrThrow() throws Exception {
    // Create a completed future
    CompletableFuture<String> completedFuture = CompletableFuture.completedFuture("test");
    
    // Check if it's ready
    assertTrue(Flow.futureReadyOrThrow(completedFuture), 
        "Completed future should be ready");
    
    // Create an incomplete future
    CompletableFuture<String> incompleteFuture = new CompletableFuture<>();
    
    // Check if it's ready
    assertFalse(Flow.futureReadyOrThrow(incompleteFuture), 
        "Incomplete future should not be ready");
    
    // Create a failed future
    RuntimeException testException = new RuntimeException("Test exception");
    CompletableFuture<String> failedFuture = CompletableFuture.failedFuture(testException);
    
    // Should throw the exception
    Exception thrown = assertThrows(RuntimeException.class, 
        () -> Flow.futureReadyOrThrow(failedFuture),
        "Should throw the exception from the failed future");
    
    assertEquals("Test exception", thrown.getMessage(), 
        "Exception message should match");
  }
  
  @Test
  void testCompletableFutureToCompletableFuture() throws Exception {
    // Create a future
    CompletableFuture<String> future = new CompletableFuture<>();
    future.complete("test");
    
    // Convert to CompletableFuture
    assertEquals("test", future.toCompletableFuture().get(), 
        "CompletableFuture should have the same value");
  }
  
  @Test
  void testCompletableFutureGetNow() throws Exception {
    // Create a completed future
    CompletableFuture<String> completedFuture = CompletableFuture.completedFuture("test");
    
    // Get the value now
    assertEquals("test", completedFuture.getNow(null), 
        "getNow should return the value for a completed future");
    
    // Create a failed future
    RuntimeException testException = new RuntimeException("Test exception");
    CompletableFuture<String> failedFuture = CompletableFuture.failedFuture(testException);

    // Should throw the exception
    Exception thrown = assertThrows(Exception.class,
        () -> failedFuture.getNow(null),
        "Should throw the exception from the failed future");

    assertEquals("Test exception", thrown.getCause().getMessage(),
        "Exception message should match");
  }
  
  @Test
  void testAwaitApi() throws Exception {
    // Create a future
    CompletableFuture<String> future = new CompletableFuture<>();
    
    // Start a task that will await the future
    AtomicBoolean awaited = new AtomicBoolean(false);
    CompletableFuture<String> resultFuture = Flow.startActor(() -> {
      // This will be a no-op initially since the future is not complete
      try {
        String result = Flow.await(future);
        awaited.set(true);
        return result;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    
    // Complete the future
    future.complete("test");
    
    // Get the result
    assertEquals("test", resultFuture.toCompletableFuture().get(), 
        "The await should return the future's value");
    assertTrue(awaited.get(), "The await should have been executed");
  }
  
  @Test
  void testNonFlowContext() {
    // Call await outside of a flow context
    assertThrows(IllegalStateException.class, 
        () -> Flow.await(new CompletableFuture<>()),
        "await should throw IllegalStateException when called outside flow context");
  }
}