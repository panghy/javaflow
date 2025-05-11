package io.github.panghy.javaflow.scheduler;

/**
 * Defines priority levels for tasks in the Flow system.
 * Lower numeric values indicate higher priority.
 */
public final class TaskPriority {

  private TaskPriority() {
    // Prevent instantiation
  }

  /** Critical system tasks, highest user-settable priority. */
  public static final int CRITICAL = 10;

  /** High priority user tasks. */
  public static final int HIGH = 20;

  /** Default priority for most tasks. */
  public static final int DEFAULT = 30;

  /** Low priority background tasks. */
  public static final int LOW = 40;

  /** Lowest priority tasks, run only when no higher priority tasks are ready. */
  public static final int IDLE = 50;

  /**
   * Validates that a user-provided priority is valid for external use.
   *
   * @param priority The priority to check
   * @throws IllegalArgumentException if the priority is negative
   */
  public static void validateUserPriority(int priority) {
    if (priority < 0) {
      throw new IllegalArgumentException("Task priority cannot be negative: " + priority);
    }
  }
}