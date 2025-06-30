package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.core.FlowStream;
import java.util.concurrent.CompletableFuture;

import java.nio.ByteBuffer;

/**
 * Represents an asynchronous network connection in the JavaFlow actor system.
 * This interface defines the operations for sending and receiving data over a connection.
 * All operations return CompletableFuture objects that can be awaited using Flow.await().
 * 
 * <p>FlowConnection provides key network communication capabilities including:</p>
 * <ul>
 *   <li>Sending bytes to a remote endpoint</li>
 *   <li>Receiving bytes from a remote endpoint</li>
 *   <li>Streaming continuous data from a connection</li>
 *   <li>Managing connection lifecycle (closing, checking status)</li>
 * </ul>
 * 
 * <p>In normal operation mode, FlowConnection is implemented by RealFlowConnection
 * which uses Java NIO's AsynchronousSocketChannel for actual network I/O. In simulation
 * mode, it's implemented by SimulatedFlowConnection which simulates network behavior
 * in-memory with configurable parameters.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Open a connection
 * FlowConnection connection = Flow.await(FlowTransport.getDefault().connect(endpoint));
 * 
 * // Send data
 * ByteBuffer data = ByteBuffer.wrap("Hello".getBytes(StandardCharsets.UTF_8));
 * Flow.await(connection.send(data));
 * 
 * // Receive data
 * ByteBuffer response = Flow.await(connection.receive(1024));
 * 
 * // Use a stream to continuously receive data
 * FlowStream<ByteBuffer> stream = connection.receiveStream();
 * while (true) {
 *   ByteBuffer packet = Flow.await(stream.nextAsync());
 *   // Process packet...
 * }
 * 
 * // Close the connection when done
 * Flow.await(connection.close());
 * }</pre>
 * 
 * <p>All operations can throw IOException if there's a network error or if
 * the connection is closed.</p>
 * 
 * @see FlowTransport
 * @see RealFlowConnection
 * @see SimulatedFlowConnection
 */
public interface FlowConnection {

  /**
   * Sends data over the connection.
   *
   * @param data The data to send
   * @return A future that completes when the data has been sent successfully
   */
  CompletableFuture<Void> send(ByteBuffer data);

  /**
   * Reads available data from the connection.
   * This method will return a ByteBuffer containing at most maxBytes bytes.
   * If more data is available, it will be returned in subsequent calls to this method.
   *
   * @param maxBytes The maximum number of bytes to read (must be positive)
   * @return A future that completes with the data read from the connection
   * @throws IllegalArgumentException if maxBytes is not positive
   */
  CompletableFuture<ByteBuffer> receive(int maxBytes);

  /**
   * Provides a stream of incoming data packets.
   * This can be used to continuously receive data from the connection.
   *
   * @return A stream of received data packets
   */
  FlowStream<ByteBuffer> receiveStream();

  /**
   * Gets the local endpoint of this connection.
   *
   * @return The local endpoint
   */
  Endpoint getLocalEndpoint();

  /**
   * Gets the remote endpoint of this connection.
   *
   * @return The remote endpoint
   */
  Endpoint getRemoteEndpoint();

  /**
   * Checks if this connection is currently open.
   *
   * @return true if the connection is open, false otherwise
   */
  boolean isOpen();

  /**
   * Returns a future that completes when the connection is closed.
   * This can be used to monitor the connection status.
   *
   * @return A future that completes when the connection is closed
   */
  CompletableFuture<Void> closeFuture();

  /**
   * Closes this connection.
   *
   * @return A future that completes when the connection is closed
   */
  CompletableFuture<Void> close();
}