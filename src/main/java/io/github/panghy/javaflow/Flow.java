package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.scheduler.FlowScheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Main utility class for the JavaFlow actor framework.
 * Provides static methods to create and manage actors, await futures, and control execution.
 */
public final class Flow {

  // Singleton scheduler instance
  private static final FlowScheduler SCHEDULER = new FlowScheduler();
  
  // Register shutdown hook to close the scheduler when the JVM exits
  static {
    Runtime.getRuntime().addShutdownHook(new Thread(SCHEDULER::close));
  }

  private Flow() {
    // Utility class should not be instantiated
  }

  /**
   * Returns the global scheduler instance.
   *
   * @return The flow scheduler
   */
  public static FlowScheduler scheduler() {
    return SCHEDULER;
  }

  /**
   * Starts a new actor with the given task.
   *
   * @param task The task to run in the actor
   * @param <T> The return type of the actor
   * @return A future that will be completed with the actor's result
   */
  public static <T> FlowFuture<T> start(Callable<T> task) {
    return SCHEDULER.schedule(task);
  }

  /**
   * Starts a new actor with the given task and priority.
   *
   * @param task The task to run in the actor
   * @param priority The priority of the actor
   * @param <T> The return type of the actor
   * @return A future that will be completed with the actor's result
   */
  public static <T> FlowFuture<T> start(Callable<T> task, int priority) {
    return SCHEDULER.schedule(task, priority);
  }

  /**
   * Starts a new actor with the given task.
   *
   * @param task The task to run in the actor (returns void)
   * @return A future that will be completed when the actor finishes
   */
  public static FlowFuture<Void> start(Runnable task) {
    return SCHEDULER.schedule(() -> {
      task.run();
      return null;
    });
  }

  /**
   * Starts a new actor with the given task and priority.
   *
   * @param task The task to run in the actor (returns void)
   * @param priority The priority of the actor
   * @return A future that will be completed when the actor finishes
   */
  public static FlowFuture<Void> start(Runnable task, int priority) {
    return SCHEDULER.schedule(() -> {
      task.run();
      return null;
    }, priority);
  }

  /**
   * Awaits the completion of a future, suspending the current actor until the future completes.
   * This method must be called from within an actor (a virtual thread managed by the flow 
   * scheduler).
   *
   * @param future The future to await
   * @param <T> The type of the future value
   * @return The value of the completed future
   * @throws Exception If the future completes exceptionally
   */
  public static <T> T await(FlowFuture<T> future) throws Exception {
    try {
      // In this simplified implementation, we just directly call get() which will
      // block the current thread until the future completes
      return future.get();
    } catch (ExecutionException e) {
      // Unwrap the execution exception to propagate the original cause
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      } else {
        throw e;
      }
    }
  }

  /**
   * Creates a future that will be completed after the specified delay.
   *
   * @param seconds The delay in seconds
   * @return A future that completes after the delay
   */
  public static FlowFuture<Void> delay(double seconds) {
    return SCHEDULER.scheduleDelay(seconds);
  }

  /**
   * Yields control from the current actor to allow other actors to run.
   * The current actor will be rescheduled to continue execution in the next event loop cycle.
   *
   * @return A future that completes when the actor is resumed
   */
  public static FlowFuture<Void> yield() {
    return SCHEDULER.yield();
  }
}