package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.simulation.FlowRandom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.io.FlowFutureUtil.delayThenRun;
import static io.github.panghy.javaflow.util.LoggingUtil.debug;

/**
 * A simulated implementation of FlowConnection for testing purposes.
 * This implementation simulates network behavior in memory with configurable
 * delays, errors, and other parameters.
 * 
 * <p>SimulatedFlowConnection provides a fully in-memory implementation of the
 * FlowConnection interface, allowing for deterministic testing of network code
 * without requiring actual network hardware. It simulates realistic network behavior
 * including latency, throughput limitations, and error conditions.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Realistic simulation of network latency based on message size</li>
 *   <li>Configurable throughput rates to simulate slow or fast networks</li>
 *   <li>Controllable error injection for testing error handling</li>
 *   <li>Simulation of disconnects and connection failures</li>
 *   <li>Full integration with Flow's cooperative multitasking model</li>
 * </ul>
 * 
 * <p>SimulatedFlowConnection instances always come in pairs, with each instance
 * representing one end of a connection. Data sent from one end is automatically
 * delivered to the other end's receive stream after applying the appropriate 
 * simulated delay and error conditions.</p>
 * 
 * <p>This class is typically not instantiated directly by user code.
 * Instead, it is created by {@link SimulatedFlowTransport} when establishing
 * new connections. It is exposed to user code through the {@link FlowConnection}
 * interface.</p>
 * 
 * <p>The behavior of the simulation is controlled by the {@link NetworkSimulationParameters}
 * object provided at construction time. This allows for fine-grained control over
 * every aspect of the simulated network behavior.</p>
 * 
 * @see FlowConnection
 * @see SimulatedFlowTransport
 * @see NetworkSimulationParameters
 */
public class SimulatedFlowConnection implements FlowConnection {

  private static final Logger LOGGER = Logger.getLogger(SimulatedFlowConnection.class.getName());
  
  private final Endpoint localEndpoint;
  private final Endpoint remoteEndpoint;
  private final NetworkSimulationParameters params;
  private final PromiseStream<ByteBuffer> receivePromiseStream = new PromiseStream<>();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final CompletableFuture<Void> closePromise;
  
  // Reference to the other end of this connection
  private SimulatedFlowConnection peer;

  /**
   * Creates a new SimulatedFlowConnection.
   *
   * @param localEndpoint  The local endpoint of this connection
   * @param remoteEndpoint The remote endpoint of this connection
   * @param params         The simulation parameters to control behavior
   */
  public SimulatedFlowConnection(Endpoint localEndpoint, Endpoint remoteEndpoint, NetworkSimulationParameters params) {
    this.localEndpoint = localEndpoint;
    this.remoteEndpoint = remoteEndpoint;
    this.params = params;
    
    this.closePromise = new CompletableFuture<>();
  }

  /**
   * Sets the peer connection that this connection is connected to.
   * This is used to simulate the two ends of a network connection.
   *
   * @param peer The other end of this connection
   */
  public void setPeer(SimulatedFlowConnection peer) {
    this.peer = peer;
  }

  @Override
  public CompletableFuture<Void> send(ByteBuffer data) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("Connection is closed"));
    }

    // Check for injected errors
    if (params.getSendErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getSendErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated send error"));
    }

    // Check for random disconnects
    if (params.getDisconnectProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getDisconnectProbability()) {
      close();
      return CompletableFuture.failedFuture(new IOException("Connection closed during send"));
    }

    // Check for packet loss
    if (params.getPacketLossProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getPacketLossProbability()) {
      // Simulate packet loss - pretend the send succeeded but don't deliver
      debug(LOGGER, "Simulating packet loss from " + localEndpoint + " to " + remoteEndpoint);
      double delay = params.calculateSendDelay(data.remaining());
      return delayThenRun(delay, () -> null);
    }

    // Calculate a realistic delay based on the simulation parameters
    double delay = params.calculateSendDelay(data.remaining());
    
    // Check for packet reordering
    if (params.getPacketReorderProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getPacketReorderProbability()) {
      // Add random additional delay for reordering
      double reorderDelay = FlowRandom.current().nextDouble() * params.getMaxReorderDelay();
      delay += reorderDelay;
      debug(LOGGER, "Simulating packet reordering with additional delay: " + reorderDelay + "s");
    }

    // Duplicate the buffer so we don't affect the original
    ByteBuffer toSend = data.duplicate();
    
    // Get exact size for a new buffer
    int size = toSend.remaining();
    
    // Create a copy of the data
    ByteBuffer copy = ByteBuffer.allocate(size);
    copy.put(toSend.duplicate());
    copy.flip();

    // Delay the send, then deliver to the peer
    return delayThenRun(delay, () -> {
      if (peer != null && !peer.closed.get()) {
        // Deliver to the peer's receive stream
        peer.receivePromiseStream.send(copy);
      }
      return null;
    });
  }

  @Override
  public CompletableFuture<ByteBuffer> receive(int maxBytes) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("Connection is closed"));
    }

    // Check for injected errors
    if (params.getReceiveErrorProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getReceiveErrorProbability()) {
      return CompletableFuture.failedFuture(new IOException("Simulated receive error"));
    }

    // Check for random disconnects
    if (params.getDisconnectProbability() > 0.0 &&
        FlowRandom.current().nextDouble() < params.getDisconnectProbability()) {
      close();
      return CompletableFuture.failedFuture(new IOException("Connection closed during receive"));
    }

    // Get the next item from the receive stream
    return receivePromiseStream.getFutureStream().nextAsync();
  }

  @Override
  public FlowStream<ByteBuffer> receiveStream() {
    return receivePromiseStream.getFutureStream();
  }

  @Override
  public Endpoint getLocalEndpoint() {
    return localEndpoint;
  }

  @Override
  public Endpoint getRemoteEndpoint() {
    return remoteEndpoint;
  }

  @Override
  public boolean isOpen() {
    return !closed.get();
  }

  @Override
  public CompletableFuture<Void> closeFuture() {
    return closePromise;
  }

  @Override
  public CompletableFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      // Close the receive stream
      receivePromiseStream.close();
      
      // Complete the close promise
      closePromise.complete(null);
      
      // Close the peer if it exists
      if (peer != null && !peer.closed.get()) {
        peer.close();
      }
    }
    
    return closePromise;
  }
}