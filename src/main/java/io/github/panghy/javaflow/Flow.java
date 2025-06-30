package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.scheduler.FlowClock;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.Task;
import io.github.panghy.javaflow.simulation.SimulationContext;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.github.panghy.javaflow.scheduler.TaskPriority.validateUserPriority;

/**
 * Main utility class for the JavaFlow actor framework.
 * Provides static methods to create and manage actors, await futures, and control execution.
 *
 * <p>JavaFlow is an actor-based cooperative multitasking framework. All operations that involve
 * suspension of execution ({@code await}, {@code delay}, and {@code yield}) must be called from
 * within an actor that was started using {@link #startActor}. Attempting to call these methods from
 * outside of a flow task/actor will result in an {@link IllegalStateException}.</p>
 *
 * <p>This restriction is fundamental to the actor model: tasks execute one at a time on a single thread,
 * and can only yield control at well-defined suspension points. This ensures deterministic execution
 * order and makes concurrent code easier to reason about.</p>
 *
 * <p>The recommended pattern is to use {@link #startActor} to create actors, and then use
 * {@link #await}, {@link #delay}, and other suspension methods from within those actors:</p>
 *
 * <pre>{@code
 * // Create an actor:
 * CompletableFuture<String> result = Flow.startActor(() -> {
 *   // Inside an actor, you can use suspension methods:
 *   Flow.delay(1.0).await(); // Wait for 1 second
 *   CompletableFuture<Data> dataFuture = fetchDataAsync();
 *   Data data = Flow.await(dataFuture); // Suspend until data is available
 *   return processData(data);
 * });
 *
 * // INCORRECT - This will throw IllegalStateException:
 * // Flow.delay(1.0).await(); // Error: await called outside of a flow task
 *
 * // Instead, always wrap in a flow task/actor:
 * Flow.startActor(() -> {
 *   // Now this works correctly:
 *   Flow.delay(1.0).await();
 *   return null;
 * });
 * }</pre>
 *
 * <h2>Cancellation Support</h2>
 * 
 * <p>JavaFlow provides comprehensive cancellation support through cooperative cancellation.
 * When a {@link CompletableFuture} is cancelled, the associated task will throw a
 * {@link FlowCancellationException} at the next suspension point (await, yield, or delay)
 * or when {@link #checkCancellation()} is called.</p>
 * 
 * <p><b>Cancellation Methods:</b></p>
 * <ul>
 *   <li>{@link #checkCancellation()} - Throws if cancelled (for periodic checks)</li>
 *   <li>{@link #isCancelled()} - Returns cancellation status (for conditional logic)</li>
 *   <li>{@link #await(CompletableFuture)} - Automatically checks cancellation</li>
 *   <li>{@link #yield()} - Checks cancellation when resuming</li>
 * </ul>
 * 
 * <p><b>Example: Long-running operation with cancellation support:</b></p>
 * <pre>{@code
 * CompletableFuture<String> operation = Flow.startActor(() -> {
 *   try {
 *     for (int i = 0; i < 1000; i++) {
 *       // Check for cancellation
 *       Flow.checkCancellation();
 *       
 *       // Or use conditional check
 *       if (Flow.isCancelled()) {
 *         return "Cancelled at " + i;
 *       }
 *       
 *       // Do work and yield periodically
 *       processItem(i);
 *       if (i % 10 == 0) {
 *         Flow.await(Flow.yield());
 *       }
 *     }
 *     return "Completed";
 *   } finally {
 *     // Cleanup code runs even if cancelled
 *     cleanup();
 *   }
 * });
 * 
 * // Later, cancel the operation
 * operation.cancel();
 * }</pre>
 * 
 * <p><b>Best Practices:</b></p>
 * <ul>
 *   <li>Call {@link #checkCancellation()} periodically in CPU-intensive loops</li>
 *   <li>Use finally blocks for cleanup that must run even if cancelled</li>
 *   <li>Don't catch {@link FlowCancellationException} unless you need specific cleanup</li>
 *   <li>Cancellation automatically propagates to child tasks</li>
 * </ul>
 */
public final class Flow {

  // Singleton scheduler instance
  private static FlowScheduler scheduler = new FlowScheduler();

  // Register shutdown hook to close the scheduler when the JVM exits
  static {
    Runtime.getRuntime().addShutdownHook(new Thread(scheduler::close));
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
    return scheduler;
  }

  /**
   * Sets the global scheduler instance. This should only be used for testing.
   *
   * @param scheduler The new scheduler instance
   */
  public static void setScheduler(FlowScheduler scheduler) {
    Flow.scheduler = scheduler;
  }

