package io.github.panghy.javaflow.examples;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Examples demonstrating various cancellation patterns in JavaFlow.
 * These examples show best practices for implementing cancellable operations.
 */
public class CancellationExamples {

  /**
   * Example 1: Basic cancellation with delay.
   * Shows how cancellation interrupts a waiting operation.
   */
  public static class BasicCancellation {
    
    public static FlowFuture<String> longRunningOperation() {
      return Flow.startActor(() -> {
        System.out.println("Starting long operation...");
        
        try {
          // This will be interrupted by cancellation
          Flow.await(Flow.delay(10.0)); // 10 second delay
          
          System.out.println("Operation completed");
          return "Success";
          
        } catch (FlowCancellationException e) {
          System.out.println("Operation was cancelled");
          throw e;
        }
      });
    }
    
    public static void runExample() {
      FlowFuture<String> operation = longRunningOperation();
      
      // Cancel after 1 second
      Flow.startActor(() -> {
        Flow.await(Flow.delay(1.0));
        System.out.println("Cancelling operation...");
        operation.cancel();
        return null;
      });
    }
  }

  /**
   * Example 2: Cooperative cancellation in CPU-intensive operations.
   * Shows periodic checking for cancellation.
   */
  public static class CooperativeCancellation {
    
    public static FlowFuture<Integer> processLargeDataset(List<Integer> data) {
      return Flow.startActor(() -> {
        int sum = 0;
        int processed = 0;
        
        for (Integer value : data) {
          // Check for cancellation every iteration
          Flow.checkCancellation();
          
          // Simulate processing
          sum += value;
          processed++;
          
          // Yield periodically to be cooperative
          if (processed % 1000 == 0) {
            System.out.println("Processed " + processed + " items");
            Flow.await(Flow.yieldF());
          }
        }
        
        return sum;
      });
    }
    
    public static FlowFuture<ProcessingResult> processWithPartialResults(List<Integer> data) {
      return Flow.startActor(() -> {
        List<Integer> results = new ArrayList<>();
        int processed = 0;
        
        for (Integer value : data) {
          // Check cancellation without throwing
          if (Flow.isCancelled()) {
            System.out.println("Cancelled after processing " + processed + " items");
            return new ProcessingResult(results, true);
          }
          
          // Process item
          results.add(value * 2);
          processed++;
          
          // Yield periodically
          if (processed % 1000 == 0) {
            Flow.await(Flow.yieldF());
          }
        }
        
        return new ProcessingResult(results, false);
      });
    }
    
    public static class ProcessingResult {
      public final List<Integer> results;
      public final boolean wasCancelled;
      
      public ProcessingResult(List<Integer> results, boolean wasCancelled) {
        this.results = results;
        this.wasCancelled = wasCancelled;
      }
    }
  }

  /**
   * Example 3: Resource cleanup during cancellation.
   * Shows proper use of try-finally for cleanup.
   */
  public static class ResourceCleanup {
    
    public static class Resource {
      private final String name;
      private final AtomicBoolean closed = new AtomicBoolean(false);
      
      public Resource(String name) {
        this.name = name;
        System.out.println("Resource " + name + " acquired");
      }
      
      public void use() {
        if (closed.get()) {
          throw new IllegalStateException("Resource is closed");
        }
        // Simulate resource usage
      }
      
      public void close() {
        if (closed.compareAndSet(false, true)) {
          System.out.println("Resource " + name + " closed");
        }
      }
    }
    
    public static FlowFuture<String> operationWithCleanup() {
      return Flow.startActor(() -> {
        Resource resource = null;
        
        try {
          // Acquire resource
          resource = new Resource("DB-Connection");
          
          // Use resource with cancellation checks
          for (int i = 0; i < 100; i++) {
            Flow.checkCancellation();
            
            resource.use();
            Flow.await(Flow.delay(0.1));
            
            if (i % 10 == 0) {
              System.out.println("Progress: " + i + "%");
            }
          }
          
          return "Operation completed successfully";
          
        } finally {
          // Always cleanup, even if cancelled
          if (resource != null) {
            resource.close();
          }
        }
      });
    }
  }

  /**
   * Example 4: Streaming with cancellation.
   * Shows how to handle cancellation in streaming scenarios.
   */
  public static class StreamingCancellation {
    
    public static PromiseStream<Integer> createCountingStream(int delayMs) {
      PromiseStream<Integer> stream = new PromiseStream<>();
      
      Flow.startActor(() -> {
        try {
          int count = 0;
          
          while (true) {
            // Check for cancellation
            Flow.checkCancellation();
            
            // Send next value
            stream.send(count++);
            System.out.println("Sent: " + count);
            
            // Delay between values
            Flow.await(Flow.delay(delayMs / 1000.0));
          }
          
        } catch (FlowCancellationException e) {
          System.out.println("Stream producer cancelled");
          stream.close();
          throw e;
        } catch (Exception e) {
          System.out.println("Stream producer error: " + e.getMessage());
          stream.close();
          throw e;
        }
      });
      
      return stream;
    }
    
    public static FlowFuture<Integer> consumeStreamWithLimit(
        FutureStream<Integer> stream, int limit) {
      
      return Flow.startActor(() -> {
        int sum = 0;
        int count = 0;
        
        try {
          while (!stream.isClosed() && count < limit) {
            Integer value = Flow.await(stream.nextAsync());
            
            if (value != null) {
              sum += value;
              count++;
              System.out.println("Consumed: " + value + " (total: " + sum + ")");
            }
          }
          
          System.out.println("Consumed " + count + " items, sum = " + sum);
          return sum;
          
        } catch (FlowCancellationException e) {
          System.out.println("Stream consumer cancelled after " + count + " items");
          throw e;
        }
      });
    }
  }

