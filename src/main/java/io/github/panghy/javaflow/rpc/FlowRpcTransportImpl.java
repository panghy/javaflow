package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowStream;
import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.FlowTransport;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.TransportProvider;
import io.github.panghy.javaflow.rpc.error.RpcException;
import io.github.panghy.javaflow.rpc.error.RpcSerializationException;
import io.github.panghy.javaflow.rpc.message.RpcMessage;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import io.github.panghy.javaflow.rpc.serialization.TypeDescription;
import io.github.panghy.javaflow.rpc.util.RpcStreamTimeoutUtil;
import io.github.panghy.javaflow.rpc.util.RpcTimeoutUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static io.github.panghy.javaflow.util.LoggingUtil.debug;
import static io.github.panghy.javaflow.util.LoggingUtil.info;
import static io.github.panghy.javaflow.util.LoggingUtil.warn;

/**
 * Implementation of the {@link FlowRpcTransport} interface.
 * This class provides the main implementation for RPC operations in JavaFlow,
 * handling both local and remote service invocations.
 *
 * <p>This implementation manages:</p>
 * <ul>
 *   <li>Service registration through an EndpointResolver</li>
 *   <li>Creation of RPC stubs for remote service invocation</li>
 *   <li>Creation of local stubs for serialized local invocation</li>
 *   <li>Connection management through ConnectionManager</li>
 *   <li>Promise and stream tracking for cross-network communication</li>
 * </ul>
 *
 * <p>The transport automatically switches between local and remote invocation
 * based on the endpoint configuration, providing location transparency.</p>
 */
public class FlowRpcTransportImpl implements FlowRpcTransport, RemotePromiseTracker.MessageSender {

  private static final Logger LOGGER = Logger.getLogger(FlowRpcTransportImpl.class.getName());

  // The endpoint resolver for service registration and discovery
  private final EndpointResolver endpointResolver;

  // Connection manager for handling network connections
  private final ConnectionManager connectionManager;

  // Remote promise tracker for cross-network promise resolution
  private final RemotePromiseTracker promiseTracker;

  // The underlying network transport
  private final FlowTransport networkTransport;

  // Connection message handlers for multiplexing
  private final Map<FlowConnection, ConnectionMessageHandler> connectionHandlers = new ConcurrentHashMap<>();

  // Map of listening endpoints to their connection streams
  private final Map<LocalEndpoint, FlowStream<FlowConnection>> listeningEndpoints = new ConcurrentHashMap<>();

  // Closed state flag
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // Map of method IDs to service registrations for handling incoming RPC calls
  private final Map<String, ServiceRegistration> registeredServices = new ConcurrentHashMap<>();

  // Configuration for the RPC transport
  private final FlowRpcConfiguration configuration;

  /**
   * Creates a new FlowRpcTransportImpl with default components.
   */
  public FlowRpcTransportImpl() {
    this(TransportProvider.getDefaultTransport());
  }

  /**
   * Creates a new FlowRpcTransportImpl with the specified network transport.
   *
   * @param networkTransport The underlying network transport to use
   */
  public FlowRpcTransportImpl(FlowTransport networkTransport) {
    this(networkTransport, FlowRpcConfiguration.defaultConfig());
  }

  /**
   * Creates a new FlowRpcTransportImpl with the specified network transport and configuration.
   *
   * @param networkTransport The underlying network transport to use
   * @param configuration    The configuration for the RPC transport
   */
  public FlowRpcTransportImpl(FlowTransport networkTransport, FlowRpcConfiguration configuration) {
    // The underlying network transport
    this.networkTransport = networkTransport;
    this.configuration = configuration;
    this.endpointResolver = new DefaultEndpointResolver();
    this.connectionManager = new ConnectionManager(networkTransport, endpointResolver, configuration);
    this.promiseTracker = new RemotePromiseTracker(this);
  }

  @Override
  public EndpointResolver getEndpointResolver() {
    return endpointResolver;
  }

  /**
   * Gets the configuration for this RPC transport.
   *
   * @return The configuration
   */
  public FlowRpcConfiguration getConfiguration() {
    return configuration;
  }

  @Override
  public <T> T getRpcStub(EndpointId id, Class<T> interfaceClass) {
    if (closed.get()) {
      throw new IllegalStateException("RPC transport is closed");
    }

    if (id == null) {
      throw new IllegalArgumentException("Endpoint cannot be null");
    }

    // Check if this is a local endpoint first (bias towards local)
    if (endpointResolver.isLocalEndpoint(id)) {
      // Get the local implementation
      Object implementation = endpointResolver.getLocalImplementation(id)
          .orElseThrow(() -> new IllegalArgumentException(
              "No local implementation found for endpoint: " + id));

      // Verify the implementation implements the requested interface
      if (!interfaceClass.isInstance(implementation)) {
        throw new ClassCastException(
            "Local implementation does not implement " + interfaceClass.getName());
      }

      // Create a local stub that performs serialization
      return createLocalStub(implementation, interfaceClass);
    }

    // Create a remote stub with round-robin load balancing
    return createRemoteStub(id, interfaceClass);
  }

  @Override
  public <T> T getRpcStub(Endpoint endpoint, Class<T> interfaceClass) {
    if (closed.get()) {
      throw new IllegalStateException("RPC transport is closed");
    }

    if (endpoint == null) {
      throw new IllegalArgumentException("Endpoint cannot be null");
    }

    // Check if the endpoint is registered locally
    Set<EndpointId> endpointIds = endpointResolver.findEndpointIds(endpoint);
    if (!endpointIds.isEmpty()) {
      // If we find a local endpoint, prefer it
      for (EndpointId id : endpointIds) {
        if (endpointResolver.isLocalEndpoint(id)) {
          return getRpcStub(id, interfaceClass);
        }
      }
      // Otherwise use the first one found for remote stub
      EndpointId endpointId = endpointIds.iterator().next();
      return createDirectRemoteStub(endpoint, endpointId, interfaceClass);
    }

    // If not registered, create a direct remote stub without an EndpointId
    // This allows connecting to endpoints that haven't been registered
    return createDirectRemoteStub(endpoint, null, interfaceClass);
  }



  /**
   * Registers a service implementation for handling incoming RPC calls.
   * This method is called internally when endpoints are registered.
   *
   * @param implementation The service implementation
   * @param interfaceClass The interface class
   */
  void registerService(Object implementation, Class<?> interfaceClass) {
    ServiceRegistration registration = new ServiceRegistration(implementation, interfaceClass);

    // Register all methods from the interface
    for (Method method : interfaceClass.getMethods()) {
      String methodId = ServiceRegistration.buildMethodId(method);
      registeredServices.put(methodId, registration);
    }
  }

