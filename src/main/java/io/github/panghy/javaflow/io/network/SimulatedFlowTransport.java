package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.simulation.FlowRandom;
import io.github.panghy.javaflow.simulation.SimulationConfiguration;
import io.github.panghy.javaflow.simulation.SimulationContext;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.panghy.javaflow.io.FlowFutureUtil.delayThenRun;

/**
 * A simulated implementation of FlowTransport for testing purposes.
 * This implementation simulates network behavior in memory with configurable
 * delays, errors, and other parameters.
 * 
 * <p>SimulatedFlowTransport provides a fully in-memory implementation of the
 * FlowTransport interface, designed specifically for testing and simulation.
 * It allows deterministic testing of network code without requiring actual
 * network hardware, making it perfect for unit testing and integration testing
 * of distributed systems.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Complete in-memory simulation of a network topology</li>
 *   <li>Configurable connection delays and error rates</li>
 *   <li>Support for simulating network partitions</li>
 *   <li>Deterministic execution compatible with Flow's simulation mode</li>
 *   <li>Realistic network behavior including latency based on message size</li>
 * </ul>
 * 
 * <p>The simulated network consists of "nodes" representing network endpoints,
 * with connections between them. Each node can have both incoming and outgoing
 * connections. The simulation models network topology with endpoints, allowing
 * for partitioning between specific endpoints.</p>
 * 
 * <p>This transport is automatically selected by {@link FlowTransport#getDefault()}
 * when JavaFlow is running in simulation mode. It creates pairs of {@link SimulatedFlowConnection}
 * instances that communicate with each other in memory.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create custom simulation parameters
 * NetworkSimulationParameters params = new NetworkSimulationParameters()
 *     .setConnectDelay(0.1)               // 100ms connection delay
 *     .setSendErrorProbability(0.05);     // 5% chance of send errors
 *     
 * // Create a transport with these parameters
 * SimulatedFlowTransport transport = new SimulatedFlowTransport(params);
 * 
 * // Create a network partition between two endpoints
 * transport.createPartition(endpoint1, endpoint2);
 * 
 * // Later, heal the partition
 * transport.healPartition(endpoint1, endpoint2);
 * }</pre>
 * 
 * <p>The behavior of the simulation is controlled by the {@link NetworkSimulationParameters}
 * object provided at construction time. Default parameters simulate a reasonably fast
 * and reliable network.</p>
 * 
 * @see FlowTransport
 * @see SimulatedFlowConnection
 * @see NetworkSimulationParameters
 */
public class SimulatedFlowTransport implements FlowTransport {

  // Maps endpoint strings to nodes
  private final Map<Endpoint, SimulatedNode> nodes = new ConcurrentHashMap<>();
  
  // Maps disconnected node pairs (used for network partitioning)
  private final Set<String> disconnectedPairs = ConcurrentHashMap.newKeySet();
  
  private final NetworkSimulationParameters params;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final FlowPromise<Void> closePromise;

  /**
   * Creates a new simulated transport with default parameters.
   */
  public SimulatedFlowTransport() {
    this(new NetworkSimulationParameters());
  }

  /**
   * Creates a new simulated transport with the specified parameters.
   *
   * @param params The simulation parameters to control behavior
   */
  public SimulatedFlowTransport(NetworkSimulationParameters params) {
    this.params = params;
    
    FlowFuture<Void> closeFuture = new FlowFuture<>();
    this.closePromise = closeFuture.getPromise();
  }
  
  /**
   * Creates a new simulated transport using parameters from the current SimulationConfiguration.
   * This factory method integrates with the simulation framework to automatically
   * configure network behavior based on the current simulation settings.
   *
   * @return A new simulated transport configured from SimulationConfiguration
   */
  public static SimulatedFlowTransport fromSimulationConfig() {
    SimulationConfiguration config = SimulationContext.currentConfiguration();
    NetworkSimulationParameters params = new NetworkSimulationParameters();
    
    if (config != null) {
      // Apply network-related configuration
      params.setConnectDelay(config.getNetworkConnectDelay());
      params.setSendDelay(config.getNetworkSendDelay());
      params.setReceiveDelay(config.getNetworkReceiveDelay());
      params.setSendBytesPerSecond(config.getNetworkBytesPerSecond());
      params.setReceiveBytesPerSecond(config.getNetworkBytesPerSecond());
      
      // Apply error probabilities
      params.setConnectErrorProbability(config.getNetworkErrorProbability());
      params.setSendErrorProbability(config.getNetworkErrorProbability());
      params.setReceiveErrorProbability(config.getNetworkErrorProbability());
      params.setDisconnectProbability(config.getNetworkDisconnectProbability());
      
      // Apply packet-level fault injection
      params.setPacketLossProbability(config.getPacketLossProbability());
      params.setPacketReorderProbability(config.getPacketReorderProbability());
    }
    
    return new SimulatedFlowTransport(params);
  }

