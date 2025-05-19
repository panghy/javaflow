package io.github.panghy.javaflow.rpc.stream;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages streams that cross network boundaries in the JavaFlow RPC framework.
 * This class tracks both incoming streams (from remote endpoints) and outgoing
 * streams (to remote endpoints), and handles the forwarding of values between them.
 * 
 * <p>The StreamManager is responsible for:</p>
 * <ul>
 *   <li>Creating and tracking RemotePromiseStream instances</li>
 *   <li>Mapping stream IDs to local PromiseStream instances</li>
 *   <li>Forwarding stream values and close events across the network</li>
 * </ul>
 * 
 * <p>This component is used by the RPC transport to implement the streaming
 * capabilities of the RPC framework.</p>
 */
public class StreamManager {

  // Maps stream IDs to local PromiseStream instances (for incoming streams)
  private final Map<UUID, PromiseStream<?>> incomingStreams = new ConcurrentHashMap<>();
  
  // Maps stream IDs to RemotePromiseStream instances (for outgoing streams)
  private final Map<UUID, RemotePromiseStream<?>> outgoingStreams = new ConcurrentHashMap<>();
  
  // The RPC message sender function (to be injected by the transport implementation)
  private StreamMessageSender messageSender;
  
  /**
   * Interface for sending stream messages over the network.
   * This is implemented by the transport layer and injected into the StreamManager.
   */
  public interface StreamMessageSender {
    /**
     * Sends a message to a specific endpoint.
     *
     * @param destination The destination endpoint
     * @param type        The message type
     * @param streamId    The stream ID
     * @param payload     The message payload
     * @return A future that completes when the message is sent
     */
    FlowFuture<Void> sendMessage(EndpointId destination, RpcMessageHeader.MessageType type,
                                UUID streamId, Object payload);
  }
  
  /**
   * Sets the message sender for this stream manager.
   *
   * @param messageSender The message sender
   */
  public void setMessageSender(StreamMessageSender messageSender) {
    this.messageSender = messageSender;
  }
  
  /**
   * Creates a new remote promise stream that will send values to a remote endpoint.
   *
   * @param <T>         The type of value in the stream
   * @param destination The destination endpoint
   * @param valueType   The type of values in the stream
   * @return A new RemotePromiseStream
   */
  public <T> RemotePromiseStream<T> createRemoteStream(EndpointId destination, Class<T> valueType) {
    UUID streamId = UUID.randomUUID();
    RemotePromiseStream<T> stream = new RemotePromiseStream<>(streamId, destination, valueType, this);
    outgoingStreams.put(streamId, stream);
    
    // Set up cleanup when the stream is closed
    stream.getFutureStream().onClose().whenComplete((v, ex) -> outgoingStreams.remove(streamId));
    
    return stream;
  }
  
  /**
   * Registers a local promise stream to receive values from a remote endpoint.
   *
   * @param <T>     The type of value in the stream
   * @param streamId The ID of the remote stream
   * @param stream  The local stream to receive values
   */
  public <T> void registerIncomingStream(UUID streamId, PromiseStream<T> stream) {
    incomingStreams.put(streamId, stream);
    
    // Set up cleanup when the stream is closed
    stream.getFutureStream().onClose().whenComplete((v, ex) -> incomingStreams.remove(streamId));
  }
  
  /**
   * Sends a value to a remote stream.
   *
   * @param <T>        The type of value
   * @param destination The destination endpoint
   * @param streamId   The stream ID
   * @param value      The value to send
   */
  public <T> void sendToStream(EndpointId destination, UUID streamId, T value) {
    if (messageSender != null) {
      messageSender.sendMessage(destination, RpcMessageHeader.MessageType.STREAM_VALUE, streamId, value);
    }
  }
  
  /**
   * Closes a remote stream.
   *
   * @param destination The destination endpoint
   * @param streamId   The stream ID
   */
  public void closeStream(EndpointId destination, UUID streamId) {
    if (messageSender != null) {
      messageSender.sendMessage(destination, RpcMessageHeader.MessageType.STREAM_CLOSE, streamId, null);
    }
    outgoingStreams.remove(streamId);
  }
  
  /**
   * Closes a remote stream with an exception.
   *
   * @param destination The destination endpoint
   * @param streamId   The stream ID
   * @param exception  The exception to close with
   */
  public void closeStreamExceptionally(EndpointId destination, UUID streamId, Throwable exception) {
    if (messageSender != null) {
      messageSender.sendMessage(destination, RpcMessageHeader.MessageType.STREAM_CLOSE, streamId, exception);
    }
    outgoingStreams.remove(streamId);
  }
  
  /**
   * Handles an incoming stream value message.
   *
   * @param <T>      The type of value
   * @param streamId The stream ID
   * @param value    The value
   * @return true if the stream was found and the value was delivered, false otherwise
   */
  @SuppressWarnings("unchecked")
  public <T> boolean handleStreamValue(UUID streamId, T value) {
    PromiseStream<?> stream = incomingStreams.get(streamId);
    if (stream != null) {
      try {
        ((PromiseStream<T>) stream).send(value);
        return true;
      } catch (ClassCastException e) {
        // Type mismatch, close the stream
        incomingStreams.remove(streamId);
        stream.closeExceptionally(
            new ClassCastException("Stream value type mismatch: " + value.getClass().getName()));
        return false;
      }
    }
    return false;
  }
  
  /**
   * Handles an incoming stream close message.
   *
   * @param streamId  The stream ID
   * @param exception The exception, or null for normal close
   * @return true if the stream was found and closed, false otherwise
   */
  public boolean handleStreamClose(UUID streamId, Throwable exception) {
    PromiseStream<?> stream = incomingStreams.remove(streamId);
    if (stream != null) {
      if (exception != null) {
        stream.closeExceptionally(exception);
      } else {
        stream.close();
      }
      return true;
    }
    return false;
  }
  
  /**
   * Clears all tracked streams.
   * This should be called when shutting down the transport.
   * All streams will be closed with an exception.
   */
  public void clear() {
    // Close all incoming streams with an exception
    for (PromiseStream<?> stream : incomingStreams.values()) {
      stream.closeExceptionally(
          new IllegalStateException("RPC transport was shut down"));
    }
    incomingStreams.clear();
    
    // Clear outgoing streams
    outgoingStreams.clear();
  }
}