  /**
   * Starts listening on the specified local endpoint for incoming connections.
   * This method should be called when a service is registered with a local endpoint.
   *
   * @param localEndpoint The local endpoint to listen on
   */
  private void startListening(LocalEndpoint localEndpoint) {
    // Check if we're already listening on this endpoint
    FlowStream<FlowConnection> existingStream = listeningEndpoints.get(localEndpoint);
    if (existingStream != null) {
      return;
    }

    // Start listening for incoming connections
    FlowStream<FlowConnection> connectionStream = networkTransport.listen(localEndpoint);
    listeningEndpoints.put(localEndpoint, connectionStream);

    // Handle incoming connections
    startActor(() -> {
      try {
        connectionStream.forEach(this::handleIncomingConnection);
      } catch (Exception e) {
        warn(LOGGER, "Error handling incoming connections on " + localEndpoint, e);
      }
      return null;
    });

  }

  /**
   * Handles an incoming connection from a client.
   * Creates a ConnectionMessageHandler to process all messages on this connection.
   *
   * @param connection The incoming connection
   */
  private void handleIncomingConnection(FlowConnection connection) {
    debug(LOGGER, "Handling incoming connection from: " + connection.getRemoteEndpoint());
    // Create a message handler for this connection
    ConnectionMessageHandler handler = getConnectionHandler(connection);

    // The handler's startMessageReader() will be called automatically when
    // the first message is registered, but for server-side we need to start
    // reading immediately to handle incoming requests
    startActor(() -> {
      try {
        while (connection.isOpen()) {
          // Read incoming messages from the client
          ByteBuffer buffer = await(connection.receive(configuration.getReceiveBufferSize()));
          debug(LOGGER, "Received " + buffer.remaining() + " bytes from client");
          handler.handleIncomingMessage(buffer);
        }
      } catch (Exception e) {
        warn(LOGGER, "Error reading from client connection", e);
        // Clean up the connection handler when connection fails
        connectionHandlers.remove(connection);
        // Cancel all promises and streams for this endpoint
        promiseTracker.cancelAllForEndpoint(connection.getRemoteEndpoint());
      }
      return null;
    });
  }

  @Override
  public void registerServiceAndListen(EndpointId endpointId,
                                       Object implementation,
                                       Class<?> interfaceClass,
                                       LocalEndpoint localEndpoint) {
    if (closed.get()) {
      throw new IllegalStateException("RPC transport is closed");
    }
    
    if (endpointId == null || implementation == null || interfaceClass == null || localEndpoint == null) {
      throw new IllegalArgumentException("All parameters must be non-null");
    }
    
    // Convert LocalEndpoint to Endpoint for registration
    Endpoint physicalEndpoint = new Endpoint(localEndpoint.getHost(), localEndpoint.getPort());
    
    // Register in the EndpointResolver for local invocation
    // This will throw IllegalStateException if already registered with a different implementation
    endpointResolver.registerLocalEndpoint(endpointId, implementation, physicalEndpoint);
    
    // Register the service for handling incoming RPC calls
    registerService(implementation, interfaceClass);

    // Start listening on the endpoint (only if not already listening)
    startListening(localEndpoint);
  }

  @Override
  public CompletableFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      // Close all listening endpoints
      for (FlowStream<FlowConnection> stream : listeningEndpoints.values()) {
        stream.close();
      }
      listeningEndpoints.clear();

      // Close the connection manager
      return connectionManager.close();
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Creates a remote stub for the specified endpoint.
   *
   * @param id             The endpoint ID
   * @param interfaceClass The interface class
   * @param <T>            The interface type
   * @return A remote stub
   */
  private <T> T createRemoteStub(EndpointId id, Class<T> interfaceClass) {
    // Create the invocation handler with round-robin support
    // Pass null as physicalEndpoint to enable dynamic resolution per call
    InvocationHandler handler = new RemoteInvocationHandler(
        id, null, connectionManager, promiseTracker, configuration);

    // Create and return the proxy
    return interfaceClass.cast(Proxy.newProxyInstance(
        interfaceClass.getClassLoader(),
        new Class<?>[]{interfaceClass},
        handler));
  }

  /**
   * Creates a direct remote stub to a specific endpoint.
   * This method allows direct connection to a physical endpoint that has been
   * registered with the EndpointResolver.
   *
   * @param endpoint       The physical endpoint
   * @param endpointId     The logical endpoint ID associated with this physical endpoint
   * @param interfaceClass The interface class
   * @param <T>            The interface type
   * @return A remote stub
   */
  private <T> T createDirectRemoteStub(Endpoint endpoint, EndpointId endpointId, Class<T> interfaceClass) {
    // Create the invocation handler with both EndpointId and the specific endpoint
    InvocationHandler handler = new RemoteInvocationHandler(
        endpointId, endpoint, connectionManager, promiseTracker, configuration);

    // Create and return the proxy
    return interfaceClass.cast(Proxy.newProxyInstance(
        interfaceClass.getClassLoader(),
        new Class<?>[]{interfaceClass},
        handler));
  }

  /**
   * Creates a local stub that performs serialization of arguments and return values.
   *
   * @param implementation The local implementation
   * @param interfaceClass The interface class
   * @param <T>            The interface type
   * @return A local stub
   */
  private <T> T createLocalStub(Object implementation, Class<T> interfaceClass) {
    // Create the invocation handler
    InvocationHandler handler = new LocalInvocationHandler(implementation);

    // Create and return the proxy
    return interfaceClass.cast(Proxy.newProxyInstance(
        interfaceClass.getClassLoader(),
        new Class<?>[]{interfaceClass},
        handler));
  }

  /**
   * Gets or creates a connection message handler for the given connection.
   */
  private ConnectionMessageHandler getConnectionHandler(FlowConnection connection) {
    return connectionHandlers.computeIfAbsent(connection,
        conn -> new ConnectionMessageHandler(conn, promiseTracker, configuration));
  }

  /**
   * Manages message multiplexing for a single connection.
   * Ensures that responses are routed to the correct waiting callers.
   */
  private class ConnectionMessageHandler {
    private final FlowConnection connection;
    private final Map<UUID, PendingCall> pendingCalls = new ConcurrentHashMap<>();
    private final RemotePromiseTracker promiseTracker;
    private final FlowRpcConfiguration configuration;
    private final AtomicBoolean readerStarted = new AtomicBoolean(false);

