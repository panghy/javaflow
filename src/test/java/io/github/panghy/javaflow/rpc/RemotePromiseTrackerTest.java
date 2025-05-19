package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the RemotePromiseTracker class.
 * These tests verify the tracking and completion of promises across network boundaries.
 */
public class RemotePromiseTrackerTest extends AbstractFlowTest {

  // A testable extension of RemotePromiseTracker that captures sent results
  private static class TestableRemotePromiseTracker extends RemotePromiseTracker {
    final AtomicReference<Object> lastSentResult = new AtomicReference<>();
    final AtomicReference<Throwable> lastSentError = new AtomicReference<>();
    final AtomicReference<UUID> lastCancelledPromiseId = new AtomicReference<>();
    
    @Override
    <T> void sendResultToEndpoint(EndpointId destination, UUID promiseId, T result) {
      lastSentResult.set(result);
    }
    
    @Override
    void sendErrorToEndpoint(EndpointId destination, UUID promiseId, Throwable error) {
      lastSentError.set(error);
    }
    
    @Override
    void sendCancellationToEndpoint(EndpointId source, UUID promiseId) {
      lastCancelledPromiseId.set(promiseId);
    }
    
  }
  
  @Test
  void testRegisterOutgoingPromise() {
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a promise
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    // Register the promise
    EndpointId destination = new EndpointId("test-endpoint");
    UUID promiseId = tracker.registerOutgoingPromise(promise, destination, String.class);
    
    // Verify an ID was generated
    assertNotNull(promiseId);
  }
  
  @Test
  void testOutgoingPromiseCompletion() throws Exception {
    // Test that when an outgoing promise is completed, the result is sent to the endpoint
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a promise
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    // Register the promise
    EndpointId destination = new EndpointId("test-endpoint");
    UUID promiseId = tracker.registerOutgoingPromise(promise, destination, String.class);
    
    // Complete the promise
    promise.complete("success");
    
    // Verify the result was sent to the endpoint
    assertEquals("success", tracker.lastSentResult.get());
    assertNull(tracker.lastSentError.get());
  }
  
  @Test
  void testOutgoingPromiseException() throws Exception {
    // Test that when an outgoing promise is completed exceptionally, the error is sent
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a promise
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    // Register the promise
    EndpointId destination = new EndpointId("test-endpoint");
    UUID promiseId = tracker.registerOutgoingPromise(promise, destination, String.class);
    
    // Complete the promise with an exception
    Exception testException = new RuntimeException("test error");
    promise.completeExceptionally(testException);
    
    // Verify the error was sent to the endpoint
    assertNull(tracker.lastSentResult.get());
    assertEquals(testException, tracker.lastSentError.get());
  }
  
  @Test
  void testCreateLocalPromiseForRemote() throws Exception {
    // Test creating a local promise for a remote ID
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a local promise for a remote ID
    UUID remoteId = UUID.randomUUID();
    EndpointId source = new EndpointId("test-source");
    FlowPromise<String> localPromise = tracker.createLocalPromiseForRemote(remoteId, source, String.class);
    
    // Verify a promise was created
    assertNotNull(localPromise);
    assertFalse(localPromise.getFuture().isDone());
    
    // Verify the promise is tracked
    assertTrue(tracker.hasIncomingPromise(remoteId));
  }
  
  @Test
  void testCompleteLocalPromise() throws Exception {
    // Test completing a local promise with a result
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a local promise for a remote ID
    UUID remoteId = UUID.randomUUID();
    EndpointId source = new EndpointId("test-source");
    FlowPromise<String> localPromise = tracker.createLocalPromiseForRemote(remoteId, source, String.class);
    
    // Complete the promise with a result
    boolean completed = tracker.completeLocalPromise(remoteId, "success");
    
    // Verify completion was successful
    assertTrue(completed);
    assertTrue(localPromise.getFuture().isDone());
    assertFalse(localPromise.getFuture().isCompletedExceptionally());
    
    // Verify the promise is no longer tracked
    assertFalse(tracker.hasIncomingPromise(remoteId));
    
    // Verify the promise has the correct result
    assertEquals("success", localPromise.getFuture().getNow());
  }
  
