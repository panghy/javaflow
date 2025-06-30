package io.github.panghy.javaflow.examples;

import io.github.panghy.javaflow.Flow;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * This example demonstrates the actor model constraints in JavaFlow.
 * It shows how to properly use delay, await, and yield operations within flow tasks,
 * and illustrates the errors that occur when these operations are used incorrectly.
 */
public class ActorModelConstraintsExample {

  /**
   * Demonstrates the correct way to use delay, await, and yield operations.
   * These operations must be called from within a flow task (actor).
   *
   * @return A future that completes when the example finishes
   */
  public static CompletableFuture<String> correctUsage() {
    System.out.println("--- Correct Actor Model Usage Example ---");
    
    // Create a flow actor using Flow.start
    // This creates a proper flow task context
    CompletableFuture<String> result = Flow.startActor(() -> {
      System.out.println("Inside actor: Flow context active = " + Flow.isInFlowContext());
      
      // Inside an actor, we can use delay operations
      System.out.println("Actor: Delaying for 0.1 seconds");
      Flow.await(Flow.delay(0.1));
      
      // We can start child actors from within an actor
      CompletableFuture<Integer> childFuture = Flow.startActor(() -> {
        System.out.println("Child actor: Flow context active = " + Flow.isInFlowContext());
        
        // The child actor can also use delay operations
        System.out.println("Child actor: Delaying for 0.2 seconds");
        Flow.await(Flow.delay(0.2));
        
        // And yield operations
        System.out.println("Child actor: Yielding control");
        Flow.await(Flow.yield());
        
        return 42;
      });
      
      // We can await futures from other actors
      System.out.println("Actor: Awaiting child result");
      int childResult = Flow.await(childFuture);
      
      // And yield control to other actors
      System.out.println("Actor: Yielding control");
      Flow.await(Flow.yield());
      
      return "Main actor completed with child result: " + childResult;
    });
    
    // Convert to a CompletableFuture for easier integration with non-Flow code
    return result.toCompletableFuture();
  }
  
  /**
   * Demonstrates what happens when you try to use suspension operations
   * outside of a flow task context.
   *
   * @return A future that completes when the example finishes
   */
  public static CompletableFuture<String> incorrectUsage() {
    System.out.println("--- Incorrect Actor Model Usage Example ---");
    
    CompletableFuture<String> result = new CompletableFuture<>();
    
    // Check if we're currently in a flow context (we shouldn't be)
    System.out.println("Outside actor: Flow context active = " + Flow.isInFlowContext());
    
    try {
      // This will throw an IllegalStateException because delay requires a flow context
      System.out.println("Attempting to call Flow.delay() outside an actor...");
      CompletableFuture<Void> delayFuture = Flow.delay(0.1);
      System.out.println("This line won't execute - exception will be thrown");
      result.complete("This shouldn't happen");
    } catch (IllegalStateException e) {
      System.out.println("Caught expected exception: " + e.getMessage());
      
      // The correct way is to wrap in a flow task
      System.out.println("Correcting by wrapping in Flow.start():");
      Flow.startActor(() -> {
        System.out.println("Now inside an actor: Flow context active = " + Flow.isInFlowContext());
        
        // Now delay works correctly
        System.out.println("Delaying for 0.1 seconds");
        Flow.await(Flow.delay(0.1));
        
        System.out.println("Delay completed successfully inside actor");
        result.complete("Correct usage demonstrated");
        return null;
      });
    }
    
    return result;
  }
  
  /**
   * Demonstrates the actor model's cooperative multitasking approach.
   *
   * @return A future that completes when the example finishes
   */
  public static CompletableFuture<String> cooperativeMultitasking() {
    System.out.println("--- Cooperative Multitasking Example ---");
    
    // Use a counter to track actor execution
    AtomicInteger counter = new AtomicInteger(0);
    CompletableFuture<String> result = new CompletableFuture<>();
    
    // Create multiple actors that increment the counter
    for (int i = 0; i < 3; i++) {
      final int actorId = i;
      Flow.startActor(() -> {
        System.out.println("Actor " + actorId + " starting");
        
        // Each actor increments the counter three times, yielding after each increment
        for (int j = 0; j < 3; j++) {
          int count = counter.incrementAndGet();
          System.out.println("Actor " + actorId + " incremented counter to " + count);
          
          // Yield to allow other actors to run
          Flow.await(Flow.yield());
        }
        
        System.out.println("Actor " + actorId + " completed");
        
        // If this is the last actor to complete, resolve the result
        if (counter.get() == 9) {
          result.complete("All actors completed, counter = " + counter.get());
        }
        return null;
      });
    }
    
    return result;
  }
  
  /**
   * Main method to run the examples.
   */
  public static void main(String[] args) throws ExecutionException, InterruptedException {
    // Run the correct usage example
    try {
      String correctResult = correctUsage().get();
      System.out.println("Correct usage result: " + correctResult);
    } catch (Exception e) {
      System.out.println("Correct usage failed: " + e.getMessage());
      e.printStackTrace();
    }
    
    System.out.println();
    
    // Run the incorrect usage example
    try {
      String incorrectResult = incorrectUsage().get();
      System.out.println("Incorrect usage correction result: " + incorrectResult);
    } catch (Exception e) {
      System.out.println("Incorrect usage example failed: " + e.getMessage());
      e.printStackTrace();
    }
    
    System.out.println();
    
    // Run the cooperative multitasking example
    try {
      String coopResult = cooperativeMultitasking().get();
      System.out.println("Cooperative multitasking result: " + coopResult);
    } catch (Exception e) {
      System.out.println("Cooperative multitasking example failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}