# JavaFlow Cancellation Examples

This document provides comprehensive examples of cancellation patterns in JavaFlow, demonstrating best practices for implementing cancellable operations.

## Table of Contents
1. [Basic Cancellation](#basic-cancellation)
2. [Cooperative Cancellation](#cooperative-cancellation)
3. [Resource Cleanup](#resource-cleanup)
4. [Streaming Operations](#streaming-operations)
5. [Nested Operations](#nested-operations)
6. [Timeout Patterns](#timeout-patterns)
7. [Graceful Shutdown](#graceful-shutdown)
8. [Remote Cancellation](#remote-cancellation)

## Basic Cancellation

The simplest form of cancellation occurs when a task is cancelled while waiting on a future.

```java
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowCancellationException;

public class BasicCancellationExample {
    
    public static void main(String[] args) {
        // Start a long-running operation
        FlowFuture<String> future = Flow.startActor(() -> {
            System.out.println("Starting long operation...");
            
            // This will be interrupted by cancellation
            Flow.await(Flow.delay(10.0)); // 10 second delay
            
            System.out.println("This won't be printed");
            return "completed";
        });
        
        // Cancel after 1 second
        Flow.startActor(() -> {
            Flow.await(Flow.delay(1.0));
            System.out.println("Cancelling operation...");
            future.cancel();
            return null;
        });
        
        // Wait for completion
        try {
            String result = future.get();
            System.out.println("Result: " + result);
        } catch (CancellationException e) {
            System.out.println("Operation was cancelled");
        }
    }
}
```

## Cooperative Cancellation

For CPU-intensive operations, use periodic cancellation checks to enable responsive cancellation.

```java
public class CooperativeCancellationExample {
    
    /**
     * Processes a large dataset with cancellation support.
     */
    public static FlowFuture<Integer> processLargeDataset(List<String> data) {
        return Flow.startActor(() -> {
            int processed = 0;
            
            for (String item : data) {
                // Check for cancellation every iteration
                Flow.checkCancellation();
                
                // Process the item
                processItem(item);
                processed++;
                
                // Yield periodically to be cooperative
                if (processed % 100 == 0) {
                    Flow.await(Flow.yield());
                }
            }
            
            return processed;
        });
    }
    
    /**
     * Alternative using isCancelled() for graceful handling.
     */
    public static FlowFuture<ProcessingResult> processWithPartialResults(List<String> data) {
        return Flow.startActor(() -> {
            List<String> results = new ArrayList<>();
            int processed = 0;
            
            for (String item : data) {
                // Check cancellation without throwing
                if (Flow.isCancelled()) {
                    // Return partial results
                    return new ProcessingResult(results, processed, true);
                }
                
                String result = processItem(item);
                results.add(result);
                processed++;
                
                // Yield periodically
                if (processed % 100 == 0) {
                    Flow.await(Flow.yield());
                }
            }
            
            return new ProcessingResult(results, processed, false);
        });
    }
    
    static class ProcessingResult {
        final List<String> results;
        final int itemsProcessed;
        final boolean wasCancelled;
        
        ProcessingResult(List<String> results, int itemsProcessed, boolean wasCancelled) {
            this.results = results;
            this.itemsProcessed = itemsProcessed;
            this.wasCancelled = wasCancelled;
        }
    }
}
```

## Resource Cleanup

Always use try-finally blocks to ensure resources are cleaned up even when cancelled.

```java
public class ResourceCleanupExample {
    
    /**
     * Demonstrates proper resource cleanup during cancellation.
     */
    public static FlowFuture<String> processWithResources() {
        return Flow.startActor(() -> {
            Resource resource = null;
            try {
                // Acquire resource
                resource = acquireResource();
                
                // Long-running operation with the resource
                for (int i = 0; i < 100; i++) {
                    Flow.checkCancellation();
                    
                    resource.process(i);
                    Flow.await(Flow.delay(0.1));
                }
                
                return resource.getResult();
                
            } finally {
                // Always cleanup, even if cancelled
                if (resource != null) {
                    resource.close();
                    System.out.println("Resource cleaned up");
                }
            }
        });
    }
    
    /**
     * Using try-with-resources pattern (if Resource implements AutoCloseable).
     */
    public static FlowFuture<String> processWithAutoCloseable() {
        return Flow.startActor(() -> {
            try (AutoCloseableResource resource = new AutoCloseableResource()) {
                for (int i = 0; i < 100; i++) {
                    Flow.checkCancellation();
                    
                    resource.process(i);
                    Flow.await(Flow.delay(0.1));
                }
                
                return resource.getResult();
            }
            // Resource automatically cleaned up, even if cancelled
        });
    }
}
```

## Streaming Operations

Cancellation of streaming operations requires special handling to ensure streams are properly closed.

```java
public class StreamingCancellationExample {
    
    /**
     * Producer that generates a stream of values until cancelled.
     */
    public static PromiseStream<Integer> createCountingStream() {
        PromiseStream<Integer> stream = new PromiseStream<>();
        
        Flow.startActor(() -> {
            try {
                int count = 0;
                while (true) {
                    // Check for cancellation
                    Flow.checkCancellation();
                    
                    // Send next value
                    stream.send(count++);
                    
                    // Delay between values
                    Flow.await(Flow.delay(0.1));
                }
            } catch (FlowCancellationException e) {
                // Clean shutdown on cancellation
                stream.close();
                throw e;
            } catch (Exception e) {
                // Close with error on other exceptions
                stream.close();
                throw e;
            }
        });
        
        return stream;
    }
    
    /**
     * Consumer that processes stream values with cancellation support.
     */
    public static FlowFuture<Integer> consumeStream(FutureStream<Integer> stream) {
        return Flow.startActor(() -> {
            int sum = 0;
            
            try {
                while (!stream.isClosed()) {
                    // This will throw FlowCancellationException if cancelled
                    Integer value = Flow.await(stream.nextAsync());
                    
                    if (value != null) {
                        sum += value;
                        
                        // Process value
                        System.out.println("Received: " + value);
                    }
                }
            } catch (FlowCancellationException e) {
                System.out.println("Stream consumption cancelled");
                throw e;
            }
            
            return sum;
        });
    }
}
```

## Nested Operations

Cancellation automatically propagates to child operations.

```java
public class NestedCancellationExample {
    
    /**
     * Parent operation that spawns child operations.
     */
    public static FlowFuture<String> parentOperation() {
        return Flow.startActor(() -> {
            System.out.println("Parent starting");
            
            // Start child operations
            FlowFuture<String> child1 = childOperation("Child1", 2.0);
            FlowFuture<String> child2 = childOperation("Child2", 3.0);
            
            // Wait for both children
            String result1 = Flow.await(child1);
            String result2 = Flow.await(child2);
            
            return result1 + " and " + result2;
        });
    }
    
    private static FlowFuture<String> childOperation(String name, double duration) {
        return Flow.startActor(() -> {
            System.out.println(name + " starting");
            
            try {
                // Simulate work
                Flow.await(Flow.delay(duration));
                System.out.println(name + " completed");
                return name + " result";
                
            } catch (FlowCancellationException e) {
                System.out.println(name + " cancelled");
                throw e;
            }
        });
    }
    
    /**
     * Demonstrates selective cancellation of child operations.
     */
    public static FlowFuture<String> selectiveCancellation() {
        return Flow.startActor(() -> {
            // Start multiple operations
            FlowFuture<String> critical = criticalOperation();
            FlowFuture<String> optional = optionalOperation();
            
            // Set up timeout for optional operation only
            Flow.startActor(() -> {
                Flow.await(Flow.delay(1.0));
                optional.cancel();
                return null;
            });
            
            // Wait for critical operation
            String criticalResult = Flow.await(critical);
            
            // Try to get optional result, but don't fail if cancelled
            String optionalResult;
            try {
                optionalResult = Flow.await(optional);
            } catch (FlowCancellationException e) {
                optionalResult = "cancelled";
            }
            
            return "Critical: " + criticalResult + ", Optional: " + optionalResult;
        });
    }
}
```

## Timeout Patterns

Implement timeout patterns using cancellation.

```java
public class TimeoutExample {
    
    /**
     * Executes an operation with a timeout.
     */
    public static <T> FlowFuture<T> withTimeout(
            FlowFuture<T> operation, 
            double timeoutSeconds) {
        
        return Flow.startActor(() -> {
            // Create timeout task
            FlowFuture<Void> timeoutFuture = Flow.startActor(() -> {
                Flow.await(Flow.delay(timeoutSeconds));
                operation.cancel();
                return null;
            });
            
            try {
                // Wait for operation to complete
                T result = Flow.await(operation);
                
                // Cancel timeout if operation completed
                timeoutFuture.cancel();
                
                return result;
                
            } catch (FlowCancellationException e) {
                throw new TimeoutException("Operation timed out after " + 
                    timeoutSeconds + " seconds");
            }
        });
    }
    
    /**
     * Retry with timeout pattern.
     */
    public static <T> FlowFuture<T> retryWithTimeout(
            Supplier<FlowFuture<T>> operationSupplier,
            double timeoutSeconds,
            int maxRetries) {
        
        return Flow.startActor(() -> {
            Exception lastException = null;
            
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    FlowFuture<T> operation = operationSupplier.get();
                    return Flow.await(withTimeout(operation, timeoutSeconds));
                    
                } catch (TimeoutException e) {
                    lastException = e;
                    System.out.println("Attempt " + (attempt + 1) + 
                        " timed out, retrying...");
                }
            }
            
            throw new RuntimeException("All attempts failed", lastException);
        });
    }
}
```

## Graceful Shutdown

Implement graceful shutdown patterns for services.

```java
public class GracefulShutdownExample {
    
    public static class Service {
        private final List<FlowFuture<?>> activeOperations = new ArrayList<>();
        private volatile boolean shuttingDown = false;
        
        /**
         * Starts a new operation if not shutting down.
         */
        public FlowFuture<String> startOperation(String id) {
            if (shuttingDown) {
                throw new IllegalStateException("Service is shutting down");
            }
            
            FlowFuture<String> operation = Flow.startActor(() -> {
                try {
                    // Check if we should stop
                    if (shuttingDown) {
                        return "Aborted: " + id;
                    }
                    
                    // Simulate work
                    for (int i = 0; i < 10; i++) {
                        if (shuttingDown && Flow.isCancelled()) {
                            return "Interrupted: " + id;
                        }
                        
                        processStep(id, i);
                        Flow.await(Flow.delay(0.5));
                    }
                    
                    return "Completed: " + id;
                    
                } finally {
                    synchronized (activeOperations) {
                        activeOperations.remove(Flow.currentFuture());
                    }
                }
            });
            
            synchronized (activeOperations) {
                activeOperations.add(operation);
            }
            
            return operation;
        }
        
        /**
         * Initiates graceful shutdown.
         */
        public FlowFuture<Void> shutdown(double gracePeriodSeconds) {
            return Flow.startActor(() -> {
                shuttingDown = true;
                
                // Cancel all active operations
                List<FlowFuture<?>> toCancel;
                synchronized (activeOperations) {
                    toCancel = new ArrayList<>(activeOperations);
                }
                
                for (FlowFuture<?> op : toCancel) {
                    op.cancel();
                }
                
                // Wait for grace period
                double startTime = Flow.nowSeconds();
                while (Flow.nowSeconds() - startTime < gracePeriodSeconds) {
                    synchronized (activeOperations) {
                        if (activeOperations.isEmpty()) {
                            System.out.println("All operations completed");
                            return null;
                        }
                    }
                    
                    Flow.await(Flow.delay(0.1));
                }
                
                // Force remaining operations
                synchronized (activeOperations) {
                    System.out.println("Forcing shutdown, " + 
                        activeOperations.size() + " operations remaining");
                }
                
                return null;
            });
        }
    }
}
```

## Remote Cancellation

Example of cancellation in RPC scenarios.

```java
public class RemoteCancellationExample {
    
    /**
     * RPC service interface with cancellable operations.
     */
    public interface DataService {
        FlowFuture<String> fetchData(String query);
        PromiseStream<DataChunk> streamData(String query);
    }
    
    /**
     * Client-side usage with cancellation.
     */
    public static class Client {
        private final DataService service;
        
        public Client(DataService service) {
            this.service = service;
        }
        
        /**
         * Fetches data with timeout and cancellation.
         */
        public FlowFuture<String> fetchWithTimeout(String query, double timeout) {
            return Flow.startActor(() -> {
                FlowFuture<String> fetchFuture = service.fetchData(query);
                
                // Set up timeout
                FlowFuture<Void> timeoutFuture = Flow.startActor(() -> {
                    Flow.await(Flow.delay(timeout));
                    System.out.println("Timeout reached, cancelling fetch");
                    fetchFuture.cancel();
                    return null;
                });
                
                try {
                    String result = Flow.await(fetchFuture);
                    timeoutFuture.cancel(); // Cancel timeout
                    return result;
                    
                } catch (FlowCancellationException e) {
                    throw new TimeoutException("Fetch timed out");
                }
            });
        }
        
        /**
         * Streams data with cancellation on condition.
         */
        public FlowFuture<List<DataChunk>> streamUntilCondition(
                String query, 
                Predicate<DataChunk> stopCondition) {
            
            return Flow.startActor(() -> {
                PromiseStream<DataChunk> stream = service.streamData(query);
                List<DataChunk> chunks = new ArrayList<>();
                
                try {
                    FutureStream<DataChunk> futureStream = stream.getFutureStream();
                    
                    while (!futureStream.isClosed()) {
                        DataChunk chunk = Flow.await(futureStream.nextAsync());
                        
                        if (chunk != null) {
                            chunks.add(chunk);
                            
                            // Check stop condition
                            if (stopCondition.test(chunk)) {
                                // Cancel the stream
                                futureStream.cancel();
                                break;
                            }
                        }
                    }
                    
                } catch (FlowCancellationException e) {
                    System.out.println("Stream cancelled");
                }
                
                return chunks;
            });
        }
    }
    
    /**
     * Server-side implementation with cancellation awareness.
     */
    public static class DataServiceImpl implements DataService {
        
        @Override
        public FlowFuture<String> fetchData(String query) {
            return Flow.startActor(() -> {
                System.out.println("Starting fetch for: " + query);
                
                try {
                    // Simulate multiple steps
                    for (int step = 0; step < 5; step++) {
                        // Check cancellation between steps
                        Flow.checkCancellation();
                        
                        String partial = fetchStep(query, step);
                        Flow.await(Flow.delay(0.5));
                    }
                    
                    return "Data for: " + query;
                    
                } catch (FlowCancellationException e) {
                    System.out.println("Fetch cancelled for: " + query);
                    cleanup(query);
                    throw e;
                }
            });
        }
        
        @Override
        public PromiseStream<DataChunk> streamData(String query) {
            PromiseStream<DataChunk> stream = new PromiseStream<>();
            
            Flow.startActor(() -> {
                try {
                    int sequence = 0;
                    
                    while (true) {
                        // Check for cancellation
                        Flow.checkCancellation();
                        
                        DataChunk chunk = generateChunk(query, sequence++);
                        stream.send(chunk);
                        
                        Flow.await(Flow.delay(0.2));
                    }
                    
                } catch (FlowCancellationException e) {
                    System.out.println("Stream cancelled for: " + query);
                    stream.close();
                } catch (Exception e) {
                    stream.close();
                    throw e;
                }
            });
            
            return stream;
        }
    }
}
```

## Best Practices Summary

1. **Always use try-finally** for cleanup code that must run even if cancelled
2. **Check cancellation periodically** in long-running loops using `Flow.checkCancellation()`
3. **Use `Flow.isCancelled()`** when you want to handle cancellation gracefully
4. **Let FlowCancellationException propagate** unless you have specific cleanup needs
5. **Close streams properly** when cancelled to avoid resource leaks
6. **Consider partial results** - sometimes returning what you have is better than failing
7. **Test cancellation paths** using CancellationTestUtils for reliable testing
8. **Document cancellation behavior** in your APIs so users know what to expect

These examples demonstrate the flexibility and power of JavaFlow's cancellation system, enabling robust and responsive concurrent applications.