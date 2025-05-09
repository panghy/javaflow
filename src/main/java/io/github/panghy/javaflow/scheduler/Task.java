package io.github.panghy.javaflow.scheduler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
  private TaskState state;
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private final Task parent;
  private final AtomicReference<HashSet<Task>> children = new AtomicReference<>();
  private final AtomicReference<Runnable> cancellationCallback = new AtomicReference<>();

  /**
   * Task state enum.
   */
  public enum TaskState {
    CREATED,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    FAILED
  }

  /**
   * Creates a new task with the given ID, priority, and callable.
   *
   * @param id       The task ID (for debugging)
   * @param priority The task priority (lower value means higher priority)
   * @param callable The callable to execute
   * @param parent   The parent task, if any.
   */
  public Task(long id, int priority, Callable<?> callable, Task parent) {
    this.id = id;
    this.priority = priority;
    this.parent = parent;
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
    if (this.state == state ||
        this.state == TaskState.COMPLETED ||
        this.state == TaskState.FAILED) {
      return;
    }
    if (state == TaskState.COMPLETED || state == TaskState.FAILED) {
      if (parent != null) {
        parent.removeChild(this);
      }
    }
    this.state = state;
  }

  /**
   * Gets the parent task, if any.
   *
   * @return The parent task, or null if this is a top-level task
   */
  public Task getParent() {
    return parent;
  }

  /**
   * Adds a child task.
   * This operation will be rejected if the task is already cancelled or completed/failed.
   *
   * @param child The child task
   * @throws IllegalStateException if this task is already cancelled or completed/failed
   */
  public void addChild(Task child) {
    if (isCancelled()) {
      throw new IllegalStateException("Cannot add child to cancelled task");
    }
    if (state == TaskState.COMPLETED || state == TaskState.FAILED) {
      throw new IllegalStateException("Cannot add child to completed or failed task");
    }
    children.updateAndGet(list -> {
      if (list == null) {
        list = new HashSet<>();
      }
      list.add(child);
      return list;
    });
  }

  /**
   * Removes a child task.
   *
   * @param child The child task
   */
  public void removeChild(Task child) {
    children.updateAndGet(list -> {
      if (list != null) {
        list.remove(child);
      }
      return list;
    });
  }

  /**
   * Sets the cancellation callback.
   *
   * @param callback The cancellation callback.
   */
  public void setCancellationCallback(Runnable callback) {
    cancellationCallback.set(callback);
  }

  /**
   * Cancels the task. This will also cancel all child tasks.
   */
  public void cancel() {
    if (!isCancelled.getAndSet(true)) {
      Runnable callback = cancellationCallback.get();
      if (callback != null) {
        callback.run();
      }
      HashSet<Task> children = this.children.get();
      if (children != null) {
        Arrays.stream(children.toArray(Task[]::new)).
            forEach(Task::cancel);
      }
      if (parent != null) {
        parent.removeChild(this);
      }
    }
  }

  /**
   * Checks if this task has been cancelled.
   * This can be called from within task execution to bail early from CPU-intensive operations.
   *
   * @return true if the task has been cancelled, false otherwise
   */
  public boolean isCancelled() {
    return isCancelled.get();
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