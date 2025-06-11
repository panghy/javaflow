package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.rpc.serialization.TypeDescription;
import io.github.panghy.javaflow.rpc.stream.RemotePromiseStream;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.Flow.startActor;
import static io.github.panghy.javaflow.util.LoggingUtil.debug;

/**
 * Tracks promises and streams that cross network boundaries in the JavaFlow RPC framework.
 * This class maintains mappings between promise/stream IDs and the actual promises/streams,
 * enabling results to be delivered to the correct endpoints when promises are fulfilled
 * or stream values are sent.
 *
 * <p>The RemotePromiseTracker is a crucial component of the RPC system that makes
 * it possible to pass promises and streams over the network. It maintains four types of mappings:</p>
 * <ul>
 *   <li>Local promises that were sent to remote endpoints</li>
 *   <li>Remote promises that were received from other endpoints</li>
 *   <li>Local streams that were sent to remote endpoints</li>
 *   <li>Remote streams that were received from other endpoints</li>
 * </ul>
 *
 * <p>When a promise is sent to a remote endpoint, it's registered with a unique ID.
 * When that promise is later fulfilled, the result can be sent back to the remote
 * endpoint using the ID. Similarly, when a message arrives with a remote promise ID,
 * a local promise is created and registered so that when a completion message
 * arrives for that ID, the right promise can be fulfilled.</p>
 *
 * <p>For streams, when a PromiseStream is sent to a remote endpoint, a RemotePromiseStream
 * is created that forwards values across the network. When a stream ID is received from
 * a remote endpoint, a local PromiseStream is created to receive the values.</p>
 */
public class RemotePromiseTracker {

  private static final Logger LOGGER = Logger.getLogger(RemotePromiseTracker.class.getName());

  /**
   * Callback interface for sending promise completion and stream messages.
   */
  public interface MessageSender {
    /**
     * Sends a result to an endpoint when a promise is completed.
     */
    <T> void sendResult(Endpoint destination, UUID promiseId, T result);

    /**
     * Sends an error to an endpoint when a promise fails.
     */
    void sendError(Endpoint destination, UUID promiseId, Throwable error);

    /**
     * Sends a cancellation notification to an endpoint.
     */
    void sendCancellation(Endpoint source, UUID promiseId);

    /**
     * Sends a stream value to an endpoint.
     */
    <T> void sendStreamValue(Endpoint destination, UUID streamId, T value);

    /**
     * Sends a stream close notification to an endpoint.
     */
    void sendStreamClose(Endpoint destination, UUID streamId);

    /**
     * Sends a stream error notification to an endpoint.
     */
    void sendStreamError(Endpoint destination, UUID streamId, Throwable error);
  }

  /**
   * Information about a remote promise.
   */
  record RemotePromiseInfo(Endpoint destination, TypeDescription resultType, FlowPromise<?> promise) {
  }

  /**
   * Information about a local promise that was created for a remote promise.
   */
  record LocalPromiseInfo(FlowPromise<?> promise, Endpoint source,
                          TypeDescription resultType) {
  }

  /**
   * Information about a remote stream.
   */
  record RemoteStreamInfo(Endpoint destination, TypeDescription elementType) {
  }

  /**
   * Information about a local stream that was created for a remote stream.
   */
  record LocalStreamInfo(PromiseStream<?> stream, Endpoint source, TypeDescription elementType) {
  }

  // Callback for sending messages
  private final MessageSender messageSender;

  // Maps promise IDs to information about remote endpoints waiting for results
  private final Map<UUID, RemotePromiseInfo> outgoingPromises = new ConcurrentHashMap<>();

  // Maps incoming promise IDs to local promise information (promise, source, and type)
  private final Map<UUID, LocalPromiseInfo> incomingPromises = new ConcurrentHashMap<>();

  // Maps stream IDs to information about remote endpoints waiting for stream data
  private final Map<UUID, RemoteStreamInfo> outgoingStreams = new ConcurrentHashMap<>();

  // Maps incoming stream IDs to local stream information (stream, source, and type)
  private final Map<UUID, LocalStreamInfo> incomingStreams = new ConcurrentHashMap<>();

