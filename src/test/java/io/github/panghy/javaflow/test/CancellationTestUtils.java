package io.github.panghy.javaflow.test;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.PromiseStream;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;

/**
 * Utility methods for testing cancellation behavior in JavaFlow.
 * Provides helper methods for creating long-running operations, mock services,
 * and performance measurement utilities.
 */
public class CancellationTestUtils {

  /**
   * Creates a long-running operation that checks for cancellation periodically.
   * The operation will run for approximately the specified duration unless cancelled.
   * 
   * @param durationSeconds The approximate duration in seconds
   * @param checkIntervalSeconds How often to check for cancellation
   * @return A future that completes with the actual run time or throws FlowCancellationException
   */
  public static FlowFuture<Double> createLongRunningOperation(
      double durationSeconds, double checkIntervalSeconds) {
    return startActor(() -> {
      double startTime = Flow.nowSeconds();
      double endTime = startTime + durationSeconds;
      
      while (Flow.nowSeconds() < endTime) {
        // Check for cancellation
        Flow.checkCancellation();
        
        // Wait for check interval
        await(Flow.delay(checkIntervalSeconds));
      }
      
      return Flow.nowSeconds() - startTime;
    });
  }

  /**
   * Creates a CPU-intensive operation that yields periodically to allow cancellation.
   * Useful for testing cancellation of compute-bound tasks.
   * 
   * @param iterations Number of iterations to perform
   * @param yieldInterval How often to yield (every N iterations)
   * @return A future that completes with the number of iterations completed
   */
  public static FlowFuture<Integer> createCpuIntensiveOperation(
      int iterations, int yieldInterval) {
    return startActor(() -> {
      int completed = 0;
      
      for (int i = 0; i < iterations; i++) {
        // Simulate some CPU work
        simulateCpuWork();
        completed++;
        
        // Yield periodically to allow cancellation
        if (i % yieldInterval == 0) {
          await(Flow.yieldF());
          Flow.checkCancellation();
        }
      }
      
      return completed;
    });
  }

  /**
   * Creates a streaming operation that produces values until cancelled.
   * Useful for testing cancellation of streaming operations.
   * 
   * @param intervalSeconds Interval between producing values
   * @return A stream that produces incrementing integers until cancelled
   */
  public static PromiseStream<Integer> createStreamingOperation(double intervalSeconds) {
    PromiseStream<Integer> stream = new PromiseStream<>();
    AtomicInteger counter = new AtomicInteger(0);
    
    startActor(() -> {
      try {
        while (true) {
          // Check for cancellation
          Flow.checkCancellation();
          
          // Send next value
          stream.send(counter.incrementAndGet());
          
          // Wait before sending next
          await(Flow.delay(intervalSeconds));
        }
      } catch (FlowCancellationException e) {
        // Clean shutdown on cancellation
        stream.close();
        throw e;
      } catch (Exception e) {
        // Stream doesn't have closeWithError, just close it
        stream.close();
        throw e;
      }
    });
    
    return stream;
  }

  /**
   * Creates a nested operation where parent starts child operations.
   * Useful for testing cancellation propagation through operation hierarchies.
   * 
   * @param depth Number of nested levels
   * @param delayPerLevel Delay at each level in seconds
   * @return A future that completes when all nested operations complete
   */
  public static FlowFuture<String> createNestedOperation(int depth, double delayPerLevel) {
    return startActor(() -> {
      if (depth <= 0) {
        return "leaf";
      }
      
      // Start child operation
      FlowFuture<String> childFuture = createNestedOperation(depth - 1, delayPerLevel);
      
      // Do some work at this level
      await(Flow.delay(delayPerLevel));
      Flow.checkCancellation();
      
      // Wait for child
      String childResult = await(childFuture);
      
      return "level-" + depth + "->" + childResult;
    });
  }

  /**
   * Measures the time taken for a cancellation to propagate.
   * Returns the time in seconds from when cancel() was called to when 
   * the operation detected the cancellation.
   * 
   * @param operation The operation to measure
   * @param cancelAfterSeconds When to cancel the operation
   * @return The cancellation latency in seconds
   */
  public static FlowFuture<Double> measureCancellationLatency(
      Supplier<FlowFuture<?>> operation, double cancelAfterSeconds) {
    return startActor(() -> {
      AtomicLong cancelTime = new AtomicLong(-1);
      AtomicLong detectionTime = new AtomicLong(-1);
      
      // Start the operation
      FlowFuture<?> future = operation.get();
      
      // Schedule cancellation
      startActor(() -> {
        await(Flow.delay(cancelAfterSeconds));
        cancelTime.set(System.nanoTime());
        future.cancel();
        return null;
      });
      
      // Wait for the operation to detect cancellation
      try {
        await(future);
      } catch (FlowCancellationException e) {
        detectionTime.set(System.nanoTime());
      }
      
      if (cancelTime.get() == -1 || detectionTime.get() == -1) {
        throw new IllegalStateException("Cancellation was not detected");
      }
      
      // Return latency in seconds
      return (detectionTime.get() - cancelTime.get()) / 1_000_000_000.0;
    });
  }