  @Override
  public FlowFuture<FlowConnection> connect(Endpoint endpoint) {
    if (closed.get()) {
      return FlowFuture.failed(new IOException("Transport is closed"));
    }

    // Check for injected errors
    if (params.getConnectErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getConnectErrorProbability()) {
      return FlowFuture.failed(new IOException("Simulated connection error"));
    }

    // Get the target node (create if doesn't exist)
    SimulatedNode targetNode = getOrCreateNode(endpoint);

    // Get the local node (assign a random local endpoint)
    Endpoint localEndpoint = new Endpoint("127.0.0.1", 10000 + FlowRandom.current().nextInt(55536));
    SimulatedNode localNode = getOrCreateNode(localEndpoint);

    // Check if the connection is partitioned
    String pairKey = makePairKey(localEndpoint, endpoint);
    if (disconnectedPairs.contains(pairKey)) {
      return FlowFuture.failed(new IOException("Network partitioned - connection refused"));
    }

    // Create the connection
    SimulatedFlowConnection localEnd = new SimulatedFlowConnection(localEndpoint, endpoint, params);
    SimulatedFlowConnection remoteEnd = new SimulatedFlowConnection(endpoint, localEndpoint, params);
    
    // Link the two ends
    localEnd.setPeer(remoteEnd);
    remoteEnd.setPeer(localEnd);
    
    // Simulate connection delay
    double delay = params.getConnectDelay();
    
    // Complete after delay
    return delayThenRun(delay, () -> {
      // Register the connection with the nodes
      localNode.addOutgoingConnection(endpoint, localEnd);
      targetNode.addIncomingConnection(localEndpoint, remoteEnd);
      
      return localEnd;
    });
  }