  /**
   * Creates a new RemotePromiseTracker.
   *
   * @param messageSender The callback for sending messages to endpoints
   */
  public RemotePromiseTracker(MessageSender messageSender) {
    this.messageSender = messageSender;
  }

  /**
   * Creates a new RemotePromiseTracker without a message sender.
   * This constructor is for testing purposes only.
   */
  public RemotePromiseTracker() {
    this.messageSender = null;
  }

  /**
   * Registers a promise that was sent to a remote endpoint.
   * When this promise is fulfilled, the result should be sent to the destination.
   *
   * @param <T>         The type of value the promise will deliver
   * @param promise     The promise to register
   * @param destination The endpoint that will receive the result
   * @param resultType  The expected type of the result
   * @return A unique ID for the promise that can be sent to the remote endpoint
   */
  public <T> UUID registerOutgoingPromise(FlowPromise<T> promise, Endpoint destination,
                                          TypeDescription resultType) {
    UUID promiseId = UUID.randomUUID();
    debug(LOGGER, "Registering outgoing promise " + promiseId + " to " + destination);
    // Store both the promise info AND the promise itself
    outgoingPromises.put(promiseId, new RemotePromiseInfo(destination, resultType, promise));

    // Note: We don't set up a completion handler here anymore.
    // For promises sent as arguments, completion will come from the remote side.
    // For promises returned as results, they complete locally and we send the result.

    return promiseId;
  }

  /**
   * Creates a local promise for a remote promise ID.
   * When a result arrives for this ID, the local promise will be fulfilled.
   *
   * @param <T>             The type of value the promise will deliver
   * @param remotePromiseId The ID of the remote promise
   * @param source          The endpoint that sent the promise
   * @param resultType      The expected type of the result
   * @return A local promise that will be fulfilled when the remote promise is completed
   */
  public <T> FlowPromise<T> createLocalPromiseForRemote(UUID remotePromiseId, Endpoint source,
                                                        TypeDescription resultType) {
    return createLocalPromiseForRemote(remotePromiseId, source, resultType, true);
  }

  /**
   * Creates a local promise for a remote promise ID.
   *
   * @param <T>             The type of value the promise will deliver
   * @param remotePromiseId The ID of the remote promise
   * @param source          The endpoint that sent the promise
   * @param resultType      The expected type of the result
   * @param sendResultBack  Whether to send results back to source when completed
   * @return A local promise that will be fulfilled when the remote promise is completed
   */
  public <T> FlowPromise<T> createLocalPromiseForRemote(UUID remotePromiseId, Endpoint source,
                                                        TypeDescription resultType, boolean sendResultBack) {
    FlowFuture<T> future = new FlowFuture<>();
    FlowPromise<T> localPromise = future.getPromise();

    // Store the promise info (promise, source, and type) in the map
    incomingPromises.put(remotePromiseId, new LocalPromiseInfo(localPromise, source, resultType));

    // Add a completion handler to send the result back to the source if requested
    if (sendResultBack) {
      future.whenComplete((result, error) -> {
        // Remove from tracking map
        incomingPromises.remove(remotePromiseId);

        if (future.isCancelled()) {
          // If the local future is cancelled, notify the source
          debug(LOGGER, "Local promise " + remotePromiseId + " was cancelled, notifying source: " + source);
          sendCancellationToEndpoint(source, remotePromiseId);
        } else if (error != null) {
          // Send error back to source
          debug(LOGGER, "Local promise " + remotePromiseId + " completed with error, sending to source: " + source);
          sendErrorToEndpoint(source, remotePromiseId, error);
        } else {
          // Send result back to source
          debug(LOGGER, "Local promise " + remotePromiseId + " completed with result: " + result
                        + ", sending to source: " + source);
          sendResultToEndpoint(source, remotePromiseId, result);
        }
      });
    } else {
      // Still need to clean up when completed
      future.whenComplete((result, error) -> {
        // Remove from tracking map
        incomingPromises.remove(remotePromiseId);
      });
    }

    return localPromise;
  }

