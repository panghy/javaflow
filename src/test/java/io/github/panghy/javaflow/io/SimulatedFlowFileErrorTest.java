package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the error handling in SimulatedFlowFile.
 * These tests focus on edge cases and error conditions not covered in the regular tests.
 */
class SimulatedFlowFileErrorTest extends AbstractFlowTest {

  private Path testPath;
  private SimulationParameters params;
  private SimulatedFlowFile file;
  
  @BeforeEach
  void setUp() {
    testPath = Paths.get("/test/error-test.txt");
    
    // Create simulation parameters with guaranteed error rates
    params = new SimulationParameters();
    params.setReadDelay(0.001);
    params.setWriteDelay(0.001);
    params.setMetadataDelay(0.001);
    params.setReadErrorProbability(1.0); // Always fail reads
    params.setWriteErrorProbability(1.0); // Always fail writes
    params.setMetadataErrorProbability(1.0); // Always fail metadata ops
    
    // Create the file with error-prone parameters
    file = new SimulatedFlowFile(testPath, params);
  }
  
  @Test
  void testSimulatedReadError() {
    // First, create a file with normal parameters
    SimulationParameters normalParams = new SimulationParameters();
    normalParams.setReadDelay(0.001);
    normalParams.setWriteDelay(0.001);
    normalParams.setMetadataDelay(0.001);
    
    // Use a retry approach since error injection is probabilistic
    boolean testPassed = false;
    for (int attempt = 0; attempt < 5; attempt++) {
      // Create the file with error parameters but directly assert the failure
      FlowFuture<ByteBuffer> readFuture = file.read(0, 10);
      pumpAndAdvanceTimeUntilDone(readFuture);
      
      try {
        // Try to get the result (should throw exception)
        readFuture.getNow();
        // If no exception, wait a bit and try again (error is probabilistic)
        testScheduler.advanceTime(0.1);
        testScheduler.pump();
        continue;
      } catch (ExecutionException e) {
        // Success - got the expected exception
        assertTrue(e.getCause() instanceof IOException);
        assertEquals("Simulated read error", e.getCause().getMessage());
        testPassed = true;
        break;
      }
    }
    
    // Make sure the test succeeded
    assertTrue(testPassed, "Failed to get simulated read error after multiple attempts");
  }
  
  @Test
  void testSimulatedWriteError() {
    String testData = "Test data for write error";
    ByteBuffer writeBuffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Use a retry approach since error injection is probabilistic
    boolean testPassed = false;
    for (int attempt = 0; attempt < 5; attempt++) {
      // Create write future and wait for it to complete
      FlowFuture<Void> writeFuture = file.write(0, writeBuffer);
      pumpAndAdvanceTimeUntilDone(writeFuture);
      
      try {
        // Try to get the result (should throw exception)
        writeFuture.getNow();
        // If no exception, wait a bit and try again (error is probabilistic)
        testScheduler.advanceTime(0.1);
        testScheduler.pump();
        continue;
      } catch (ExecutionException e) {
        // Success - got the expected exception
        assertTrue(e.getCause() instanceof IOException);
        assertEquals("Simulated write error", e.getCause().getMessage());
        testPassed = true;
        break;
      }
    }
    
    // Make sure the test succeeded
    assertTrue(testPassed, "Failed to get simulated error after multiple attempts");
  }
  
  @Test
  void testSimulatedTruncateError() {
    // Use a retry approach since error injection is probabilistic
    boolean testPassed = false;
    for (int attempt = 0; attempt < 5; attempt++) {
      // Create truncate future and wait for it to complete
      FlowFuture<Void> truncateFuture = file.truncate(100);
      pumpAndAdvanceTimeUntilDone(truncateFuture);
      
      try {
        // Try to get the result (should throw exception)
        truncateFuture.getNow();
        // If no exception, wait a bit and try again (error is probabilistic)
        testScheduler.advanceTime(0.1);
        testScheduler.pump();
        continue;
      } catch (ExecutionException e) {
        // Success - got the expected exception
        assertTrue(e.getCause() instanceof IOException);
        assertEquals("Simulated truncate error", e.getCause().getMessage());
        testPassed = true;
        break;
      }
    }
    
    // Make sure the test succeeded
    assertTrue(testPassed, "Failed to get simulated error after multiple attempts");
  }
  
