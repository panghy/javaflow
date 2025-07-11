package io.github.panghy.javaflow.io.network;
import java.util.concurrent.CompletableFuture;

import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.PromiseStream;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.util.IOUtil.closeQuietly;
import static io.github.panghy.javaflow.util.LoggingUtil.warn;

/**
 * A real implementation of FlowTransport using Java NIO's asynchronous channels.
 * This implementation performs actual network I/O operations.
 *
 * <p>RealFlowTransport provides a concrete implementation of the FlowTransport
 * interface for real network operations. It manages the lifecycle of network connections
 * and server endpoints, integrating Java NIO's asynchronous channel API with JavaFlow's
 * cooperative multitasking model.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>A dedicated thread pool for asynchronous I/O operations</li>
 *   <li>Non-blocking connection establishment and acceptance</li>
 *   <li>Stream-based handling of incoming connections</li>
 *   <li>Resource management for server and client sockets</li>
 * </ul>
 *
 * <p>This transport is automatically selected by {@link FlowTransport#getDefault()}
 * when JavaFlow is not running in simulation mode. It creates {@link RealFlowConnection}
 * instances for both outbound and inbound connections.</p>
 *
 * <p>The implementation uses an {@link AsynchronousChannelGroup} with a dedicated
 * thread pool sized based on the number of available processors. This ensures efficient
 * handling of network I/O without blocking Flow's cooperative multitasking.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create a transport explicitly (normally you'd use FlowTransport.getDefault())
 * RealFlowTransport transport = new RealFlowTransport();
 *
 * // Listen for connections
 * FlowStream<FlowConnection> connectionStream = transport.listen(LocalEndpoint.localhost(8080));
 *
 * // Process incoming connections
 * Flow.startActor(() -> {
 *   while (true) {
 *     FlowConnection connection = Flow.await(connectionStream.nextAsync());
 *     // Process the connection...
 *   }
 *   return null;
 * });
 *
 * // Close the transport when done
 * Flow.await(transport.close());
 * }</pre>
 *
 * <p>When the transport is closed, all managed server sockets and connection streams
 * are automatically closed as well. It is important to close the transport properly
 * to release all network resources.</p>
 *
 * @see FlowTransport
 * @see RealFlowConnection
 * @see AsynchronousChannelGroup
 */
public class RealFlowTransport implements FlowTransport {

  private static final Logger logger = Logger.getLogger(RealFlowTransport.class.getName());

  private final AsynchronousChannelGroup channelGroup;
  private final Map<LocalEndpoint, AsynchronousServerSocketChannel> serverChannels = new ConcurrentHashMap<>();
  private final Map<LocalEndpoint, PromiseStream<FlowConnection>> connectionStreams = new ConcurrentHashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final CompletableFuture<Void> closePromise;

  /**
   * Creates a new RealFlowTransport with a default channel group.
   *
   * @throws IOException If an I/O error occurs
   */
  public RealFlowTransport() throws IOException {
    // don't scale with processors by default so unit tests are replicable
    this(AsynchronousChannelGroup.withThreadPool(Executors.newFixedThreadPool(4)));
  }

  /**
   * Creates a new RealFlowTransport with the specified channel group.
   *
   * @param channelGroup The channel group to use for asynchronous operations
   */
  public RealFlowTransport(AsynchronousChannelGroup channelGroup) {
    this.channelGroup = channelGroup;
    this.closePromise = new CompletableFuture<>();
  }