  /**
   * Example 5: Timeout pattern using cancellation.
   * Shows how to implement timeouts.
   */
  public static class TimeoutPattern {
    
    public static <T> FlowFuture<T> withTimeout(
        FlowFuture<T> operation, double timeoutSeconds) {
      
      return Flow.startActor(() -> {
        // Create timeout task
        FlowFuture<Void> timeoutTask = Flow.startActor(() -> {
          Flow.await(Flow.delay(timeoutSeconds));
          System.out.println("Timeout reached, cancelling operation");
          operation.cancel();
          return null;
        });
        
        try {
          // Wait for operation
          T result = Flow.await(operation);
          
          // Cancel timeout if operation succeeded
          timeoutTask.cancel();
          
          return result;
          
        } catch (FlowCancellationException e) {
          // Operation was cancelled by timeout
          throw new RuntimeException("Operation timed out after " + 
              timeoutSeconds + " seconds", e);
        }
      });
    }
    
    public static FlowFuture<String> slowOperation(double duration) {
      return Flow.startActor(() -> {
        System.out.println("Slow operation starting (will take " + duration + "s)");
        Flow.await(Flow.delay(duration));
        System.out.println("Slow operation completed");
        return "Result after " + duration + " seconds";
      });
    }
  }

  /**
   * Example 6: Nested operations with cancellation propagation.
   * Shows how cancellation propagates through operation hierarchies.
   */
  public static class NestedCancellation {
    
    public static FlowFuture<String> parentOperation() {
      return Flow.startActor(() -> {
        System.out.println("Parent operation starting");
        
        try {
          // Start child operations
          FlowFuture<String> child1 = childOperation("Child-1", 2.0);
          FlowFuture<String> child2 = childOperation("Child-2", 3.0);
          
          // Wait for children
          String result1 = Flow.await(child1);
          String result2 = Flow.await(child2);
          
          return "Parent completed with: " + result1 + " and " + result2;
          
        } catch (FlowCancellationException e) {
          System.out.println("Parent operation cancelled");
          throw e;
        }
      });
    }
    
    private static FlowFuture<String> childOperation(String name, double duration) {
      return Flow.startActor(() -> {
        System.out.println(name + " starting");
        
        try {
          // Simulate work with cancellation checks
          int steps = (int) (duration * 10);
          for (int i = 0; i < steps; i++) {
            Flow.checkCancellation();
            Flow.await(Flow.delay(0.1));
            
            if (i % 5 == 0) {
              System.out.println(name + " progress: " + 
                  (i * 100 / steps) + "%");
            }
          }
          
          System.out.println(name + " completed");
          return name + " result";
          
        } catch (FlowCancellationException e) {
          System.out.println(name + " cancelled");
          throw e;
        }
      });
    }
  }

  /**
   * Example 7: Graceful shutdown pattern.
   * Shows how to implement services that can be shut down gracefully.
   */
  public static class GracefulShutdown {
    
    public static class WorkerService {
      private final List<FlowFuture<?>> activeWorkers = new ArrayList<>();
      private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
      private final AtomicInteger taskCounter = new AtomicInteger(0);
      
      public FlowFuture<String> submitTask(String taskName) {
        if (shuttingDown.get()) {
          throw new IllegalStateException("Service is shutting down");
        }
        
        int taskId = taskCounter.incrementAndGet();
        
        // Create a holder for the future reference
        final FlowFuture<String>[] workerHolder = new FlowFuture[1];
        
        FlowFuture<String> worker = Flow.startActor(() -> {
          try {
            System.out.println("Task " + taskId + " (" + taskName + ") started");
            
            // Simulate work with periodic shutdown checks
            for (int i = 0; i < 20; i++) {
              if (shuttingDown.get() && Flow.isCancelled()) {
                System.out.println("Task " + taskId + " interrupted by shutdown");
                return "Interrupted: " + taskName;
              }
              
              Flow.await(Flow.delay(0.1));
              
              if (i % 5 == 0) {
                System.out.println("Task " + taskId + " progress: " + (i * 5) + "%");
              }
            }
            
            System.out.println("Task " + taskId + " (" + taskName + ") completed");
            return "Completed: " + taskName;
            
          } finally {
            // Remove this worker from active list
            synchronized (activeWorkers) {
              activeWorkers.remove(workerHolder[0]);
            }
          }
        });
        
        workerHolder[0] = worker;
        
        synchronized (activeWorkers) {
          activeWorkers.add(worker);
        }
        
        return worker;
      }
      
      public FlowFuture<Integer> shutdown(double gracePeriodSeconds) {
        return Flow.startActor(() -> {
          shuttingDown.set(true);
          System.out.println("Initiating graceful shutdown...");
          
          // Get current active workers
          List<FlowFuture<?>> workers;
          synchronized (activeWorkers) {
            workers = new ArrayList<>(activeWorkers);
          }
          
          System.out.println("Cancelling " + workers.size() + " active tasks");
          
          // Request cancellation
          for (FlowFuture<?> worker : workers) {
            worker.cancel();
          }
          
          // Wait for grace period
          double startTime = Flow.nowSeconds();
          int completed = 0;
          
          while (Flow.nowSeconds() - startTime < gracePeriodSeconds) {
            synchronized (activeWorkers) {
              if (activeWorkers.isEmpty()) {
                System.out.println("All tasks completed gracefully");
                return workers.size();
              }
            }
            
            Flow.await(Flow.delay(0.1));
          }
          
          // Check remaining
          synchronized (activeWorkers) {
            int remaining = activeWorkers.size();
            if (remaining > 0) {
              System.out.println("Grace period expired, " + 
                  remaining + " tasks still running");
            }
            completed = workers.size() - remaining;
            return completed;
          }
        });
      }
    }
  }
}