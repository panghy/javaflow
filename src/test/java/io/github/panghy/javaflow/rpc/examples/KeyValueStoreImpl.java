package io.github.panghy.javaflow.rpc.examples;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.util.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example implementation of a key-value store service.
 * This class demonstrates how to implement a service based on the
 * PromiseStream interface design pattern.
 * 
 * <p>The implementation:</p>
 * <ul>
 *   <li>Maintains a map of key-value pairs</li>
 *   <li>Handles get, set, and watch requests via actors</li>
 *   <li>Manages watchers and sends notifications when values change</li>
 *   <li>Provides a convenient method to get the service interface</li>
 * </ul>
 * 
 * <p>This is intended as a demonstration of the RPC framework patterns,
 * not as a production-ready key-value store.</p>
 */
public class KeyValueStoreImpl {

  private final KeyValueStoreInterface iface;
  private final Map<String, ByteBuffer> store = new ConcurrentHashMap<>();
  private final Map<String, List<PromiseStream<KeyValueStoreInterface.KeyChangeEvent>>> watchers = 
      new ConcurrentHashMap<>();
  
  /**
   * Creates a new key-value store implementation and starts actors to handle requests.
   */
  public KeyValueStoreImpl() {
    // Create the interface
    this.iface = new KeyValueStoreInterface();
    
    // Start actors to handle the different request types
    handleGetRequests();
    handleSetRequests();
    handleWatchRequests();
  }
  
  /**
   * Starts an actor to handle get requests.
   *
   * @return A future that completes when the actor finishes (should never happen)
   */
  private FlowFuture<Void> handleGetRequests() {
    return Flow.startActor(() -> {
      try {
        while (true) {
          // Wait for the next get request
          Pair<KeyValueStoreInterface.GetRequest, FlowPromise<Optional<ByteBuffer>>> request = 
              Flow.await(iface.get.getFutureStream().nextAsync());
          
          String key = request.first().getKey();
          FlowPromise<Optional<ByteBuffer>> promise = request.second();
          
          try {
            // Get the value from the store
            ByteBuffer value = store.get(key);
            
            // Complete the promise with the result
            promise.complete(Optional.ofNullable(value).map(ByteBuffer::duplicate));
          } catch (Exception e) {
            // If there's an error, complete the promise exceptionally
            promise.completeExceptionally(e);
          }
        }
      } catch (Exception e) {
        System.err.println("Error in get request handler: " + e.getMessage());
      }
      return null;
    });
  }
  
  /**
   * Starts an actor to handle set requests.
   *
   * @return A future that completes when the actor finishes (should never happen)
   */
  private FlowFuture<Void> handleSetRequests() {
    return Flow.startActor(() -> {
      try {
        while (true) {
          // Wait for the next set request
          KeyValueStoreInterface.SetRequest request = 
              Flow.await(iface.set.getFutureStream().nextAsync());
          
          String key = request.getKey();
          ByteBuffer value = request.getValue().duplicate();
          
          // Store the value
          ByteBuffer oldValue = store.put(key, value);
          
          // Notify watchers if there are any
          notifyWatchers(key, value, false);
        }
      } catch (Exception e) {
        System.err.println("Error in set request handler: " + e.getMessage());
      }
      return null;
    });
  }
  
  /**
   * Starts an actor to handle watch requests.
   *
   * @return A future that completes when the actor finishes (should never happen)
   */
  private FlowFuture<Void> handleWatchRequests() {
    return Flow.startActor(() -> {
      try {
        while (true) {
          // Wait for the next watch request
          Pair<KeyValueStoreInterface.WatchRequest, PromiseStream<KeyValueStoreInterface.KeyChangeEvent>> request = 
              Flow.await(iface.watch.getFutureStream().nextAsync());
          
          String keyPrefix = request.first().getKeyPrefix();
          PromiseStream<KeyValueStoreInterface.KeyChangeEvent> events = request.second();
          
          // Register the watcher
          watchers.computeIfAbsent(keyPrefix, k -> new ArrayList<>()).add(events);
          
          // Handle stream closure
          events.getFutureStream().onClose().whenComplete((v, ex) -> {
            removeWatcher(keyPrefix, events);
          });
          
          // Send initial values for all matching keys
          for (Map.Entry<String, ByteBuffer> entry : store.entrySet()) {
            if (entry.getKey().startsWith(keyPrefix)) {
              events.send(new KeyValueStoreInterface.KeyChangeEvent(
                  entry.getKey(), entry.getValue().duplicate(), false));
            }
          }
        }
      } catch (Exception e) {
        System.err.println("Error in watch request handler: " + e.getMessage());
      }
      return null;
    });
  }
  
  /**
   * Notifies watchers about a change to a key.
   *
   * @param key     The key that changed
   * @param value   The new value
   * @param deleted Whether the key was deleted
   */
  private void notifyWatchers(String key, ByteBuffer value, boolean deleted) {
    // Find all matching watchers
    for (Map.Entry<String, List<PromiseStream<KeyValueStoreInterface.KeyChangeEvent>>> entry : 
        watchers.entrySet()) {
      String prefix = entry.getKey();
      if (key.startsWith(prefix)) {
        // Notify all watchers for this prefix
        List<PromiseStream<KeyValueStoreInterface.KeyChangeEvent>> prefixWatchers = entry.getValue();
        KeyValueStoreInterface.KeyChangeEvent event = 
            new KeyValueStoreInterface.KeyChangeEvent(key, value.duplicate(), deleted);
        
        // Create a copy to avoid concurrent modification
        List<PromiseStream<KeyValueStoreInterface.KeyChangeEvent>> watchersCopy = 
            new ArrayList<>(prefixWatchers);
        
        for (PromiseStream<KeyValueStoreInterface.KeyChangeEvent> watcher : watchersCopy) {
          try {
            watcher.send(event);
          } catch (Exception e) {
            // If sending fails, remove the watcher
            removeWatcher(prefix, watcher);
          }
        }
      }
    }
  }
  
  /**
   * Removes a watcher from the list of watchers for a prefix.
   *
   * @param prefix  The key prefix
   * @param watcher The watcher to remove
   */
  private void removeWatcher(String prefix, PromiseStream<KeyValueStoreInterface.KeyChangeEvent> watcher) {
    List<PromiseStream<KeyValueStoreInterface.KeyChangeEvent>> prefixWatchers = watchers.get(prefix);
    if (prefixWatchers != null) {
      prefixWatchers.remove(watcher);
      if (prefixWatchers.isEmpty()) {
        watchers.remove(prefix);
      }
    }
  }
  
  /**
   * Gets the service interface for this implementation.
   *
   * @return The service interface
   */
  public KeyValueStoreInterface getInterface() {
    return iface;
  }
  
  /**
   * Registers this service with the RPC transport.
   *
   * @param endpointId The endpoint ID to register with
   */
  public void registerRemote(EndpointId endpointId) {
    iface.registerRemote(endpointId);
  }
}