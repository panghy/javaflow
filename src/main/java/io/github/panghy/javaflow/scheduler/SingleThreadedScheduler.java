package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SingleThreadedScheduler implements a cooperative multitasking scheduler
 * where only one task is active at a time and tasks must explicitly yield
 * to allow other tasks to run.
 * 
 * <p>This scheduler is designed to work efficiently with virtual threads 
 * and follows best practices for virtual thread usage:
 * <ul>
 *   <li>Using ReentrantLock instead of synchronized blocks to avoid carrier thread pinning</li>
 *   <li>Using proper blocking mechanisms for waiting rather than busy-spinning</li>
 *   <li>Using signaling conditions for efficient thread coordination</li>
 * </ul>
 * </p>
 */
public class SingleThreadedScheduler implements AutoCloseable {
  private static final Logger LOGGER = Logger.getLogger(SingleThreadedScheduler.class.getName());
  
  // Task ID counter
  private final AtomicLong taskIdCounter = new AtomicLong(0);
  
  // Configuration for this scheduler
  private final FlowSchedulerConfig config;
  
  /**
   * Gets the scheduler's configuration.
   * 
   * @return Configuration
   */
  public FlowSchedulerConfig getConfig() {
    return config;
  }
  
  // Queue of ready tasks sorted by priority
  private final PriorityBlockingQueue<Task> readyTasks = new PriorityBlockingQueue<>();
  
  // Lock to protect access to readyTasks and related operations
  // Using ReentrantLock instead of synchronized for better virtual thread performance
  private final ReentrantLock taskLock = new ReentrantLock();
  
  // Condition for signaling when tasks are added or when scheduler should wake up
  private final Condition tasksAvailableCondition = taskLock.newCondition();
  
  // Condition for signaling when a task is resumed
  private final Condition taskResumedCondition = taskLock.newCondition();
  
  // Currently running task
  private final AtomicInteger runningTaskCount = new AtomicInteger(0);
  
  // Map to track tasks by their thread
  private final Map<Thread, Task> threadToTask = new ConcurrentHashMap<>();
  
  // Thread factory for creating virtual threads
  // Virtual threads are lightweight and efficiently managed by the JVM
  // They are ideal for I/O-bound or blocking operations as they don't consume OS threads
  private final ThreadFactory virtualThreadFactory;
  
  // Thread for the scheduler loop
  private Thread schedulerThread;
  
  // Flag to control scheduler running state
  private final AtomicBoolean running = new AtomicBoolean(false);
  
  // Map of threads that are yielding to their resume callbacks
  private final Map<Thread, List<Runnable>> yieldCallbacks = new ConcurrentHashMap<>();
  
  /**
   * Creates a new single-threaded scheduler with the specified configuration.
   * 
   * @param config Configuration options
   */
  public SingleThreadedScheduler(FlowSchedulerConfig config) {
    this.config = config;
    
    // Create a virtual thread factory (using JDK 21+ API)
    // Virtual threads are preferred over platform threads for tasks that may block
    // as they have minimal overhead and don't consume OS resources when blocked
    this.virtualThreadFactory = Thread.ofVirtual()
        .name("flow-actor-", 0)
        .factory();
  }
  
  /**
   * Creates a new single-threaded scheduler with default configuration.
   */
  public SingleThreadedScheduler() {
    this(FlowSchedulerConfig.DEFAULT);
  }
  
