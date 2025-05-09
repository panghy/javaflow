package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.Flow;

/**
 * A utility class for testing with JavaFlow's simulation capabilities.
 *
 * <p>This class provides a clean interface for switching between real time and
 * simulated time execution for tests. It manages the global Flow scheduler reference
 * so tests can easily switch to simulation mode without using reflection directly.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * // Create a test scheduler with a simulated clock
 * FlowScheduler simulatedScheduler = new FlowScheduler(false, FlowClock.createSimulatedClock());
 * TestScheduler testScheduler = new TestScheduler(simulatedScheduler);
 *
 * // Start simulation mode
 * testScheduler.startSimulation();
 *
 * // Run tests with simulated time...
 *
 * // End simulation and restore original scheduler
 * testScheduler.endSimulation();
 * }</pre>
 */
public class TestScheduler {

  private final FlowScheduler simulatedScheduler;
  private FlowScheduler originalScheduler;
  private boolean simulationActive = false;

  /**
   * Creates a new TestScheduler using the provided simulated scheduler.
   *
   * @param simulatedScheduler A FlowScheduler configured with a simulated clock
   */
  public TestScheduler(FlowScheduler simulatedScheduler) {
    this.simulatedScheduler = simulatedScheduler;
  }

  /**
   * Activates simulation mode by replacing the global Flow scheduler with
   * the simulated scheduler.
   *
   * @throws IllegalStateException if simulation is already active
   */
  public void startSimulation() {
    if (simulationActive) {
      throw new IllegalStateException("Simulation is already active");
    }

    try {
      // Save the original scheduler
      originalScheduler = Flow.scheduler();

      // Replace the global scheduler with the simulated one
      Flow.setScheduler(simulatedScheduler);

      // Pre-initialize the scheduler by pumping once
      simulatedScheduler.pump();

      simulationActive = true;
    } catch (Exception e) {
      throw new RuntimeException("Failed to set up simulated scheduler", e);
    }
  }

  /**
   * Ends the simulation and restores the original scheduler.
   *
   * @throws IllegalStateException if simulation is not active
   */
  public void endSimulation() {
    if (!simulationActive) {
      throw new IllegalStateException("Simulation is not active");
    }

    try {
      // Restore the original scheduler
      Flow.setScheduler(originalScheduler);

      simulationActive = false;
    } catch (Exception e) {
      throw new RuntimeException("Failed to restore original scheduler", e);
    }
  }

  /**
   * Advances the simulated clock by the specified duration in milliseconds.
   *
   * @param millis the number of milliseconds to advance
   * @return the number of timer tasks executed
   * @throws IllegalStateException if simulation is not active
   */
  public int advanceTime(long millis) {
    if (!simulationActive) {
      throw new IllegalStateException("Simulation is not active");
    }

    // First, pump to process any pending tasks before time advancement
    int initialPumpTasks = pump();

    // Now advance the time
    System.out.println("*** ADVANCING TIME by " + millis + "ms, current time: " +
        simulatedScheduler.getClock().currentTimeMillis() + "ms");
    int timerTasks = simulatedScheduler.advanceTime(millis);
    System.out.println("*** AFTER ADVANCING TIME: " + simulatedScheduler.getClock().currentTimeMillis() +
        "ms, executed " + timerTasks + " timer tasks");

    // Pump again to process any tasks that became ready due to time advancement
    int finalPumpTasks = pump();

    System.out.println("*** PUMPING after time advancement processed " + finalPumpTasks + " tasks");

    return initialPumpTasks + timerTasks + finalPumpTasks;
  }

  /**
   * Advances the simulated clock by the specified duration in seconds.
   *
   * @param seconds the number of seconds to advance
   * @return the number of timer tasks executed
   * @throws IllegalStateException if simulation is not active
   */
  public int advanceTime(double seconds) {
    return advanceTime((long) (seconds * 1000));
  }

  /**
   * Pumps the scheduler to process pending tasks.
   *
   * @return the number of tasks processed
   * @throws IllegalStateException if simulation is not active
   */
  public int pump() {
    if (!simulationActive) {
      throw new IllegalStateException("Simulation is not active");
    }

    // Get current time for context
    long currentTime = simulatedScheduler.getClock().currentTimeMillis();

    // Run the pump
    System.out.println("*** PUMPING at time: " + currentTime + "ms");
    int processed = simulatedScheduler.pump();
    System.out.println("*** PUMP processed " + processed + " tasks");

    return processed;
  }

  /**
   * Gets the underlying simulated scheduler.
   *
   * @return the simulated scheduler
   */
  public FlowScheduler getSimulatedScheduler() {
    return simulatedScheduler;
  }

  /**
   * Checks if simulation is currently active.
   *
   * @return true if simulation is active, false otherwise
   */
  public boolean isSimulationActive() {
    return simulationActive;
  }
}