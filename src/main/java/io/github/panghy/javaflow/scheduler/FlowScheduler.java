package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;

import java.util.Set;
import java.util.concurrent.Callable;

/**
 * The central scheduler for JavaFlow.
 * Manages virtual threads and ensures cooperative execution with only one thread active at a time.
 * This implementation uses a SingleThreadedScheduler to ensure proper priority-based scheduling
 * and true single-threaded execution.
 *
 * <p>The FlowScheduler is responsible for enforcing the actor model constraints, ensuring that
 * suspension operations ({@code await}, {@code delay}, and {@code yield}) can only be used
 * within flow tasks (actors). These constraints are fundamental to the actor model:</p>
 *
 * <ul>
 *   <li>Actors are isolated units of concurrency that communicate via messages (futures)</li>
 *   <li>Only one actor executes at a time (single-threaded execution)</li>
 *   <li>Actors yield control at well-defined suspension points</li>
 *   <li>Tasks are scheduled according to priority</li>
 * </ul>
 *
 * <p>The scheduler uses a ThreadLocal context to track when code is executing within an actor,
 * and will throw IllegalStateException if suspension operations are called outside of this
 * context.</p>
 */
public class FlowScheduler implements AutoCloseable {

  /**
   * ThreadLocal to track if we are in a flow context.
   */
  static final ThreadLocal<Task> CURRENT_TASK = new ThreadLocal<>();

  /**
   * The delegate scheduler that does the actual work.
   */
  protected SingleThreadedScheduler delegate;

  /**
   * Creates a new FlowScheduler with the single threaded scheduler.
   * The scheduler will use a carrier thread for automatic task processing
   * and a real-time clock for timing operations.
   */
  public FlowScheduler() {
    this(true);
  }

  /**
   * Creates a new FlowScheduler with control over the carrier thread.
   * The scheduler will use a real-time clock for timing operations.
   *
   * @param enableCarrierThread if false, the scheduler will not automatically start
   *                            a carrier thread, and task execution will only happen
   *                            through explicit calls to pump()
   */
  public FlowScheduler(boolean enableCarrierThread) {
    this(enableCarrierThread, FlowClock.createRealClock());
  }

  /**
   * Creates a new FlowScheduler with control over the carrier thread and clock.
   *
   * @param enableCarrierThread if false, the scheduler will not automatically start
   *                            a carrier thread
   * @param clock               the clock to use for timing operations
   */
  public FlowScheduler(boolean enableCarrierThread, FlowClock clock) {
    this.delegate = new SingleThreadedScheduler(enableCarrierThread, clock);
  }


  /**
   * Gets the clock being used by this scheduler.
   *
   * @return The clock instance
   */
  public FlowClock getClock() {
    return delegate.getClock();
  }

  /**
   * Schedules a task to be executed by the flow scheduler.
   *
   * @param task The task to execute
   * @param <T>  The return type of the task
   * @return A future that will be completed with the task's result
   */
  public <T> FlowFuture<T> schedule(Callable<T> task) {
    return delegate.schedule(task);
  }

  /**
   * Schedules a task to be executed by the flow scheduler with the specified priority.
   *
   * @param task     The task to execute
   * @param priority The priority of the task
   * @param <T>      The return type of the task
   * @return A future that will be completed with the task's result
   */
  public <T> FlowFuture<T> schedule(Callable<T> task, int priority) {
    return delegate.schedule(task, priority);
  }

