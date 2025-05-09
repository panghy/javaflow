package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.util.LoggingUtil.debug;
import static io.github.panghy.javaflow.util.LoggingUtil.error;
import static io.github.panghy.javaflow.util.LoggingUtil.info;
import static io.github.panghy.javaflow.util.LoggingUtil.warn;

/**
 * SingleThreadedScheduler implements a cooperative multitasking scheduler
 * where only one task is active at a time and tasks must explicitly yield
 * to allow other tasks to run.
 *
 * <p>This scheduler is designed to work with the JDK internal Continuation API
 * to provide true single-threaded execution with explicit yielding:
 * <ul>
 *   <li>Using a single platform thread as the carrier thread</li>
 *   <li>Using Continuation.yield() for cooperative task switching</li>
 *   <li>Maintaining a priority queue for task scheduling</li>
 * </ul>
 * </p>
 */
public class SingleThreadedScheduler implements AutoCloseable {
  private static final Logger LOGGER = Logger.getLogger(SingleThreadedScheduler.class.getName());

  // Task ID counter
  private final AtomicLong taskIdCounter = new AtomicLong(0);

  /**
   * Queue of ready tasks sorted by priority
   */
  private final PriorityBlockingQueue<Task> readyTasks = new PriorityBlockingQueue<>();

  /**
   * Lock to protect access to readyTasks and related operations
   * Using ReentrantLock instead of synchronized for better thread safety
   */
  private final ReentrantLock taskLock = new ReentrantLock();

  /**
   * Condition for signaling when tasks are added or when scheduler should wake up
   */
  private final Condition tasksAvailableCondition = taskLock.newCondition();

  /**
   * Map to track continuations by their task ID
   */
  private final Map<Long, Continuation> taskToContinuation = new ConcurrentHashMap<>();

  /**
   * Map to track tasks by their task ID
   */
  private final Map<Long, Task> idToTask = new ConcurrentHashMap<>();

  /**
   * Thread for the scheduler loop
   */
  private Thread schedulerThread;

  /**
   * Flag to control scheduler running state
   */
  private final AtomicBoolean running = new AtomicBoolean(false);

  /**
   * Map to store the continuation scope for each task
   */
  private final Map<Long, ContinuationScope> taskToScope = new HashMap<>();

  /**
   * Flag to indicate whether the scheduler should start its carrier thread.
   * Setting this to false is useful for testing to control task execution manually.
   */
  private final boolean enableCarrierThread;

  /**
   * Map to store promises for yield operations
   */
  private final Map<Long, FlowPromise<Void>> yieldPromises = new HashMap<>();


  /**
   * Creates a new single-threaded scheduler with default configuration.
   * The scheduler will use a carrier thread for automatic task processing.
   */
  public SingleThreadedScheduler() {
    this(true);
  }

  /**
   * Creates a new single-threaded scheduler with control over the carrier thread.
   * This constructor is useful for testing when you want to control task execution
   * manually using the pump() method.
   *
   * @param enableCarrierThread if false, the scheduler will not automatically start
   *                            a carrier thread, and task execution will only happen
   *                            through explicit calls to pump()
   */
  public SingleThreadedScheduler(boolean enableCarrierThread) {
    this.enableCarrierThread = enableCarrierThread;
  }

  /**
   * Starts the scheduler if it hasn't been started yet.
   * If the carrier thread is disabled, this only marks the scheduler as running
   * without starting the scheduler thread.
   */
  public synchronized void start() {
    if (running.compareAndSet(false, true)) {
      info(LOGGER, "starting flow scheduler");

      if (enableCarrierThread) {
        // Start scheduler thread only if carrier thread is enabled
        schedulerThread = Thread.ofPlatform()
            .name("flow-scheduler")
            .daemon(true)
            .start(this::schedulerLoop);
      } else {
        debug(LOGGER, "carrier thread disabled, tasks will only execute via pump()");
      }
    }
  }

  /**
   * Schedules a task to be executed with default priority.
   *
   * @param task Task to execute
   * @return Future for the task's result
   */
  public <T> FlowFuture<T> schedule(Callable<T> task) {
    return schedule(task, TaskPriority.DEFAULT);
  }

