package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the FlowFileSystem interface.
 */
class FlowFileSystemTest extends AbstractFlowTest {
  
  @Test
  void testGetDefault() {
    // Test the static getDefault() method of FlowFileSystem
    FlowFileSystem fs = FlowFileSystem.getDefault();
    
    // In test context, it should return a simulated file system
    assertNotNull(fs);
    assertTrue(fs instanceof SimulatedFlowFileSystem);
  }
}