  /**
   * Starts a new actor with the given task.
   *
   * <p><b>Cancellation behavior:</b> When the returned future is cancelled using
   * {@link CompletableFuture#cancel(boolean)}, the actor's execution is immediately terminated. 
   * The cancellation happens eagerly - any cleanup code in the actor will not run
   * unless it's in a {@code finally} block or unless the scheduler is allowed to
   * run all remaining tasks (not just until the future resolves).</p>
   *
   * @param task The task to run in the actor
   * @param <T>  The return type of the actor
   * @return A future that will be completed with the actor's result
   */
  public static <T> CompletableFuture<T> startActor(Callable<T> task) {
    return scheduler.schedule(task);
  }

  /**
   * Starts a new actor with the given task and priority.
   *
   * @param task     The task to run in the actor
   * @param priority The priority of the actor (must be non-negative)
   * @param <T>      The return type of the actor
   * @return A future that will be completed with the actor's result
   * @throws IllegalArgumentException if the priority is negative
   */
  public static <T> CompletableFuture<T> startActor(Callable<T> task, int priority) {
    // Validate that user-provided priority isn't negative
    validateUserPriority(priority);
    return scheduler.schedule(task, priority);
  }

  /**
   * Starts a new actor with the given task.
   *
   * @param task The task to run in the actor (returns void)
   * @return A future that will be completed when the actor finishes
   */
  public static CompletableFuture<Void> startActor(Runnable task) {
    return scheduler.schedule(() -> {
      task.run();
      return null;
    });
  }

  /**
   * Starts a new actor with the given task and priority.
   *
   * @param task     The task to run in the actor (returns void)
   * @param priority The priority of the actor (must be non-negative)
   * @return A future that will be completed when the actor finishes
   * @throws IllegalArgumentException if the priority is negative
   */
  public static CompletableFuture<Void> startActor(Runnable task, int priority) {
    // Validate that user-provided priority isn't negative
    validateUserPriority(priority);
    return scheduler.schedule(() -> {
      task.run();
      return null;
    }, priority);
  }