  /**
   * Creates a promise for an incoming RPC call.
   *
   * @param <T>       The type of value the promise will deliver
   * @param promiseId The ID of the promise
   * @param endpoint  The endpoint that owns this promise
   * @return A promise that will deliver its result over the connection when completed
   */
  public <T> FlowPromise<T> createIncomingPromise(UUID promiseId,
                                                  Endpoint endpoint) {
    FlowFuture<T> future = new FlowFuture<>();
    FlowPromise<T> promise = future.getPromise();

    // When the promise is completed, send the result to the source
    future.whenComplete((result, error) -> {
      if (error != null) {
        sendErrorToEndpoint(endpoint, promiseId, error);
      } else {
        sendResultToEndpoint(endpoint, promiseId, result);
      }
    });

    return promise;
  }

  /**
   * Completes a local promise for a remote ID with a result.
   *
   * @param <T>             The type of value the promise will deliver
   * @param remotePromiseId The ID of the remote promise
   * @param result          The result to complete the promise with
   * @return true if a promise was found and completed, false otherwise
   */
  @SuppressWarnings("unchecked")
  public <T> boolean completeLocalPromise(UUID remotePromiseId, T result) {
    // First check incoming promises (promises created for incoming RPC calls)
    LocalPromiseInfo promiseInfo = incomingPromises.remove(remotePromiseId);
    if (promiseInfo != null) {
      try {
        debug(LOGGER, "Completing incoming promise " + remotePromiseId + " with result: " + result);
        // Convert the result to the expected type if needed
        Object convertedResult = convertPromiseResult(result, promiseInfo.resultType());
        ((FlowPromise<Object>) promiseInfo.promise()).complete(convertedResult);
        return true;
      } catch (Exception e) {
        // Wrong type or conversion error, complete with exception
        promiseInfo.promise().completeExceptionally(e);
        return false;
      }
    }

    // Also check outgoing promises (promises sent as arguments)
    RemotePromiseInfo outgoingInfo = outgoingPromises.get(remotePromiseId);
    if (outgoingInfo != null && outgoingInfo.promise() != null) {
      try {
        debug(LOGGER, "Completing outgoing promise " + remotePromiseId + " with result: " + result);
        // Convert the result to the expected type if needed
        Object convertedResult = convertPromiseResult(result, outgoingInfo.resultType());
        ((FlowPromise<Object>) outgoingInfo.promise()).complete(convertedResult);
        // Remove from map after completion - this is a promise sent as an argument
        // that has now been completed by the remote side
        outgoingPromises.remove(remotePromiseId);
        return true;
      } catch (Exception e) {
        // Wrong type or conversion error, complete with exception
        outgoingInfo.promise().completeExceptionally(e);
        // Still remove from map even on error
        outgoingPromises.remove(remotePromiseId);
        return false;
      }
    }

    debug(LOGGER, "No promise found for ID " + remotePromiseId);
    return false;
  }

  /**
   * Completes a local promise for a remote ID with an exception.
   *
   * @param remotePromiseId The ID of the remote promise
   * @param error           The error to complete the promise with
   * @return true if a promise was found and completed, false otherwise
   */
  public boolean completeLocalPromiseExceptionally(UUID remotePromiseId, Throwable error) {
    // First check incoming promises
    LocalPromiseInfo promiseInfo = incomingPromises.remove(remotePromiseId);
    if (promiseInfo != null) {
      debug(LOGGER, "Completing incoming promise " + remotePromiseId + " exceptionally");
      promiseInfo.promise().completeExceptionally(error);
      return true;
    }

    // Also check outgoing promises
    RemotePromiseInfo outgoingInfo = outgoingPromises.get(remotePromiseId);
    if (outgoingInfo != null && outgoingInfo.promise() != null) {
      debug(LOGGER, "Completing outgoing promise " + remotePromiseId + " exceptionally");
      outgoingInfo.promise().completeExceptionally(error);
      // Remove from map after completion - this is a promise sent as an argument
      // that has now been completed exceptionally by the remote side
      outgoingPromises.remove(remotePromiseId);
      return true;
    }

    debug(LOGGER, "No promise found for ID " + remotePromiseId + " to complete exceptionally");
    return false;
  }