  /**
   * Creates a future that will be completed after the specified delay.
   * This method must be called from within a flow task.
   *
   * <p>This method enforces the actor model constraint that suspension operations
   * can only be called from within flow tasks. It uses {@link #isInFlowContext()}
   * to verify the current execution context and will throw an exception if called
   * from outside a flow task.</p>
   *
   * @param seconds The delay in seconds
   * @return A future that completes after the delay
   * @throws IllegalStateException if called outside a flow task
   */
  public FlowFuture<Void> scheduleDelay(double seconds) {
    return delegate.scheduleDelay(seconds);
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
  public FlowFuture<Void> scheduleDelay(double seconds, int priority) {
    return delegate.scheduleDelay(seconds, priority);
  }


  /**
   * Cancels a scheduled timer.
   *
   * @param timerId The ID of the timer to cancel
   * @return true if the timer was found and canceled, false otherwise
   */
  public boolean cancelTimer(long timerId) {
    return delegate.cancelTimer(timerId);
  }

  /**
   * Gets the current time from the scheduler's clock.
   *
   * @return The current time in milliseconds
   */
  public long currentTimeMillis() {
    return delegate.getClock().currentTimeMillis();
  }

  /**
   * Gets the current time from the scheduler's clock.
   *
   * @return The current time in seconds
   */
  public double currentTimeSeconds() {
    return delegate.getClock().currentTimeSeconds();
  }

  /**
   * If using a simulated clock, advances the clock by the specified duration
   * and processes any timer tasks that become due.
   *
   * @param millis The number of milliseconds to advance
   * @return The number of timer tasks processed
   * @throws IllegalStateException if the clock is not a simulated clock
   */
  public int advanceTime(long millis) {
    return delegate.advanceTime(millis);
  }

  /**
   * Gets all active tasks in the scheduler.
   * This is primarily for testing and debugging purposes.
   *
   * @return A set of all active tasks
   */
  public Set<Task> getActiveTasks() {
    return delegate.getActiveTasks();
  }

  /**
   * Gets the current task for a given future.
   * This is primarily for testing and debugging purposes.
   *
   * @param future The future to get the task for
   * @return The task that created the future, or null if not found
   */
  public Task getCurrentTaskForFuture(FlowFuture<?> future) {
    return delegate.getCurrentTaskForFuture(future);
  }


  /**
   * Yields control from the current actor to allow other actors to run.
   *
   * @return A future that completes when the actor is resumed
   */
  public FlowFuture<Void> yield() {
    return delegate.yield();
  }

  /**
   * Yields control from the current actor to allow other actors to run with the specified priority.
   *
   * @param priority The priority to use when rescheduling the task
   * @return A future that completes when the actor is resumed
   */
  public FlowFuture<Void> yield(int priority) {
    return delegate.yield(priority);
  }

  /**
   * Processes all ready tasks until they have yielded or completed.
   * This is useful for testing, particularly when testing timers or other asynchronous operations,
   * to ensure all ready tasks have been processed before checking results.
   *
   * @return The number of tasks that were processed
   */
  public int pump() {
    return delegate.pump();
  }

  /**
   * Checks if the current thread is executing within a flow managed context.
   *
   * <p>This method is used internally to verify that suspension operations
   * ({@code await}, {@code delay}, and {@code yield}) are only called from within
   * flow tasks. It checks the ThreadLocal context to determine if the current
   * execution is happening within an actor started using
   * {@link io.github.panghy.javaflow.Flow#startActor}.</p>
   *
   * <p>Users can also call this method to check if code is running within a flow task
   * when designing APIs that need to interact with the JavaFlow framework.</p>
   *
   * @return true if the current thread is managed by the flow scheduler and executing within
   * a flow task
   */
  public static boolean isInFlowContext() {
    return FlowScheduler.CURRENT_TASK.get() != null;
  }

  /**
   * Awaits the completion of a future, suspending the current actor until the future
   * completes.
   *
   * @param future The future to await
   * @param <T>    The type of the future value
   * @return The value of the completed future
   */
  public <T> T await(FlowFuture<T> future) throws Exception {
    return delegate.await(future);
  }

  /**
   * Shuts down the scheduler.
   */
  @Override
  public void close() {
    delegate.close();
  }

  /**
   * Shuts down the scheduler (alias for close).
   */
  public void shutdown() {
    close();
  }
}