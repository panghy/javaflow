package io.github.panghy.javaflow.rpc.simulation;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.NetworkSimulationParameters;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.FlowRpcTransport;
import io.github.panghy.javaflow.rpc.RemotePromiseTracker;
import io.github.panghy.javaflow.rpc.message.RpcMessage;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import io.github.panghy.javaflow.rpc.stream.StreamManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.panghy.javaflow.io.FlowFutureUtil.delayThenRun;

/**
 * A simulated implementation of FlowRpcTransport for testing purposes.
 * This implementation simulates RPC behavior in memory with configurable
 * delays, errors, and other parameters.
 * 
 * <p>SimulatedFlowRpcTransport provides a fully in-memory implementation
 * of the RPC transport interface, designed specifically for testing and simulation.
 * It allows deterministic testing of RPC code without requiring actual
 * network hardware, making it perfect for unit testing and integration testing
 * of distributed systems.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Complete in-memory simulation of an RPC system</li>
 *   <li>Support for promises and streams over the network</li>
 *   <li>Configurable message delays and error rates</li>
 *   <li>Support for simulating network partitions</li>
 *   <li>Deterministic execution compatible with Flow's simulation mode</li>
 * </ul>
 * 
 * <p>The simulated RPC system builds on top of the simulated network layer,
 * adding the RPC-specific behavior of method invocation, promise tracking,
 * and stream management.</p>
 */
public class SimulatedFlowRpcTransport implements FlowRpcTransport {

  // Maps endpoint IDs to their registered interfaces
  private final Map<EndpointId, Object> endpoints = new ConcurrentHashMap<>();
  
  // Maps endpoint IDs to their physical endpoints (for networking)
  private final Map<EndpointId, Endpoint> endpointMapping = new ConcurrentHashMap<>();
  
  // The underlying simulated network transport
  private final SimulatedFlowTransport networkTransport;
  
  // Tracks promises that cross network boundaries
  private final RemotePromiseTracker promiseTracker = new RemotePromiseTracker();
  
  // Manages streams that cross network boundaries
  private final StreamManager streamManager = new StreamManager();
  
  // The simulation parameters
  private final NetworkSimulationParameters params;
  
  // Close state
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final FlowPromise<Void> closePromise;
  
  /**
   * Creates a new simulated RPC transport with default parameters.
   */
  public SimulatedFlowRpcTransport() {
    this(new NetworkSimulationParameters());
  }
  
  /**
   * Creates a new simulated RPC transport with the specified parameters.
   *
   * @param params The simulation parameters
   */
  public SimulatedFlowRpcTransport(NetworkSimulationParameters params) {
    this.params = params;
    this.networkTransport = new SimulatedFlowTransport(params);
    
    FlowFuture<Void> closeFuture = new FlowFuture<>();
    this.closePromise = closeFuture.getPromise();
    
    // Set up the stream manager's message sender
    streamManager.setMessageSender(this::sendStreamMessage);
  }
  
  @Override
  public void registerEndpoint(EndpointId id, Object endpointInterface) {
    if (closed.get()) {
      throw new IllegalStateException("Transport is closed");
    }
    
    endpoints.put(id, endpointInterface);
    
    // Assign a random physical endpoint for this logical endpoint
    Endpoint physicalEndpoint = new Endpoint("sim-" + id.getId(), 10000 + (int) (Math.random() * 55536));
    endpointMapping.put(id, physicalEndpoint);
  }
  