    ConnectionMessageHandler(FlowConnection connection,
                             RemotePromiseTracker promiseTracker,
                             FlowRpcConfiguration configuration) {
      this.connection = connection;
      this.promiseTracker = promiseTracker;
      this.configuration = configuration;
    }

    /**
     * Registers a pending call and starts the message reader if needed.
     */
    CompletableFuture<Object> registerCall(UUID messageId, TypeDescription returnType) {
      CompletableFuture<Object> future = new CompletableFuture<>();
      pendingCalls.put(messageId, new PendingCall(future, returnType));

      // Start the message reader actor if not already started
      if (readerStarted.compareAndSet(false, true)) {
        startMessageReader();
      }

      return future;
    }

    private void startMessageReader() {
      startActor(() -> {
        try {
          while (connection.isOpen()) {
            ByteBuffer buffer = await(connection.receive(configuration.getReceiveBufferSize()));
            handleIncomingMessage(buffer);
          }
        } catch (Exception e) {
          warn(LOGGER, "Error reading from connection", e);
          // Connection closed or error - complete all pending calls with error
          for (PendingCall call : pendingCalls.values()) {
            call.promise.completeExceptionally(
                new RpcException(RpcException.ErrorCode.TRANSPORT_ERROR,
                    "Connection closed", e));
          }
          pendingCalls.clear();
          // Cancel all promises and streams for this endpoint
          promiseTracker.cancelAllForEndpoint(connection.getRemoteEndpoint());
        }
        return null;
      });
    }

    void handleIncomingMessage(ByteBuffer buffer) {
      try {
        RpcMessage message = RpcMessage.deserialize(buffer);
        UUID messageId = message.getHeader().getMessageId();
        debug(LOGGER, "Handling message: type=" + message.getHeader().getType()
                      + ", messageId=" + messageId
                      + ", methodId=" + message.getHeader().getMethodId());

        switch (message.getHeader().getType()) {
          case REQUEST:
            handleRequest(message);
            break;
          case RESPONSE:
            handleResponse(messageId, message);
            break;
          case ERROR:
            // ERROR messages can be for either regular RPC calls or promises
            // Check if this is a pending RPC call first
            if (pendingCalls.containsKey(messageId)) {
              handleResponse(messageId, message);
            } else {
              // Otherwise, treat it as a promise completion error
              handlePromiseCompletion(messageId, message);
            }
            break;
          case PROMISE_COMPLETE:
            handlePromiseCompletion(messageId, message);
            break;
          case STREAM_DATA:
            handleStreamData(messageId, message);
            break;
          case STREAM_CLOSE:
            handleStreamClose(messageId, message);
            break;
          default:
            warn(LOGGER, "Unknown message type: " + message.getHeader().getType());
        }
      } catch (Exception e) {
        warn(LOGGER, "Failed to deserialize message", e);
      }
    }

    private void handleResponse(UUID messageId, RpcMessage message) {
      PendingCall call = pendingCalls.remove(messageId);
      if (call != null) {
        try {
          if (message.getHeader().getType() == RpcMessageHeader.MessageType.ERROR) {
            Exception error = FlowSerialization.deserialize(message.getPayload(), Exception.class);
            call.promise.completeExceptionally(error);
          } else {
            Object result = mapResponse(message, call.returnType);
            call.promise.complete(result);
          }
        } catch (Exception e) {
          warn(LOGGER, "Failed to deserialize response from remote server", e);
          call.promise.completeExceptionally(e);
        }
      }
    }

    private void handlePromiseCompletion(UUID promiseId, RpcMessage message) {
      debug(LOGGER, "Handling promise completion for promiseId=" + promiseId);
      try {
        // Get the expected type for this promise from the tracker
        TypeDescription promiseTypeDesc = promiseTracker.getIncomingPromiseType(promiseId);
        Class<?> promiseType = Object.class; // Default to Object if type unknown

        if (promiseTypeDesc != null) {
          try {
            promiseType = (Class<?>) promiseTypeDesc.toType();
          } catch (ClassNotFoundException e) {
            // Fall back to Object if type can't be resolved
            // promiseType already set to Object.class
          }
        }

        // Check if it's an error completion
        if (message.getHeader().getType() == RpcMessageHeader.MessageType.ERROR) {
          Exception error = FlowSerialization.deserialize(message.getPayload(), Exception.class);
          debug(LOGGER, "Completing promise exceptionally with error: " + error);
          promiseTracker.completeLocalPromiseExceptionally(promiseId, error);
        } else {
          // Deserialize the result with the correct type
          Object result = FlowSerialization.deserialize(message.getPayload(), promiseType);
          debug(LOGGER, "Completing promise with result: " + result);
          promiseTracker.completeLocalPromise(promiseId, result);
        }
      } catch (Exception e) {
        warn(LOGGER, "Failed to deserialize promise completion from remote server", e);
        // If deserialization fails, complete the promise exceptionally
        promiseTracker.completeLocalPromiseExceptionally(promiseId, e);
      }
    }

    private void handleStreamData(UUID streamId, RpcMessage message) {
      try {
        // Get the expected type for this stream from the tracker
        TypeDescription streamTypeDesc = promiseTracker.getIncomingStreamType(streamId);
        Class<?> elementType = Object.class; // Default to Object if type unknown

        if (streamTypeDesc != null) {
          try {
            elementType = (Class<?>) streamTypeDesc.toType();
          } catch (ClassNotFoundException e) {
            // Fall back to Object if type can't be resolved
            // elementType already set to Object.class
          }
        }

        // Deserialize the value with the correct type
        Object value = FlowSerialization.deserialize(message.getPayload(), elementType);
        promiseTracker.sendToLocalStream(streamId, value);
      } catch (Exception e) {
        warn(LOGGER, "Failed to deserialize stream value from remote server", e);
        // If deserialization fails, close the stream exceptionally
        promiseTracker.closeLocalStreamExceptionally(streamId, e);
      }
    }

    private void handleStreamClose(UUID streamId, RpcMessage message) {
      try {
        // Check if there's an error payload
        if (message.getPayload() != null && message.getPayload().hasRemaining()) {
          // Deserialize the error
          Exception error = FlowSerialization.deserialize(message.getPayload(), Exception.class);
          promiseTracker.closeLocalStreamExceptionally(streamId, error);
        } else {
          // Normal close
          promiseTracker.closeLocalStream(streamId);
        }
      } catch (Exception e) {
        warn(LOGGER, "Failed to deserialize stream close error from remote server", e);
        // If deserialization fails, close the stream with the deserialization error
        promiseTracker.closeLocalStreamExceptionally(streamId, e);
      }
    }