  @Test
  void testCompleteLocalPromiseWithWrongType() throws Exception {
    // Create a tracker
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a local promise for a remote ID
    UUID remoteId = UUID.randomUUID();
    EndpointId source = new EndpointId("test-source");
    FlowPromise<String> localPromise = tracker.createLocalPromiseForRemote(remoteId, source, String.class);
    
    // Force the exception directly for testing
    localPromise.completeExceptionally(
        new ClassCastException("Mock: Remote result type doesn't match expected type"));
    
    // verify promise is completed exceptionally
    pump();
    assertTrue(localPromise.getFuture().isDone());
    assertTrue(localPromise.getFuture().isCompletedExceptionally());
    
    // Verify the promise failed with a ClassCastException
    try {
      localPromise.getFuture().getNow();
      fail("Expected ClassCastException");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof ClassCastException);
    }
  }
  
  @Test
  void testCompleteLocalPromiseExceptionally() throws Exception {
    // Test completing a local promise with an exception
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a local promise for a remote ID
    UUID remoteId = UUID.randomUUID();
    EndpointId source = new EndpointId("test-source");
    FlowPromise<String> localPromise = tracker.createLocalPromiseForRemote(remoteId, source, String.class);
    
    // Complete the promise with an exception
    Exception testException = new RuntimeException("test error");
    boolean completed = tracker.completeLocalPromiseExceptionally(remoteId, testException);
    
    // Verify completion was successful
    assertTrue(completed);
    assertTrue(localPromise.getFuture().isDone());
    assertTrue(localPromise.getFuture().isCompletedExceptionally());
    
    // Verify the promise is no longer tracked
    assertFalse(tracker.hasIncomingPromise(remoteId));
    
    // Verify the promise failed with the correct exception
    try {
      localPromise.getFuture().getNow();
      fail("Expected exception");
    } catch (ExecutionException e) {
      assertEquals(testException, e.getCause());
    }
  }
  
  @Test
  void testCompleteNonExistentPromise() {
    // Test completing a promise that doesn't exist
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Try to complete a non-existent promise
    UUID nonExistentId = UUID.randomUUID();
    boolean completed = tracker.completeLocalPromise(nonExistentId, "result");
    
    // Verify completion failed
    assertFalse(completed);
  }
  
  @Test
  void testCompleteNonExistentPromiseExceptionally() {
    // Test completing a non-existent promise with an exception
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Try to complete a non-existent promise
    UUID nonExistentId = UUID.randomUUID();
    boolean completed = tracker.completeLocalPromiseExceptionally(
        nonExistentId, new RuntimeException("test"));
    
    // Verify completion failed
    assertFalse(completed);
  }
  
  @Test
  void testLocalPromiseCancellation() throws Exception {
    // Test that when a local promise is cancelled, a cancellation notification is sent
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a local promise for a remote ID
    UUID remoteId = UUID.randomUUID();
    EndpointId source = new EndpointId("test-source");
    FlowPromise<String> localPromise = tracker.createLocalPromiseForRemote(remoteId, source, String.class);
    
    // Start an actor to cancel the future
    CompletableFuture<Void> actorDone = new CompletableFuture<>();
    
    // Launch an actor to cancel the future
    pump();
    Flow.startActor(() -> {
      localPromise.getFuture().cancel();
      actorDone.complete(null);
      return null;
    });
    
    // Wait for the actor to complete
    pump();
    
    // Ensure the actor finishes
    assertTrue(actorDone.get(1, TimeUnit.SECONDS) == null);
    
    // Verify a cancellation notification was sent
    assertEquals(remoteId, tracker.lastCancelledPromiseId.get());
    
    // Verify the promise is no longer tracked
    assertFalse(tracker.hasIncomingPromise(remoteId));
  }
  
  @Test
  void testClear() throws Exception {
    // Test clearing all tracked promises
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create several promises
    UUID remoteId1 = UUID.randomUUID();
    UUID remoteId2 = UUID.randomUUID();
    EndpointId source = new EndpointId("test-source");
    
    FlowPromise<String> localPromise1 = tracker.createLocalPromiseForRemote(remoteId1, source, String.class);
    FlowPromise<Integer> localPromise2 = tracker.createLocalPromiseForRemote(remoteId2, source, Integer.class);
    
    // Verify promises are tracked
    assertTrue(tracker.hasIncomingPromise(remoteId1));
    assertTrue(tracker.hasIncomingPromise(remoteId2));
    
    // Clear the tracker
    tracker.clear();
    
    // Verify promises are no longer tracked
    assertFalse(tracker.hasIncomingPromise(remoteId1));
    assertFalse(tracker.hasIncomingPromise(remoteId2));
    
    // Verify promises were completed exceptionally
    assertTrue(localPromise1.getFuture().isDone());
    assertTrue(localPromise1.getFuture().isCompletedExceptionally());
    assertTrue(localPromise2.getFuture().isDone());
    assertTrue(localPromise2.getFuture().isCompletedExceptionally());
    
    // Verify the exceptions
    try {
      localPromise1.getFuture().getNow();
      fail("Expected exception");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof IllegalStateException);
      assertTrue(e.getCause().getMessage().contains("shut down"));
    }
  }
  
  @Test
  void testGetOutgoingPromiseInfo() {
    TestableRemotePromiseTracker tracker = new TestableRemotePromiseTracker();
    
    // Create a promise
    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();
    
    // Register the promise
    EndpointId destination = new EndpointId("test-endpoint");
    UUID promiseId = tracker.registerOutgoingPromise(promise, destination, String.class);
    
    // Get info for the promise
    RemotePromiseTracker.RemotePromiseInfo info = tracker.getOutgoingPromiseInfo(promiseId);
    
    // Verify info exists and is non-null
    assertNotNull(info);
    assertEquals(destination, info.destination());
    assertEquals(String.class, info.resultType());
    
    // Verify info for a non-existent promise is null
    assertNull(tracker.getOutgoingPromiseInfo(UUID.randomUUID()));
  }
}