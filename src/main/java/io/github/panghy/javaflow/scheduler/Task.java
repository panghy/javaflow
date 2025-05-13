package io.github.panghy.javaflow.scheduler;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Represents a schedulable task in the JavaFlow system.
 */
public class Task {
  private static final AtomicLong SEQUENCE = new AtomicLong(0);

  private final long id;
  // Priority as assigned during creation
  private final int originalPriority;
  private final long creationTime;
  private final long sequence;
  private final Callable<?> callable;
  private TaskState state;
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private final Task parent;
  private final AtomicReference<HashSet<Task>> children = new AtomicReference<>();
  private final AtomicReference<Consumer<Collection<Long>>> cancellationCallback =
      new AtomicReference<>();

  // Track timer tasks associated with this task for cancellation propagation
  private final Collection<Long> associatedTimerIds = new HashSet<>();

  // Last time the priority was boosted - used for priority aging calculation
  private volatile long lastPriorityBoostTime;
  private volatile int effectivePriority;

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
    this.originalPriority = priority;
    this.parent = parent;
    this.creationTime = System.currentTimeMillis();
    this.lastPriorityBoostTime = this.creationTime; // Initialize the last boost time
    this.sequence = SEQUENCE.getAndIncrement();
    this.callable = Objects.requireNonNull(callable, "Callable cannot be null");
    this.state = TaskState.CREATED;
    this.effectivePriority = priority;
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
   * Gets the task's priority as assigned during creation.
   *
   * @return The original priority
   */
  public int getOriginalPriority() {
    return originalPriority;
  }

  /**
   * Gets the task's priority.
   * The effective priority used for scheduling is calculated on-the-fly by the scheduler
   * taking into account the time since this task was last boosted.
   *
   * @return The task's base priority
   */
  public int getPriority() {
    return originalPriority;
  }
  
  /**
   * Gets the cached effective priority for this task.
   * 
   * @return The effective priority (lower values mean higher priority)
   */
  public int getEffectivePriority() {
    return effectivePriority;
  }
  
  /**
   * Sets the cached effective priority for this task.
   * 
   * @param effectivePriority The new effective priority value
   */
  public void setEffectivePriority(int effectivePriority) {
    this.effectivePriority = effectivePriority;
  }

  /**
   * Resets the last boost time for this task to the current time.
   * This is called when a task is picked for execution to reset its aging.
   *
   * @param currentTime The current time in milliseconds
   */
  public void resetPriorityBoostTime(long currentTime) {
    this.lastPriorityBoostTime = currentTime;
  }

  /**
   * Gets the time when this task's priority was last boosted.
   * Used to calculate priority aging.
   *
   * @return The timestamp of the last priority boost
   */
  public long getLastPriorityBoostTime() {
    return lastPriorityBoostTime;
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
  public void setCancellationCallback(Consumer<Collection<Long>> callback) {
    cancellationCallback.updateAndGet(existing -> {
      if (existing == null) {
        return callback;
      } else {
        // Chain the callbacks to preserve multiple registrations
        return (timerIds) -> {
          existing.accept(timerIds);
          callback.accept(timerIds);
        };
      }
    });
  }

  /**
   * Registers a timer task with this task for cancellation propagation.
   *
   * @param timerId The ID of the timer task to register
   */
  public void registerTimerTask(long timerId) {
    associatedTimerIds.add(timerId);
  }

  /**
   * Unregisters a timer task from this task.
   *
   * @param timerId The ID of the timer task to unregister
   */
  public void unregisterTimerTask(long timerId) {
    associatedTimerIds.remove(timerId);
  }

  /**
   * Gets the set of timer task IDs associated with this task.
   *
   * @return An unmodifiable set of timer task IDs
   */
  public Set<Long> getAssociatedTimerIds() {
    return Set.copyOf(associatedTimerIds);
  }

  /**
   * Cancels the task. This will also cancel all child tasks and associated timer tasks.
   */
  public void cancel() {
    if (!isCancelled.getAndSet(true)) {
      // Run the cancellation callback if one is set
      Consumer<Collection<Long>> callback = cancellationCallback.get();
      if (callback != null) {
        try {
          callback.accept(associatedTimerIds);
        } catch (Exception e) {
          throw new RuntimeException("Error running cancellation callback for task " + id, e);
        }
      }

      // Cancel all child tasks
      HashSet<Task> children = this.children.get();
      if (children != null) {
        Arrays.stream(children.toArray(Task[]::new)).
            forEach(Task::cancel);
      }

      // Remove this task from its parent
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
  public String toString() {
    return "Task{id=" + id +
           ", priority=" + originalPriority +
           ", state=" + state + "}";
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