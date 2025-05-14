package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to diagnose issues with the Flow.delay method in a test environment.
 */
class FlowDelayTest extends AbstractFlowTest {

  @Test
  void testBasicDelay() {
    // Create a simple delay within an actor context
    double delaySeconds = 0.5;
    FlowFuture<Boolean> actorFuture = Flow.startActor(() -> {
      FlowFuture<Void> delayFuture = Flow.delay(delaySeconds);
      // Check initial state within actor context
      assertFalse(delayFuture.isDone(), "Delay should not be done immediately");
      return true;
    });
    
    // Wait for the actor to run
    testScheduler.pump();
    assertTrue(actorFuture.isDone(), "Actor should complete immediately");
    
    // Create another actor to test delay completion
    FlowFuture<Boolean> delayTestFuture = Flow.startActor(() -> {
      // Create a delay in actor context
      FlowFuture<Void> delayFuture = Flow.delay(delaySeconds);
      
      // Print debug info
      System.out.println("Current time before advancing: " + currentTimeSeconds() + "s");
      
      // Return a future that completes when the delay completes
      try {
        Flow.await(delayFuture); // This will suspend until the delay completes
        return true;
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
    });
    
    // Initial check - the delay actor should be suspended
    testScheduler.pump();
    assertFalse(delayTestFuture.isDone(), "Delay should not be done before advancing time");
    
    // Advance time past the delay
    System.out.println("Advancing time by " + delaySeconds + "s");
    testScheduler.advanceTime(delaySeconds);
    System.out.println("Current time after advancing: " + currentTimeSeconds() + "s");
    
    // Pump to process the timer completion
    testScheduler.pump();
    System.out.println("Is delay actor done after pump? " + delayTestFuture.isDone());
    
    // Force more time advancement if needed
    if (!delayTestFuture.isDone()) {
      testScheduler.advanceTime(1.0); // Add an extra second
      testScheduler.pump();
      System.out.println("Is delay actor done after extra time? " + delayTestFuture.isDone());
    }
    
    // Report final state
    System.out.println("Current time at end: " + currentTimeSeconds() + "s");
    assertTrue(delayTestFuture.isDone(), "Delay should complete after advancing time");
    try {
      assertTrue(delayTestFuture.getNow(), "Delay actor should return true on success");
    } catch (ExecutionException e) {
      throw new AssertionError("Delay actor completed exceptionally", e);
    }
  }
}