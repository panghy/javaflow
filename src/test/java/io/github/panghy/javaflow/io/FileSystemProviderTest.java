package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.scheduler.FlowClock;
import io.github.panghy.javaflow.scheduler.FlowScheduler;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the FileSystemProvider class.
 */
class FileSystemProviderTest extends AbstractFlowTest {
  
  @Override
  protected void onSetUp() {
    // Reset before each test
    FileSystemProvider.reset();
  }
  
  @Override
  protected void onTearDown() {
    // Reset after each test
    FileSystemProvider.reset();
  }
  
  @Test
  void testGetRealFileSystem() {
    // First reset to ensure a clean state
    FileSystemProvider.reset();
    
    // Get the real file system
    FlowFileSystem fs = FileSystemProvider.getRealFileSystem();
    assertNotNull(fs);
    assertTrue(fs instanceof RealFlowFileSystem);
    
    // Getting it again should return the same instance
    FlowFileSystem fs2 = FileSystemProvider.getRealFileSystem();
    assertSame(fs, fs2);
    
    // Reset and get it again to test the initialization path again
    FileSystemProvider.reset();
    FlowFileSystem fs3 = FileSystemProvider.getRealFileSystem();
    assertNotNull(fs3);
    assertTrue(fs3 instanceof RealFlowFileSystem);
  }
  
  @Test
  void testGetSimulatedFileSystem() {
    // First reset to ensure a clean state
    FileSystemProvider.reset();
    
    // Get the simulated file system
    FlowFileSystem fs = FileSystemProvider.getSimulatedFileSystem();
    assertNotNull(fs);
    assertTrue(fs instanceof SimulatedFlowFileSystem);
    
    // Getting it again should return the same instance
    FlowFileSystem fs2 = FileSystemProvider.getSimulatedFileSystem();
    assertSame(fs, fs2);
    
    // Reset and get it again to test the initialization path again
    FileSystemProvider.reset();
    FlowFileSystem fs3 = FileSystemProvider.getSimulatedFileSystem();
    assertNotNull(fs3);
    assertTrue(fs3 instanceof SimulatedFlowFileSystem);
  }
  
  @Test
  void testGetDefaultFileSystemSimulated() {
    // We can't easily test this since we're using AbstractFlowTest
    // which sets up a simulated scheduler. Instead, we can verify
    // the simulated mode behavior.
    FlowFileSystem fs = FileSystemProvider.getDefaultFileSystem();
    assertNotNull(fs);
    assertTrue(fs instanceof SimulatedFlowFileSystem);
    
    // Also test the case where defaultFileSystem is already set
    // Call getDefaultFileSystem again to ensure it returns the cached instance
    FlowFileSystem fs2 = FileSystemProvider.getDefaultFileSystem();
    assertSame(fs, fs2);
  }
  
  @Test
  void testGetDefaultFileSystemCached() throws Exception {
    // Reset to start with a clean state
    FileSystemProvider.reset();
    
    // Set up a non-null defaultFileSystem
    FlowFileSystem mockFs = new SimulatedFlowFileSystem(new SimulationParameters());
    setPrivateStaticField("defaultFileSystem", mockFs);
    
    // Get the default file system
    FlowFileSystem fs = FileSystemProvider.getDefaultFileSystem();
    assertSame(mockFs, fs);
  }
  
  @Test
  void testGetRealFileSystemCached() throws Exception {
    // Reset to start with a clean state
    FileSystemProvider.reset();
    
    // Set up a non-null realFileSystem
    FlowFileSystem mockFs = new RealFlowFileSystem();
    setPrivateStaticField("realFileSystem", mockFs);
    
    // Get the real file system
    FlowFileSystem fs = FileSystemProvider.getRealFileSystem();
    assertSame(mockFs, fs);
  }
  
