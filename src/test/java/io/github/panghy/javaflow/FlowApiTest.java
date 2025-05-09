package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowFuture;
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
  
  @Test
  void testFlowYieldF() throws Exception {
    // Create a counter
    AtomicInteger counter = new AtomicInteger(0);
    
    // Start a task that will increment twice with a yield in between
    FlowFuture<Integer> future = Flow.startActor(() -> {
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
  void testFlowFutureReadyOrThrow() throws Exception {
    // Create a completed future
    FlowFuture<String> completedFuture = FlowFuture.completed("test");
    
    // Check if it's ready
    assertTrue(Flow.futureReadyOrThrow(completedFuture), 
        "Completed future should be ready");
    
    // Create an incomplete future
    FlowFuture<String> incompleteFuture = new FlowFuture<>();
    
    // Check if it's ready
    assertFalse(Flow.futureReadyOrThrow(incompleteFuture), 
        "Incomplete future should not be ready");
    
    // Create a failed future
    RuntimeException testException = new RuntimeException("Test exception");
    FlowFuture<String> failedFuture = FlowFuture.failed(testException);
    
    // Should throw the exception
    Exception thrown = assertThrows(RuntimeException.class, 
        () -> Flow.futureReadyOrThrow(failedFuture),
        "Should throw the exception from the failed future");
    
    assertEquals("Test exception", thrown.getMessage(), 
        "Exception message should match");
  }
  
  @Test
  void testFlowFutureToCompletableFuture() throws Exception {
    // Create a future
    FlowFuture<String> future = new FlowFuture<>();
    future.getPromise().complete("test");
    
    // Convert to CompletableFuture
    assertEquals("test", future.toCompletableFuture().get(), 
        "CompletableFuture should have the same value");
  }
  
  @Test
  void testFlowFutureGetNow() throws Exception {
    // Create a completed future
    FlowFuture<String> completedFuture = FlowFuture.completed("test");
    
    // Get the value now
    assertEquals("test", completedFuture.getNow(), 
        "getNow should return the value for a completed future");
    
    // Create an incomplete future
    FlowFuture<String> incompleteFuture = new FlowFuture<>();
    
    // Should throw an exception
    assertThrows(IllegalStateException.class, 
        incompleteFuture::getNow,
        "getNow should throw IllegalStateException for incomplete future");
  }
  
  @Test
  void testAwaitApi() throws Exception {
    // Create a future
    FlowFuture<String> future = new FlowFuture<>();
    
    // Start a task that will await the future
    AtomicBoolean awaited = new AtomicBoolean(false);
    FlowFuture<String> resultFuture = Flow.startActor(() -> {
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
    future.getPromise().complete("test");
    
    // Get the result
    assertEquals("test", resultFuture.toCompletableFuture().get(), 
        "The await should return the future's value");
    assertTrue(awaited.get(), "The await should have been executed");
  }
  
  @Test
  void testNonFlowContext() {
    // Call await outside of a flow context
    assertThrows(IllegalStateException.class, 
        () -> Flow.await(new FlowFuture<>()),
        "await should throw IllegalStateException when called outside flow context");
  }
}