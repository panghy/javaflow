package io.github.panghy.javaflow.rpc.serialization;

import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.RemotePromiseTracker;
import io.github.panghy.javaflow.rpc.error.RpcSerializationException;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Serializer for FlowPromise objects in the JavaFlow RPC framework.
 * This serializer doesn't actually serialize the promise itself, but rather
 * registers it with the RemotePromiseTracker and serializes its ID.
 * 
 * <p>Promises in the RPC framework are not serialized directly. Instead,
 * they are registered with the RemotePromiseTracker and a unique ID is generated
 * for each promise. This ID is then serialized and sent over the network. On the
 * receiving side, a local promise is created and mapped to the ID.</p>
 * 
 * <p>When the original promise is completed, the system sends a message to
 * complete the remote promise with the same result. This allows promises to
 * be effectively used across network boundaries.</p>
 * 
 * <p>This serializer requires access to a RemotePromiseTracker and the
 * destination endpoint ID, which are typically provided by the RPC transport
 * layer when serializing/deserializing messages.</p>
 * 
 * @param <T> The type of value the promise will deliver
 */
public class PromiseSerializer<T> implements Serializer<FlowPromise<T>> {

  private final RemotePromiseTracker promiseTracker;
  private final EndpointId destination;
  private final Class<T> resultType;
  
  /**
   * Creates a new PromiseSerializer.
   *
   * @param promiseTracker The RemotePromiseTracker to register promises with
   * @param destination    The destination endpoint
   * @param resultType     The expected result type
   */
  public PromiseSerializer(RemotePromiseTracker promiseTracker, EndpointId destination, 
                           Class<T> resultType) {
    this.promiseTracker = promiseTracker;
    this.destination = destination;
    this.resultType = resultType;
  }
  
  @Override
  public ByteBuffer serialize(FlowPromise<T> promise) {
    if (promise == null) {
      return ByteBuffer.allocate(0);
    }
    
    // Register the promise with the RemotePromiseTracker
    UUID promiseId = promiseTracker.registerOutgoingPromise(promise, destination, resultType);
    
    // Serialize the promise ID
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.putLong(promiseId.getMostSignificantBits());
    buffer.putLong(promiseId.getLeastSignificantBits());
    buffer.flip();
    
    return buffer;
  }
  
  @Override
  public FlowPromise<T> deserialize(ByteBuffer buffer, Class<? extends FlowPromise<T>> expectedType) {
    if (buffer == null || buffer.remaining() < 16) {
      throw new RpcSerializationException(FlowPromise.class, "Buffer too small to contain a promise ID");
    }
    
    // Extract the promise ID from the buffer
    long mostSigBits = buffer.getLong();
    long leastSigBits = buffer.getLong();
    UUID promiseId = new UUID(mostSigBits, leastSigBits);
    
    // Create a local promise for the remote promise
    return promiseTracker.createLocalPromiseForRemote(promiseId, destination, resultType);
  }
}