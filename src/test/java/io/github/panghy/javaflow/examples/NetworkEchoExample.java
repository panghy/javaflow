package io.github.panghy.javaflow.examples;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.FlowTransport;
import io.github.panghy.javaflow.io.network.LocalEndpoint;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Example demonstrating the use of the network transport layer in JavaFlow.
 * This example creates a simple echo server that listens for connections
 * and echoes back any messages it receives.
 */
public class NetworkEchoExample {

  /**
   * Main method to run the example.
   */
  public static void main(String[] args) throws Exception {
    int port = 8080;
    if (args.length > 0) {
      port = Integer.parseInt(args[0]);
    }

    System.out.println("Starting echo server on port " + port);
    
    // Get the default transport
    FlowTransport transport = FlowTransport.getDefault();
    
    // Start the echo server
    FlowFuture<Void> serverFuture = startEchoServer(transport, port);
    
    if (args.length <= 1) {
      // If no client flag, just run the server
      System.out.println("Server running. Press CTRL+C to exit.");
      serverFuture.toCompletableFuture().get();
    } else {
      // If client flag is provided, run a client
      System.out.println("Running in client mode. Will send messages to the server.");
      runEchoClient(transport, port);
    }
  }

  /**
   * Starts an echo server that listens for connections and echoes back any messages it receives.
   *
   * @param transport The transport to use
   * @param port      The port to listen on
   * @return A future that completes when the server finishes
   */
  private static FlowFuture<Void> startEchoServer(FlowTransport transport, int port) {
    return Flow.startActor(() -> {
      try {
        // Create a local endpoint to listen on
        LocalEndpoint endpoint = LocalEndpoint.localhost(port);
        
        System.out.println("Server listening on " + endpoint);
        
        // Start listening for connections
        FlowStream<FlowConnection> connectionStream = transport.listen(endpoint);
        
        // Accept and handle connections in a loop
        while (true) {
          // Wait for a connection
          FlowConnection connection = Flow.await(connectionStream.nextAsync());
          
          System.out.println("Connection received from " + connection.getRemoteEndpoint());
          
          // Start a new actor to handle this connection
          Flow.startActor(() -> handleEchoConnection(connection));
        }
      } catch (Exception e) {
        System.err.println("Server error: " + e.getMessage());
        e.printStackTrace();
      }
      return null;
    });
  }

  /**
   * Handles a single client connection in the echo server.
   *
   * @param connection The client connection to handle
   * @return A future that completes when the connection is closed
   */
  private static FlowFuture<Void> handleEchoConnection(FlowConnection connection) {
    return Flow.startActor(() -> {
      try {
        System.out.println("Starting echo handler for " + connection.getRemoteEndpoint());
        
        // Get the receive stream for this connection
        FlowStream<ByteBuffer> receiveStream = connection.receiveStream();
        
        // Handle messages in a loop
        while (true) {
          // Wait for a message
          ByteBuffer buffer = Flow.await(receiveStream.nextAsync());
          
          // Convert to string for logging
          byte[] bytes = new byte[buffer.remaining()];
          buffer.duplicate().get(bytes);
          String message = new String(bytes, StandardCharsets.UTF_8);
          
          System.out.println("Received: " + message);
          
          // Echo the message back
          Flow.await(connection.send(buffer.duplicate()));
          
          System.out.println("Echoed message back");
        }
      } catch (Exception e) {
        System.out.println("Connection closed: " + e.getMessage());
        
        // Close the connection if it's still open
        if (connection.isOpen()) {
          try {
            Flow.await(connection.close());
          } catch (Exception ex) {
            // Ignore errors on close
          }
        }
      }
      return null;
    });
  }

  /**
   * Runs a client that connects to the echo server, sends messages, and displays responses.
   *
   * @param transport The transport to use
   * @param port      The port to connect to
   * @throws Exception if an error occurs
   */
  private static void runEchoClient(FlowTransport transport, int port) throws Exception {
    // Create a future for the client
    FlowFuture<Void> clientFuture = Flow.startActor(() -> {
      try {
        // Connect to the server
        Endpoint serverEndpoint = new Endpoint("localhost", port);
        System.out.println("Connecting to server at " + serverEndpoint);
        
        FlowConnection connection = Flow.await(transport.connect(serverEndpoint));
        System.out.println("Connected to server");
        
        // Start a separate actor to receive responses
        Flow.startActor(() -> receiveResponses(connection));
        
        // Send messages to the server
        for (int i = 1; i <= 5; i++) {
          // Create the message
          String message = "Message #" + i;
          System.out.println("Sending: " + message);
          
          // Convert to ByteBuffer and send
          ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
          Flow.await(connection.send(buffer));
          
          // Wait a bit before sending the next message
          Flow.await(Flow.delay(1.0));
        }
        
        // Close the connection when done
        System.out.println("Client finished sending messages");
        Flow.await(connection.close());
        
      } catch (Exception e) {
        System.err.println("Client error: " + e.getMessage());
        e.printStackTrace();
      }
      return null;
    });
    
    // Wait for the client to finish
    clientFuture.toCompletableFuture().get();
    System.out.println("Client exited");
    
    // Exit after a brief delay
    Thread.sleep(500);
    System.exit(0);
  }

  /**
   * Receives and displays responses from the server.
   *
   * @param connection The connection to receive from
   * @return A future that completes when the connection is closed
   */
  private static FlowFuture<Void> receiveResponses(FlowConnection connection) {
    return Flow.startActor(() -> {
      try {
        // Get the receive stream
        FlowStream<ByteBuffer> receiveStream = connection.receiveStream();
        
        // Receive messages in a loop
        while (true) {
          // Wait for a response
          ByteBuffer buffer = Flow.await(receiveStream.nextAsync());
          
          // Convert to string and display
          byte[] bytes = new byte[buffer.remaining()];
          buffer.get(bytes);
          String response = new String(bytes, StandardCharsets.UTF_8);
          
          System.out.println("Received response: " + response);
        }
      } catch (Exception e) {
        System.out.println("Receive stream closed: " + e.getMessage());
      }
      return null;
    });
  }
}