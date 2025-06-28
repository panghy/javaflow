package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.CompletableFuture;

import java.util.Objects;

/**
 * Represents a timer task in the JavaFlow scheduler.
 * A TimerTask encapsulates a task to be executed at a scheduled time and
 * provides metadata about its scheduling and execution.
 */
public class TimerTask implements Comparable<TimerTask> {
  
  private final long id;
  private final long scheduledTimeMillis;
  private final Runnable task;
  private final int priority;
  private final CompletableFuture<Void> future;
  private final Task parentTask;
  
  /**
   * Creates a new timer task.
   *
   * @param id                  Unique ID for the timer task
   * @param scheduledTimeMillis Absolute time in milliseconds when the task should execute
   * @param task                The task to execute when the timer fires
   * @param priority            The priority of the task
   * @param future              Future to complete when the task executes
   * @param parentTask          The parent flow task if any
   */
  public TimerTask(long id, long scheduledTimeMillis, Runnable task, int priority,
                   CompletableFuture<Void> future, Task parentTask) {
    this.id = id;
    this.scheduledTimeMillis = scheduledTimeMillis;
    this.task = Objects.requireNonNull(task, "Task cannot be null");
    this.priority = priority;
    this.future = Objects.requireNonNull(future, "Future cannot be null");
    this.parentTask = parentTask;
  }
  
  /**
   * Gets the timer task ID.
   *
   * @return The timer ID
   */
  public long getId() {
    return id;
  }
  
  /**
   * Gets the scheduled execution time in milliseconds.
   *
   * @return The scheduled time
   */
  public long getScheduledTimeMillis() {
    return scheduledTimeMillis;
  }
  
  /**
   * Gets the task to execute.
   *
   * @return The task
   */
  public Runnable getTask() {
    return task;
  }
  
  /**
   * Gets the task priority.
   *
   * @return The priority
   */
  public int getPriority() {
    return priority;
  }
  
  /**
   * Gets the future associated with this timer task.
   *
   * @return The future to complete when the task executes
   */
  public CompletableFuture<Void> getFuture() {
    return future;
  }
  
  /**
   * Gets the parent flow task if any.
   *
   * @return The parent task or null if none
   */
  public Task getParentTask() {
    return parentTask;
  }
  
  /**
   * Executes this timer task, completing the promise with the result or exception.
   */
  public void execute() {
    try {
      task.run();
      future.complete(null);
    } catch (Throwable t) {
      future.completeExceptionally(t);
    }
  }
  
  @Override
  public int compareTo(TimerTask other) {
    // First compare by scheduled time
    int result = Long.compare(this.scheduledTimeMillis, other.scheduledTimeMillis);
    if (result != 0) {
      return result;
    }
    
    // Then by priority (lower value means higher priority)
    result = Integer.compare(this.priority, other.priority);
    if (result != 0) {
      return result;
    }
    
    // Lastly by ID for stable ordering
    return Long.compare(this.id, other.id);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TimerTask timerTask = (TimerTask) o;
    return id == timerTask.id;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
  
  @Override
  public String toString() {
    return "TimerTask{" +
        "id=" + id +
        ", scheduledTime=" + scheduledTimeMillis +
        ", priority=" + priority +
        '}';
  }
}