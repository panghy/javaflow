package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;

import java.util.concurrent.Callable;

/**
 * The central scheduler for JavaFlow.
 * Manages virtual threads and ensures cooperative execution with only one thread active at a time.
 * This implementation uses a SingleThreadedScheduler to ensure proper priority-based scheduling
 * and true single-threaded execution.
 */
public class FlowScheduler implements AutoCloseable {

  /**
   * ThreadLocal to track if we are in a flow context.
   */
  static final ThreadLocal<Task> CURRENT_TASK = new ThreadLocal<>();

  /**
   * The delegate scheduler that does the actual work.
   */
  final SingleThreadedScheduler delegate;

  /**
   * Creates a new FlowScheduler with the single threaded scheduler.
   * The scheduler will use a carrier thread for automatic task processing.
   */
  public FlowScheduler() {
    this(true);
  }

  /**
   * Creates a new FlowScheduler with control over the carrier thread.
   * This constructor is useful for testing when you want to control task execution
   * manually using the pump() method.
   *
   * @param enableCarrierThread if false, the scheduler will not automatically start
   *                            a carrier thread, and task execution will only happen
   *                            through explicit calls to pump()
   */
  public FlowScheduler(boolean enableCarrierThread) {
    this.delegate = new SingleThreadedScheduler(enableCarrierThread);
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
   *
   * @param seconds The delay in seconds
   * @return A future that completes after the delay
   */
  public FlowFuture<Void> scheduleDelay(double seconds) {
    return delegate.scheduleDelay(seconds);
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
   * @return true if the current thread is managed by the flow scheduler
   */
  public static boolean isInFlowContext() {
    return FlowScheduler.CURRENT_TASK.get() != null;
  }

  /**
   * Checks if a task with the specified ID is cancelled.
   * Static method to be used from the Flow API.
   *
   * @param taskId The ID of the task to check
   * @return true if the task is cancelled, false otherwise
   */
  public static boolean isTaskCancelled(Long taskId) {
    return Flow.scheduler().delegate.isTaskCancelled(taskId);
  }

  /**
   * Awaits the completion of a future, suspending the current actor until the future completes.
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