package io.github.panghy.javaflow.rpc.stream;

import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.EndpointId;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 * A PromiseStream implementation that forwards values to a remote endpoint.
 * This class is used to implement PromiseStream functionality over the network,
 * allowing values sent to the stream to be delivered to a remote endpoint.
 * 
 * <p>RemotePromiseStream acts as a proxy for a PromiseStream on a remote node.
 * When values are sent to this stream, they are serialized and transmitted to
 * the remote endpoint, where they are delivered to the actual PromiseStream.</p>
 * 
 * <p>This enables the RPC framework to support the streaming pattern seamlessly
 * across network boundaries, including proper handling of generic type information.</p>
 * 
 * @param <T> The type of value flowing through this stream
 */
public class RemotePromiseStream<T> extends PromiseStream<T> {

  private final UUID streamId;
  private final EndpointId destination;
  private final Class<T> valueType;
  private final StreamManager streamManager;
  
  // Complete type information including generics
  private Type valueTypeInfo;
  
  /**
   * Creates a new RemotePromiseStream.
   *
   * @param streamId      The unique ID of this stream
   * @param destination   The endpoint to send values to
   * @param valueType     The type of values in this stream
   * @param streamManager The manager for remote streams
   */
  public RemotePromiseStream(UUID streamId, EndpointId destination, 
                           Class<T> valueType, StreamManager streamManager) {
    this.streamId = streamId;
    this.destination = destination;
    this.valueType = valueType;
    this.streamManager = streamManager;
    this.valueTypeInfo = valueType; // Default to the raw type
  }
  
  @Override
  public boolean send(T value) {
    // Forward the value to the remote endpoint
    streamManager.sendToStream(destination, streamId, value);
    return true;
  }
  
  @Override
  public boolean close() {
    // Notify the remote endpoint that this stream is closed
    streamManager.closeStream(destination, streamId);
    
    // Also close locally
    return super.close();
  }
  
  @Override
  public boolean closeExceptionally(Throwable exception) {
    // Notify the remote endpoint that this stream is closed with an exception
    streamManager.closeStreamExceptionally(destination, streamId, exception);
    
    // Also close locally
    return super.closeExceptionally(exception);
  }
  
  /**
   * Gets the unique ID of this stream.
   *
   * @return The stream ID
   */
  public UUID getStreamId() {
    return streamId;
  }
  
  /**
   * Gets the destination endpoint for this stream.
   *
   * @return The destination endpoint
   */
  public EndpointId getDestination() {
    return destination;
  }
  
  /**
   * Gets the raw type of values in this stream.
   *
   * @return The value type
   */
  public Class<T> getValueType() {
    return valueType;
  }
  
  /**
   * Gets the complete type information for values in this stream,
   * including generic type parameters.
   *
   * @return The complete type information
   */
  public Type getValueTypeInfo() {
    return valueTypeInfo;
  }
  
  /**
   * Sets the complete type information for values in this stream.
   *
   * @param valueTypeInfo The complete type information
   */
  public void setValueTypeInfo(Type valueTypeInfo) {
    this.valueTypeInfo = valueTypeInfo;
  }
}