  @Test
  void testGetSimulatedFileSystemCached() throws Exception {
    // Reset to start with a clean state
    FileSystemProvider.reset();
    
    // Set up a non-null simulatedFileSystem
    FlowFileSystem mockFs = new SimulatedFlowFileSystem(new SimulationParameters());
    setPrivateStaticField("simulatedFileSystem", mockFs);
    
    // Get the simulated file system
    FlowFileSystem fs = FileSystemProvider.getSimulatedFileSystem();
    assertSame(mockFs, fs);
  }
  
  @Test
  void testSetDefaultFileSystem() {
    // Create a mock file system
    FlowFileSystem mockFs = new SimulatedFlowFileSystem(new SimulationParameters());
    
    // Set it as default
    FileSystemProvider.setDefaultFileSystem(mockFs);
    
    // Get default should return our mock
    FlowFileSystem fs = FileSystemProvider.getDefaultFileSystem();
    assertSame(mockFs, fs);
  }
  
  @Test
  void testReset() {
    // Get a file system first
    FlowFileSystem fs1 = FileSystemProvider.getDefaultFileSystem();
    assertNotNull(fs1);
    
    // Reset the provider
    FileSystemProvider.reset();
    
    // Get a file system again
    FlowFileSystem fs2 = FileSystemProvider.getDefaultFileSystem();
    assertNotNull(fs2);
    
    // Should be a different instance
    assertNotSame(fs1, fs2);
  }
  
  @Test
  void testRaceCondition() throws Exception {
    // Test the branch where we enter the synchronization block for default file system
    // but another thread has already initialized it
    
    // Reset to start with a clean state
    FileSystemProvider.reset();
    
    // First check is null
    assertNull(getPrivateStaticField("defaultFileSystem"));
    
    // Simulate race condition by setting the field while we're "inside" the synchronized block
    // Set up a non-null defaultFileSystem as if another thread did it
    FlowFileSystem mockFs = new SimulatedFlowFileSystem(new SimulationParameters());
    setPrivateStaticField("defaultFileSystem", mockFs);
    
    // Call getDefaultFileSystem - this should realize the field is already set
    FlowFileSystem fs = FileSystemProvider.getDefaultFileSystem();
    assertSame(mockFs, fs);
  }
  
  @Test
  void testSimulationModeSwitch() throws Exception {
    // Test both branches of the simulation mode check in getDefaultFileSystem
    
    // Reset to start with a clean state
    FileSystemProvider.reset();
    
    // Current state (in test we're in simulation mode)
    FlowFileSystem fs1 = FileSystemProvider.getDefaultFileSystem();
    assertTrue(fs1 instanceof SimulatedFlowFileSystem);
    
    // Reset and prepare for second part of test
    FileSystemProvider.reset();
    
    // Create a helper class to test the non-simulated branch
    simulateRealMode(() -> {
      FlowFileSystem fs2 = FileSystemProvider.getDefaultFileSystem();
      assertTrue(fs2 instanceof RealFlowFileSystem);
    });
  }
  
  // Helper method to set a private static field in the FileSystemProvider
  private void setPrivateStaticField(String fieldName, Object value) throws Exception {
    Field field = FileSystemProvider.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(null, value);
  }
  
  // Helper method to get a private static field from the FileSystemProvider
  private Object getPrivateStaticField(String fieldName) throws Exception {
    Field field = FileSystemProvider.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(null);
  }
  
  // Helper method to temporarily simulate non-simulation mode
  private void simulateRealMode(Runnable action) throws Exception {
    // Create a mock FlowClock that reports as not simulated
    FlowScheduler originalScheduler = Flow.scheduler();
    
    // Create a new scheduler with a mock clock that returns isSimulated = false
    FlowScheduler mockScheduler = new FlowScheduler() {
      @Override
      public FlowClock getClock() {
        return new FlowClock() {
          @Override
          public long currentTimeMillis() {
            return System.currentTimeMillis();
          }
          
          @Override
          public boolean isSimulated() {
            return false;
          }
        };
      }
    };
    
    try {
      // Set the mock scheduler
      Flow.setScheduler(mockScheduler);
      
      // Run the action with the mocked scheduler
      action.run();
    } finally {
      // Restore the original scheduler
      Flow.setScheduler(originalScheduler);
    }
  }
}