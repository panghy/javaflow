package io.github.panghy.javaflow.test;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.scheduler.FlowClock;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.TestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Abstract base class for Flow-based tests that use a simulated scheduler.
 * Provides common setup, teardown, and utility methods for working with the
 * Flow scheduler in a test environment.
 */
public abstract class AbstractFlowTest {

  /**
   * The test scheduler that controls simulation time and task execution.
   */
  protected TestScheduler testScheduler;

  /**
   * Sets up the test environment with a simulated scheduler.
   */
  @BeforeEach
  void setUpScheduler() {
    // Create a simulated scheduler with a simulated clock
    FlowScheduler simulatedScheduler = new FlowScheduler(false, FlowClock.createSimulatedClock());
    testScheduler = new TestScheduler(simulatedScheduler);

    // Start the simulation
    testScheduler.startSimulation();

    // Additional setup if needed
    onSetUp();
  }

  /**
   * Hook method for subclasses to perform additional setup after scheduler initialization.
   * Override this method in subclasses to add test-specific setup logic.
   */
  protected void onSetUp() {
    // Default implementation does nothing
  }

  /**
   * Tears down the test environment and restores the original scheduler.
   */
  @AfterEach
  void tearDownScheduler() {
    // Allow subclasses to clean up
    onTearDown();

    // End the simulation and restore the original scheduler
    testScheduler.endSimulation();
  }

  /**
   * Hook method for subclasses to perform additional tear down before scheduler cleanup.
   * Override this method in subclasses to add test-specific cleanup logic.
   */
  protected void onTearDown() {
    // Default implementation does nothing
  }

  /**
   * Pumps the scheduler until all specified futures are done or a maximum number of steps is reached.
   * This method simulates the passage of time in small increments to allow delayed futures to complete.
   * <p>
   * WARNING: This method should ONLY be used for tests in a simulated environment.
   * Do NOT use this method for tests involving real file systems, network I/O, or other
   * blocking operations, as it cannot properly wait for these operations to complete.
   * For real I/O operations, use {@link FlowFuture#getNow()} instead.
   *
   * @param futures The futures to wait for completion
   */
  protected void pumpUntilDone(FlowFuture<?>... futures) {
    int maxSteps = 50; // Maximum number of time steps
    int steps = 0;

    // First pump to process any immediately ready tasks
    testScheduler.pump();

    // Check if all futures are completed
    boolean allDone = checkAllFuturesDone(futures);

    // If not all done, advance time in small increments
    while (!allDone && steps < maxSteps) {
      // Advance time by a small increment and pump
      testScheduler.advanceTime(0.01); // 10ms in simulation time
      testScheduler.pump(); // Make sure to pump after each time advance

      // Check if all futures are completed after time advancement
      allDone = checkAllFuturesDone(futures);

      steps++;
    }

    // If we still haven't completed all futures, use larger time increments
    if (!allDone) {
      System.out.println("Futures not done after small steps, trying larger steps");

      // Try with medium delay
      testScheduler.advanceTime(0.1); // 100ms
      testScheduler.pump();

      // Check again
      allDone = checkAllFuturesDone(futures);

      // If still not done, use increasingly larger delays
      if (!allDone) {
        testScheduler.advanceTime(0.5); // 500ms
        testScheduler.pump();
        allDone = checkAllFuturesDone(futures);

        if (!allDone) {
          testScheduler.advanceTime(1.0); // 1 second
          testScheduler.pump();
          allDone = checkAllFuturesDone(futures);

          if (!allDone) {
            testScheduler.advanceTime(3.0); // 3 seconds
            testScheduler.pump();
            allDone = checkAllFuturesDone(futures);
          }
        }
      }
    }

    // Final check to fail fast if futures aren't done
    for (FlowFuture<?> future : futures) {
      if (!future.isDone()) {
        StringBuilder warning = new StringBuilder();
        warning.append("WARNING: Future ").append(future)
            .append(" is still not done after significant time advancement!");
        System.err.println(warning);

        // This helps debugging by showing the current state
        System.err.println("Current simulation time: " + currentTimeSeconds() + " seconds");
      }
    }
  }

  /**
   * Checks if all futures are completed.
   *
   * @param futures The futures to check
   * @return true if all futures are done, false otherwise
   */
  private boolean checkAllFuturesDone(FlowFuture<?>[] futures) {
    for (FlowFuture<?> future : futures) {
      if (!future.isDone()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Advances simulation time by the specified duration.
   *
   * @param seconds The time to advance in seconds
   */
  protected void advanceTime(double seconds) {
    testScheduler.advanceTime(seconds);
  }

  /**
   * Pumps the scheduler to process any pending tasks.
   *
   * @return The number of tasks processed
   */
  protected int pump() {
    return testScheduler.pump();
  }

  /**
   * Gets the current simulation time in seconds.
   *
   * @return The current time in seconds
   */
  protected double currentTimeSeconds() {
    return testScheduler.getSimulatedScheduler().getClock().currentTimeSeconds();
  }

  /**
   * Gets the current simulation time in milliseconds.
   *
   * @return The current time in milliseconds
   */
  protected long currentTimeMillis() {
    return testScheduler.getSimulatedScheduler().getClock().currentTimeMillis();
  }
}