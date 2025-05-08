package io.github.panghy.javaflow.scheduler;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a schedulable task in the JavaFlow system.
 * Tasks are ordered by priority, creation time, and task ID for consistent ordering.
 */
public class Task implements Comparable<Task> {
  private static final AtomicLong SEQUENCE = new AtomicLong(0);

  private final long id;
  private final int priority;
  private final long creationTime;
  private final long sequence;
  private final Callable<?> callable;
  private Thread thread;
  private TaskState state;
  
  /**
   * Task state enum.
   */
  public enum TaskState {
    CREATED,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  /**
   * Creates a new task with the given ID, priority, and callable.
   *
   * @param id The task ID (for debugging)
   * @param priority The task priority (lower value means higher priority)
   * @param callable The callable to execute
   */
  public Task(long id, int priority, Callable<?> callable) {
    this.id = id;
    this.priority = priority;
    this.creationTime = System.currentTimeMillis();
    this.sequence = SEQUENCE.getAndIncrement();
    this.callable = Objects.requireNonNull(callable, "Callable cannot be null");
    this.state = TaskState.CREATED;
  }

  /**
   * Gets the task's ID.
   *
   * @return The ID
   */
  public long getId() {
    return id;
  }

  /**
   * Gets the task's priority.
   *
   * @return The priority
   */
  public int getPriority() {
    return priority;
  }
  
  /**
   * Gets the task's creation time.
   *
   * @return The creation time in milliseconds since epoch
   */
  public long getCreationTime() {
    return creationTime;
  }
  
  /**
   * Gets the task's sequence number for FIFO ordering of same-priority tasks.
   *
   * @return The sequence number
   */
  public long getSequence() {
    return sequence;
  }

  /**
   * Gets the callable to execute.
   *
   * @return The callable
   */
  public Callable<?> getCallable() {
    return callable;
  }

  /**
   * Gets the thread running this task.
   *
   * @return The thread
   */
  public Thread getThread() {
    return thread;
  }

  /**
   * Sets the thread running this task.
   *
   * @param thread The thread
   */
  public void setThread(Thread thread) {
    this.thread = thread;
  }
  
  /**
   * Gets the task's state.
   *
   * @return The task state
   */
  public TaskState getState() {
    return state;
  }
  
  /**
   * Sets the task's state.
   *
   * @param state The new state
   */
  public void setState(TaskState state) {
    this.state = state;
  }

  @Override
  public int compareTo(Task other) {
    // First compare by priority (lower value means higher priority)
    int result = Integer.compare(this.priority, other.priority);
    if (result != 0) {
      return result;
    }
    
    // If same priority, compare by creation time (earlier time means higher priority)
    result = Long.compare(this.creationTime, other.creationTime);
    if (result != 0) {
      return result;
    }
    
    // If same creation time, use sequence number for stable FIFO ordering
    return Long.compare(this.sequence, other.sequence);
  }
  
  @Override
  public String toString() {
    return "Task{id=" + id + ", priority=" + priority + ", state=" + state + "}";
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Task task = (Task) o;
    return id == task.id;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}