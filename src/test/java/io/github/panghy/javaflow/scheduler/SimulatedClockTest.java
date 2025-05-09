package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SimulatedClock implementation.
 */
class SimulatedClockTest {

  private SimulatedClock clock;

  @BeforeEach
  void setUp() {
    clock = new SimulatedClock();
  }

  @Test
  void testInitialState() {
    assertEquals(0, clock.currentTimeMillis());
    assertTrue(clock.isSimulated());
  }

  @Test
  void testAdvanceTime() {
    // Advance time by a positive amount
    assertEquals(1000, clock.advanceTime(1000));
    assertEquals(1000, clock.currentTimeMillis());

    // Advance time again
    assertEquals(2500, clock.advanceTime(1500));
    assertEquals(2500, clock.currentTimeMillis());

    // Cannot advance by negative time
    assertThrows(IllegalArgumentException.class, () -> clock.advanceTime(-100));
  }

  @Test
  void testAdvanceTimeTo() {
    // Advance to a specific time
    assertEquals(5000, clock.advanceTimeTo(5000));
    assertEquals(5000, clock.currentTimeMillis());

    // Advance to a later time
    assertEquals(7000, clock.advanceTimeTo(7000));
    assertEquals(7000, clock.currentTimeMillis());

    // Cannot move backwards
    assertThrows(IllegalArgumentException.class, () -> clock.advanceTimeTo(6000));
    assertEquals(7000, clock.currentTimeMillis(), "Time should not have changed");

    // Cannot move to the same time (doesn't throw, just returns current time)
    assertEquals(7000, clock.advanceTimeTo(7000));
  }

  @Test
  void testReset() {
    // Set time to non-zero
    clock.advanceTime(5000);
    assertEquals(5000, clock.currentTimeMillis());

    // Reset the clock
    clock.reset();
    assertEquals(0, clock.currentTimeMillis());
  }

  @Test
  void testSetCurrentTime() {
    // Set to a positive time
    clock.setCurrentTime(3000);
    assertEquals(3000, clock.currentTimeMillis());

    // Set to a lower time
    clock.setCurrentTime(1000);
    assertEquals(1000, clock.currentTimeMillis());

    // Set to zero
    clock.setCurrentTime(0);
    assertEquals(0, clock.currentTimeMillis());

    // Cannot set to negative time
    assertThrows(IllegalArgumentException.class, () -> clock.setCurrentTime(-1));
    assertEquals(0, clock.currentTimeMillis(), "Time should not have changed");
  }

  @Test
  void testConcurrency() {
    // Execute a simple concurrent operation
    Runnable timeChecker = () -> {
      for (int i = 0; i < 100; i++) {
        long time = clock.currentTimeMillis();
        assertTrue(time >= 0, "Time should never be negative: " + time);
      }
    };

    // Start a thread that checks time concurrently
    Thread thread = new Thread(timeChecker);
    thread.start();

    // Modify time while the other thread is running
    for (int i = 0; i < 50; i++) {
      clock.advanceTime(10);
    }

    // Wait for the thread to finish
    try {
      thread.join(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertEquals(500, clock.currentTimeMillis());
  }
}