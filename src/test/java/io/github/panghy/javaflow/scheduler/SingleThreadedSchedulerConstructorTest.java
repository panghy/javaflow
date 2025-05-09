package io.github.panghy.javaflow.scheduler;

import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the SingleThreadedScheduler constructor options, particularly
 * for the carrier thread disabled mode.
 */
class SingleThreadedSchedulerConstructorTest {

  private SingleThreadedScheduler scheduler;
  
  @AfterEach
  void tearDown() {
    if (scheduler != null) {
      scheduler.close();
    }
  }
  
  @Test
  void testDefaultConstructor() {
    scheduler = new SingleThreadedScheduler();
    assertTrue(getEnableCarrierThreadValue(scheduler), 
        "Default constructor should enable carrier thread");
        
    // Schedule a task and verify it executes automatically
    AtomicBoolean executed = new AtomicBoolean(false);
    FlowFuture<Void> future = scheduler.schedule(() -> {
      executed.set(true);
      return null;
    });
    
    try {
      future.toCompletableFuture().get(1, TimeUnit.SECONDS);
      assertTrue(executed.get(), "Task should execute automatically with carrier thread enabled");
    } catch (Exception e) {
      fail("Task execution failed", e);
    }
  }
  
  @Test
  void testDisabledCarrierThread() throws Exception {
    scheduler = new SingleThreadedScheduler(false);
    assertFalse(getEnableCarrierThreadValue(scheduler), 
        "Constructor with false should disable carrier thread");
    
    // Start the scheduler (this should not start a carrier thread)
    scheduler.start();
    
    // Schedule a task
    AtomicBoolean executed = new AtomicBoolean(false);
    scheduler.schedule(() -> {
      executed.set(true);
      return null;
    });
    
    // Wait a moment - the task should NOT execute automatically
    Thread.sleep(200);
    assertFalse(executed.get(), "Task should not execute automatically with carrier thread off");
    
    // Now manually pump the scheduler
    int tasksProcessed = scheduler.pump();
    assertEquals(1, tasksProcessed, "Pump should process exactly one task");
    assertTrue(executed.get(), "Task should execute after manual pump");
  }
  
  @Test
  void testPumpWithDisabledCarrierThread() throws Exception {
    scheduler = new SingleThreadedScheduler(false);
    
    // Start the scheduler (this should not start a carrier thread)
    scheduler.start();
    
    // Schedule multiple tasks
    AtomicInteger counter = new AtomicInteger(0);
    
    scheduler.schedule(() -> {
      counter.incrementAndGet();
      return null;
    });
    
    scheduler.schedule(() -> {
      counter.incrementAndGet();
      return null;
    });
    
    scheduler.schedule(() -> {
      counter.incrementAndGet();
      return null;
    });
    
    // Verify tasks have not executed automatically
    assertEquals(0, counter.get(), "Tasks should not execute automatically");
    
    // Pump the scheduler to process tasks
    int processed = scheduler.pump();
    assertEquals(3, processed, "Pump should process exactly three tasks");
    assertEquals(3, counter.get(), "All tasks should have been processed");
  }
  
  @Test
  void testCarrierThreadEnabledPump() {
    scheduler = new SingleThreadedScheduler(true);
    
    // Should throw an exception when trying to pump with carrier thread enabled
    assertThrows(IllegalStateException.class, () -> scheduler.pump(), 
        "Pump should throw exception when carrier thread enabled");
  }
  
  /**
   * Helper method to access the enableCarrierThread field via reflection.
   */
  private boolean getEnableCarrierThreadValue(SingleThreadedScheduler scheduler) {
    try {
      Field field = SingleThreadedScheduler.class.getDeclaredField("enableCarrierThread");
      field.setAccessible(true);
      return (boolean) field.get(scheduler);
    } catch (Exception e) {
      throw new RuntimeException("Could not access enableCarrierThread field", e);
    }
  }
}