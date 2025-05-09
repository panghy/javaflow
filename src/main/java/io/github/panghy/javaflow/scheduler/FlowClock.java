package io.github.panghy.javaflow.scheduler;

/**
 * Interface for a clock that can be used by the Flow scheduler.
 * Different implementations of this interface can provide either
 * real-time or simulated time for deterministic testing.
 *
 * <p>The clock's primary responsibility is to provide time information.
 * All timer scheduling is handled by the scheduler.</p>
 */
public interface FlowClock {

  /**
   * Returns the current time in milliseconds.
   *
   * @return Current time in milliseconds since epoch
   */
  long currentTimeMillis();

  /**
   * Returns the current time in seconds.
   *
   * @return Current time in seconds since epoch
   */
  default double currentTimeSeconds() {
    return currentTimeMillis() / 1000.0;
  }

  /**
   * Determines if this clock implementation supports simulated time control.
   * Simulation-capable clocks allow manual advancement of time for testing.
   *
   * @return true if this clock supports simulation mode, false otherwise
   */
  boolean isSimulated();

  /**
   * Creates a real-time clock implementation.
   *
   * @return A new instance of a real-time clock
   */
  static FlowClock createRealClock() {
    return new RealClock();
  }

  /**
   * Creates a simulated clock implementation for deterministic testing.
   *
   * @return A new instance of a simulated clock
   */
  static FlowClock createSimulatedClock() {
    return new SimulatedClock();
  }
}