package io.github.panghy.javaflow.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowPromiseTest {

  @Test
  void testCreateAndAccess() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    assertNotNull(promise);
    assertSame(future, promise.getFuture());
    assertFalse(promise.isCompleted());
  }
  
  @Test
  void testCreateWithCompletedFuture() {
    // Test the branch where the future is already completed in the constructor
    FlowFuture<String> future = FlowFuture.completed("already done");
    FlowPromise<String> promise = future.getPromise();
    
    assertNotNull(promise);
    assertSame(future, promise.getFuture());
    assertTrue(promise.isCompleted());
    
    // Test the delegate field was properly set by trying to complete again
    // This should be a no-op since it's already completed
    assertFalse(promise.complete("new value"));
    
    try {
      assertEquals("already done", future.get());
    } catch (Exception e) {
      throw new AssertionError("Should not throw", e);
    }
  }

  @Test
  void testComplete() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    boolean result = promise.complete("success");
    
    assertTrue(result);
    assertTrue(promise.isCompleted());
    assertEquals("success", future.get());
  }

  @Test
  void testCompleteExceptionally() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    RuntimeException exception = new RuntimeException("failed");
    
    boolean result = promise.completeExceptionally(exception);
    
    assertTrue(result);
    assertTrue(promise.isCompleted());
    try {
      future.get();
    } catch (Exception e) {
      assertEquals("failed", e.getCause().getMessage());
    }
  }

  @Test
  void testCannotCompleteMultipleTimes() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    boolean firstResult = promise.complete("first");
    boolean secondResult = promise.complete("second");
    
    assertTrue(firstResult);
    assertFalse(secondResult);
    
    try {
      assertEquals("first", future.get());
    } catch (Exception e) {
      throw new AssertionError("Should not throw", e);
    }
  }
  
  @Test
  void testCannotCompleteExceptionallyMultipleTimes() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    Exception exception1 = new RuntimeException("first error");
    Exception exception2 = new RuntimeException("second error");
    
    boolean firstResult = promise.completeExceptionally(exception1);
    boolean secondResult = promise.completeExceptionally(exception2);
    
    assertTrue(firstResult);
    assertFalse(secondResult);
    
    try {
      future.get();
    } catch (Exception e) {
      assertEquals("first error", e.getCause().getMessage());
    }
  }
  
  @Test
  void testCannotCompleteAfterExceptional() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    Exception exception = new RuntimeException("error first");
    
    boolean firstResult = promise.completeExceptionally(exception);
    boolean secondResult = promise.complete("success after");
    
    assertTrue(firstResult);
    assertFalse(secondResult);
    
    try {
      future.get();
    } catch (Exception e) {
      assertEquals("error first", e.getCause().getMessage());
    }
  }
  
  @Test
  void testCannotCompleteExceptionallyAfterSuccess() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    Exception exception = new RuntimeException("error after");
    
    boolean firstResult = promise.complete("success first");
    boolean secondResult = promise.completeExceptionally(exception);
    
    assertTrue(firstResult);
    assertFalse(secondResult);
    
    try {
      assertEquals("success first", future.get());
    } catch (Exception e) {
      throw new AssertionError("Should not throw", e);
    }
  }

  @Test
  void testWhenComplete() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> result = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    
    promise.whenComplete((value, throwable) -> {
      result.set(value);
      error.set(throwable);
      latch.countDown();
    });
    
    promise.complete("done");
    
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals("done", result.get());
    assertSame(null, error.get());
  }
  
  @Test
  void testWhenCompleteReturnValue() {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    BiConsumer<String, Throwable> callback = (value, throwable) -> { };
    CompletionStage<String> stage = promise.whenComplete(callback);
    
    assertNotNull(stage);
  }

  @Test
  void testWhenCompleteWithException() throws Exception {
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> result = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    
    promise.whenComplete((value, throwable) -> {
      result.set(value);
      error.set(throwable);
      latch.countDown();
    });
    
    RuntimeException exception = new RuntimeException("error occurred");
    promise.completeExceptionally(exception);
    
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertSame(null, result.get());
    assertEquals("error occurred", error.get().getMessage());
  }
  
  @Test
  void testAlreadyCompletedFuture() throws Exception {
    // Create an already completed future
    FlowFuture<String> future = FlowFuture.completed("pre-completed");
    FlowPromise<String> promise = future.getPromise();
    
    // Should already be completed
    assertTrue(promise.isCompleted());
    
    // Trying to complete again should return false
    boolean result = promise.complete("another value");
    assertFalse(result);
    
    // Original value should be preserved
    assertEquals("pre-completed", future.get());
  }
  
  @Test
  void testCreateWithCompletedFutureNull() {
    // Create a future that's completed with null to hit the specific branch
    FlowFuture<String> future = new FlowFuture<>();
    future.getPromise().complete(null);
    
    // Now create a new promise from this completed future
    FlowPromise<String> promise = new FlowPromise<>(future);
    
    // Verify it works correctly with the completed(null) path
    assertTrue(promise.isCompleted());
    assertFalse(promise.complete("should not work"));
    
    try {
      Object result = future.get();
      assertSame(null, result);
    } catch (Exception e) {
      throw new AssertionError("Should not throw", e);
    }
  }
}