    private void handleRequest(RpcMessage requestMessage) {
      UUID messageId = requestMessage.getHeader().getMessageId();
      String methodId = requestMessage.getHeader().getMethodId();
      debug(LOGGER, "Handling RPC request: messageId=" + messageId + ", methodId=" + methodId);

      // Execute the request handling in an actor to ensure proper Flow context
      startActor(() -> {
        try {
          // Look up the service registration
          ServiceRegistration registration = registeredServices.get(methodId);
          if (registration == null) {
            warn(LOGGER, "Method not found in registeredServices: " + methodId);
            sendErrorResponse(messageId, new RpcException(
                RpcException.ErrorCode.METHOD_NOT_FOUND,
                "Method not found: " + methodId));
            return null;
          }

          Method method = registration.methods.get(methodId);
          if (method == null) {
            warn(LOGGER, "Method not found in registration: " + methodId);
            sendErrorResponse(messageId, new RpcException(
                RpcException.ErrorCode.METHOD_NOT_FOUND,
                "Method not found: " + methodId));
            return null;
          }

          // extract the result type
          Type resultType = method.getGenericReturnType();

          // Deserialize arguments
          Object[] args;
          if (requestMessage.getPayload() == null || requestMessage.getPayload().remaining() == 0) {
            args = new Object[0];
          } else {
            args = FlowSerialization.deserialize(requestMessage.getPayload(), Object[].class);
          }
          debug(LOGGER, "Deserialized " + args.length + " arguments");

          // Process incoming arguments (reconstruct promises/streams from UUIDs)
          List<UUID> promiseIds = requestMessage.getPromiseIds();
          debug(LOGGER, "Promise IDs in request: " + promiseIds);
          Object[] processedArgs = processIncomingArguments(
              args, promiseIds, method.getParameterTypes(), connection.getRemoteEndpoint());

          // Log processed arguments
          for (int i = 0; i < processedArgs.length; i++) {
            debug(LOGGER, "Processed arg[" + i + "]: " +
                          (processedArgs[i] == null ? "null" :
                              processedArgs[i].getClass().getName() + " = " + processedArgs[i]));
          }

          // Invoke the method
          debug(LOGGER, "Invoking method: " + method.getName() + " on "
                        + registration.implementation.getClass().getName());
          Object result = method.invoke(registration.implementation, processedArgs);

          // Handle the result based on return type
          if (method.getReturnType() == void.class) {
            debug(LOGGER, "Sending void response for messageId=" + messageId);
            sendResponse(messageId, null, resultType);
          } else {
            debug(LOGGER, "Sending response with result for messageId=" + messageId);
            sendResponse(messageId, result, resultType);
          }

        } catch (Exception e) {
          warn(LOGGER, "Error handling RPC request", e);
          sendErrorResponse(messageId, e);
        }
        return null;
      });
    }

    private Object[] processIncomingArguments(Object[] args, List<UUID> promiseIds,
                                              Class<?>[] paramTypes, Endpoint sourceEndpoint) {
      if (args == null || args.length == 0) {
        return args;
      }

      Object[] processed = new Object[args.length];

      for (int i = 0; i < args.length; i++) {
        Object arg = args[i];
        Class<?> paramType = paramTypes[i];

        // Check if this argument is a UUID that represents a promise or stream
        if (arg instanceof UUID id && promiseIds != null && promiseIds.contains(id)) {
          if (PromiseStream.class.isAssignableFrom(paramType)) {
            // Create a local stream that tracks the remote stream
            debug(LOGGER, "Creating local stream for remote UUID: " + id);
            TypeDescription streamType = new TypeDescription(paramType);
            processed[i] = promiseTracker.createLocalStreamForRemote(id, sourceEndpoint, streamType);
          } else if (CompletableFuture.class.isAssignableFrom(paramType)) {
            // Create a local future that tracks the remote promise
            debug(LOGGER, "Creating local future for remote UUID: " + id);
            TypeDescription futureType = new TypeDescription(paramType);
            CompletableFuture<?> promise = promiseTracker.createLocalPromiseForRemote(id, sourceEndpoint, futureType);
            // The promise is already a CompletableFuture
            processed[i] = promise;
          } else {
            // Regular UUID argument
            processed[i] = arg;
          }
        } else {
          // Regular argument - handle numeric conversions
          processed[i] = convertArgumentType(arg, paramType);
        }
      }

      return processed;
    }

    private Object convertArgumentType(Object value, Class<?> targetType) {
      if (value == null) {
        return null;
      }

      // Handle numeric conversions since Tuple stores all integers as Long
      // Only allow non-precision losing conversions
      if (value instanceof Long longValue) {
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
        } else if (targetType == Double.class || targetType == double.class) {
          // Long to Double is generally safe for most practical values
          return longValue.doubleValue();
        }
        // Removed Long → Float conversion (precision loss)
      } else if (value instanceof Integer intValue) {
        if (targetType == Long.class || targetType == long.class) {
          // Integer to Long is always safe (widening)
          return intValue.longValue();
        } else if (targetType == Double.class || targetType == double.class) {
          // Integer to Double is always safe (double has enough precision for all int values)
          return intValue.doubleValue();
        }
        // Removed Integer → Float conversion (precision loss for large integers)
      } else if (value instanceof Float floatValue) {
        if (targetType == Double.class || targetType == double.class) {
          // Float to Double is always safe (widening)
          return floatValue.doubleValue();
        }
      }

      // No conversion needed
      return value;
    }