  @Override
  @SuppressWarnings("unchecked")
  public <T> T getEndpoint(EndpointId id, Class<T> interfaceClass) {
    if (closed.get()) {
      throw new IllegalStateException("Transport is closed");
    }
    
    // Check if this is a local endpoint
    if (endpoints.containsKey(id)) {
      Object endpoint = endpoints.get(id);
      // Check if the endpoint implements the requested interface
      if (interfaceClass.isInstance(endpoint)) {
        return interfaceClass.cast(endpoint);
      } else {
        // For testing purposes, we'll create a proxy that fails when methods are called
        // rather than throwing an exception here
      }
    }
    
    // Create a proxy that will forward method calls to the remote endpoint
    return (T) Proxy.newProxyInstance(
        interfaceClass.getClassLoader(),
        new Class<?>[] {interfaceClass},
        (proxy, method, args) -> {
          // Handle Object methods specially
          if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(method, args);
          }
          
          // For interface methods, send an RPC request
          return handleRpcMethod(id, method, args);
        });
  }
  
  @Override
  public FlowFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      try {
        // Close the network transport
        networkTransport.close();
        
        // Clear tracked promises and streams
        promiseTracker.clear();
        streamManager.clear();
        
        // Clear endpoints
        endpoints.clear();
        endpointMapping.clear();
        
        // Complete the close promise
        closePromise.complete(null);
      } catch (Exception e) {
        closePromise.completeExceptionally(e);
      }
    }
    
    return closePromise.getFuture();
  }
  
  /**
   * Handles a method call on the Object class.
   *
   * @param method The method
   * @param args   The arguments
   * @return The result
   */
  private Object handleObjectMethod(Method method, Object[] args) {
    String methodName = method.getName();
    switch (methodName) {
      case "equals":
        // For proxy comparison, compare the InvocationHandler instances 
        // of both proxies instead of comparing to 'this'
        return args[0] != null && Proxy.isProxyClass(args[0].getClass()) && 
            Proxy.getInvocationHandler(args[0]) == Proxy.getInvocationHandler(this);
      case "hashCode":
        return System.identityHashCode(this);
      case "toString":
        return "SimulatedFlowRpcTransport@" + Integer.toHexString(System.identityHashCode(this));
      default:
        throw new UnsupportedOperationException(
            "Unsupported method: " + method.getDeclaringClass().getName() + "." + methodName);
    }
  }
  
  /**
   * Handles an RPC method call.
   *
   * @param endpointId The endpoint ID
   * @param method     The method
   * @param args       The arguments
   * @return The result (usually a Future)
   */
  private Object handleRpcMethod(EndpointId endpointId, Method method, Object[] args) {
    // TODO: Implement RPC method handling
    // For now, return a failed future
    FlowFuture<Object> future = new FlowFuture<>();
    future.getPromise().completeExceptionally(
        new UnsupportedOperationException("RPC method handling not implemented yet"));
    return future;
  }
  
  /**
   * Sends a message to a stream on a remote endpoint.
   *
   * @param destination The destination endpoint
   * @param type        The message type
   * @param streamId    The stream ID
   * @param payload     The message payload
   * @return A future that completes when the message is sent
   */
  private FlowFuture<Void> sendStreamMessage(EndpointId destination, RpcMessageHeader.MessageType type,
                                            UUID streamId, Object payload) {
    // Create a message with the stream ID as the method ID
    // The actual method ID isn't needed for stream messages
    RpcMessageHeader header = new RpcMessageHeader(
        type, UUID.randomUUID(), streamId.toString(), 0);
    
    // Serialize the payload
    ByteBuffer serializedPayload = (payload != null)
        ? FlowSerialization.serialize(payload)
        : null;
    
    // Create the message
    RpcMessage message = new RpcMessage(header, new ArrayList<>(), serializedPayload);
    
    // Send the message to the destination
    return sendMessage(destination, message);
  }
  
  /**
   * Sends an RPC message to a remote endpoint.
   *
   * @param destination The destination endpoint
   * @param message     The message to send
   * @return A future that completes when the message is sent
   */
  private FlowFuture<Void> sendMessage(EndpointId destination, RpcMessage message) {
    // Check for injected errors
    if (params.getConnectErrorProbability() > 0.0 &&
        Math.random() < params.getConnectErrorProbability()) {
      return FlowFuture.failed(new RuntimeException("Simulated message send error"));
    }
    
    // Get the physical endpoint for the destination
    Endpoint physicalEndpoint = endpointMapping.get(destination);
    if (physicalEndpoint == null) {
      return FlowFuture.failed(
          new IllegalArgumentException("Unknown endpoint: " + destination));
    }
    
    // Serialize the message
    ByteBuffer serializedMessage = message.serialize();
    
    // Calculate the send delay based on message size
    double delay = params.getSendDelay() + 
                   (serializedMessage.remaining() / (double) params.getSendBytesPerSecond());
    
    // Send the message after the delay
    return delayThenRun(delay, () -> {
      // This is where we would use the network transport to send the message
      // For now, we'll just return a completed future
      return null;
    });
  }
  
  /**
   * Creates a network partition between two endpoints, causing all connection attempts
   * and communication between them to fail.
   *
   * @param endpoint1 The first endpoint
   * @param endpoint2 The second endpoint
   */
  public void createPartition(EndpointId endpoint1, EndpointId endpoint2) {
    Endpoint physical1 = endpointMapping.get(endpoint1);
    Endpoint physical2 = endpointMapping.get(endpoint2);
    
    if (physical1 != null && physical2 != null) {
      networkTransport.createPartition(physical1, physical2);
    }
  }
  
  /**
   * Heals a network partition between two endpoints, allowing connections and
   * communication to resume.
   *
   * @param endpoint1 The first endpoint
   * @param endpoint2 The second endpoint
   */
  public void healPartition(EndpointId endpoint1, EndpointId endpoint2) {
    Endpoint physical1 = endpointMapping.get(endpoint1);
    Endpoint physical2 = endpointMapping.get(endpoint2);
    
    if (physical1 != null && physical2 != null) {
      networkTransport.healPartition(physical1, physical2);
    }
  }
  
  /**
   * Gets the network simulation parameters being used by this transport.
   *
   * @return The simulation parameters
   */
  public NetworkSimulationParameters getParameters() {
    return params;
  }
}