  /**
   * Creates a mock service that can be cancelled remotely.
   * The service performs a long-running operation and checks for cancellation.
   */
  public static class MockCancellableService {
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger checkCount = new AtomicInteger(0);
    
    /**
     * Performs a long-running operation with cancellation checks.
     * 
     * @param durationSeconds Total duration if not cancelled
     * @param checkIntervalSeconds How often to check for cancellation
     * @return Result string or throws FlowCancellationException
     */
    public FlowFuture<String> longRunningOperation(
        double durationSeconds, double checkIntervalSeconds) {
      return startActor(() -> {
        started.set(true);
        double startTime = Flow.nowSeconds();
        double endTime = startTime + durationSeconds;
        
        try {
          while (Flow.nowSeconds() < endTime) {
            checkCount.incrementAndGet();
            Flow.checkCancellation();
            await(Flow.delay(checkIntervalSeconds));
          }
          return "completed after " + (Flow.nowSeconds() - startTime) + " seconds";
        } catch (FlowCancellationException e) {
          cancelled.set(true);
          throw e;
        }
      });
    }
    
    /**
     * Returns whether the operation has started.
     */
    public boolean hasStarted() {
      return started.get();
    }
    
    /**
     * Returns whether the operation detected cancellation.
     */
    public boolean wasCancelled() {
      return cancelled.get();
    }
    
    /**
     * Returns the number of cancellation checks performed.
     */
    public int getCancellationCheckCount() {
      return checkCount.get();
    }
    
    /**
     * Resets the service state for reuse in tests.
     */
    public void reset() {
      started.set(false);
      cancelled.set(false);
      checkCount.set(0);
    }
  }

  /**
   * Helper to verify that an operation properly cleans up resources on cancellation.
   * 
   * @param operation Operation that should clean up on cancellation
   * @param cancelAfter When to cancel in seconds
   * @param cleanupChecker Function that returns true if cleanup was performed
   * @return Future that completes successfully if cleanup was verified
   */
  public static FlowFuture<Void> verifyCancellationCleanup(
      Supplier<FlowFuture<?>> operation, 
      double cancelAfter,
      Supplier<Boolean> cleanupChecker) {
    return startActor(() -> {
      // Start the operation
      FlowFuture<?> future = operation.get();
      
      // Cancel after delay
      await(Flow.delay(cancelAfter));
      future.cancel();
      
      // Give some time for cleanup
      await(Flow.delay(0.1));
      
      // Verify cleanup
      if (!cleanupChecker.get()) {
        throw new AssertionError("Cleanup was not performed after cancellation");
      }
      
      return null;
    });
  }

  /**
   * Creates multiple concurrent operations and cancels them all.
   * Useful for testing concurrent cancellation scenarios.
   * 
   * @param operationCount Number of operations to create
   * @param operationSupplier Supplier for creating each operation
   * @param cancelAfter When to cancel all operations
   * @return Future that completes when all operations have been cancelled
   */
  public static FlowFuture<Integer> testConcurrentCancellation(
      int operationCount,
      Supplier<FlowFuture<?>> operationSupplier,
      double cancelAfter) {
    return startActor(() -> {
      // Start all operations
      FlowFuture<?>[] futures = new FlowFuture<?>[operationCount];
      for (int i = 0; i < operationCount; i++) {
        futures[i] = operationSupplier.get();
      }
      
      // Cancel all after delay
      await(Flow.delay(cancelAfter));
      for (FlowFuture<?> future : futures) {
        future.cancel();
      }
      
      // Count how many were successfully cancelled
      int cancelledCount = 0;
      for (FlowFuture<?> future : futures) {
        try {
          await(future);
        } catch (FlowCancellationException e) {
          cancelledCount++;
        }
      }
      
      return cancelledCount;
    });
  }

  /**
   * Simulates CPU work for testing purposes.
   */
  private static void simulateCpuWork() {
    // Simple CPU work simulation
    double sum = 0;
    for (int i = 0; i < 1000; i++) {
      sum += Math.sqrt(i);
    }
    // Prevent optimization
    if (sum < 0) {
      System.out.println("Never happens: " + sum);
    }
  }
}