    private void sendResponse(UUID messageId, Object result, Type resultType) {
      try {
        ByteBuffer payload = null;
        TypeDescription returnTypeDesc = TypeDescription.fromType(resultType);

        if (result != null) {
          // Handle special return types
          debug(LOGGER, "Handling result of type: " + resultType.getTypeName());
          switch (result) {
            case CompletableFuture<?> flowPromise -> {
              // For CompletableFuture, check if it's already completed
              @SuppressWarnings("unchecked")
              CompletableFuture<Object> future = (CompletableFuture<Object>) flowPromise;

              if (future.isDone()) {
                // If already completed, send the value directly
                debug(LOGGER, "CompletableFuture is already done");
                try {
                  Object value = await(future);
                  debug(LOGGER, "CompletableFuture value: " + value);
                  payload = FlowSerialization.serialize(value);
                } catch (Exception e) {
                  // Future completed exceptionally
                  debug(LOGGER, "CompletableFuture completed exceptionally: " + e);
                  sendErrorResponse(messageId, e);
                  return;
                }
              } else {
                UUID promiseId = promiseTracker.registerOutgoingPromise(
                    future, connection.getRemoteEndpoint(), returnTypeDesc.getTypeArguments()[0]);
                payload = FlowSerialization.serialize(promiseId);

                // Set up completion handler for when the promise completes
                future.whenComplete((res, err) -> {
                  if (err != null) {
                    debug(LOGGER, "Promise result " + promiseId + " completed with error: " + err);
                    promiseTracker.sendErrorToEndpoint(connection.getRemoteEndpoint(), promiseId, err);
                  } else {
                    debug(LOGGER, "Promise result " + promiseId + " completed with result: " + res);
                    promiseTracker.sendResultToEndpoint(connection.getRemoteEndpoint(), promiseId, res);
                  }
                });
              }
            }
            case PromiseStream<?> promiseStream -> {
              // Generate stream UUID and send it in response, but defer registration
              UUID streamId = UUID.randomUUID();
              payload = FlowSerialization.serialize(streamId);

              // Register the stream immediately to capture its current state (values, close status)
              // but the actual message sending will be deferred by the RemotePromiseTracker
              // to ensure proper message ordering (RESPONSE before STREAM_DATA)
              promiseTracker.registerOutgoingStreamWithId(
                  streamId,
                  promiseStream.getFutureStream(),
                  connection.getRemoteEndpoint(),
                  returnTypeDesc.getTypeArguments()[0]);
            }
            case FutureStream<?> futureStream -> {
              // For FutureStream, we need to handle it specially
              // Generate stream UUID and send it in response, but defer registration
              UUID streamId = UUID.randomUUID();
              payload = FlowSerialization.serialize(streamId);

              // Register the stream immediately to capture its current state (values, close status)
              // but the actual message sending will be deferred by the RemotePromiseTracker
              promiseTracker.registerOutgoingStreamWithId(
                  streamId,
                  futureStream,
                  connection.getRemoteEndpoint(),
                  returnTypeDesc.getTypeArguments()[0]);
            }
            default -> {
              // Regular result
              debug(LOGGER, "Handling default case for type: " + result.getClass().getName());
              payload = FlowSerialization.serialize(result);
            }
          }
        }

        // Create and send response message
        RpcMessage responseMessage = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            messageId,
            null, // No method ID in response
            null, // No promise IDs in response
            payload);

        connection.send(responseMessage.serialize());

      } catch (Exception e) {
        warn(LOGGER, "Failed to send response", e);
        sendErrorResponse(messageId, e);
      }
    }

    private void sendErrorResponse(UUID messageId, Throwable error) {
      try {
        ByteBuffer errorPayload = FlowSerialization.serialize(
            error instanceof Exception ? error : new RuntimeException(error));

        RpcMessage errorMessage = new RpcMessage(
            RpcMessageHeader.MessageType.ERROR,
            messageId,
            null, // No method ID in error response
            null, // No promise IDs in error response
            errorPayload);

        connection.send(errorMessage.serialize());

      } catch (Exception e) {
        warn(LOGGER, "Failed to send error response", e);
      }
    }

    private Object mapResponse(RpcMessage responseMessage, TypeDescription returnType) {
      // Get the raw class from TypeDescription
      Class<?> rawReturnType;
      try {
        Type type = returnType.toType();
        if (type instanceof Class<?>) {
          rawReturnType = (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
          rawReturnType = (Class<?>) ((ParameterizedType) type).getRawType();
        } else {
          rawReturnType = Object.class;
        }
      } catch (ClassNotFoundException e) {
        rawReturnType = Object.class;
      }

      // Handle void return type
      if (rawReturnType == void.class) {
        return null;
      }

      // No payload means null result
      if (responseMessage.getPayload() == null) {
        return null;
      }


      // Handle CompletableFuture return types
      if (CompletableFuture.class.isAssignableFrom(rawReturnType)) {
        // For CompletableFuture, we expect either:
        // 1. A direct value if the future was already completed
        // 2. A UUID if the future is still pending (treated as a promise)

        debug(LOGGER, "mapResponse: Handling CompletableFuture return type");

        // Try to deserialize as UUID first
        try {
          UUID promiseId = FlowSerialization.deserialize(
              responseMessage.getPayload(), UUID.class);
          debug(LOGGER, "mapResponse: Deserialized UUID: " + promiseId);
          // It's a promise ID, create a local promise
          TypeDescription elementType = new TypeDescription(Object.class);
          if (returnType.getTypeArguments().length > 0) {
            elementType = returnType.getTypeArguments()[0];
          }
          Endpoint sourceEndpoint = connection.getRemoteEndpoint();
          CompletableFuture<Object> localPromise = promiseTracker.createLocalPromiseForRemote(
              promiseId, sourceEndpoint, elementType, false);
          debug(LOGGER, "mapResponse: Created local promise for remote UUID, returning future");
          // Return the CompletableFuture directly
          return localPromise;
        } catch (Exception e) {
          // Not a UUID, must be a direct value
          debug(LOGGER, "mapResponse: Not a UUID, deserializing as direct value. Error was: " + e);
          Class<?> valueClass = Object.class;
          if (returnType.getTypeArguments().length > 0) {
            try {
              Type valueType = returnType.getTypeArguments()[0].toType();
              if (valueType instanceof Class<?>) {
                valueClass = (Class<?>) valueType;
              }
              debug(LOGGER, "mapResponse: Value class for CompletableFuture<T> is: " + valueClass.getName());
            } catch (ClassNotFoundException ex) {
              // Use Object.class as fallback
              debug(LOGGER, "mapResponse: Could not determine value class, using Object.class");
            }
          }
          Object value = FlowSerialization.deserialize(
              responseMessage.getPayload(), valueClass);
          debug(LOGGER, "mapResponse: Deserialized value: " + value + " (type: " +
                        (value != null ? value.getClass().getName() : "null") + ")");
          return CompletableFuture.completedFuture(value);
        }
      }

      // Handle PromiseStream return types
      if (PromiseStream.class.isAssignableFrom(rawReturnType)) {
        UUID streamId = FlowSerialization.deserialize(
            responseMessage.getPayload(), UUID.class);
        // Extract the element type from the TypeDescription
        TypeDescription elementType = new TypeDescription(Object.class);
        if (returnType.getTypeArguments().length > 0) {
          elementType = returnType.getTypeArguments()[0];
        }
        // Get the source endpoint from the connection
        Endpoint sourceEndpoint = connection.getRemoteEndpoint();
        PromiseStream<?> stream = promiseTracker.createLocalStreamForRemote(
            streamId, sourceEndpoint, elementType);
        
        // Don't apply timeout here - it will be applied in RemoteInvocationHandler.invoke()
        // to avoid double wrapping
        return stream;
      }

      // Regular return type - deserialize directly
      return FlowSerialization.deserialize(responseMessage.getPayload(), rawReturnType);
    }


    private record PendingCall(CompletableFuture<Object> promise, TypeDescription returnType) {
    }
  }

  /**
   * Extracts the root RPC exception from a potentially wrapped exception.
   * This is useful when exceptions are wrapped in ExecutionException or other wrappers.
   *
   * @param e The exception to unwrap
   * @return The root RPC exception if found, or null if not found
   */
  private static RpcException extractRpcException(Throwable e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof RpcException) {
        return (RpcException) cause;
      }
      cause = cause.getCause();
    }
    return null;
  }

  /**
   * Invocation handler for remote service calls.
   */
  class RemoteInvocationHandler implements InvocationHandler {
    private static final Logger logger = Logger.getLogger(RemoteInvocationHandler.class.getName());
    private final EndpointId endpointId;
    /**
     * The physical endpoint to connect to. If null, the endpoint resolver
     * will be used to resolve the endpoint based on the endpoint ID.
     */
    private final Endpoint physicalEndpoint;
    private final ConnectionManager connectionManager;
    private final RemotePromiseTracker promiseTracker;
    private final FlowRpcConfiguration configuration;

    RemoteInvocationHandler(EndpointId endpointId,
                            Endpoint physicalEndpoint,
                            ConnectionManager connectionManager,
                            RemotePromiseTracker promiseTracker,
                            FlowRpcConfiguration configuration) {
      this.endpointId = endpointId;
      this.physicalEndpoint = physicalEndpoint;
      this.connectionManager = connectionManager;
      this.promiseTracker = promiseTracker;
      this.configuration = configuration;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      // Handle Object methods
      if (method.getDeclaringClass() == Object.class) {
        return handleObjectMethod(proxy, method, args);
      }

      // Generate a unique message ID for this RPC call
      UUID messageId = UUID.randomUUID();

      // Build the method ID from class and method signature
      String methodId = buildMethodId(method);

      // Get a connection to the remote endpoint
      CompletableFuture<FlowConnection> connectionFuture;
      if (physicalEndpoint != null) {
        // Direct connection to specific endpoint
        connectionFuture = connectionManager.getConnectionToEndpoint(physicalEndpoint);
      } else {
        // Use endpoint resolver for load balancing
        connectionFuture = connectionManager.getConnection(endpointId);
      }

      // Handle different return types appropriately
      Class<?> returnType = method.getReturnType();
      boolean isVoidMethod = returnType == void.class || returnType == Void.class;

      CompletableFuture<Object> responseFuture = connectionFuture.thenCompose(connection -> {
        // Get the actual target endpoint from the connection
        Endpoint targetEndpoint = connection.getRemoteEndpoint();

        // Handle promise and stream arguments with the target endpoint
        List<UUID> promiseIds = new ArrayList<>();
        Object[] processedArgs = processArguments(args, promiseIds, targetEndpoint, method);
        debug(LOGGER, "Sending RPC request: methodId=" + methodId + ", promiseIds=" + promiseIds);

        // Serialize the arguments
        ByteBuffer payload;
        try {
          payload = FlowSerialization.serialize(processedArgs);
        } catch (Exception e) {
          throw new RpcSerializationException(Object[].class, "Failed to serialize method arguments", e);
        }

        // Create the RPC message
        RpcMessage requestMessage = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            promiseIds,
            payload);

        // Get the connection handler for message multiplexing
        ConnectionMessageHandler handler = getConnectionHandler(connection);

        // Register this call before sending to avoid race conditions
        TypeDescription returnTypeDesc = TypeDescription.fromType(method.getGenericReturnType());
        CompletableFuture<Object> callFuture = handler.registerCall(messageId, returnTypeDesc);

        // Send the request
        ByteBuffer serializedMessage = requestMessage.serialize();
        CompletableFuture<Void> sendF = connection.send(serializedMessage);

        // Void methods return a completed future after sending
        if (returnType == void.class || returnType == Void.class) {
          try {
            return sendF.thenApply($ -> null);
          } catch (Exception e) {
            throw new RpcException(RpcException.ErrorCode.INVOCATION_ERROR,
                "RPC invocation failed for method: " + method.getName(), e);
          }
        }
        return sendF.thenCompose(v -> callFuture);
      });

      // Apply timeout for non-void unary RPC calls (not for streams or void methods)
      if (!isVoidMethod && !PromiseStream.class.isAssignableFrom(returnType)
          && !FutureStream.class.isAssignableFrom(returnType)
          && configuration.getUnaryRpcTimeoutMs() > 0) {
        logger.fine(() -> "Applying timeout of " + configuration.getUnaryRpcTimeoutMs() + "ms to " + method.getName());
        responseFuture = RpcTimeoutUtil.withTimeout(
            responseFuture, endpointId, method.getName(),
            configuration.getUnaryRpcTimeoutMs());
      }

      // If the method returns a CompletableFuture, return the future directly
      if (CompletableFuture.class.isAssignableFrom(returnType)) {
        // Handle potential nested CompletableFuture from mapResponse
        return responseFuture.thenCompose(result -> {
          if (result instanceof CompletableFuture) {
            // Flatten nested CompletableFuture
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> nestedFuture = (CompletableFuture<Object>) result;
            return nestedFuture;
          } else {
            // Return completed future with the direct result
            return CompletableFuture.completedFuture(result);
          }
        });
      }

      // If the method returns a PromiseStream, the future will complete with the stream
      if (PromiseStream.class.isAssignableFrom(returnType)) {
        // The waitForResponse method will have created a PromiseStream
        // that will receive values asynchronously
        try {
          PromiseStream<?> stream = (PromiseStream<?>) await(responseFuture);
          
          // Apply inactivity timeout to the stream
          long timeoutMs = configuration.getStreamInactivityTimeoutMs();
          if (timeoutMs > 0) {
            logger.fine(() -> "Applying stream inactivity timeout of " + timeoutMs + "ms to " + method.getName());
            return RpcStreamTimeoutUtil.withInactivityTimeout(
                stream, endpointId, method.getName(), timeoutMs);
          }
          
          return stream;
        } catch (Exception e) {
          // Check if this is a wrapped RPC exception (e.g., timeout)
          RpcException rpcEx = extractRpcException(e);
          if (rpcEx != null) {
            throw rpcEx;
          }
          throw new RpcException(RpcException.ErrorCode.INVOCATION_ERROR,
              "RPC invocation failed for method: " + method.getName(), e);
        }
      }

      // For regular return types, block and wait for the result
      try {
        logger.fine(() -> "Awaiting responseFuture for " + method.getName());
        Object result = await(responseFuture);
        // Convert the result to match the method's return type if needed
        return convertReturnValue(result, method.getReturnType());
      } catch (Exception e) {
        // Check if this is a wrapped RPC exception (e.g., timeout)
        RpcException rpcEx = extractRpcException(e);
        if (rpcEx != null) {
          throw rpcEx;
        }
        throw new RpcException(RpcException.ErrorCode.INVOCATION_ERROR,
            "RPC invocation failed for method: " + method.getName(), e);
      }
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
      String methodName = method.getName();
      return switch (methodName) {
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> {
          if (physicalEndpoint != null) {
            yield "RemoteStub[" + endpointId + " -> " + physicalEndpoint + "]";
          } else {
            yield "RemoteStub[" + endpointId + " (round-robin)]";
          }
        }
        default -> throw new UnsupportedOperationException("Unsupported Object method: " + methodName);
      };
    }

    private String buildMethodId(Method method) {
      // Build a unique method identifier from class and method signature
      StringBuilder sb = new StringBuilder();
      sb.append(method.getDeclaringClass().getName());
      sb.append(".");
      sb.append(method.getName());
      sb.append("(");

      Class<?>[] paramTypes = method.getParameterTypes();
      for (int i = 0; i < paramTypes.length; i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append(paramTypes[i].getName());
      }
      sb.append(")");

      return sb.toString();
    }

    private Object[] processArguments(Object[] args, List<UUID> promiseIds, Endpoint targetEndpoint, Method method) {
      if (args == null) {
        return null;
      }

      // Handle system types by converting them
      // to serializable representations. Promises are registered with the
      // RemotePromiseTracker and replaced with UUIDs. Streams are replaced
      // with remote stream proxies. This preprocessing is necessary because
      // these types cannot be directly serialized across network boundaries
      // and need special handling to maintain their semantics.

      Type[] genericParameterTypes = method.getGenericParameterTypes();
      Object[] processed = new Object[args.length];
      for (int i = 0; i < args.length; i++) {
        Object arg = args[i];

        if (arg instanceof CompletableFuture<?> promise) {
          // Extract the generic type from the method parameter
          TypeDescription promiseType = extractTypeFromMethodParameter(genericParameterTypes[i]);
          UUID promiseId = promiseTracker.registerOutgoingPromise(
              promise, targetEndpoint, promiseType);
          promiseIds.add(promiseId);
          processed[i] = promiseId;
        } else if (arg instanceof CompletableFuture<?> future) {
          // Extract the generic type from the method parameter
          TypeDescription promiseType = extractTypeFromMethodParameter(genericParameterTypes[i]);
          UUID promiseId = promiseTracker.registerOutgoingPromise(
              future, targetEndpoint, promiseType);
          promiseIds.add(promiseId);
          processed[i] = promiseId;
        } else if (arg instanceof FutureStream<?> futureStream) {
          // Extract the generic type from the method parameter
          TypeDescription streamType = extractTypeFromMethodParameter(genericParameterTypes[i]);
          UUID streamId = promiseTracker.registerOutgoingStream(
              futureStream, targetEndpoint, streamType);
          promiseIds.add(streamId);
          processed[i] = streamId;
        } else if (arg instanceof PromiseStream<?> stream) {
          // Extract the generic type from the method parameter
          TypeDescription streamType = extractTypeFromMethodParameter(genericParameterTypes[i]);
          UUID streamId = promiseTracker.registerOutgoingStream(
              stream.getFutureStream(), targetEndpoint, streamType);
          // Add to promise IDs list (streams are tracked similarly to promises)
          promiseIds.add(streamId);
          processed[i] = streamId;
        } else {
          // Regular argument - will be serialized
          processed[i] = arg;
        }
      }

      return processed;
    }

    static Object convertReturnValue(Object value, Class<?> returnType) {
      if (value == null) {
        return null;
      }

      // Handle numeric conversions since Tuple stores all integers as Long
      if (value instanceof Long longValue) {
        if (returnType == Integer.class || returnType == int.class) {
          // Check if the value fits in an Integer
          if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
          } else {
            throw new RpcException(RpcException.ErrorCode.INVOCATION_ERROR,
                "Long value " + longValue + " cannot be converted to Integer");
          }
        } else if (returnType == Short.class || returnType == short.class) {
          // Check if the value fits in a Short
          if (longValue >= Short.MIN_VALUE && longValue <= Short.MAX_VALUE) {
            return longValue.shortValue();
          } else {
            throw new RpcException(RpcException.ErrorCode.INVOCATION_ERROR,
                "Long value " + longValue + " cannot be converted to Short");
          }
        } else if (returnType == Byte.class || returnType == byte.class) {
          // Check if the value fits in a Byte
          if (longValue >= Byte.MIN_VALUE && longValue <= Byte.MAX_VALUE) {
            return longValue.byteValue();
          } else {
            throw new RpcException(RpcException.ErrorCode.INVOCATION_ERROR,
                "Long value " + longValue + " cannot be converted to Byte");
          }
        }
      }

      // No conversion needed
      return value;
    }

    private TypeDescription extractTypeFromMethodParameter(Type parameterType) {
      if (parameterType instanceof ParameterizedType paramType) {
        Type rawType = paramType.getRawType();
        if (rawType == CompletableFuture.class || rawType == PromiseStream.class ||
            rawType == FutureStream.class) {
          Type[] typeArgs = paramType.getActualTypeArguments();
          if (typeArgs.length > 0) {
            return TypeDescription.fromType(typeArgs[0]);
          }
        }
      }
      // Fall back to Object if we can't determine the type
      return new TypeDescription(Object.class);
    }

  }

  /**
   * Represents a registered service with its implementation and interface.
   */
  private static class ServiceRegistration {
    final Object implementation;
    final Class<?> interfaceClass;
    final Map<String, Method> methods = new HashMap<>();

    ServiceRegistration(Object implementation, Class<?> interfaceClass) {
      this.implementation = implementation;
      this.interfaceClass = interfaceClass;

      // Cache all methods by their method ID
      for (Method method : interfaceClass.getMethods()) {
        String methodId = buildMethodId(method);
        methods.put(methodId, method);
      }
    }

    private static String buildMethodId(Method method) {
      // Build a unique method identifier from class and method signature
      StringBuilder sb = new StringBuilder();
      sb.append(method.getDeclaringClass().getName());
      sb.append(".");
      sb.append(method.getName());
      sb.append("(");

      Class<?>[] paramTypes = method.getParameterTypes();
      for (int i = 0; i < paramTypes.length; i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append(paramTypes[i].getName());
      }
      sb.append(")");

      return sb.toString();
    }
  }

  /**
   * Implementation of the MessageSender interface for sending promise and stream messages.
   */
  @Override
  public <T> void sendResult(Endpoint destination, UUID promiseId, T result) {
    debug(LOGGER, "MessageSender.sendResult called: destination=" + destination
                  + ", promiseId=" + promiseId + ", result=" + result);
    // Create a result message
    try {
      ByteBuffer payload = FlowSerialization.serialize(result);
      RpcMessage resultMessage = new RpcMessage(
          RpcMessageHeader.MessageType.PROMISE_COMPLETE,
          promiseId,
          null, // No method ID for promise completion
          null, // No promise IDs in completion messages
          payload);
      sendMessageToEndpoint(destination, resultMessage);
    } catch (Exception e) {
      warn(LOGGER, "Failed to serialize promise result", e);
      // If serialization fails, send an error instead
      sendError(destination, promiseId, new RpcSerializationException(
          result.getClass(), "Failed to serialize promise result", e));
    }
  }

  /**
   * Sends an error to an endpoint when a promise fails.
   */
  @Override
  public void sendError(Endpoint destination, UUID promiseId, Throwable error) {
    try {
      ByteBuffer payload = FlowSerialization.serialize(error);
      RpcMessage errorMessage = new RpcMessage(
          RpcMessageHeader.MessageType.ERROR,
          promiseId,
          null, // No method ID for promise completion
          null, // No promise IDs in completion messages
          payload);
      sendMessageToEndpoint(destination, errorMessage);
    } catch (Exception e) {
      // If we can't even serialize the error, there's not much we can do
      warn(LOGGER, "Failed to serialize error message", e);
    }
  }

  /**
   * Sends a cancellation notification to an endpoint.
   */
  @Override
  public void sendCancellation(Endpoint source, UUID promiseId) {
    // For cancellation, we can send an error message with a special exception
    info(LOGGER, "Sending cancellation to " + source + " for promise " + promiseId);
    sendError(source, promiseId, new IllegalStateException("Promise was cancelled"));
  }

  /**
   * Sends a stream value to an endpoint.
   */
  @Override
  public <T> void sendStreamValue(Endpoint destination, UUID streamId, T value) {
    // Create a stream data message
    try {
      ByteBuffer payload = FlowSerialization.serialize(value);
      RpcMessage streamMessage = new RpcMessage(
          RpcMessageHeader.MessageType.STREAM_DATA,
          streamId,
          null, // No method ID for stream data
          null, // No promise IDs in stream messages
          payload);
      sendMessageToEndpoint(destination, streamMessage);
    } catch (Exception e) {
      // If serialization fails, close the stream with an error
      warn(LOGGER, "Failed to serialize stream value", e);
      sendStreamError(destination, streamId, new RpcSerializationException(
          value.getClass(), "Failed to serialize stream value", e));
    }
  }

  /**
   * Sends a stream close notification to an endpoint.
   */
  @Override
  public void sendStreamClose(Endpoint destination, UUID streamId) {
    // Create a stream close message with no payload
    RpcMessage closeMessage = new RpcMessage(
        RpcMessageHeader.MessageType.STREAM_CLOSE,
        streamId,
        null, // No method ID for stream close
        null, // No promise IDs in stream messages
        null); // No payload for normal close
    sendMessageToEndpoint(destination, closeMessage);
  }

  /**
   * Sends a stream error notification to an endpoint.
   */
  @Override
  public void sendStreamError(Endpoint destination, UUID streamId, Throwable error) {
    // Create a stream close message with error payload
    try {
      ByteBuffer payload = FlowSerialization.serialize(error);
      RpcMessage errorMessage = new RpcMessage(
          RpcMessageHeader.MessageType.STREAM_CLOSE,
          streamId,
          null, // No method ID for stream close
          null, // No promise IDs in stream messages
          payload); // Error payload
      sendMessageToEndpoint(destination, errorMessage);
    } catch (Exception e) {
      // If we can't even serialize the error, send close without payload
      sendStreamClose(destination, streamId);
    }
  }

  /**
   * Sends an RPC message to an endpoint.
   */
  private void sendMessageToEndpoint(Endpoint destination, RpcMessage message) {
    debug(LOGGER, "sendMessageToEndpoint: sending " + message.getHeader().getType() + " to " + destination);
    ByteBuffer serializedMessage = message.serialize();

    // First, check if we have an existing connection handler for this endpoint
    // This happens when the endpoint is the remote side of an incoming connection
    FlowConnection existingConnection = null;
    for (Map.Entry<FlowConnection, ConnectionMessageHandler> entry : connectionHandlers.entrySet()) {
      if (entry.getKey().getRemoteEndpoint().equals(destination)) {
        existingConnection = entry.getKey();
        break;
      }
    }

    if (existingConnection != null && existingConnection.isOpen()) {
      debug(LOGGER, "Using existing connection to " + destination + ", sending message");
      existingConnection.send(serializedMessage);
    } else {
      // No existing connection, try to establish one
      CompletableFuture<FlowConnection> connectionFuture = connectionManager.getConnectionToEndpoint(destination);
      connectionFuture.whenComplete((connection, error) -> {
        if (error == null && connection != null) {
          debug(LOGGER, "Got new connection to " + destination + ", sending message");
          connection.send(serializedMessage);
        } else if (error != null) {
          warn(LOGGER, "Failed to get connection to " + destination, error);
        }
      });
    }
  }

  /**
   * Invocation handler for local service calls with serialization.
   */
  private record LocalInvocationHandler(Object implementation) implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      // Handle Object methods
      if (method.getDeclaringClass() == Object.class) {
        return handleObjectMethod(proxy, method, args);
      }

      // For local invocations, we can pass arguments directly
      // The RPC framework design doc states that local stubs should provide
      // serialization isolation, but for simplicity we'll pass arguments directly
      // in this implementation. In a production system, you'd want to implement
      // proper deep copying or serialization.

      try {
        // Invoke the method with arguments
        // For local invocations, return the result directly
        // In a production system, you'd want to implement proper deep copying
        // or serialization to maintain isolation.
        return method.invoke(implementation, args);
      } catch (Exception e) {
        // Wrap the exception to ensure proper error handling
        throw new RuntimeException("Local invocation failed for method: " + method.getName(), e);
      }
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
      String methodName = method.getName();
      return switch (methodName) {
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "LocalStub[" + implementation.getClass().getSimpleName() + "]";
        default -> throw new UnsupportedOperationException("Unsupported Object method: " + methodName);
      };
    }
  }
}