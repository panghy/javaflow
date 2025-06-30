package io.github.panghy.javaflow.io;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for enhanced file system simulation with fault injection capabilities.
 */
@Timeout(30)
public class EnhancedFileSystemSimulationTest extends AbstractFlowTest {

  private SimulatedFlowFileSystem fileSystem;

  @BeforeEach
  public void setUp() {
    SimulationContext.clear();
  }

  @AfterEach
  public void tearDown() {
    SimulationContext.clear();
  }

  @Test
  public void testDataCorruptionSimulation() throws Exception {
    // Configure simulation with data corruption
    SimulationConfiguration config = new SimulationConfiguration()
        .setDiskCorruptionProbability(0.5)  // 50% chance of corruption
        .setTaskExecutionLogging(true);
    
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Create file system with corruption
    SimulationParameters params = new SimulationParameters()
        .setCorruptionProbability(0.5)
        .setReadDelay(0.001)
        .setWriteDelay(0.001);
    
    fileSystem = new SimulatedFlowFileSystem(params);
    
    // Write test data
    Path testFile = Paths.get("/test.txt");
    String originalData = "This is test data that should get corrupted sometimes";
    ByteBuffer writeBuffer = StandardCharsets.UTF_8.encode(originalData);
    
    // Open file for writing
    CompletableFuture<FlowFile> openFuture = Flow.startActor(() -> {
      return Flow.await(fileSystem.open(testFile, OpenOptions.CREATE, OpenOptions.WRITE));
    });
    pumpAndAdvanceTimeUntilDone(openFuture);
    FlowFile file = openFuture.getNow(null);
    
    // Write data
    CompletableFuture<Void> writeFuture = Flow.startActor(() -> {
      return Flow.await(file.write(0, writeBuffer));
    });
    pumpAndAdvanceTimeUntilDone(writeFuture);
    
    // Close and reopen for reading
    CompletableFuture<Void> closeFuture = Flow.startActor(() -> {
      return Flow.await(file.close());
    });
    pumpAndAdvanceTimeUntilDone(closeFuture);
    
    // Track corrupted reads
    AtomicInteger corruptedReads = new AtomicInteger(0);
    AtomicInteger totalReads = new AtomicInteger(0);
    
    // Read data multiple times to observe corruption
    for (int i = 0; i < 20; i++) {
      CompletableFuture<FlowFile> readOpenFuture = Flow.startActor(() -> {
        return Flow.await(fileSystem.open(testFile, OpenOptions.READ));
      });
      pumpAndAdvanceTimeUntilDone(readOpenFuture);
      FlowFile readFile = readOpenFuture.getNow(null);
      
      CompletableFuture<ByteBuffer> readFuture = Flow.startActor(() -> {
        return Flow.await(readFile.read(0, originalData.getBytes().length));
      });
      pumpAndAdvanceTimeUntilDone(readFuture);
      ByteBuffer readBuffer = readFuture.getNow(null);
      
      String readData = StandardCharsets.UTF_8.decode(readBuffer).toString();
      totalReads.incrementAndGet();
      
      if (!readData.equals(originalData)) {
        corruptedReads.incrementAndGet();
      }
      
      CompletableFuture<Void> readCloseFuture = Flow.startActor(() -> {
        return Flow.await(readFile.close());
      });
      pumpAndAdvanceTimeUntilDone(readCloseFuture);
    }
    
    // With 50% corruption probability, we expect roughly 10 out of 20 reads to be corrupted
    // Allow some variance (between 5 and 15 corrupted reads)
    int corrupted = corruptedReads.get();
    assertTrue(corrupted >= 5 && corrupted <= 15, 
        "Expected 5-15 corrupted reads with 50% corruption probability, but got " + corrupted);
    
    // Verify that some reads were actually corrupted
    assertTrue(corrupted > 0, "Expected some data corruption, but all reads were clean");
  }

  @Test
  public void testDiskFullSimulation() throws Exception {
    // Configure simulation with disk full errors
    SimulationParameters params = new SimulationParameters()
        .setDiskFullProbability(0.3)  // 30% chance of disk full
        .setWriteDelay(0.001);
    
    fileSystem = new SimulatedFlowFileSystem(params);
    
    // Track write errors
    AtomicInteger diskFullErrors = new AtomicInteger(0);
    AtomicInteger successfulWrites = new AtomicInteger(0);
    
    // Attempt multiple writes
    for (int i = 0; i < 20; i++) {
      final int fileNum = i;
      Path testFile = Paths.get("/file" + fileNum + ".txt");
      
      CompletableFuture<Void> writeFuture = Flow.startActor(() -> {
        try {
          FlowFile file = Flow.await(fileSystem.open(testFile, 
              OpenOptions.CREATE, OpenOptions.WRITE));
          
          String data = "Test data for file " + fileNum;
          ByteBuffer buffer = StandardCharsets.UTF_8.encode(data);
          Flow.await(file.write(0, buffer));
          Flow.await(file.close());
          
          successfulWrites.incrementAndGet();
        } catch (IOException e) {
          if (e.getMessage().contains("No space left on device")) {
            diskFullErrors.incrementAndGet();
          } else {
            throw e;
          }
        }
        return null;
      });
      
      pumpAndAdvanceTimeUntilDone(writeFuture);
    }
    
    // With 30% disk full probability, expect some failures
    assertTrue(diskFullErrors.get() > 0, 
        "Expected some disk full errors but got none");
    assertTrue(successfulWrites.get() > 0,
        "Expected some successful writes but got none");
    
    // Total should be 20
    assertEquals(20, diskFullErrors.get() + successfulWrites.get(),
        "Total operations should equal attempts");
  }