  /**
   * Starts the scheduler if it hasn't been started yet.
   */
  public synchronized void start() {
    if (running.compareAndSet(false, true)) {
      log("Starting scheduler");
      
      // Start scheduler thread
      schedulerThread = Thread.ofPlatform()
          .name("flow-scheduler")
          .daemon(true)
          .start(this::schedulerLoop);
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
   * @param task Task to execute
   * @param priority Priority level
   * @return Future for the task's result
   */
  public <T> FlowFuture<T> schedule(Callable<T> task, int priority) {
    // Start scheduler if not already running
    start();
    
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
    
    // Create and schedule the task
    long taskId = taskIdCounter.incrementAndGet();
    Task flowTask = new Task(taskId, priority, wrappedTask);
    
    log("Scheduling task " + flowTask);
    
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
   * Yields control from the current actor to allow other actors to run.
   * 
   * @return Future that completes when actor resumes
   */
  public FlowFuture<Void> yield() {
    Thread currentThread = Thread.currentThread();
    FlowFuture<Void> future = new FlowFuture<>();
    FlowPromise<Void> promise = future.getPromise();
    
    Task task = threadToTask.get(currentThread);
    if (task == null) {
      // Not a flow task, just complete immediately
      promise.complete(null);
      return future;
    }
    
    log("Task " + task + " yielding");
    
    // Mark this task as yielding
    task.setState(Task.TaskState.SUSPENDED);
    
    // Atomic decrement of running count allows another task to start
    runningTaskCount.decrementAndGet();
    
    // Create resume callback that completes the future
    Runnable resumeCallback = () -> {
      task.setState(Task.TaskState.RUNNING);
      promise.complete(null);
    };
    
    taskLock.lock();
    try {
      // Store callback for when task is resumed
      yieldCallbacks.computeIfAbsent(currentThread, k -> new ArrayList<>())
          .add(resumeCallback);
      
      // Create continuation task with same task ID but possibly different priority
      // Lower priority yielding tasks allow higher priority ones to run first
      Task resumeTask = new Task(task.getId(), task.getPriority(), new Callable<Void>() {
        @Override
        public Void call() {
          resumeTask(currentThread);
          return null;
        }
      });
      
      // Add to priority queue
      readyTasks.add(resumeTask);
      tasksAvailableCondition.signalAll(); // Wake up scheduler
      
      // Wait for task to be resumed - more efficient than spinning for virtual threads
      // This avoids busy-waiting and allows the carrier thread to execute other virtual threads
      while (task.getState() == Task.TaskState.SUSPENDED) {
        try {
          taskResumedCondition.await(); // Will release lock and re-acquire it when signaled
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          // Continue waiting if interrupted, as we need to be resumed
        }
      }
    } finally {
      taskLock.unlock();
    }
    
    return future;
  }
  
  /**
   * Resume a task that was yielded.
   */
  private void resumeTask(Thread thread) {
    taskLock.lock();
    try {
      // Mark thread as running again
      Task task = threadToTask.get(thread);
      if (task != null) {
        task.setState(Task.TaskState.RUNNING);
      }
      
      // Process all callbacks for this thread
      List<Runnable> callbacks = yieldCallbacks.remove(thread);
      if (callbacks != null) {
        for (Runnable callback : callbacks) {
          callback.run();
        }
      }
      
      // Increment running count
      runningTaskCount.incrementAndGet();
      
      // Signal all waiting tasks in case this task was the one they were waiting for
      taskResumedCondition.signalAll();
    } finally {
      taskLock.unlock();
    }
  }
  
  /**
   * Main scheduler loop that processes tasks from the queue.
   */
  private void schedulerLoop() {
    log("Scheduler loop starting");
    
    while (running.get()) {
      Task task = null;
      boolean lockAcquired = false;
      
      try {
        // Attempt to acquire the lock - if we can't, other operations may be in progress
        taskLock.lock();
        lockAcquired = true;
        
        // Wait until we can run a task or there's a task in the queue
        while (readyTasks.isEmpty() || runningTaskCount.get() >= config.getCarrierThreadCount()) {
          // Exit early if scheduler is stopping
          if (!running.get()) {
            taskLock.unlock();
            lockAcquired = false;
            return;
          }
          
          // Await with a timeout to periodically check conditions
          tasksAvailableCondition.await(100, TimeUnit.MILLISECONDS);
          
          // Check again if we should exit
          if (!running.get()) {
            taskLock.unlock();
            lockAcquired = false;
            return;
          }
        }
        
        // Take next task if available
        if (!readyTasks.isEmpty()) {
          // If strict priority ordering is enabled, find highest priority task
          if (config.isEnforcePriorities()) {
            task = findHighestPriorityTask();
          } else {
            // Otherwise just take the next one from the queue
            task = readyTasks.poll();
          }
        }
        
        // We can release the lock once we have obtained a task
        taskLock.unlock();
        lockAcquired = false;
        
        // Process task if we got one
        if (task != null) {
          log("Processing task " + task);
          
          if (task.getThread() != null && threadToTask.containsKey(task.getThread())) {
            // This is a resume task for an existing thread
            resumeTask(task.getThread());
          } else {
            // This is a new task - run in a new virtual thread
            runningTaskCount.incrementAndGet();
            startTask(task);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log("Scheduler thread interrupted");
        break;
      } catch (Exception e) {
        log("Error in scheduler: " + e);
        e.printStackTrace();
      } finally {
        // Ensure we release the lock if we still hold it
        if (lockAcquired) {
          taskLock.unlock();
        }
      }
    }
    
    log("Scheduler loop ending");
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
   * Start a new task in a virtual thread.
   * 
   * <p>This method creates and starts a new virtual thread for each task.
   * Virtual threads have low overhead and are automatically managed by the JVM.
   * When a virtual thread blocks (e.g., on I/O or when yielding), it doesn't
   * block its carrier thread, allowing other virtual threads to execute.
   * This makes them ideal for cooperative multitasking.</p>
   */
  private void startTask(Task task) {
    Thread thread = virtualThreadFactory.newThread(new Runnable() {
      @Override
      public void run() {
        Thread currentThread = Thread.currentThread();
        
        try {
          // Register thread with task
          threadToTask.put(currentThread, task);
          task.setThread(currentThread);
          task.setState(Task.TaskState.RUNNING);
          
          log("Task " + task + " starting");
          
          // Execute the task
          task.getCallable().call();
          
          // Mark as completed
          task.setState(Task.TaskState.COMPLETED);
          log("Task " + task + " completed");
        } catch (InterruptedException e) {
          task.setState(Task.TaskState.CANCELLED);
          log("Task " + task + " cancelled");
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          task.setState(Task.TaskState.FAILED);
          log("Task " + task + " failed: " + e);
          e.printStackTrace();
        } finally {
          // Unregister the thread
          threadToTask.remove(currentThread);
          
          // Decrement running count
          runningTaskCount.decrementAndGet();
          
          // Wake up scheduler
          taskLock.lock();
          try {
            tasksAvailableCondition.signalAll();
          } finally {
            taskLock.unlock();
          }
        }
      }
    });
    
    // Start the thread
    thread.start();
  }
  
  /**
   * Logs a message if debug logging is enabled.
   */
  private void log(String message) {
    if (config.isDebugLogging()) {
      LOGGER.log(Level.INFO, message);
    }
  }
  
  /**
   * Shuts down the scheduler.
   */
  @Override
  public void close() {
    if (running.compareAndSet(true, false)) {
      log("Shutting down scheduler");
      
      // Interrupt scheduler thread
      if (schedulerThread != null) {
        schedulerThread.interrupt();
      }
      
      // Wake up waiting threads
      taskLock.lock();
      try {
        tasksAvailableCondition.signalAll();
        taskResumedCondition.signalAll();
      } finally {
        taskLock.unlock();
      }
    }
  }
}