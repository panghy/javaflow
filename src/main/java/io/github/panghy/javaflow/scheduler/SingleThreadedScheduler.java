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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SingleThreadedScheduler implements a cooperative multitasking scheduler
 * where only one task is active at a time and tasks must explicitly yield
 * to allow other tasks to run.
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
  
  // Currently running task
  private final AtomicInteger runningTaskCount = new AtomicInteger(0);
  
  // Map to track tasks by their thread
  private final Map<Thread, Task> threadToTask = new ConcurrentHashMap<>();
  
  // Thread factory for creating virtual threads
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
    
    // Create a virtual thread factory
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
    
    synchronized (readyTasks) {
      readyTasks.add(flowTask);
      readyTasks.notifyAll(); // Wake up scheduler
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
    
    // Schedule the task to be resumed
    synchronized (readyTasks) {
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
      readyTasks.notifyAll(); // Wake up scheduler
    }
    
    // Spin until we're resumed (this is faster than park/unpark for simple cooperative scheduling)
    // We know we're resumed when the task state is RUNNING again
    while (task.getState() == Task.TaskState.SUSPENDED) {
      Thread.onSpinWait();
    }
    
    return future;
  }
  
  /**
   * Resume a task that was yielded.
   */
  private void resumeTask(Thread thread) {
    synchronized (readyTasks) {
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
    }
  }
  
  /**
   * Main scheduler loop that processes tasks from the queue.
   */
  private void schedulerLoop() {
    log("Scheduler loop starting");
    
    while (running.get()) {
      try {
        Task task = null;
        
        // Wait for a task to be ready or running count to allow more
        synchronized (readyTasks) {
          // Wait until we can run a task or there's a task in the queue
          while (readyTasks.isEmpty() || runningTaskCount.get() >= config.getCarrierThreadCount()) {
            readyTasks.wait(10); // Short wait to check conditions again
            
            // Exit if scheduler is stopping
            if (!running.get()) {
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
        }
        
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
      }
    }
    
    log("Scheduler loop ending");
  }
  
  /**
   * Finds the highest priority task in the ready queue.
   * 
   * @return The highest priority task, or null if queue is empty
   */
  private Task findHighestPriorityTask() {
    synchronized (readyTasks) {
      if (readyTasks.isEmpty()) {
        return null;
      }
      
      // Simply poll from the PriorityBlockingQueue which will return
      // the highest priority task based on the Task's natural ordering
      return readyTasks.poll();
    }
  }
  
  /**
   * Start a new task in a virtual thread.
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
          synchronized (readyTasks) {
            readyTasks.notifyAll();
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
      synchronized (readyTasks) {
        readyTasks.notifyAll();
      }
    }
  }
}