  @Override
  public CompletableFuture<FlowConnection> connect(Endpoint endpoint) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IOException("Transport is closed"));
    }

    CompletableFuture<FlowConnection> result = new CompletableFuture<>();

    try {
      // Create an async socket channel
      AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(channelGroup);

      // Convert endpoint to InetSocketAddress
      InetSocketAddress address = endpoint.toInetSocketAddress();

      // Connect asynchronously
      channel.connect(address, null, new CompletionHandler<Void, Void>() {
        @Override
        public void completed(Void v, Void attachment) {
          try {
            InetSocketAddress localAddress = (InetSocketAddress) channel.getLocalAddress();
            Endpoint localEndpoint = new Endpoint(localAddress);
            // Create the connection
            FlowConnection connection = new RealFlowConnection(channel, localEndpoint, endpoint);

            // Complete the promise
            result.complete(connection);
          } catch (IOException e) {
            failed(e, attachment);
          }
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
          closeQuietly(channel);
          result.completeExceptionally(exc);
        }
      });
    } catch (IOException e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowStream<FlowConnection> listen(LocalEndpoint localEndpoint) {
    if (closed.get()) {
      PromiseStream<FlowConnection> errorStream = new PromiseStream<>();
      errorStream.closeExceptionally(new IOException("Transport is closed"));
      return errorStream.getFutureStream();
    }

    // Check if we're already listening on this endpoint
    PromiseStream<FlowConnection> existingStream = connectionStreams.get(localEndpoint);
    if (existingStream != null) {
      return existingStream.getFutureStream();
    }

    // Create a new promise stream for connections
    PromiseStream<FlowConnection> connectionStream = new PromiseStream<>();
    connectionStreams.put(localEndpoint, connectionStream);

    try {
      // Open the server socket
      AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open(channelGroup);

      // Bind to the address
      InetSocketAddress bindAddress = localEndpoint.toInetSocketAddress();
      serverChannel.bind(bindAddress);

      // Store the server channel
      serverChannels.put(localEndpoint, serverChannel);

      // Start accepting connections
      startAccepting(serverChannel, localEndpoint, connectionStream);
    } catch (IOException e) {
      connectionStream.closeExceptionally(e);
      connectionStreams.remove(localEndpoint);
    }

    return connectionStream.getFutureStream();
  }

  @Override
  public ConnectionListener listenOnAvailablePort(LocalEndpoint localEndpoint) {
    if (closed.get()) {
      PromiseStream<FlowConnection> errorStream = new PromiseStream<>();
      errorStream.closeExceptionally(new IOException("Transport is closed"));
      return new ConnectionListener(errorStream.getFutureStream(), localEndpoint);
    }

    // Create a new promise stream for connections
    PromiseStream<FlowConnection> connectionStream = new PromiseStream<>();

    try {
      // Open the server socket
      AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open(channelGroup);

      // Bind to the provided address with port 0 to get a random available port
      InetSocketAddress bindAddress = localEndpoint.toInetSocketAddress();
      serverChannel.bind(bindAddress);

      // Get the actual bound address with the assigned port
      InetSocketAddress boundAddress = (InetSocketAddress) serverChannel.getLocalAddress();
      LocalEndpoint boundEndpoint = LocalEndpoint.create(
          boundAddress.getHostString(), boundAddress.getPort());

      // Store the server channel and connection stream using the bound endpoint
      serverChannels.put(boundEndpoint, serverChannel);
      connectionStreams.put(boundEndpoint, connectionStream);

      // Start accepting connections
      startAccepting(serverChannel, boundEndpoint, connectionStream);

      // Return both the stream and the bound endpoint
      return new ConnectionListener(connectionStream.getFutureStream(), boundEndpoint);
    } catch (IOException e) {
      connectionStream.closeExceptionally(e);
      return new ConnectionListener(connectionStream.getFutureStream(), localEndpoint);
    }
  }

  @Override
  public CompletableFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      try {
        // Close all server channels
        for (Map.Entry<LocalEndpoint, AsynchronousServerSocketChannel> entry : serverChannels.entrySet()) {
          closeQuietly(entry.getValue());
        }
        serverChannels.clear();

        // Close all connection streams
        for (PromiseStream<FlowConnection> stream : connectionStreams.values()) {
          stream.close();
        }
        connectionStreams.clear();

        // Shutdown the channel group
        channelGroup.shutdownNow();

        // Complete the close promise
        closePromise.complete(null);
      } catch (Exception e) {
        closePromise.completeExceptionally(e);
      }
    }

    return closePromise;
  }

  /**
   * Starts accepting connections on the given server channel.
   *
   * @param serverChannel    The server channel to accept on
   * @param localEndpoint    The local endpoint being listened on
   * @param connectionStream The stream to send new connections to
   */
  private void startAccepting(
      AsynchronousServerSocketChannel serverChannel,
      LocalEndpoint localEndpoint,
      PromiseStream<FlowConnection> connectionStream) {

    if (closed.get() || connectionStream.isClosed()) {
      return;
    }

    serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
      @Override
      public void completed(AsynchronousSocketChannel clientChannel, Void attachment) {
        try {
          // Create endpoints
          InetSocketAddress remoteAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
          Endpoint remoteEndpoint = new Endpoint(remoteAddress);

          // Create the connection
          FlowConnection connection = new RealFlowConnection(clientChannel, localEndpoint, remoteEndpoint);

          // Send the connection to the stream
          connectionStream.send(connection);

          // Continue accepting - always accept again if not closed
          serverChannel.accept(null, this);
        } catch (IOException e) {
          failed(e, attachment);
        }
      }

      @Override
      public void failed(Throwable exc, Void attachment) {
        warn(logger, "Error accepting connection", exc);
        connectionStream.closeExceptionally(exc);
        connectionStreams.remove(localEndpoint);
      }
    });
  }

  /**
   * For testing purposes only.
   *
   * @return The map of server channels
   */
  Map<LocalEndpoint, AsynchronousServerSocketChannel> getServerChannels() {
    return serverChannels;
  }
}