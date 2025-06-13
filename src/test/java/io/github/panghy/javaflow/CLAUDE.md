# AbstractFlowTest Guide

## Overview
`AbstractFlowTest` is the base class for all Flow-based tests in JavaFlow. It provides a simulated scheduler environment that allows deterministic testing of asynchronous code without real time delays.

## Key Features
- **Simulated Time**: Uses a simulated clock and scheduler, allowing tests to run instantly while simulating time passage
- **Deterministic Execution**: All tasks are executed in a controlled, deterministic manner
- **Automatic Setup/Teardown**: Handles scheduler initialization and cleanup automatically

## Important Methods

### `pumpAndAdvanceTimeUntilDone(FlowFuture<?>... futures)`
This is the primary method for waiting for asynchronous operations to complete in tests.

**Usage:**
```java
// Wait for specific futures
FlowFuture<String> future = startActor(() -> someAsyncOperation());
pumpAndAdvanceTimeUntilDone(future);
String result = future.getNow();

// Pump all tasks until no work remains
pumpAndAdvanceTimeUntilDone();  // No arguments - pumps until no tasks are outstanding
```

**How it works:**
1. Executes all ready tasks by calling `pump()` repeatedly
2. If no tasks are ready, advances simulated time to the next scheduled timer
3. When called with futures: continues until all specified futures are complete or a maximum iteration limit is reached
4. When called without futures: continues until no tasks are pending and no timers are outstanding (by advancing time continuously)
5. Handles delayed futures by advancing simulation time as needed

**Important Notes:**
- ⚠️ **DO NOT** use `pumpAndAdvanceTimeUntilDone()` for tests involving real I/O operations (file system, network, etc.)
- ⚠️ **DO NOT** implement your own version of `pumpAndAdvanceTimeUntilDone()` - always use the one from `AbstractFlowTest`
- ⚠️ **DO NOT** use `Thread.sleep()` or real time waiting in tests extending `AbstractFlowTest`
- For real I/O operations, use `FlowFuture.getNow()` instead
- When called without arguments, `pumpAndAdvanceTimeUntilDone()` is useful for processing all pending work in a test scenario

### `pump()`
Executes all currently ready tasks once.
```java
int tasksExecuted = pump();
```

### `advanceTime(double seconds)`
Advances the simulation time by the specified duration.
```java
advanceTime(1.0); // Advance by 1 second
```

### `currentTimeSeconds()` / `currentTimeMillis()`
Gets the current simulation time.
```java
double timeInSeconds = currentTimeSeconds();
long timeInMillis = currentTimeMillis();
```

## Best Practices

1. **Always use @Timeout annotation**: Add `@Timeout(30)` to test classes or methods to prevent hanging tests
   ```java
   @Test
   @Timeout(30)
   public void testSomething() {
       // test code
   }
   ```

2. **Use simulated time for delays**: Instead of `Thread.sleep()`, use Flow's delay mechanisms
   ```java
   FlowFuture<Void> delayed = Flow.delay(1.0); // 1 second delay
   pumpAndAdvanceTimeUntilDone(delayed); // Will advance simulated time
   ```

3. **Avoid mixing real and simulated I/O**: Tests should either use all simulated components or all real components, not a mix

4. **Check future completion**: After `pumpAndAdvanceTimeUntilDone()`, always verify futures completed as expected
   ```java
   pumpAndAdvanceTimeUntilDone(future);
   assertTrue(future.isDone());
   assertFalse(future.isCompletedExceptionally());
   ```

## Example Test
```java
public class MyServiceTest extends AbstractFlowTest {
    
    @Test
    @Timeout(30)
    public void testAsyncOperation() {
        // Setup
        MyService service = new MyService();
        
        // Execute async operation
        FlowFuture<String> resultFuture = startActor(() -> 
            service.performAsyncOperation()
        );
        
        // Wait for completion using simulated time
        pumpAndAdvanceTimeUntilDone(resultFuture);
        
        // Verify results
        assertTrue(resultFuture.isDone());
        assertEquals("expected result", resultFuture.getNow());
    }
}
```

## Common Pitfalls to Avoid

1. **Creating custom pump methods**: Always use the provided `pumpAndAdvanceTimeUntilDone()`
2. **Using real time**: Never use `System.currentTimeMillis()` or `Thread.sleep()`
3. **Infinite loops**: The built-in `pumpAndAdvanceTimeUntilDone()` has safety limits to prevent infinite loops
4. **Missing timeouts**: Always add `@Timeout` annotations to prevent hanging tests