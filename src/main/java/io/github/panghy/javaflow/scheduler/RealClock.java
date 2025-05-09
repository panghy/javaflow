package io.github.panghy.javaflow.scheduler;

/**
 * Implementation of FlowClock that uses the real system clock.
 * This class simply delegates to System.currentTimeMillis().
 * All timer scheduling is handled by the scheduler.
 */
public class RealClock implements FlowClock {
  
  @Override
  public long currentTimeMillis() {
    return System.currentTimeMillis();
  }
  
  @Override
  public boolean isSimulated() {
    return false;
  }
}