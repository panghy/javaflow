package io.github.panghy.javaflow.rpc.simulation;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.io.network.NetworkSimulationParameters;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.FlowRpcProvider;
import io.github.panghy.javaflow.rpc.FlowRpcTransport;
import io.github.panghy.javaflow.rpc.examples.KeyValueStoreImpl;
import io.github.panghy.javaflow.rpc.examples.KeyValueStoreInterface;
import io.github.panghy.javaflow.rpc.util.Pair;
import io.github.panghy.javaflow.test.AbstractFlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the simulated RPC transport.
 * These tests verify that RPC operations work correctly in simulation mode.
 */
public class RpcSimulationTest extends AbstractFlowTest {

  private SimulatedFlowRpcTransport transport;
  private KeyValueStoreImpl keyValueStore;
  private KeyValueStoreInterface localInterface;
  private KeyValueStoreInterface remoteInterface;
  
  @BeforeEach
  void setUp() {
    // Enable simulation mode
    FlowRpcProvider.setSimulationMode(true);
    
    // Create a transport with custom parameters
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setConnectDelay(0.01)  // 10ms connection delay
        .setSendDelay(0.005)    // 5ms send delay
        .setSendBytesPerSecond(1_000_000); // 1MB/s throughput
    
    transport = new SimulatedFlowRpcTransport(params);
    
    // Override the default transport
    FlowRpcProvider.setDefaultTransport(transport);
    
    // Create and register a key-value store service
    keyValueStore = new KeyValueStoreImpl();
    localInterface = keyValueStore.getInterface();
    localInterface.registerRemote(new EndpointId("key-value-store"));
    
    // Get a reference to the remote interface (this will be a proxy)
    remoteInterface = FlowRpcTransport.getInstance()
        .getEndpoint(new EndpointId("key-value-store"), KeyValueStoreInterface.class);
  }
  
  @AfterEach
  void tearDown() throws Exception {
    // Reset simulation mode
    FlowRpcProvider.setSimulationMode(false);
    
    // Close the transport
    if (transport != null) {
      Flow.await(transport.close());
    }
  }
  
  /**
   * Tests that a basic request-response RPC call works in simulation.
   */
  @Test
  void testBasicRpcCall() throws Exception {
    // Create a request
    KeyValueStoreInterface.GetRequest request = new KeyValueStoreInterface.GetRequest("test-key");
    
    // Create a promise for the response
    FlowFuture<Optional<ByteBuffer>> future = new FlowFuture<>();
    FlowPromise<Optional<ByteBuffer>> promise = future.getPromise();
    
    // Send the request via the local interface (simulating an incoming RPC)
    localInterface.get.send(new Pair<>(request, promise));
    
    // Test that the request was received and processed
    pumpUntilReady(future);
    
    // The key doesn't exist yet, so the result should be empty
    Optional<ByteBuffer> result = future.getNow();
    assertNotNull(result);
    assertFalse(result.isPresent());
    
    // Now set a value
    ByteBuffer value = ByteBuffer.wrap("test-value".getBytes(StandardCharsets.UTF_8));
    localInterface.set.send(new KeyValueStoreInterface.SetRequest("test-key", value));
    
    // Pump to process the set request
    pump();
    
    // Try to get the value again
    FlowFuture<Optional<ByteBuffer>> future2 = new FlowFuture<>();
    FlowPromise<Optional<ByteBuffer>> promise2 = future2.getPromise();
    localInterface.get.send(new Pair<>(request, promise2));
    
    // Pump until the result is available
    pumpUntilReady(future2);
    
    // Now the result should be present
    Optional<ByteBuffer> result2 = future2.getNow();
    assertTrue(result2.isPresent());
    
    // Convert the result to a string and verify it's correct
    byte[] bytes = new byte[result2.get().remaining()];
    result2.get().get(bytes);
    String resultStr = new String(bytes, StandardCharsets.UTF_8);
    assertEquals("test-value", resultStr);
  }
  
  /**
   * Helper method to pump the scheduler until a future is ready.
   *
   * @param future The future to wait for
   */
  private void pumpUntilReady(FlowFuture<?> future) {
    pumpUntilDone(future);
  }
}