  /**
   * Schedules a task to be executed with specified priority.
   *
   * @param task     Task to execute
   * @param priority Priority level
   * @return Future for the task's result
   */
  public <T> FlowFuture<T> schedule(Callable<T> task, int priority) {
    // Start scheduler if not already running
    start();

    // Create and schedule the task
    long taskId = taskIdCounter.incrementAndGet();

    // Create a future/promise pair for the result
    FlowFuture<T> future = new FlowFuture<>();
    FlowPromise<T> promise = future.getPromise();

    // Create a task wrapper that completes the promise
    Callable<T> wrappedTask = () -> {
      try {
        T result = task.call();
        promise.complete(result);
        return result;
      } catch (Throwable t) {
        promise.completeExceptionally(t);
        throw t;
      }
    };

    // Create a task and associate it with the current task if any
    Task currentTask = FlowScheduler.CURRENT_TASK.get();
    Task flowTask = new Task(taskId, priority, wrappedTask, currentTask);
    flowTask.setCancellationCallback(() -> cancelTask(taskId));
    if (currentTask != null) {
      currentTask.addChild(flowTask);
    }
    promise.whenComplete((_, t) -> {
      if (t instanceof CancellationException) {
        flowTask.cancel();
      }
    });

    // Store task in the task map
    idToTask.put(taskId, flowTask);

    debug(LOGGER, "scheduling task " + taskId);

    taskLock.lock();
    try {
      readyTasks.add(flowTask);
      tasksAvailableCondition.signalAll(); // Wake up scheduler
    } finally {
      taskLock.unlock();
    }

    return future;
  }

  /**
   * Creates a delay task that completes after specified time.
   *
   * @param seconds Delay in seconds
   * @return Future that completes after the delay
   */
  public FlowFuture<Void> scheduleDelay(double seconds) {
    FlowFuture<Void> future = new FlowFuture<>();

    // Convert to milliseconds
    long delayMs = (long) (seconds * 1000);

    // Schedule a sleep task that completes the future
    schedule(() -> {
      try {
        Thread.sleep(Duration.ofMillis(delayMs));
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      }
    }, TaskPriority.LOW)
        .map(v -> {
          future.getPromise().complete(null);
          return null;
        });

    return future;
  }

  /**
   * Yields control from the current task to allow other tasks to run.
   *
   * @return Future that completes when task resumes
   */
  public FlowFuture<Void> yield() {
    return this.yield(null); // Maintain the same priority
  }

  /**
   * Yields control from the current task to allow other tasks to run.
   *
   * @return Future that completes when task resumes
   */
  public FlowFuture<Void> yield(Integer priority) {
    if (!FlowScheduler.isInFlowContext()) {
      throw new IllegalStateException("yield called outside of a flow task");
    }

    // Need to find the task ID for the current continuation
    final Task task = FlowScheduler.CURRENT_TASK.get();
    if (task == null) {
      throw new IllegalStateException("yield called for unknown task");
    }

    // Create future/promise for resumption
    FlowFuture<Void> future = new FlowFuture<>();
    FlowPromise<Void> promise = future.getPromise();

    // Store promise to be completed when task is resumed
    yieldPromises.put(task.getId(), promise);

    schedule(() -> {
      promise.complete(null);
      return null;
    }, priority == null ? task.getPriority() : priority);

    return future;
  }

  /**
   * Resume a task that was yielded.
   */
  private void resumeTask(long taskId) {
    Continuation continuation = taskToContinuation.get(taskId);
    Task task = idToTask.get(taskId);

    if (continuation != null && task != null) {
      debug(LOGGER, "resuming task " + taskId);

      // Mark as running
      task.setState(Task.TaskState.RUNNING);

      // Complete the promise associated with the yield operation
      FlowPromise<Void> promise = yieldPromises.remove(taskId);
      if (promise != null) {
        promise.complete(null);
      }

      try {
        FlowScheduler.CURRENT_TASK.set(task);

        // Resume the continuation
        continuation.run();
      } finally {
        FlowScheduler.CURRENT_TASK.remove();
      }

      // If the continuation is done, clean up
      if (continuation.isDone()) {
        debug(LOGGER, "task " + taskId + " completed");
        task.setState(Task.TaskState.COMPLETED);
        taskToContinuation.remove(taskId);
        taskToScope.remove(taskId);
        idToTask.remove(taskId);
      }
    }
  }