  @Test
  public void testDiskFailureSimulation() throws Exception {
    // Configure simulation with general disk failures
    SimulationParameters params = new SimulationParameters()
        .setReadErrorProbability(0.3)   // 30% read failures
        .setWriteErrorProbability(0.3)  // 30% write failures
        .setMetadataErrorProbability(0.2); // 20% metadata failures
    
    fileSystem = new SimulatedFlowFileSystem(params);
    
    // Track different types of errors
    AtomicInteger readErrors = new AtomicInteger(0);
    AtomicInteger writeErrors = new AtomicInteger(0);
    AtomicInteger openErrors = new AtomicInteger(0);
    
    // Test metadata operations (open)
    for (int i = 0; i < 30; i++) {
      Path testFile = Paths.get("/metadata" + i + ".txt");
      
      CompletableFuture<Void> openFuture = Flow.startActor(() -> {
        try {
          FlowFile file = Flow.await(fileSystem.open(testFile, 
              OpenOptions.CREATE, OpenOptions.WRITE));
          Flow.await(file.close());
        } catch (IOException e) {
          if (e.getMessage().contains("Simulated open error")) {
            openErrors.incrementAndGet();
          }
        }
        return null;
      });
      
      pumpAndAdvanceTimeUntilDone(openFuture);
    }
    
    // Create a file for read/write tests - retry until successful
    Path testFile = Paths.get("/test.txt");
    FlowFile file = null;
    for (int attempt = 0; attempt < 10 && file == null; attempt++) {
      CompletableFuture<FlowFile> createFuture = Flow.startActor(() -> {
        try {
          return Flow.await(fileSystem.open(testFile, OpenOptions.CREATE, OpenOptions.WRITE));
        } catch (IOException e) {
          return null;
        }
      });
      pumpAndAdvanceTimeUntilDone(createFuture);
      if (!createFuture.isCompletedExceptionally()) {
        file = createFuture.getNow(null);
      }
    }
    
    // If we couldn't create the file, skip the read/write tests
    if (file != null) {
      // Write initial data
      String testData = "Test data for error simulation";
      ByteBuffer writeBuffer = StandardCharsets.UTF_8.encode(testData);
      FlowFile finalFile = file;
      CompletableFuture<Void> initialWriteFuture = Flow.startActor(() -> {
        try {
          return Flow.await(finalFile.write(0, writeBuffer));
        } catch (IOException e) {
          return null;
        }
      });
      pumpAndAdvanceTimeUntilDone(initialWriteFuture);
      
      CompletableFuture<Void> closeFuture = Flow.startActor(() -> {
        return Flow.await(finalFile.close());
      });
      pumpAndAdvanceTimeUntilDone(closeFuture);
    }
    
    // Test write operations
    for (int i = 0; i < 20; i++) {
      final int writeNum = i;
      CompletableFuture<Void> writeFuture = Flow.startActor(() -> {
        try {
          FlowFile writeFile = Flow.await(fileSystem.open(testFile, OpenOptions.WRITE));
          ByteBuffer buffer = StandardCharsets.UTF_8.encode("Write " + writeNum);
          Flow.await(writeFile.write(0, buffer));
          Flow.await(writeFile.close());
        } catch (IOException e) {
          if (e.getMessage().contains("Simulated write error")) {
            writeErrors.incrementAndGet();
          } else if (!e.getMessage().contains("Simulated open error")) {
            throw e;
          }
        }
        return null;
      });
      
      pumpAndAdvanceTimeUntilDone(writeFuture);
    }
    
    // Test read operations
    for (int i = 0; i < 20; i++) {
      CompletableFuture<Void> readFuture = Flow.startActor(() -> {
        try {
          FlowFile readFile = Flow.await(fileSystem.open(testFile, OpenOptions.READ));
          Flow.await(readFile.read(0, 100));
          Flow.await(readFile.close());
        } catch (IOException e) {
          if (e.getMessage().contains("Simulated read error")) {
            readErrors.incrementAndGet();
          } else if (!e.getMessage().contains("Simulated open error")) {
            throw e;
          }
        }
        return null;
      });
      
      pumpAndAdvanceTimeUntilDone(readFuture);
    }
    
    // Verify we got some errors of each type
    assertTrue(openErrors.get() > 0, "Expected some open errors");
    assertTrue(writeErrors.get() > 0, "Expected some write errors");
    assertTrue(readErrors.get() > 0, "Expected some read errors");
  }

