package io.github.panghy.javaflow.rpc.examples;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.RpcServiceInterface;
import io.github.panghy.javaflow.rpc.util.Pair;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Example RPC service interface for a key-value store.
 * This class demonstrates the PromiseStream-based interface design pattern
 * used in the JavaFlow RPC framework.
 * 
 * <p>The interface provides three types of operations:</p>
 * <ul>
 *   <li>get: A request-response operation to retrieve a value by key</li>
 *   <li>set: A one-way notification to set a key-value pair</li>
 *   <li>watch: A streaming operation to watch for changes to keys matching a prefix</li>
 * </ul>
 * 
 * <p>Each operation is represented by a PromiseStream that accepts the appropriate
 * message type. For operations that return a result, the message includes both
 * the request and a promise that will be fulfilled with the result.</p>
 * 
 * <p>The class also provides convenience methods with more familiar signatures
 * that hide the PromiseStream details from clients.</p>
 */
public class KeyValueStoreInterface implements RpcServiceInterface {

  /**
   * Request to get a value by key.
   */
  public static class GetRequest {
    private final String key;
    
    public GetRequest(String key) {
      this.key = key;
    }
    
    public String getKey() {
      return key;
    }
  }
  
  /**
   * Request to set a key-value pair.
   */
  public static class SetRequest {
    private final String key;
    private final ByteBuffer value;
    
    public SetRequest(String key, ByteBuffer value) {
      this.key = key;
      this.value = value;
    }
    
    public String getKey() {
      return key;
    }
    
    public ByteBuffer getValue() {
      return value;
    }
  }
  
  /**
   * Request to watch for changes to keys matching a prefix.
   */
  public static class WatchRequest {
    private final String keyPrefix;
    
    public WatchRequest(String keyPrefix) {
      this.keyPrefix = keyPrefix;
    }
    
    public String getKeyPrefix() {
      return keyPrefix;
    }
  }
  
  /**
   * Event representing a change to a key.
   */
  public static class KeyChangeEvent {
    private final String key;
    private final ByteBuffer value;
    private final boolean deleted;
    
    public KeyChangeEvent(String key, ByteBuffer value, boolean deleted) {
      this.key = key;
      this.value = value;
      this.deleted = deleted;
    }
    
    public String getKey() {
      return key;
    }
    
    public ByteBuffer getValue() {
      return value;
    }
    
    public boolean isDeleted() {
      return deleted;
    }
  }
  
  /**
   * Stream for get requests (request-response pattern).
   * Each message contains a GetRequest and a promise that will be fulfilled
   * with the result (or a failure).
   */
  public final PromiseStream<Pair<GetRequest, FlowPromise<Optional<ByteBuffer>>>> get;
  
  /**
   * Stream for set requests (one-way notification pattern).
   * Each message contains a SetRequest with no response expected.
   */
  public final PromiseStream<SetRequest> set;
  
  /**
   * Stream for watch requests (streaming pattern).
   * Each message contains a WatchRequest and a stream that will receive
   * change events for keys matching the prefix.
   */
  public final PromiseStream<Pair<WatchRequest, PromiseStream<KeyChangeEvent>>> watch;
  
  /**
   * Creates a new KeyValueStoreInterface.
   */
  public KeyValueStoreInterface() {
    this.get = new PromiseStream<>();
    this.set = new PromiseStream<>();
    this.watch = new PromiseStream<>();
  }
  
  /**
   * Convenience method for the get operation.
   * This wraps the PromiseStream pattern in a more familiar Future-based API.
   *
   * @param key The key to get
   * @return A future that completes with the value, or empty if not found
   */
  public FlowFuture<Optional<ByteBuffer>> getAsync(String key) {
    FlowFuture<Optional<ByteBuffer>> future = new FlowFuture<>();
    FlowPromise<Optional<ByteBuffer>> promise = future.getPromise();
    get.send(new Pair<>(new GetRequest(key), promise));
    return future;
  }
  
  /**
   * Convenience method for the set operation.
   * This wraps the PromiseStream pattern in a more familiar API.
   *
   * @param key   The key to set
   * @param value The value to set
   */
  public void setAsync(String key, ByteBuffer value) {
    set.send(new SetRequest(key, value));
  }
  
  /**
   * Convenience method for the watch operation.
   * This wraps the PromiseStream pattern in a more familiar Stream-based API.
   *
   * @param keyPrefix The key prefix to watch
   * @return A stream of change events for keys matching the prefix
   */
  public FutureStream<KeyChangeEvent> watchAsync(String keyPrefix) {
    PromiseStream<KeyChangeEvent> events = new PromiseStream<>();
    watch.send(new Pair<>(new WatchRequest(keyPrefix), events));
    return events.getFutureStream();
  }
}