package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.scheduler.FlowClock;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.simulation.SimulationContext;

import java.util.concurrent.Callable;
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
 * FlowFuture<String> result = Flow.startActor(() -> {
 *   // Inside an actor, you can use suspension methods:
 *   Flow.delay(1.0).await(); // Wait for 1 second
 *   FlowFuture<Data> dataFuture = fetchDataAsync();
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
   * @param task The task to run in the actor
   * @param <T>  The return type of the actor
   * @return A future that will be completed with the actor's result
   */
  public static <T> FlowFuture<T> startActor(Callable<T> task) {
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
  public static <T> FlowFuture<T> startActor(Callable<T> task, int priority) {
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
  public static FlowFuture<Void> startActor(Runnable task) {
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
  public static FlowFuture<Void> startActor(Runnable task, int priority) {
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
  public static <T> boolean futureReadyOrThrow(FlowFuture<T> future) throws Exception {
    // If already completed, return the result immediately
    if (future.isDone()) {
      if (future.isCompletedExceptionally()) {
        Throwable cause = future.getException();
        if (cause instanceof Exception) {
          throw (Exception) cause;
        } else {
          throw new ExecutionException(cause);
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
   * <p>This method can only be called from within a flow task created with {@link #startActor}.
   * Attempting to call it from outside a flow task will result in an {@link IllegalStateException}.</p>
   *
   * @param future The future to await
   * @param <T>    The type of the future value
   * @return The value of the completed future
   * @throws Exception             If the future completes exceptionally
   * @throws IllegalStateException if called outside a flow task
   */
  public static <T> T await(FlowFuture<T> future) throws Exception {
    if (futureReadyOrThrow(future)) {
      return future.getNow();
    }
    return scheduler.await(future);
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
  public static FlowFuture<Void> delay(double seconds) {
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
  public static FlowFuture<Void> delay(double seconds, int priority) {
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
   * <p>This method can only be called from within a flow task created with {@link #startActor}.
   * Attempting to call it from outside a flow task will result in an {@link IllegalStateException}.</p>
   *
   * @return A future that completes when the actor is resumed
   * @throws IllegalStateException if called outside a flow task
   */
  public static FlowFuture<Void> yieldF() {
    return scheduler.yield();
  }

  /**
   * Yields control from the current actor to allow other actors to run with the specified priority.
   * The current actor will be rescheduled with this priority to continue execution in the next event loop cycle.
   *
   * @param priority The priority to use when rescheduling the task (must be non-negative)
   * @return A future that completes when the actor is resumed
   * @throws IllegalArgumentException if the priority is negative
   */
  public static FlowFuture<Void> yieldF(int priority) {
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