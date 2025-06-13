package io.github.panghy.javaflow;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.scheduler.FlowClock;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.scheduler.TestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.logging.Logger;

import static io.github.panghy.javaflow.util.LoggingUtil.debug;

/**
 * Abstract base class for Flow-based tests that use a simulated scheduler.
 * Provides common setup, teardown, and utility methods for working with the
 * Flow scheduler in a test environment.
 */
public abstract class AbstractFlowTest {

  private static final Logger LOGGER = Logger.getLogger(AbstractFlowTest.class.getName());

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
   * This method is useful for waiting for asynchronous operations to complete in tests. This does NOT advance time.
   * <p>
   * WARNING: This method should ONLY be used for tests in a simulated environment.
   * Do NOT use this method for tests involving real file systems, network I/O, or other
   * blocking operations, as it cannot properly wait for these operations to complete.
   * For real I/O operations, use {@link FlowFuture#getNow()} instead.
   *
   * @param futures The futures to wait for completion. If no futures are provided, the method
   *            will still pump the scheduler to process any pending tasks.
   */
  protected void pumpUntilDone(FlowFuture<?>... futures) {
    debug(LOGGER, "pumpUntilDone() called with futures: " + futures.length);
    int maxIterations = 1000; // Safety limit to avoid infinite loops
    int iterations = 0;

    // Continue processing until all futures are done or we hit the limit
    while (iterations < maxIterations) {
      iterations++;

      // Pump all ready tasks until there are none left
      int tasksPumped;
      do {
        // Check if futures are done after each pump
        if (futures.length > 0 && checkAllFuturesDone(futures)) {
          return;
        }
        tasksPumped = testScheduler.pump();
      } while (tasksPumped > 0);
    }

    // Final check to report any incomplete futures
    for (FlowFuture<?> future : futures) {
      if (!future.isDone()) {
        String warning = "WARNING: Future " + future +
                         " is still not done after " + iterations + " iterations!";
        System.err.println(warning);
      }
    }
  }

  /**
   * Pumps the scheduler until all specified futures are done or a maximum number of steps is reached.
   * This method is useful for waiting for asynchronous operations to complete in tests.
   * <p>
   * WARNING: This method should ONLY be used for tests in a simulated environment.
   * Do NOT use this method for tests involving real file systems, network I/O, or other
   * blocking operations, as it cannot properly wait for these operations to complete.
   * For real I/O operations, use {@link FlowFuture#getNow()} instead.
   *
   * @param futures The futures to wait for completion. If no futures are provided, the method
   *            will still pump the scheduler to process any pending tasks.
   */
  protected void pumpAndAdvanceTimeUntilDone(FlowFuture<?>... futures) {
    debug(LOGGER, "pumpAndAdvanceTimeUntilDone() called with futures: " + futures.length);
    int maxIterations = 1000; // Safety limit to avoid infinite loops
    int iterations = 0;

    // Continue processing until all futures are done or we hit the limit
    while (iterations < maxIterations) {
      iterations++;

      // Step 1: Pump all ready tasks until there are none left
      int tasksPumped;
      do {
        // Check if futures are done after each pump
        if (futures.length > 0 && checkAllFuturesDone(futures)) {
          return;
        }
        tasksPumped = testScheduler.pump();
      } while (tasksPumped > 0);

      // Step 2: If no tasks are ready, advance time to the next timer
      FlowScheduler scheduler = testScheduler.getSimulatedScheduler();
      long nextTimerTime = scheduler.getNextTimerTime();

      // If there's no timer and no ready tasks, we're stuck - bail out
      if (nextTimerTime == Long.MAX_VALUE) {
        break;
      }

      // Calculate how much time to advance
      long currentTime = (long) (currentTimeSeconds() * 1000);
      long timeToAdvance = nextTimerTime - currentTime;

      if (timeToAdvance > 0) {
        // Advance time directly to the next timer
        debug(LOGGER, "Advancing time by " + timeToAdvance + "ms to reach next timer at " + nextTimerTime + "ms");
        testScheduler.advanceTime(timeToAdvance);
      }
    }

    // Final check to report any incomplete futures
    for (FlowFuture<?> future : futures) {
      if (!future.isDone()) {
        String warning = "WARNING: Future " + future +
                         " is still not done after " + iterations + " iterations!";
        System.err.println(warning);
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