  /**
   * Converts a promise result to the expected type, handling numeric conversions.
   *
   * @param result          The result to convert
   * @param typeDescription The expected type description
   * @return The converted result
   */
  private Object convertPromiseResult(Object result, TypeDescription typeDescription) {
    if (result == null || typeDescription == null) {
      return result;
    }

    // Get the target type
    Class<?> targetType;
    try {
      Type type = typeDescription.toType();
      if (type instanceof Class<?>) {
        targetType = (Class<?>) type;
      } else if (type instanceof ParameterizedType) {
        targetType = (Class<?>) ((ParameterizedType) type).getRawType();
      } else {
        return result; // Can't determine target type
      }
    } catch (ClassNotFoundException e) {
      return result; // Can't load target type
    }

    // Handle numeric conversions since Tuple stores all integers as Long
    if (result instanceof Long longValue) {
      if (targetType == Integer.class || targetType == int.class) {
        // Check if the value fits in an Integer
        if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
          return longValue.intValue();
        } else {
          throw new IllegalArgumentException(
              "Long value " + longValue + " cannot be converted to Integer");
        }
      } else if (targetType == Short.class || targetType == short.class) {
        // Check if the value fits in a Short
        if (longValue >= Short.MIN_VALUE && longValue <= Short.MAX_VALUE) {
          return longValue.shortValue();
        } else {
          throw new IllegalArgumentException(
              "Long value " + longValue + " cannot be converted to Short");
        }
      } else if (targetType == Byte.class || targetType == byte.class) {
        // Check if the value fits in a Byte
        if (longValue >= Byte.MIN_VALUE && longValue <= Byte.MAX_VALUE) {
          return longValue.byteValue();
        } else {
          throw new IllegalArgumentException(
              "Long value " + longValue + " cannot be converted to Byte");
        }
      }
    }

