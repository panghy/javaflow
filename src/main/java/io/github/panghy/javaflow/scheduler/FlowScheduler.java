package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;

import java.util.concurrent.Callable;

/**
 * The central scheduler for JavaFlow.
 * Manages virtual threads and ensures cooperative execution with only one thread active at a time.
 * This implementation uses a SingleThreadedScheduler to ensure proper priority-based scheduling
 * and true single-threaded execution.
 */
public class FlowScheduler implements AutoCloseable {
  // The delegate scheduler that does the actual work
  private final SingleThreadedScheduler delegate;

  /**
   * Creates a new FlowScheduler with the single threaded scheduler.
   */
  public FlowScheduler() {
    this.delegate = new SingleThreadedScheduler();
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