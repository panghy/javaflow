package io.github.panghy.javaflow.io.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SimulatedFlowConnection class.
 * This test covers the connection's behavior in isolation,
 * while SimulatedFlowTransportTest tests the connection in context.
 */
public class SimulatedFlowConnectionTest {

  private SimulatedFlowConnection connection1;
  private SimulatedFlowConnection connection2;
  private NetworkSimulationParameters params;
  private Endpoint endpoint1;
  private Endpoint endpoint2;

  @BeforeEach
  void setUp() {
    params = new NetworkSimulationParameters();
    endpoint1 = new Endpoint("localhost", 8080);
    endpoint2 = new Endpoint("localhost", 8081);
    connection1 = new SimulatedFlowConnection(endpoint1, endpoint2, params);
    connection2 = new SimulatedFlowConnection(endpoint2, endpoint1, params);
    
    // Link the two connections
    connection1.setPeer(connection2);
    connection2.setPeer(connection1);
  }

  @Test
  void testGetEndpoints() {
    assertEquals(endpoint1, connection1.getLocalEndpoint());
    assertEquals(endpoint2, connection1.getRemoteEndpoint());
    assertEquals(endpoint2, connection2.getLocalEndpoint());
    assertEquals(endpoint1, connection2.getRemoteEndpoint());
  }

  @Test
  void testIsOpen() {
    assertTrue(connection1.isOpen());
    assertTrue(connection2.isOpen());
  }

  @Test
  void testClose() throws Exception {
    // Close connection1
    connection1.close();
    
    // Both connections should be closed since they are linked
    assertFalse(connection1.isOpen());
    assertFalse(connection2.isOpen());
    
    // CloseFuture should be completed
    assertTrue(connection1.closeFuture().isDone());
    assertTrue(connection2.closeFuture().isDone());
  }

  @Test
  void testCloseFuture() {
    // Shouldn't be completed initially
    assertFalse(connection1.closeFuture().isDone());
    
    // After closing, should be completed
    connection1.close();
    assertTrue(connection1.closeFuture().isDone());
  }
}