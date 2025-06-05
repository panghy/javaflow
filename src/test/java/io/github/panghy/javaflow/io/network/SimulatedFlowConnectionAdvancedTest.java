package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.StreamClosedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced tests for SimulatedFlowConnection to improve code coverage.
 * This test focuses on edge cases and error conditions not covered by basic tests.
 */
public class SimulatedFlowConnectionAdvancedTest extends AbstractFlowTest {

  private SimulatedFlowConnection connection1;
  private SimulatedFlowConnection connection2;
  private NetworkSimulationParameters params;
  
  @BeforeEach
  void setUp() {
    // Create default parameters for testing
    params = new NetworkSimulationParameters();
    
    // Create endpoints
    Endpoint endpoint1 = new Endpoint("localhost", 8080);
    Endpoint endpoint2 = new Endpoint("localhost", 8081);
    
    // Create connections
    connection1 = new SimulatedFlowConnection(endpoint1, endpoint2, params);
    connection2 = new SimulatedFlowConnection(endpoint2, endpoint1, params);
    
    // Link the connections
    connection1.setPeer(connection2);
    connection2.setPeer(connection1);
  }

  @Test
  void testReceiveStreamAfterClose() throws Exception {
    // First verify stream works
    FlowStream<ByteBuffer> stream = connection1.receiveStream();
    assertNotNull(stream);
    
    // Send some data
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    FlowFuture<Void> sendFuture = connection2.send(data);
    pumpUntilDone(sendFuture);
    
    // Receive the data
    FlowFuture<ByteBuffer> receiveFuture = stream.nextAsync();
    pumpUntilDone(receiveFuture);
    
    assertFalse(receiveFuture.isCompletedExceptionally());
    assertNotNull(receiveFuture.getNow());
    
    // Now close the connection
    connection1.close();
    pumpUntilDone();
    
    // Try to get next item from stream - should fail
    FlowFuture<ByteBuffer> failFuture = stream.nextAsync();
    pumpUntilDone(failFuture);
    
    assertTrue(failFuture.isCompletedExceptionally());
    
    // The exception is wrapped by ExecutionException
    ExecutionException ex = assertThrows(ExecutionException.class, () -> failFuture.getNow());
    // The cause should be a StreamClosedException
    assertTrue(ex.getCause() instanceof StreamClosedException, 
        "Expected StreamClosedException, got: " + ex.getCause().getClass().getName());
  }
  
  @Test
  void testSendAfterClose() throws Exception {
    // First close the connection
    connection1.close();
    pumpUntilDone();
    
    // Try to send - should fail
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    FlowFuture<Void> sendFuture = connection1.send(data);
    pumpUntilDone(sendFuture);
    
    assertTrue(sendFuture.isCompletedExceptionally());
    
    // The exception is wrapped by ExecutionException
    ExecutionException ex = assertThrows(ExecutionException.class, () -> sendFuture.getNow());
    // The cause should be an IOException
    assertTrue(ex.getCause() instanceof IOException, 
        "Expected IOException, got: " + ex.getCause().getClass().getName());
  }
  
  @Test
  void testReceiveAfterClose() throws Exception {
    // First close the connection
    connection1.close();
    pumpUntilDone();
    
    // Try to receive - should fail
    FlowFuture<ByteBuffer> receiveFuture = connection1.receive(1024);
    pumpUntilDone(receiveFuture);
    
    assertTrue(receiveFuture.isCompletedExceptionally());
    
    // The exception is wrapped by ExecutionException
    ExecutionException ex = assertThrows(ExecutionException.class, () -> receiveFuture.getNow());
    // The cause should be an IOException
    assertTrue(ex.getCause() instanceof IOException, 
        "Expected IOException, got: " + ex.getCause().getClass().getName());
  }
  