  @Override
  public FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint) {
    if (closed.get()) {
      PromiseStream<FlowConnection> errorStream = new PromiseStream<>();
      errorStream.closeExceptionally(new IOException("Transport is closed"));
      return errorStream.getFutureStream();
    }

    // Get or create the node
    SimulatedNode node = getOrCreateNode(localEndpoint);
    
    // Return the incoming connection stream
    return node.getIncomingConnections();
  }
  
  @Override
  public ConnectionListener listenOnAvailablePort(LocalEndpoint requestedEndpoint) {
    if (closed.get()) {
      PromiseStream<FlowConnection> errorStream = new PromiseStream<>();
      errorStream.closeExceptionally(new IOException("Transport is closed"));
      return new ConnectionListener(errorStream.getFutureStream(), requestedEndpoint);
    }
    
    // In simulation, we can just use a random port number without conflicts
    // If the requested port is 0, generate a random port between 10000 and 65535
    LocalEndpoint actualEndpoint;
    if (requestedEndpoint.getPort() == 0) {
      int randomPort = 10000 + FlowRandom.current().nextInt(55536);
      actualEndpoint = LocalEndpoint.create(
          requestedEndpoint.getHost(), randomPort);
    } else {
      actualEndpoint = requestedEndpoint;
    }
    
    // Get or create the node for this endpoint
    SimulatedNode node = getOrCreateNode(actualEndpoint);
    
    // Return the connection listener with the assigned port
    return new ConnectionListener(node.getIncomingConnections(), actualEndpoint);
  }

  @Override
  public FlowFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      try {
        // Close all nodes (which will close their connections)
        for (SimulatedNode node : nodes.values()) {
          node.close();
        }
        nodes.clear();
        disconnectedPairs.clear();
        
        // Complete the close promise
        closePromise.complete(null);
      } catch (Exception e) {
        closePromise.completeExceptionally(e);
      }
    }
    
    return closePromise.getFuture();
  }

  /**
   * Creates a network partition between two endpoints, causing all connection attempts
   * and communication between them to fail.
   *
   * @param endpoint1 The first endpoint
   * @param endpoint2 The second endpoint
   */
  public void createPartition(Endpoint endpoint1, Endpoint endpoint2) {
    disconnectedPairs.add(makePairKey(endpoint1, endpoint2));
    disconnectedPairs.add(makePairKey(endpoint2, endpoint1));
    
    // Close any existing connections between these endpoints
    SimulatedNode node1 = nodes.get(endpoint1);
    if (node1 != null) {
      node1.disconnectFrom(endpoint2);
    }
    
    SimulatedNode node2 = nodes.get(endpoint2);
    if (node2 != null) {
      node2.disconnectFrom(endpoint1);
    }
  }

  /**
   * Heals a network partition between two endpoints, allowing connections and
   * communication to resume.
   *
   * @param endpoint1 The first endpoint
   * @param endpoint2 The second endpoint
   */
  public void healPartition(Endpoint endpoint1, Endpoint endpoint2) {
    disconnectedPairs.remove(makePairKey(endpoint1, endpoint2));
    disconnectedPairs.remove(makePairKey(endpoint2, endpoint1));
  }

  /**
   * Gets the simulation parameters being used by this transport.
   *
   * @return The simulation parameters
   */
  public NetworkSimulationParameters getParameters() {
    return params;
  }

  /**
   * Creates a pair key for tracking disconnected pairs.
   *
   * @param endpoint1 The first endpoint
   * @param endpoint2 The second endpoint
   * @return A string key for the pair
   */
  private String makePairKey(Endpoint endpoint1, Endpoint endpoint2) {
    return endpoint1.toString() + "->" + endpoint2.toString();
  }

  /**
   * Gets or creates a simulated node for the given endpoint.
   *
   * @param endpoint The endpoint
   * @return The simulated node
   */
  private SimulatedNode getOrCreateNode(Endpoint endpoint) {
    return nodes.computeIfAbsent(endpoint, SimulatedNode::new);
  }

  /**
   * Represents a node (endpoint) in the simulated network.
   */
  private static class SimulatedNode {
    private final PromiseStream<FlowConnection> incomingConnectionStream = new PromiseStream<>();
    private final Map<Endpoint, SimulatedFlowConnection> outgoingConnections = new ConcurrentHashMap<>();
    private final Map<Endpoint, SimulatedFlowConnection> incomingConnections = new ConcurrentHashMap<>();

    SimulatedNode(Endpoint endpoint) {
      // Endpoint is only used for construction, not stored
    }

    /**
     * Adds an outgoing connection from this node.
     *
     * @param remoteEndpoint The remote endpoint
     * @param connection     The connection
     */
    void addOutgoingConnection(Endpoint remoteEndpoint, SimulatedFlowConnection connection) {
      outgoingConnections.put(remoteEndpoint, connection);
      
      // Set up cleanup when the connection is closed
      connection.closeFuture().whenComplete((v, ex) -> outgoingConnections.remove(remoteEndpoint));
    }

    /**
     * Adds an incoming connection to this node.
     *
     * @param remoteEndpoint The remote endpoint
     * @param connection     The connection
     */
    void addIncomingConnection(Endpoint remoteEndpoint, SimulatedFlowConnection connection) {
      incomingConnections.put(remoteEndpoint, connection);
      
      // Set up cleanup when the connection is closed
      connection.closeFuture().whenComplete((v, ex) -> incomingConnections.remove(remoteEndpoint));
      
      // Send to the incoming connection stream
      incomingConnectionStream.send(connection);
    }

    /**
     * Disconnects this node from the specified endpoint.
     *
     * @param otherEndpoint The endpoint to disconnect from
     */
    void disconnectFrom(Endpoint otherEndpoint) {
      // Close outgoing connection if it exists
      SimulatedFlowConnection outgoing = outgoingConnections.get(otherEndpoint);
      if (outgoing != null) {
        outgoing.close();
      }
      
      // Close incoming connection if it exists
      SimulatedFlowConnection incoming = incomingConnections.get(otherEndpoint);
      if (incoming != null) {
        incoming.close();
      }
    }

    /**
     * Gets the stream of incoming connections to this node.
     *
     * @return The incoming connection stream
     */
    FlowStream<FlowConnection> getIncomingConnections() {
      return incomingConnectionStream.getFutureStream();
    }

    /**
     * Closes this node and all its connections.
     */
    void close() {
      // Close the incoming connection stream
      incomingConnectionStream.close();
      
      // Close all outgoing connections
      for (SimulatedFlowConnection conn : outgoingConnections.values()) {
        conn.close();
      }
      outgoingConnections.clear();
      
      // Close all incoming connections
      for (SimulatedFlowConnection conn : incomingConnections.values()) {
        conn.close();
      }
      incomingConnections.clear();
    }
  }
}