package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks promises that cross network boundaries in the JavaFlow RPC framework.
 * This class maintains mappings between promise IDs and the actual promises,
 * enabling results to be delivered to the correct endpoints when promises are fulfilled.
 *
 * <p>The RemotePromiseTracker is a crucial component of the RPC system that makes
 * it possible to pass promises over the network. It maintains two types of mappings:</p>
 * <ul>
 *   <li>Local promises that were sent to remote endpoints</li>
 *   <li>Remote promises that were received from other endpoints</li>
 * </ul>
 *
 * <p>When a promise is sent to a remote endpoint, it's registered with a unique ID.
 * When that promise is later fulfilled, the result can be sent back to the remote
 * endpoint using the ID. Similarly, when a message arrives with a remote promise ID,
 * a local promise is created and registered so that when a completion message
 * arrives for that ID, the right promise can be fulfilled.</p>
 */
public class RemotePromiseTracker {

  /**
   * Information about a remote promise.
   */
  record RemotePromiseInfo(EndpointId destination, Class<?> resultType) {
  }

  // Maps promise IDs to information about remote endpoints waiting for results
  private final Map<UUID, RemotePromiseInfo> outgoingPromises = new ConcurrentHashMap<>();

  // Maps incoming promise IDs to local promises
  private final Map<UUID, FlowPromise<?>> incomingPromises = new ConcurrentHashMap<>();

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
  public <T> UUID registerOutgoingPromise(FlowPromise<T> promise, EndpointId destination, Class<T> resultType) {
    UUID promiseId = UUID.randomUUID();
    outgoingPromises.put(promiseId, new RemotePromiseInfo(destination, resultType));

    // When the future completes, send the result to the destination
    promise.getFuture().whenComplete((result, error) -> {
      RemotePromiseInfo info = outgoingPromises.remove(promiseId);
      if (info != null) {
        if (error != null) {
          sendErrorToEndpoint(info.destination, promiseId, error);
        } else {
          sendResultToEndpoint(info.destination, promiseId, result);
        }
      }
    });

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
  public <T> FlowPromise<T> createLocalPromiseForRemote(UUID remotePromiseId, EndpointId source, Class<T> resultType) {
    FlowFuture<T> future = new FlowFuture<>();
    FlowPromise<T> localPromise = future.getPromise();

    // Store in the incoming promises map
    incomingPromises.put(remotePromiseId, localPromise);

    // Add a cleanup handler
    future.whenComplete((result, error) -> {
      // If the local future is cancelled, notify the source
      if (future.isCancelled()) {
        sendCancellationToEndpoint(source, remotePromiseId);
        incomingPromises.remove(remotePromiseId);
      }
    });

    return localPromise;
  }

  /**
   * Creates a promise for an incoming RPC call.
   *
   * @param <T>        The type of value the promise will deliver
   * @param promiseId  The ID of the promise
   * @param endpointId The endpoint ID that owns this promise
   * @return A promise that will deliver its result over the connection when completed
   */
  public <T> FlowPromise<T> createIncomingPromise(UUID promiseId,
                                                  EndpointId endpointId) {
    FlowFuture<T> future = new FlowFuture<>();
    FlowPromise<T> promise = future.getPromise();

    // When the promise is completed, send the result to the source
    future.whenComplete((result, error) -> {
      if (error != null) {
        sendErrorToEndpoint(endpointId, promiseId, error);
      } else {
        sendResultToEndpoint(endpointId, promiseId, result);
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
    FlowPromise<?> promise = incomingPromises.remove(remotePromiseId);
    if (promise != null) {
      try {
        ((FlowPromise<T>) promise).complete(result);
        return true;
      } catch (ClassCastException e) {
        // Wrong type, complete with exception
        promise.completeExceptionally(
            new ClassCastException("Remote result type doesn't match expected type"));
        return false;
      }
    }
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
    FlowPromise<?> promise = incomingPromises.remove(remotePromiseId);
    if (promise != null) {
      promise.completeExceptionally(error);
      return true;
    }
    return false;
  }

  /**
   * Gets information about an outgoing promise.
   *
   * @param promiseId The promise ID
   * @return The promise information, or null if no such promise exists
   */
  public RemotePromiseInfo getOutgoingPromiseInfo(UUID promiseId) {
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
   * Clears all tracked promises.
   * This should be called when shutting down the transport.
   * All pending promises will be completed exceptionally.
   */
  public void clear() {
    // Complete all incoming promises with an exception
    for (Map.Entry<UUID, FlowPromise<?>> entry : incomingPromises.entrySet()) {
      entry.getValue().completeExceptionally(
          new IllegalStateException("RPC transport was shut down"));
    }
    incomingPromises.clear();
    outgoingPromises.clear();
  }

  // These methods would be implemented to actually send the results
  // to the appropriate endpoints using the transport layer

  <T> void sendResultToEndpoint(EndpointId destination, UUID promiseId, T result) {
    // This would use the RPC transport to send the result
    // Implementation depends on the transport layer
  }

  void sendErrorToEndpoint(EndpointId destination, UUID promiseId, Throwable error) {
    // This would use the RPC transport to send the error
    // Implementation depends on the transport layer
  }

  void sendCancellationToEndpoint(EndpointId source, UUID promiseId) {
    // This would use the RPC transport to send a cancellation notification
    // Implementation depends on the transport layer
  }
}