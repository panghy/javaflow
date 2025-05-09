package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for clock functionality.
 */
class FlowClockTest {

  @Test
  void testRealClockCurrentTime() {
    FlowClock clock = FlowClock.createRealClock();

    // Current time should be close to System.currentTimeMillis()
    long systemTime = System.currentTimeMillis();
    long clockTime = clock.currentTimeMillis();

    // Allow for larger variance due to execution time and system load
    assertTrue(Math.abs(systemTime - clockTime) < 500,
        "Clock time should be close to system time");

    // Test that currentTimeSeconds is consistent with currentTimeMillis
    double expectedSeconds = clockTime / 1000.0;
    double actualSeconds = clock.currentTimeSeconds();
    double delta = Math.abs(expectedSeconds - actualSeconds);
    assertTrue(delta < 0.1,
        "Seconds time should match milliseconds time, delta=" + delta);

    assertFalse(clock.isSimulated(), "RealClock should not be simulated");
  }

  @Test
  void testSimulatedClockCurrentTime() {
    SimulatedClock clock = (SimulatedClock) FlowClock.createSimulatedClock();

    // Initial time should be 0
    assertEquals(0, clock.currentTimeMillis());
    assertEquals(0.0, clock.currentTimeSeconds());

    assertTrue(clock.isSimulated(), "SimulatedClock should be simulated");

    // Advance time and check
    clock.advanceTime(1000);
    assertEquals(1000, clock.currentTimeMillis());
    assertEquals(1.0, clock.currentTimeSeconds());

    // Set time explicitly
    clock.setCurrentTime(2000);
    assertEquals(2000, clock.currentTimeMillis());

    // Reset clock
    clock.reset();
    assertEquals(0, clock.currentTimeMillis());
  }

  @Test
  void testSimulatedClockAdvanceTimeTo() {
    SimulatedClock clock = (SimulatedClock) FlowClock.createSimulatedClock();

    // Advance to specific time
    clock.advanceTimeTo(150);
    assertEquals(150, clock.currentTimeMillis());

    // Cannot move backwards
    assertThrows(IllegalArgumentException.class, () -> clock.advanceTimeTo(100));
  }
}