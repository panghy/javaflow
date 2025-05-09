package io.github.panghy.javaflow.scheduler;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of FlowClock that simulates time for deterministic testing.
 * This clock allows manual advancement of time, enabling controlled testing
 * of time-dependent code. The scheduling and execution of timer tasks is
 * handled by the scheduler.
 */
public class SimulatedClock implements FlowClock {
  
  // The current simulated time in milliseconds
  private long currentTimeMillis = 0;
  
  // Lock for synchronizing time operations
  private final ReentrantLock timeLock = new ReentrantLock();
  
  /**
   * Gets the current simulated time in milliseconds.
   *
   * @return Current simulated time in milliseconds
   */
  @Override
  public long currentTimeMillis() {
    timeLock.lock();
    try {
      return currentTimeMillis;
    } finally {
      timeLock.unlock();
    }
  }
  
  /**
   * Advances the simulated time by the specified duration.
   *
   * @param millis The number of milliseconds to advance
   * @return The new current time
   */
  public long advanceTime(long millis) {
    if (millis < 0) {
      throw new IllegalArgumentException("Cannot advance time by a negative amount");
    }

    timeLock.lock();
    try {
      currentTimeMillis += millis;
      return currentTimeMillis;
    } finally {
      timeLock.unlock();
    }
  }
  
  /**
   * Advances the simulated time to a specific absolute time.
   *
   * @param targetTimeMillis The target time to advance to
   * @return The new current time
   */
  public long advanceTimeTo(long targetTimeMillis) {
    timeLock.lock();
    try {
      if (targetTimeMillis < currentTimeMillis) {
        throw new IllegalArgumentException("Cannot move time backwards (current: " +
            currentTimeMillis + ", target: " + targetTimeMillis + ")");
      }
      
      currentTimeMillis = targetTimeMillis;
      return currentTimeMillis;
    } finally {
      timeLock.unlock();
    }
  }
  
  /**
   * Resets the simulated clock to time zero.
   */
  public void reset() {
    timeLock.lock();
    try {
      currentTimeMillis = 0;
    } finally {
      timeLock.unlock();
    }
  }
  
  /**
   * Sets the current time to a specific value.
   *
   * @param timeMillis The time to set in milliseconds
   */
  public void setCurrentTime(long timeMillis) {
    if (timeMillis < 0) {
      throw new IllegalArgumentException("Time cannot be negative");
    }
    
    timeLock.lock();
    try {
      currentTimeMillis = timeMillis;
    } finally {
      timeLock.unlock();
    }
  }
  
  @Override
  public boolean isSimulated() {
    return true;
  }
}