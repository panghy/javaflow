package io.github.panghy.javaflow.rpc.simulation;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.io.network.NetworkSimulationParameters;
import io.github.panghy.javaflow.rpc.EndpointId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link SimulatedFlowRpcTransport} class.
 */
public class SimulatedFlowRpcTransportTest {
  
  private SimulatedFlowRpcTransport transport;
  private NetworkSimulationParameters params;
  
  // Define a simple test interface for RPC
  private interface TestService {
    FlowFuture<String> hello(String name);
  }
  
  // Implementation of the test interface
  private static class TestServiceImpl implements TestService {
    @Override
    public FlowFuture<String> hello(String name) {
      return FlowFuture.completed("Hello, " + name + "!");
    }
  }
  
  @BeforeEach
  public void setUp() {
    params = new NetworkSimulationParameters()
        .setSendDelay(0.1)
        .setSendBytesPerSecond(1000)
        .setConnectErrorProbability(0.0)
        .setDisconnectProbability(0.0);
    
    transport = new SimulatedFlowRpcTransport(params);
  }
  
  @Test
  public void testConstructorWithDefaultParameters() {
    SimulatedFlowRpcTransport defaultTransport = new SimulatedFlowRpcTransport();
    assertNotNull(defaultTransport);
    assertNotNull(defaultTransport.getParameters());
  }
  
  @Test
  public void testConstructorWithCustomParameters() {
    assertNotNull(transport);
    assertEquals(params, transport.getParameters());
    assertEquals(0.1, transport.getParameters().getSendDelay());
    assertEquals(1000, transport.getParameters().getSendBytesPerSecond());
    assertEquals(0.0, transport.getParameters().getConnectErrorProbability());
  }
  
  @Test
  public void testRegisterEndpoint() {
    EndpointId id = new EndpointId("test-endpoint");
    TestService service = new TestServiceImpl();
    
    transport.registerEndpoint(id, service);
    
    // Verify we can retrieve the registered endpoint
    TestService retrievedService = transport.getEndpoint(id, TestService.class);
    assertNotNull(retrievedService);
    
    // The retrieved service should be the same as the original since it's a local endpoint
    assertEquals(service, retrievedService);
  }
  
  @Test
  public void testGetLocalEndpoint() {
    EndpointId id = new EndpointId("local-endpoint");
    TestService service = new TestServiceImpl();
    
    transport.registerEndpoint(id, service);
    
    // Getting a local endpoint should return the original object
    Object retrievedService = transport.getEndpoint(id, TestService.class);
    assertNotNull(retrievedService);
    assertEquals(service, retrievedService);
  }
  
  @Test
  public void testGetRemoteEndpoint() {
    EndpointId id = new EndpointId("remote-endpoint");
    
    // Getting a remote endpoint creates a proxy
    TestService proxy = transport.getEndpoint(id, TestService.class);
    assertNotNull(proxy);
    
    // This is a dynamic proxy so toString/equals/hashCode should work
    assertNotNull(proxy.toString());
    assertFalse(proxy.equals(null));
    assertTrue(proxy.hashCode() != 0);
  }
  
  @Test
  public void testClose() throws Exception {
    Flow.startActor(() -> {
      try {
        // Register some endpoints
        EndpointId id1 = new EndpointId("endpoint1");
        EndpointId id2 = new EndpointId("endpoint2");
        
        TestService service = new TestServiceImpl();
        transport.registerEndpoint(id1, service);
        transport.registerEndpoint(id2, service);
        
        // Close the transport
        FlowFuture<Void> closeFuture = transport.close();
        Flow.await(closeFuture);
        
        // After close, operations should throw exceptions
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
          transport.registerEndpoint(new EndpointId("new-endpoint"), service);
        });
        assertEquals("Transport is closed", exception.getMessage());
        
        exception = assertThrows(IllegalStateException.class, () -> {
          transport.getEndpoint(id1, TestService.class);
        });
        assertEquals("Transport is closed", exception.getMessage());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).toCompletableFuture().get();
  }
  
  @Test
  public void testCreateAndHealPartition() {
    EndpointId id1 = new EndpointId("endpoint1");
    EndpointId id2 = new EndpointId("endpoint2");
    
    TestService service = new TestServiceImpl();
    transport.registerEndpoint(id1, service);
    transport.registerEndpoint(id2, service);
    
    // Create a partition between the endpoints
    transport.createPartition(id1, id2);
    
    // Heal the partition
    transport.healPartition(id1, id2);
    
    // These operations don't return anything, so we just verify they don't throw
  }
  
  @Test
  public void testCreatePartitionWithUnknownEndpoints() {
    EndpointId knownId = new EndpointId("known");
    EndpointId unknownId = new EndpointId("unknown");
    
    TestService service = new TestServiceImpl();
    transport.registerEndpoint(knownId, service);
    
    // Creating a partition with an unknown endpoint should not throw
    transport.createPartition(knownId, unknownId);
    transport.createPartition(unknownId, unknownId);
    
    // Similarly for healing partitions
    transport.healPartition(knownId, unknownId);
  }
}