  /**
   * Main scheduler loop that processes tasks from the queue.
   */
  private void schedulerLoop() {
    while (running.get()) {
      Task task;
      boolean lockAcquired = false;

      try {
        // Attempt to acquire the lock - if we can't, other operations may be in progress
        taskLock.lock();
        lockAcquired = true;

        // Wait until there's a task in the queue
        while (readyTasks.isEmpty()) {
          // Exit early if scheduler is stopping
          if (!running.get()) {
            warn(LOGGER, "scheduler loop stopping");
            taskLock.unlock();
            lockAcquired = false;
            return;
          }

          tasksAvailableCondition.await();

          // Check again if we should exit
          if (!running.get()) {
            warn(LOGGER, "scheduler loop stopping");
            taskLock.unlock();
            lockAcquired = false;
            return;
          }
        }

        // Take next task if available
        task = findHighestPriorityTask();

        // We can release the lock once we have obtained a task
        taskLock.unlock();
        lockAcquired = false;

        // Process task if we got one
        if (task != null) {
          debug(LOGGER, "picking up task " + task.getId());

          if (taskToContinuation.containsKey(task.getId())) {
            // This is a resume task for an existing continuation
            resumeTask(task.getId());
          } else {
            // This is a new task - create and run a new continuation
            startTask(task);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        warn(LOGGER, "scheduler thread interrupted", e);
        break;
      } catch (Exception e) {
        error(LOGGER, "error in scheduler", e);
      } finally {
        // Ensure we release the lock if we still hold it
        if (lockAcquired) {
          taskLock.unlock();
        }
      }
    }

    info(LOGGER, "scheduler loop ending");
  }

  /**
   * Finds the highest priority task in the ready queue.
   * Note: This method should only be called when holding the taskLock.
   *
   * @return The highest priority task, or null if queue is empty
   */
  private Task findHighestPriorityTask() {
    if (readyTasks.isEmpty()) {
      return null;
    }

    // Simply poll from the PriorityBlockingQueue which will return
    // the highest priority task based on the Task's natural ordering
    return readyTasks.poll();
  }

  /**
   * Start a new task using a Continuation.
   *
   * <p>This method creates and runs a new Continuation for the task.
   * The continuation will run on the current thread (the scheduler thread)
   * and yield when requested, allowing other tasks to run.</p>
   */
  private void startTask(Task task) {
    debug(LOGGER, "task " + task.getId() + " starting");
    task.setState(Task.TaskState.RUNNING);

    // Create a dedicated scope for this task
    ContinuationScope taskScope = new ContinuationScope("flow-task-" + task.getId());
    taskToScope.put(task.getId(), taskScope);

    // Create a continuation for the task
    Continuation continuation = new Continuation(taskScope, () -> {
      try {
        // Set the flow context flag to true for this task
        FlowScheduler.CURRENT_TASK.set(task);

        // Execute the task
        task.getCallable().call();
      } catch (Exception e) {
        task.setState(Task.TaskState.FAILED);
        warn(LOGGER, "task " + task.getId() + " failed: ", e);
      } finally {
        // Clear the flow context flag
        FlowScheduler.CURRENT_TASK.remove();
      }
    });

    // Store the continuation for future yields/resumes
    taskToContinuation.put(task.getId(), continuation);

    // Run the continuation
    Thread.currentThread().setName("flow-task-" + task.getId());
    continuation.run();
    Thread.currentThread().setName("flow-scheduler");

    // If the continuation completed without yielding, clean up
    if (continuation.isDone()) {
      debug(LOGGER, "task " + task.getId() + " completed immediately");
      task.setState(Task.TaskState.COMPLETED);
      taskToContinuation.remove(task.getId());
      taskToScope.remove(task.getId());
      idToTask.remove(task.getId());
    }
  }

  /**
   * Waits for a future to complete, resuming the current task when it does.
   *
   * @param future The future to wait for
   * @param <T>    The type of the future value
   */
  public <T> T await(FlowFuture<T> future) throws Exception {
    if (!FlowScheduler.isInFlowContext()) {
      throw new IllegalStateException("await called outside of a flow task");
    }

    // Need to find the task ID for the current continuation
    final Task task = FlowScheduler.CURRENT_TASK.get();
    if (task == null) {
      throw new IllegalStateException("missing task");
    }
    ContinuationScope currentScope = taskToScope.get(task.getId());

    if (currentScope == null) {
      throw new IllegalStateException("missing task scope");
    }

    if (task.getState() != Task.TaskState.RUNNING) {
      throw new IllegalStateException("task is not running");
    }

    debug(LOGGER, "task " + task.getId() + " suspending");

    // Mark this task as yielding
    task.setState(Task.TaskState.SUSPENDED);

    future.getPromise().whenComplete((_, _) -> {
      taskLock.lock();
      try {
        readyTasks.add(task);
        tasksAvailableCondition.signalAll(); // Wake up scheduler
      } finally {
        taskLock.unlock();
      }
    });

    // Yield to allow other tasks to run.
    Continuation.yield(currentScope);

    // Code resumes here when future completes
    if (task.isCancelled()) {
      throw new CancellationException("task cancelled");
    } else if (future.isCompletedExceptionally()) {
      throw new ExecutionException(future.getException());
    }
    return future.getNow();
  }

  /**
   * Processes all ready tasks until all have yielded or completed.
   * This is useful for testing, where we want to ensure all tasks have a chance to run.
   * In particular, when testing timers or other asynchronous operations, this method
   * can be used to ensure all ready tasks are processed before checking the results.
   *
   * <p>This method processes tasks in a deterministic order based on task priority.</p>
   *
   * <p>The method ensures that all tasks in the ready queue at the start of the call
   * are processed at least once, even if new tasks are added to the queue during processing.</p>
   *
   * @return The number of tasks that were processed
   */
  public int pump() {
    if (enableCarrierThread) {
      throw new IllegalStateException("pump() can only be called when carrier thread is disabled");
    }
    int processedTasks = 0;

    taskLock.lock();
    try {
      // If there are no ready tasks, we're done
      if (readyTasks.isEmpty()) {
        return 0;
      }

      // Take a snapshot of all currently ready tasks
      // This ensures we process all tasks that are ready now, but don't get stuck
      // in an infinite loop if tasks keep yielding and adding themselves back to the queue
      List<Task> tasksToProcess = new ArrayList<>();
      while (!readyTasks.isEmpty()) {
        Task task = findHighestPriorityTask();
        if (task == null) {
          break;
        }
        tasksToProcess.add(task);
      }

      // Process all tasks in the snapshot
      for (Task task : tasksToProcess) {
        // Release the lock while executing the task
        taskLock.unlock();
        try {
          if (taskToContinuation.containsKey(task.getId())) {
            // This is a resume task for an existing continuation
            resumeTask(task.getId());
          } else {
            // This is a new task - create and run a new continuation
            startTask(task);
          }
          processedTasks++;
        } finally {
          // Re-acquire the lock for the next iteration
          taskLock.lock();
        }
      }

      return processedTasks;
    } finally {
      if (taskLock.isHeldByCurrentThread()) {
        taskLock.unlock();
      }
    }
  }

  /**
   * Cancels a task with the specified ID.
   * This will mark the task as cancelled, remove it from the ready queue, and complete
   * any associated yield promises with an exceptional completion.
   *
   * @param taskId The ID of the task to cancel
   */
  private void cancelTask(long taskId) {
    debug(LOGGER, "cancelling task " + taskId);

    taskLock.lock();
    try {
      Task task = idToTask.get(taskId);
      if (task == null) {
        // Task doesn't exist or has already completed
        return;
      }

      // If the task is yielding, we will complete its promise exceptionally.
      FlowPromise<Void> promise = yieldPromises.remove(taskId);
      if (promise != null) {
        promise.completeExceptionally(new CancellationException());
      }
    } finally {
      taskLock.unlock();
    }
  }

  /**
   * Shuts down the scheduler.
   */
  @Override
  public void close() {
    if (running.compareAndSet(true, false)) {
      info(LOGGER, "Shutting down scheduler");

      // Interrupt scheduler thread
      if (schedulerThread != null) {
        schedulerThread.interrupt();
      }

      // Wake up waiting threads
      taskLock.lock();
      try {
        tasksAvailableCondition.signalAll();
      } finally {
        taskLock.unlock();
      }

      // Clear all task-related maps
      taskToContinuation.clear();
      taskToScope.clear();
      idToTask.clear();
      yieldPromises.clear();
    }
  }
}