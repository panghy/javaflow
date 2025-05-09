package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
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

  // Timer task ID counter
  private final AtomicLong timerIdCounter = new AtomicLong(0);

  /**
   * Queue of ready tasks sorted by priority
   */
  private final PriorityBlockingQueue<Task> readyTasks = new PriorityBlockingQueue<>();

  /**
   * Queue of timer tasks sorted by execution time
   * Map from execution time to TimerTask
   */
  private final NavigableMap<Long, List<TimerTask>> timerTasks = new TreeMap<>();

  /**
   * Map from timer ID to TimerTask for fast lookup/cancellation
   */
  private final Map<Long, TimerTask> timerIdToTask = new ConcurrentHashMap<>();

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
   * The clock used by this scheduler for timing operations
   */
  private final FlowClock clock;

  /**
   * Creates a new single-threaded scheduler with default configuration.
   * The scheduler will use a carrier thread for automatic task processing
   * and a real-time clock for timing operations.
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
    this(enableCarrierThread, FlowClock.createRealClock());
  }

  /**
   * Creates a new single-threaded scheduler with control over the carrier thread
   * and a custom clock implementation for timing operations.
   *
   * @param enableCarrierThread if false, the scheduler will not automatically start
   *                            a carrier thread
   * @param clock               the clock to use for timing operations
   */
  public SingleThreadedScheduler(boolean enableCarrierThread, FlowClock clock) {
    this.enableCarrierThread = enableCarrierThread;
    this.clock = clock;
  }

  /**
   * Gets the clock being used by this scheduler.
   *
   * @return The clock instance
   */
  public FlowClock getClock() {
    return clock;
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
    promise.whenComplete(($, t) -> {
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
    return scheduleDelay(seconds, TaskPriority.LOW);
  }

  /**
   * Creates a delay task that completes after specified time with the given priority.
   *
   * @param seconds  Delay in seconds
   * @param priority Priority of the task
   * @return Future that completes after the delay
   */
  public FlowFuture<Void> scheduleDelay(double seconds, int priority) {
    if (seconds < 0) {
      throw new IllegalArgumentException("Delay cannot be negative");
    }

    // Verify we're in a flow context, since delay (like yield) requires a flow context
    if (!FlowScheduler.isInFlowContext()) {
      throw new IllegalStateException("scheduleDelay called outside of a flow task");
    }

    // Get the current task - we know this won't be null due to the check above
    final Task currentTask = FlowScheduler.CURRENT_TASK.get();
    if (currentTask == null) {
      throw new IllegalStateException("scheduleDelay called for unknown task");
    }

    // Start scheduler if not already running
    start();

    // Create a future/promise pair for the result
    FlowFuture<Void> future = new FlowFuture<>();
    FlowPromise<Void> promise = future.getPromise();

    // Convert to milliseconds
    long delayMs = (long) (seconds * 1000);

    // Schedule the timer task
    scheduleTimerTask(delayMs, promise, priority, currentTask);

    return future;
  }

  /**
   * Schedules a timer task to execute after the specified delay.
   *
   * @param delayMs    Delay in milliseconds
   * @param promise    Promise to complete when the timer fires
   * @param priority   Priority of the task
   * @param parentTask Parent task if any
   */
  private void scheduleTimerTask(long delayMs, FlowPromise<Void> promise, int priority,
                                 Task parentTask) {
    // Generate a unique timer ID
    final long timerId = timerIdCounter.incrementAndGet();

    // Calculate absolute execution time
    final long executionTimeMs = clock.currentTimeMillis() + delayMs;

    // Create a timer task
    TimerTask timerTask = new TimerTask(
        timerId,
        executionTimeMs,
        () -> {
        },  // Empty runnable - we just need to complete the promise
        priority,
        promise,
        parentTask
    );

    taskLock.lock();
    try {
      // Store in timer map for execution at the appropriate time
      timerTasks.computeIfAbsent(executionTimeMs, $ -> new ArrayList<>()).add(timerTask);

      // Store in ID map for cancellation
      timerIdToTask.put(timerId, timerTask);

      // Signal the scheduler thread to wake up and check for timer events
      tasksAvailableCondition.signalAll();

    } finally {
      taskLock.unlock();
    }
  }

  /**
   * Cancels a scheduled timer task.
   *
   * @param timerId ID of the timer to cancel
   * @return true if the timer was found and canceled, false otherwise
   */
  public boolean cancelTimer(long timerId) {
    taskLock.lock();
    try {
      TimerTask task = timerIdToTask.remove(timerId);
      if (task != null) {
        // Remove from the execution map
        List<TimerTask> tasksAtTime = timerTasks.get(task.getScheduledTimeMillis());
        if (tasksAtTime != null) {
          tasksAtTime.remove(task);
          if (tasksAtTime.isEmpty()) {
            timerTasks.remove(task.getScheduledTimeMillis());
          }
        }

        // No need to cancel through the clock anymore - all timer management is in the scheduler

        // Complete the promise exceptionally
        task.getPromise().completeExceptionally(new CancellationException("Timer cancelled"));

        return true;
      }
      return false;
    } finally {
      taskLock.unlock();
    }
  }

  /**
   * Processes any timer tasks that are due to execute.
   * This is called during the scheduler loop to check for and execute timer tasks.
   *
   * @return The number of timer tasks processed
   */
  private int processTimerTasks() {
    int processed = 0;
    long now = clock.currentTimeMillis();

    taskLock.lock();
    try {
      // Get all timer entries with execution time <= now
      while (!timerTasks.isEmpty()) {
        Map.Entry<Long, List<TimerTask>> entry = timerTasks.firstEntry();
        if (entry == null || entry.getKey() > now) {
          // No more timers ready to execute
          break;
        }

        // Process all tasks at this time
        List<TimerTask> tasksAtTime = entry.getValue();
        for (TimerTask task : new ArrayList<>(tasksAtTime)) {
          timerIdToTask.remove(task.getId());
          task.execute();
          processed++;
        }

        // Remove the processed time entry
        timerTasks.remove(entry.getKey());
      }
    } finally {
      taskLock.unlock();
    }

    return processed;
  }

  /**
   * Gets the time when the next timer will fire, or Long.MAX_VALUE if no timers are scheduled.
   *
   * @return The time in milliseconds of the next timer event
   */
  public long getNextTimerTime() {
    taskLock.lock();
    try {
      if (timerTasks.isEmpty()) {
        return Long.MAX_VALUE;
      }
      return timerTasks.firstKey();
    } finally {
      taskLock.unlock();
    }
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

        // Process any timer tasks that are ready
        processTimerTasks();

        // Wait until there's a task in the queue or a timer is due
        while (readyTasks.isEmpty()) {
          // Exit early if scheduler is stopping
          if (!running.get()) {
            warn(LOGGER, "scheduler loop stopping");
            taskLock.unlock();
            lockAcquired = false;
            return;
          }

          // Check if we have timers to wait for
          long nextTimer = getNextTimerTime();
          long now = clock.currentTimeMillis();

          if (nextTimer != Long.MAX_VALUE && nextTimer <= now) {
            // Process timers before waiting
            processTimerTasks();
            // Check if any tasks became ready
            if (!readyTasks.isEmpty()) {
              break;
            }
            // Recalculate next timer time
            nextTimer = getNextTimerTime();
          }

          if (nextTimer != Long.MAX_VALUE) {
            // Wait until next timer or until signaled
            long waitTime = nextTimer - now;
            if (waitTime > 0) {
              //noinspection ResultOfMethodCallIgnored
              tasksAvailableCondition.await(waitTime, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
          } else {
            // No timers, wait indefinitely for tasks
            tasksAvailableCondition.await();
          }

          // Check again if we should exit
          if (!running.get()) {
            warn(LOGGER, "scheduler loop stopping");
            taskLock.unlock();
            lockAcquired = false;
            return;
          }

          // Process any timer tasks that are ready after waiting
          processTimerTasks();

          // If any tasks were added or timer tasks made ready tasks, break out of wait loop
          if (!readyTasks.isEmpty()) {
            break;
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

    future.getPromise().whenComplete(($, __) -> {
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
   * Also processes any timer tasks that are due based on the current time.
   * This is useful for testing, where we want to ensure all tasks have a chance to run.
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

    // First process any timer tasks that are due
    int timerTasksProcessed = processTimerTasks();

    taskLock.lock();
    try {
      // If there are no ready tasks, we're done
      if (readyTasks.isEmpty()) {
        return timerTasksProcessed;
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

      return processedTasks + timerTasksProcessed;
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
   * If using a simulated clock, advances the clock by the specified duration
   * and processes any timer tasks that become due.
   *
   * @param millis The number of milliseconds to advance
   * @return The number of timer tasks processed
   * @throws IllegalStateException if the clock is not a simulated clock
   */
  public int advanceTime(long millis) {
    if (!clock.isSimulated()) {
      throw new IllegalStateException("advanceTime can only be called with a simulated clock");
    }

    SimulatedClock simulatedClock = (SimulatedClock) clock;

    System.out.println(">>> SingleThreadedScheduler.advanceTime(" + millis + ") - start time: " +
                       clock.currentTimeMillis() + "ms");

    // First process any already-due timer tasks
    int tasksExecuted = processTimerTasks();
    System.out.println(">>> Initial processTimerTasks executed " + tasksExecuted + " tasks");

    // Advance the clock without processing tasks (the clock no longer handles tasks)
    simulatedClock.advanceTime(millis);
    System.out.println(">>> Clock advanced to " + clock.currentTimeMillis() + "ms");

    // Process any new timer tasks that are now due based on the new time
    int additionalTimerTasks = processTimerTasks();
    tasksExecuted += additionalTimerTasks;
    System.out.println(">>> Timer tasks processed after advancing time: " +
                       additionalTimerTasks + " tasks");

    // Ensure callbacks are processed by doing multiple pump cycles
    // This is important for tasks with cascading futures and delays
    for (int i = 0; i < 3; i++) {
      int pumpTasks = pump();
      tasksExecuted += pumpTasks;
      System.out.println(">>> Pump cycle #" + i + " executed " + pumpTasks + " tasks");

      // Break early if no tasks were processed
      if (pumpTasks == 0) {
        break;
      }

      // Process any timer tasks that might have been scheduled during pumping
      int moreTasks = processTimerTasks();
      tasksExecuted += moreTasks;
      System.out.println(">>> Additional timer tasks after pump #" + i + ": " + moreTasks);
    }

    System.out.println(">>> advanceTime complete - total tasks executed: " + tasksExecuted);
    return tasksExecuted;
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
      timerTasks.clear();
      timerIdToTask.clear();
    }
  }
}