  @Test
  void testInvalidReadParameters() {
    // Reset parameters to normal for this test
    params.setReadErrorProbability(0.0);
    
    // Test negative position
    FlowFuture<ByteBuffer> negativePosReadFuture = file.read(-1, 10);
    pumpAndAdvanceTimeUntilDone(negativePosReadFuture);
    
    ExecutionException exception1 = assertThrows(
        ExecutionException.class, 
        () -> negativePosReadFuture.getNow());
    
    assertTrue(exception1.getCause() instanceof IllegalArgumentException);
    assertEquals("Position must be non-negative", exception1.getCause().getMessage());
    
    // Test zero length
    FlowFuture<ByteBuffer> zeroLengthReadFuture = file.read(0, 0);
    pumpAndAdvanceTimeUntilDone(zeroLengthReadFuture);
    
    ExecutionException exception2 = assertThrows(
        ExecutionException.class, 
        () -> zeroLengthReadFuture.getNow());
    
    assertTrue(exception2.getCause() instanceof IllegalArgumentException);
    assertEquals("Length must be positive", exception2.getCause().getMessage());
    
    // Test negative length
    FlowFuture<ByteBuffer> negativeLengthReadFuture = file.read(0, -10);
    pumpAndAdvanceTimeUntilDone(negativeLengthReadFuture);
    
    ExecutionException exception3 = assertThrows(
        ExecutionException.class, 
        () -> negativeLengthReadFuture.getNow());
    
    assertTrue(exception3.getCause() instanceof IllegalArgumentException);
    assertEquals("Length must be positive", exception3.getCause().getMessage());
  }
  
  @Test
  void testInvalidWriteParameters() {
    // Reset parameters to normal for this test
    params.setWriteErrorProbability(0.0);
    
    String testData = "Test data";
    ByteBuffer writeBuffer = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));
    
    // Test negative position
    FlowFuture<Void> negativePositionWriteFuture = file.write(-1, writeBuffer);
    pumpAndAdvanceTimeUntilDone(negativePositionWriteFuture);
    
    ExecutionException exception = assertThrows(
        ExecutionException.class, 
        () -> negativePositionWriteFuture.getNow());
    
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
    assertEquals("Position must be non-negative", exception.getCause().getMessage());
  }
  
  @Test
  void testInvalidTruncateParameters() {
    // Reset parameters to normal for this test
    params.setMetadataErrorProbability(0.0);
    
    // Test negative size
    FlowFuture<Void> negativeSizeTruncateFuture = file.truncate(-1);
    pumpAndAdvanceTimeUntilDone(negativeSizeTruncateFuture);
    
    ExecutionException exception = assertThrows(
        ExecutionException.class, 
        () -> negativeSizeTruncateFuture.getNow());
    
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
    assertEquals("Size must be non-negative", exception.getCause().getMessage());
  }
  
  @Test
  void testIsClosed() {
    // File should never be considered closed with our current implementation
    assertFalse(file.isClosed());
    
    // Close the file
    FlowFuture<Void> closeFuture = Flow.startActor(() -> {
      Flow.await(file.close());
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(closeFuture);
    
    // Still should not be considered closed
    assertTrue(file.isClosed());
  }
  
  @Test
  void testGetPathAndDataStore() {
    // Test the getter methods
    assertEquals(testPath, file.getPath());
    
    // DataStore should be available
    FileDataStore dataStore = file.getDataStore();
    assertTrue(dataStore != null);
    
    // Test the file size getter
    assertEquals(0, file.getFileSize());
  }
}