  /**
   * Checks if the future is ready and throws an exception if it completed exceptionally.
   *
   * @param future The future to check
   * @param <T>    The type of the future value
   * @return true if the future is ready
   * @throws Exception If the future completed exceptionally
   */
  public static <T> boolean futureReadyOrThrow(CompletableFuture<T> future) throws Exception {
    // If already completed, return the result immediately
    if (future.isDone()) {
      if (future.isCompletedExceptionally()) {
        try {
          future.getNow(null); // This will throw the exception
        } catch (CancellationException e) {
          throw new FlowCancellationException("Future was cancelled", e);
        } catch (Exception e) {
          Throwable cause = e.getCause();
          if (cause instanceof CancellationException) {
            throw new FlowCancellationException("Future was cancelled", cause);
          } else if (cause instanceof Exception) {
            throw (Exception) cause;
          } else if (cause != null) {
            throw new ExecutionException(cause);
          } else {
            throw e;
          }
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Awaits the completion of a future with a specified priority,
   * suspending the current actor until the future completes.
   * This method must be called from within an actor (a flow task managed by the flow scheduler).
   * If the future is cancelled, the cancellation will propagate to the awaiting task.
   *
   * <p>Awaiting a future is a suspension point in the actor model. When an actor awaits a future,
   * it yields control to the scheduler, allowing other actors to run. When the future completes,
   * the actor will resume execution from this point.</p>
   *
   * <p><b>Cancellation behavior:</b> When a future is cancelled, this method will throw
   * {@link FlowCancellationException}. <b>Important:</b> When this exception is thrown, the task's
   * execution does not resume after the await() call. The exception propagates up the call stack,
   * and the task is terminated unless caught. To ensure cleanup code runs during cancellation,
   * use a {@code finally} block:</p>
   *
   * <pre>{@code
   * Flow.startActor(() -> {
   *   try {
   *     String result = Flow.await(someFuture);
   *     processResult(result); // This won't run if cancelled
   *   } finally {
   *     cleanup(); // This will always run, even on cancellation
   *   }
   * });
   * }</pre>
   *
   * <p>Additionally, this method checks if the current task has been cancelled before attempting
   * to await the future. If the task is already cancelled, it will throw {@link FlowCancellationException}
   * immediately without suspending.</p>
   *
   * <p>This method can only be called from within a flow task created with {@link #startActor}.
   * Attempting to call it from outside a flow task will result in an {@link IllegalStateException}.</p>
   *
   * @param future The future to await
   * @param <T>    The type of the future value
   * @return The value of the completed future
   * @throws FlowCancellationException If the future is cancelled or the current task is cancelled
   * @throws Exception             If the future completes exceptionally
   * @throws IllegalStateException if called outside a flow task
   */
  public static <T> T await(CompletableFuture<T> future) throws Exception {
    if (futureReadyOrThrow(future)) {
      return future.getNow(null);
    }
    
    // Check for cancellation before awaiting
    checkCancellation();
    
    try {
      return scheduler.await(future);
    } catch (CancellationException e) {
      throw new FlowCancellationException("Future was cancelled", e);
    } catch (ExecutionException e) {
      // Check if the cause is a CancellationException
      Throwable cause = e.getCause();
      if (cause instanceof CancellationException) {
        throw new FlowCancellationException("Future was cancelled", cause);
      }
      throw e;
    }
  }

  /**
   * Creates a future that will be completed after the specified delay.
   * This method must be called from within a flow task (actor).
   *
   * <p>Since delay involves suspending the current execution, it can only be used within
   * a flow task created with {@link #startActor}. This ensures proper cooperative scheduling
   * and adherence to the actor model.</p>
   *
   * @param seconds The delay in seconds
   * @return A future that completes after the delay
   * @throws IllegalStateException if called outside a flow task
   */
  public static CompletableFuture<Void> delay(double seconds) {
    // Delay is only supported within flow tasks
    return scheduler.scheduleDelay(seconds);
  }

  /**
   * Creates a future that will be completed after the specified delay with the given priority.
   * This method must be called from within a flow task.
   *
   * @param seconds  The delay in seconds
   * @param priority The priority of the task
   * @return A future that completes after the delay
   * @throws IllegalStateException if called outside a flow task
   */
  public static CompletableFuture<Void> delay(double seconds, int priority) {
    validateUserPriority(priority);
    // Delay is only supported within flow tasks
    return scheduler.scheduleDelay(seconds, priority);
  }

  /**
   * Checks if the current thread is executing within a flow managed context.
   *
   * @return true if the current thread is managed by the flow scheduler
   */
  public static boolean isInFlowContext() {
    return FlowScheduler.isInFlowContext();
  }

  /**
   * Checks if the current task has been cancelled and throws an exception if so.
   * 
   * <p>This method should be called periodically in long-running operations to enable
   * cooperative cancellation. When a task is cancelled, this method will throw a
   * {@link FlowCancellationException}, allowing the task to exit cleanly.</p>
   * 
   * <p><b>Usage example:</b></p>
   * <pre>{@code
   * Flow.startActor(() -> {
   *   for (int i = 0; i < 1000; i++) {
   *     // Check for cancellation every iteration
   *     Flow.checkCancellation();
   *     
   *     // Do some work
   *     processItem(i);
   *   }
   *   return "completed";
   * });
   * }</pre>
   * 
   * <p><b>Important notes:</b></p>
   * <ul>
   *   <li>This method does not block or yield - it's a quick check</li>
   *   <li>Safe to call outside a flow context - will simply return without throwing,
   *       as there is no task to check for cancellation</li>
   *   <li>When called within a flow context, the thrown exception should typically 
   *       not be caught, allowing cancellation to propagate</li>
   * </ul>
   * 
   * @throws FlowCancellationException if the current task is cancelled
   * @see #isCancelled() for a non-throwing alternative
   * @see #await(CompletableFuture) which also checks for cancellation
   * @since 1.3
   */
  public static void checkCancellation() {
    Task currentTask = FlowScheduler.getCurrentTask();
    if (currentTask != null && currentTask.isCancelled()) {
      throw new FlowCancellationException("Task was cancelled");
    }
  }
  
  /**
   * Returns true if the current task has been cancelled.
   * 
   * <p>This is a non-throwing alternative to {@link #checkCancellation()} that allows
   * for conditional logic based on cancellation status. It's useful when you want to
   * perform different actions based on whether the task is cancelled, rather than
   * immediately throwing an exception.</p>
   * 
   * <p><b>Usage example:</b></p>
   * <pre>{@code
   * Flow.startActor(() -> {
   *   List<String> results = new ArrayList<>();
   *   
   *   for (String item : items) {
   *     if (Flow.isCancelled()) {
   *       // Graceful early exit with partial results
   *       return results;
   *     }
   *     
   *     results.add(processItem(item));
   *   }
   *   
   *   return results;
   * });
   * }</pre>
   * 
   * <p><b>Important notes:</b></p>
   * <ul>
   *   <li>Returns false when called outside a flow context</li>
   *   <li>Does not throw exceptions</li>
   *   <li>Useful for graceful shutdown or partial result handling</li>
   *   <li>Can be used in finally blocks for cleanup decisions</li>
   * </ul>
   * 
   * @return true if the current task is cancelled, false if not cancelled or outside flow context
   * @see #checkCancellation() for a throwing alternative
   * @since 1.3
   */
  public static boolean isCancelled() {
    Task currentTask = FlowScheduler.getCurrentTask();
    return currentTask != null && currentTask.isCancelled();
  }

  /**
   * Returns the current time in milliseconds from the flow scheduler's clock.
   * In normal operation, this returns system time, but in simulation mode
   * it can return a controlled time value.
   *
   * @return The current time in milliseconds
   */
  public static long now() {
    return scheduler.currentTimeMillis();
  }

  /**
   * Returns the current time in seconds from the flow scheduler's clock.
   * In normal operation, this returns system time, but in simulation mode
   * it can return a controlled time value.
   *
   * @return The current time in seconds
   */
  public static double nowSeconds() {
    return scheduler.currentTimeSeconds();
  }

  /**
   * Determines if the flow scheduler is using a simulated clock.
   * 
   * <p>This method checks both the scheduler's clock and the current
   * SimulationContext to provide a consistent view of simulation state.
   *
   * @return true if the clock is a simulated clock, false otherwise
   */
  public static boolean isSimulated() {
    // Check scheduler's clock first
    boolean schedulerSimulated = scheduler.getClock().isSimulated();
    
    // Also check SimulationContext for consistency
    SimulationContext context = SimulationContext.current();
    if (context != null) {
      // If we have a context, use its simulation state
      // This ensures consistency with simulation configuration
      return context.isSimulatedContext() && schedulerSimulated;
    }
    
    // Fall back to just scheduler's clock
    return schedulerSimulated;
  }

  /**
   * Gets the clock being used by the flow scheduler.
   *
   * @return The clock instance
   */
  public static FlowClock getClock() {
    return scheduler.getClock();
  }

  /**
   * Yields control from the current actor to allow other actors to run.
   * The current actor will be rescheduled to continue execution in the next event loop cycle.
   *
   * <p>Yielding is a cooperative scheduling mechanism that allows an actor to voluntarily
   * give up its execution slot, allowing other actors to run. This is particularly useful
   * when an actor has been running for a long time and wants to ensure fairness.</p>
   *
   * <p><b>Cancellation behavior:</b> If the task is cancelled while yielded, the returned
   * future will complete exceptionally with a {@link FlowCancellationException} when the
   * task attempts to resume. Similar to {@link #await}, execution does not continue after
   * the yield point when cancelled. Use a {@code finally} block to ensure cleanup code runs:</p>
   *
   * <pre>{@code
   * Flow.startActor(() -> {
   *   try {
   *     // Do some work
   *     Flow.await(Flow.yield()); // Yield to other tasks
   *     // More work (won't run if cancelled during yield)
   *   } finally {
   *     cleanup(); // Always runs, even if cancelled
   *   }
   * });
   * }</pre>
   *
   * <p>This method can only be called from within a flow task created with {@link #startActor}.
   * Attempting to call it from outside a flow task will result in an {@link IllegalStateException}.</p>
   *
   * @return A future that completes when the actor is resumed
   * @throws IllegalStateException if called outside a flow task
   */
  public static CompletableFuture<Void> yield() {
    return scheduler.yield();
  }

  /**
   * Yields control from the current actor to allow other actors to run with the specified priority.
   * The current actor will be rescheduled with this priority to continue execution in the next event loop cycle.
   *
   * <p><b>Cancellation behavior:</b> Same as {@link #yield()}. If the task is cancelled while yielded,
   * execution will not resume after the yield point. The returned future will complete exceptionally
   * with a {@link FlowCancellationException}.</p>
   *
   * @param priority The priority to use when rescheduling the task (must be non-negative)
   * @return A future that completes when the actor is resumed
   * @throws IllegalArgumentException if the priority is negative
   * @throws IllegalStateException if called outside a flow task
   */
  public static CompletableFuture<Void> yield(int priority) {
    // Validate that user-provided priority isn't negative
    validateUserPriority(priority);
    return scheduler.yield(priority);
  }

  /**
   * Shuts down the flow scheduler.
   */
  public static void shutdown() {
    scheduler.shutdown();
  }
}