    // No conversion needed
    return result;
  }

  /**
   * Gets information about an outgoing promise.
   *
   * @param promiseId The promise ID
   * @return The promise information, or null if no such promise exists
   */
  RemotePromiseInfo getOutgoingPromiseInfo(UUID promiseId) {
    return outgoingPromises.get(promiseId);
  }

  /**
   * Checks if there's an incoming promise with the given ID.
   *
   * @param promiseId The promise ID
   * @return true if the ID corresponds to an incoming promise, false otherwise
   */
  public boolean hasIncomingPromise(UUID promiseId) {
    return incomingPromises.containsKey(promiseId);
  }

  /**
   * Gets the type description for an incoming promise.
   *
   * @param promiseId The promise ID
   * @return The type description, or null if no such promise exists
   */
  public TypeDescription getIncomingPromiseType(UUID promiseId) {
    LocalPromiseInfo info = incomingPromises.get(promiseId);
    return info != null ? info.resultType() : null;
  }

  /**
   * Registers a stream that was sent to a remote endpoint.
   * When values are sent to this stream, they should be forwarded to the destination.
   *
   * @param <T>         The type of elements in the stream
   * @param stream      The stream to register
   * @param destination The endpoint that will receive stream values
   * @param elementType The expected type of stream elements
   * @return A unique ID for the stream that can be sent to the remote endpoint
   */
  public <T> UUID registerOutgoingStream(FutureStream<T> stream, Endpoint destination, TypeDescription elementType) {
    UUID streamId = UUID.randomUUID();
    return registerOutgoingStreamWithId(streamId, stream, destination, elementType);
  }

  /**
   * Registers a stream that was sent to a remote endpoint with a specific ID.
   * When values are sent to this stream, they should be forwarded to the destination.
   *
   * @param <T>          The type of elements in the stream
   * @param streamId     The ID to use for the stream
   * @param futureStream The stream to register
   * @param destination  The endpoint that will receive stream values
   * @param elementType  The expected type of stream elements
   * @return The stream ID that was passed in
   */
  public <T> UUID registerOutgoingStreamWithId(UUID streamId,
                                               FutureStream<T> futureStream,
                                               Endpoint destination,
                                               TypeDescription elementType) {
    outgoingStreams.put(streamId, new RemoteStreamInfo(destination, elementType));

    // Check if stream is already closed
    boolean isAlreadyClosed = futureStream.onClose().isDone();
    debug(LOGGER, "registerOutgoingStreamWithId: streamId=" + streamId + ", isAlreadyClosed=" + isAlreadyClosed);

    // For already-closed streams, we need to handle buffered values carefully
    if (isAlreadyClosed) {
      // For already-closed streams, defer both value processing and sending to ensure
      // RESPONSE is sent before STREAM_DATA messages
      startActor(() -> {
        debug(LOGGER, "Processing buffered values for already-closed streamId=" + streamId);
        futureStream.forEach(value -> {
          RemoteStreamInfo info = outgoingStreams.get(streamId);
          if (info != null) {
            debug(LOGGER, "Sending stream value for streamId=" + streamId + ", value=" + value);
            sendStreamValueToEndpoint(info.destination, streamId, value);
          }
        });
        return null;
      });
    } else {
      // For streams that are not closed, set up normal forEach processing
      futureStream.forEach(value -> {
        RemoteStreamInfo info = outgoingStreams.get(streamId);
        if (info != null) {
          debug(LOGGER, "Sending stream value for streamId=" + streamId + ", value=" + value);
          sendStreamValueToEndpoint(info.destination, streamId, value);
        }
      });
    }

    // For already-closed streams with buffered values, we need to ensure
    // the RESPONSE is sent before STREAM_DATA messages
    if (isAlreadyClosed) {
      // Defer close notification to ensure RESPONSE is sent first
      startActor(() -> {
        debug(LOGGER, "Processing close for already-closed streamId=" + streamId);
        futureStream.onClose().whenComplete((result, error) -> {
          RemoteStreamInfo info = outgoingStreams.remove(streamId);
          if (info != null) {
            if (error != null) {
              sendStreamErrorToEndpoint(info.destination, streamId, error);
            } else {
              sendStreamCloseToEndpoint(info.destination, streamId);
            }
          }
        });
        return null;
      });
    }

    // When the stream closes, notify the destination (only if not already closed)
    if (!isAlreadyClosed) {
      futureStream.onClose().whenComplete((result, error) -> {
        // Defer the close notification to ensure buffered values are processed first
        startActor(() -> {
          RemoteStreamInfo info = outgoingStreams.remove(streamId);
          if (info != null) {
            if (error != null) {
              sendStreamErrorToEndpoint(info.destination, streamId, error);
            } else {
              sendStreamCloseToEndpoint(info.destination, streamId);
            }
          }
          return null;
        });
      });
    }

    return streamId;
  }

  /**
   * Creates a local stream for a remote stream ID.
   * When values arrive for this ID, they will be sent to the local stream.
   *
   * @param <T>            The type of elements in the stream
   * @param remoteStreamId The ID of the remote stream
   * @param source         The endpoint that sent the stream
   * @param elementType    The expected type of stream elements
   * @return A local stream that will receive values from the remote stream
   */
  public <T> PromiseStream<T> createLocalStreamForRemote(UUID remoteStreamId, Endpoint source,
                                                         TypeDescription elementType) {
    PromiseStream<T> localStream = new PromiseStream<>();

    // Store the stream info (stream, source, and type) in the map
    incomingStreams.put(remoteStreamId, new LocalStreamInfo(localStream, source, elementType));

    return localStream;
  }

  /**
   * Creates a RemotePromiseStream that forwards values to a remote endpoint.
   * This is used when a PromiseStream needs to be sent as an RPC argument.
   *
   * @param <T>         The type of elements in the stream
   * @param destination The endpoint that will receive stream values
   * @param elementType The type of stream elements
   * @return A RemotePromiseStream that forwards values to the destination
   */
  @SuppressWarnings("unchecked")
  public <T> RemotePromiseStream<T> createRemoteStream(Endpoint destination, TypeDescription elementType) {
    UUID streamId = UUID.randomUUID();
    Class<T> rawType;
    try {
      rawType = (Class<T>) elementType.toType();
    } catch (ClassNotFoundException e) {
      rawType = (Class<T>) Object.class;
    }

    RemotePromiseStream<T> remoteStream = new RemotePromiseStream<>(streamId,
        new EndpointId(destination.toString()), rawType, null);

    // Register the stream
    outgoingStreams.put(streamId, new RemoteStreamInfo(destination, elementType));

    // Set up forwarding when values are sent to the remote stream
    remoteStream.getFutureStream().forEach(value -> {
      RemoteStreamInfo info = outgoingStreams.get(streamId);
      if (info != null) {
        sendStreamValueToEndpoint(info.destination, streamId, value);
      }
    });

    // When the stream closes, notify the destination
    remoteStream.getFutureStream().onClose().whenComplete((result, error) -> {
      RemoteStreamInfo info = outgoingStreams.remove(streamId);
      if (info != null) {
        if (error != null) {
          sendStreamErrorToEndpoint(info.destination, streamId, error);
        } else {
          sendStreamCloseToEndpoint(info.destination, streamId);
        }
      }
    });

    return remoteStream;
  }

  /**
   * Sends a value to a local stream for a remote ID.
   *
   * @param <T>            The type of stream element
   * @param remoteStreamId The ID of the remote stream
   * @param value          The value to send to the stream
   * @return true if a stream was found and the value was sent, false otherwise
   */
  @SuppressWarnings("unchecked")
  public <T> boolean sendToLocalStream(UUID remoteStreamId, T value) {
    LocalStreamInfo streamInfo = incomingStreams.get(remoteStreamId);
    if (streamInfo != null) {
      try {
        return ((PromiseStream<T>) streamInfo.stream()).send(value);
      } catch (ClassCastException e) {
        // Wrong type, close the stream with exception
        streamInfo.stream().closeExceptionally(
            new ClassCastException("Remote stream value type doesn't match expected type"));
        incomingStreams.remove(remoteStreamId);
        return false;
      }
    }
    debug(LOGGER, "No local stream found for ID " + remoteStreamId);
    return false;
  }

  /**
   * Closes a local stream for a remote ID.
   *
   * @param remoteStreamId The ID of the remote stream
   * @return true if a stream was found and closed, false otherwise
   */
  public boolean closeLocalStream(UUID remoteStreamId) {
    LocalStreamInfo streamInfo = incomingStreams.remove(remoteStreamId);
    if (streamInfo != null) {
      streamInfo.stream().close();
      return true;
    }
    return false;
  }

  /**
   * Closes a local stream for a remote ID with an exception.
   *
   * @param remoteStreamId The ID of the remote stream
   * @param error          The error to close the stream with
   * @return true if a stream was found and closed, false otherwise
   */
  public boolean closeLocalStreamExceptionally(UUID remoteStreamId, Throwable error) {
    LocalStreamInfo streamInfo = incomingStreams.remove(remoteStreamId);
    if (streamInfo != null) {
      streamInfo.stream().closeExceptionally(error);
      return true;
    }
    return false;
  }

  /**
   * Gets information about an outgoing stream.
   *
   * @param streamId The stream ID
   * @return The stream information, or null if no such stream exists
   */
  RemoteStreamInfo getOutgoingStreamInfo(UUID streamId) {
    return outgoingStreams.get(streamId);
  }

  /**
   * Checks if there's an incoming stream with the given ID.
   *
   * @param streamId The stream ID
   * @return true if the ID corresponds to an incoming stream, false otherwise
   */
  public boolean hasIncomingStream(UUID streamId) {
    return incomingStreams.containsKey(streamId);
  }

  /**
   * Gets the type description for an incoming stream.
   *
   * @param streamId The stream ID
   * @return The type description, or null if no such stream exists
   */
  public TypeDescription getIncomingStreamType(UUID streamId) {
    LocalStreamInfo info = incomingStreams.get(streamId);
    return info != null ? info.elementType() : null;
  }

  /**
   * Gets the local stream information for an incoming stream.
   *
   * @param streamId The stream ID
   * @return The local stream information, or null if no such stream exists
   */
  LocalStreamInfo getIncomingStreamInfo(UUID streamId) {
    return incomingStreams.get(streamId);
  }

  /**
   * Clears all tracked promises and streams.
   * This should be called when shutting down the transport.
   * All pending promises and streams will be completed exceptionally.
   */
  public void clear() {
    // Complete all incoming promises with an exception
    for (Map.Entry<UUID, LocalPromiseInfo> entry : incomingPromises.entrySet()) {
      entry.getValue().promise().completeExceptionally(
          new IllegalStateException("RPC transport was shut down"));
    }
    incomingPromises.clear();
    outgoingPromises.clear();

    // Close all incoming streams with an exception
    for (Map.Entry<UUID, LocalStreamInfo> entry : incomingStreams.entrySet()) {
      entry.getValue().stream().closeExceptionally(
          new IllegalStateException("RPC transport was shut down"));
    }
    incomingStreams.clear();
    outgoingStreams.clear();
  }

  /**
   * Cancels all promises and streams associated with a specific endpoint.
   * This should be called when a client disconnects to ensure proper cleanup.
   * All pending promises and streams will be completed exceptionally.
   *
   * @param endpoint The endpoint whose promises and streams should be cancelled
   */
  public void cancelAllForEndpoint(Endpoint endpoint) {
    debug(LOGGER, "Cancelling all promises and streams for endpoint: " + endpoint);

    // Cancel outgoing promises for this endpoint
    outgoingPromises.entrySet().removeIf(entry -> {
      RemotePromiseInfo info = entry.getValue();
      if (info.destination().equals(endpoint)) {
        debug(LOGGER, "Cancelling outgoing promise " + entry.getKey() + " for endpoint " + endpoint);
        // For outgoing promises, we just remove them from tracking
        // The remote side will get an error when the connection drops
        return true;
      }
      return false;
    });

    // Cancel incoming promises from this endpoint
    incomingPromises.entrySet().removeIf(entry -> {
      LocalPromiseInfo info = entry.getValue();
      if (info.source().equals(endpoint)) {
        debug(LOGGER, "Cancelling incoming promise " + entry.getKey() + " from endpoint " + endpoint);
        info.promise().completeExceptionally(
            new IllegalStateException("Endpoint " + endpoint + " disconnected"));
        return true;
      }
      return false;
    });

    // Cancel outgoing streams for this endpoint
    outgoingStreams.entrySet().removeIf(entry -> {
      RemoteStreamInfo info = entry.getValue();
      if (info.destination().equals(endpoint)) {
        debug(LOGGER, "Cancelling outgoing stream " + entry.getKey() + " for endpoint " + endpoint);
        // For outgoing streams, we just remove them from tracking
        // The remote side will get an error when the connection drops
        return true;
      }
      return false;
    });

    // Cancel incoming streams from this endpoint
    incomingStreams.entrySet().removeIf(entry -> {
      LocalStreamInfo info = entry.getValue();
      if (info.source().equals(endpoint)) {
        debug(LOGGER, "Cancelling incoming stream " + entry.getKey() + " from endpoint " + endpoint);
        info.stream().closeExceptionally(
            new IllegalStateException("Endpoint " + endpoint + " disconnected"));
        return true;
      }
      return false;
    });
  }

  // These methods would be implemented to actually send the results
  // to the appropriate endpoints using the transport layer

  <T> void sendResultToEndpoint(Endpoint destination, UUID promiseId, T result) {
    if (messageSender != null) {
      debug(LOGGER, "Sending result to " + destination + " for promise " + promiseId);
      messageSender.sendResult(destination, promiseId, result);
      // Clean up the outgoing promise now that the result has been sent
      outgoingPromises.remove(promiseId);
    } else {
      debug(LOGGER, "No message sender available to send result for promise " + promiseId);
    }
  }

  void sendErrorToEndpoint(Endpoint destination, UUID promiseId, Throwable error) {
    if (messageSender != null) {
      messageSender.sendError(destination, promiseId, error);
      // Clean up the outgoing promise now that the error has been sent
      outgoingPromises.remove(promiseId);
    }
  }

  void sendCancellationToEndpoint(Endpoint source, UUID promiseId) {
    if (messageSender != null) {
      messageSender.sendCancellation(source, promiseId);
      // Clean up the promise (could be either incoming or outgoing)
      incomingPromises.remove(promiseId);
      outgoingPromises.remove(promiseId);
    }
  }

  <T> void sendStreamValueToEndpoint(Endpoint destination, UUID streamId, T value) {
    if (messageSender != null) {
      messageSender.sendStreamValue(destination, streamId, value);
    }
  }

  void sendStreamCloseToEndpoint(Endpoint destination, UUID streamId) {
    if (messageSender != null) {
      messageSender.sendStreamClose(destination, streamId);
    }
  }

  void sendStreamErrorToEndpoint(Endpoint destination, UUID streamId, Throwable error) {
    if (messageSender != null) {
      messageSender.sendStreamError(destination, streamId, error);
    }
  }
}