  @Test
  public void testIntegrationWithSimulationConfiguration() throws Exception {
    // Use SimulationConfiguration to configure file system behavior
    SimulationConfiguration config = SimulationConfiguration.chaos()
        .setDiskReadDelay(0.01)
        .setDiskWriteDelay(0.02)
        .setDiskBytesPerSecond(1_000_000)  // 1MB/s
        .setDiskFailureProbability(0.1)
        .setDiskFullProbability(0.05)
        .setDiskCorruptionProbability(0.2);
    
    SimulationContext context = new SimulationContext(12345, true, config);
    SimulationContext.setCurrent(context);
    
    // Create file system from simulation config
    fileSystem = SimulatedFlowFileSystem.fromSimulationConfig();
    
    // Create a simple test file
    Path testFile = Paths.get("/config-test.txt");
    
    CompletableFuture<Void> testFuture = Flow.startActor(() -> {
      FlowFile file = Flow.await(fileSystem.open(testFile, 
          OpenOptions.CREATE, OpenOptions.WRITE));
      
      String data = "Testing configuration integration";
      ByteBuffer buffer = StandardCharsets.UTF_8.encode(data);
      Flow.await(file.write(0, buffer));
      Flow.await(file.close());
      
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Verify the file was created (unless there was an error)
    if (!testFuture.isCompletedExceptionally()) {
      CompletableFuture<Boolean> existsFuture = Flow.startActor(() -> {
        return Flow.await(fileSystem.exists(testFile));
      });
      pumpAndAdvanceTimeUntilDone(existsFuture);
      assertTrue(existsFuture.getNow(null), "File should exist after successful write");
    }
  }

  @Test
  public void testFileSizeAndThroughputSimulation() throws Exception {
    // Configure with specific throughput limits
    SimulationParameters params = new SimulationParameters()
        .setReadBytesPerSecond(100_000)   // 100KB/s
        .setWriteBytesPerSecond(50_000)   // 50KB/s
        .setReadDelay(0.001)
        .setWriteDelay(0.001);
    
    fileSystem = new SimulatedFlowFileSystem(params);
    
    // Create a large file
    Path largeFile = Paths.get("/large.dat");
    int fileSize = 10_000; // 10KB
    byte[] data = new byte[fileSize];
    for (int i = 0; i < fileSize; i++) {
      data[i] = (byte) (i % 256);
    }
    ByteBuffer writeBuffer = ByteBuffer.wrap(data);
    
    // Measure write time
    double startTime = currentTimeSeconds();
    
    CompletableFuture<Void> writeFuture = Flow.startActor(() -> {
      FlowFile file = Flow.await(fileSystem.open(largeFile, 
          OpenOptions.CREATE, OpenOptions.WRITE));
      Flow.await(file.write(0, writeBuffer));
      Flow.await(file.close());
      return null;
    });
    
    pumpAndAdvanceTimeUntilDone(writeFuture);
    
    double writeTime = currentTimeSeconds() - startTime;
    // Expected time: base delay + (10KB / 50KB/s) = 0.001 + 0.2 = 0.201s
    // Allow some tolerance
    assertTrue(writeTime >= 0.19 && writeTime <= 0.22,
        "Write time should be around 0.201s but was " + writeTime);
    
    // Measure read time
    startTime = currentTimeSeconds();
    
    CompletableFuture<ByteBuffer> readFuture = Flow.startActor(() -> {
      FlowFile file = Flow.await(fileSystem.open(largeFile, OpenOptions.READ));
      ByteBuffer result = Flow.await(file.read(0, fileSize));
      Flow.await(file.close());
      return result;
    });
    
    pumpAndAdvanceTimeUntilDone(readFuture);
    
    double readTime = currentTimeSeconds() - startTime;
    // Expected time: base delay + (10KB / 100KB/s) = 0.001 + 0.1 = 0.101s
    // Allow some tolerance
    assertTrue(readTime >= 0.09 && readTime <= 0.12,
        "Read time should be around 0.101s but was " + readTime);
    
    // Verify data integrity
    ByteBuffer readBuffer = readFuture.getNow(null);
    byte[] readData = new byte[readBuffer.remaining()];
    readBuffer.get(readData);
    for (int i = 0; i < fileSize; i++) {
      assertEquals(data[i], readData[i], "Data mismatch at position " + i);
    }
  }
}