  @Test
  void testCalculatedDelays() throws Exception {
    // Create parameters with specific delays
    NetworkSimulationParameters delayParams = new NetworkSimulationParameters()
        .setSendDelay(0.1)
        .setSendBytesPerSecond(1_048_576); // 1MB/s
    
    // Create new connections with these parameters
    Endpoint endpoint1 = new Endpoint("localhost", 8085);
    Endpoint endpoint2 = new Endpoint("localhost", 8086);
    
    SimulatedFlowConnection delayConn1 = new SimulatedFlowConnection(endpoint1, endpoint2, delayParams);
    SimulatedFlowConnection delayConn2 = new SimulatedFlowConnection(endpoint2, endpoint1, delayParams);
    
    delayConn1.setPeer(delayConn2);
    delayConn2.setPeer(delayConn1);
    
    // Send data of different sizes and measure time
    ByteBuffer smallData = ByteBuffer.wrap(new byte[1024]); // 1KB
    ByteBuffer largeData = ByteBuffer.wrap(new byte[1024 * 1024]); // 1MB
    
    // Record start time in simulation time
    double startTime = currentTimeSeconds();
    
    // Send small data
    FlowFuture<Void> smallSendFuture = delayConn1.send(smallData);
    pumpUntilDone(smallSendFuture);
    
    // Check elapsed time
    double smallElapsed = currentTimeSeconds() - startTime;
    
    // Should be approximately base delay (0.1) plus very small throughput delay
    assertTrue(smallElapsed >= 0.1, "Small data delay should be at least base delay");
    assertTrue(smallElapsed < 0.2, "Small data delay should not be too large");
    
    // Reset start time
    startTime = currentTimeSeconds();
    
    // Send large data
    FlowFuture<Void> largeSendFuture = delayConn1.send(largeData);
    pumpUntilDone(largeSendFuture);
    
    // Check elapsed time
    double largeElapsed = currentTimeSeconds() - startTime;
    
    // Should be approximately base delay (0.1) plus 1s for 1MB at 1MB/s
    assertTrue(largeElapsed >= 1.0, "Large data delay should include throughput delay");
    assertTrue(largeElapsed < 1.2, "Large data delay should not be too large");
    
    // Clean up
    delayConn1.close();
    delayConn2.close();
    pumpUntilDone();
  }
  
  @Test
  void testDisconnectDuringOperation() throws Exception {
    // Create parameters with 100% disconnect probability
    NetworkSimulationParameters disconnectParams = new NetworkSimulationParameters()
        .setDisconnectProbability(1.0);
    
    // Create new connections with these parameters
    Endpoint endpoint1 = new Endpoint("localhost", 8087);
    Endpoint endpoint2 = new Endpoint("localhost", 8088);
    
    SimulatedFlowConnection disconnectConn1 = new SimulatedFlowConnection(endpoint1, endpoint2, disconnectParams);
    SimulatedFlowConnection disconnectConn2 = new SimulatedFlowConnection(endpoint2, endpoint1, disconnectParams);
    
    disconnectConn1.setPeer(disconnectConn2);
    disconnectConn2.setPeer(disconnectConn1);
    
    // Send data - should cause disconnect
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    FlowFuture<Void> sendFuture = disconnectConn1.send(data);
    pumpUntilDone(sendFuture);
    
    // Should fail with IOException
    assertTrue(sendFuture.isCompletedExceptionally());
    
    // The exception is wrapped by ExecutionException
    ExecutionException ex = assertThrows(ExecutionException.class, () -> sendFuture.getNow());
    // The cause should be an IOException
    assertTrue(ex.getCause() instanceof IOException, 
        "Expected IOException, got: " + ex.getCause().getClass().getName());
    
    // Connections should be closed
    assertFalse(disconnectConn1.isOpen());
    
    // Trying to receive should also fail
    FlowFuture<ByteBuffer> receiveFuture = disconnectConn2.receive(1024);
    pumpUntilDone(receiveFuture);
    
    assertTrue(receiveFuture.isCompletedExceptionally());
    
    // Clean up
    disconnectConn1.close();
    disconnectConn2.close();
    pumpUntilDone();
  }
  
  @Test
  void testCloseFutureLifecycle() throws Exception {
    // Get close future
    FlowFuture<Void> closeFuture = connection1.closeFuture();
    
    // Should not be done yet
    assertFalse(closeFuture.isDone());
    
    // Close connection
    connection1.close();
    pumpUntilDone();
    
    // Now the future should be done
    assertTrue(closeFuture.isDone());
    
    // And close should be idempotent (second close shouldn't affect anything)
    connection1.close();
    pumpUntilDone();
    
    assertTrue(closeFuture.isDone());
  }
  
  @Test
  void testSendingDataWithNoPeer() throws Exception {
    // Create a connection with no peer
    Endpoint endpoint1 = new Endpoint("localhost", 8089);
    Endpoint endpoint2 = new Endpoint("localhost", 8090);
    
    SimulatedFlowConnection noPeerConn = new SimulatedFlowConnection(endpoint1, endpoint2, params);
    
    // Try to send data - should not fail, data is just lost
    ByteBuffer data = ByteBuffer.wrap("test".getBytes());
    FlowFuture<Void> sendFuture = noPeerConn.send(data);
    pumpUntilDone(sendFuture);
    
    // Should succeed (data is just lost)
    assertFalse(sendFuture.isCompletedExceptionally());
    
    // Clean up
    noPeerConn.close();
    pumpUntilDone();
  }
}