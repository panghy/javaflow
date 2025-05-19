package io.github.panghy.javaflow.rpc.examples.stream;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.RpcServiceInterface;
import io.github.panghy.javaflow.rpc.util.Pair;

/**
 * Example of bi-directional streaming in the JavaFlow RPC framework.
 * This example demonstrates how to implement a bi-directional chat service
 * where both client and server can send messages to each other.
 */
public class BiDirectionalStreamExample {

  /**
   * Interface for a chat service that supports bi-directional streaming.
   */
  public static class ChatServiceInterface implements RpcServiceInterface {
    
    /**
     * Message sent in the chat.
     */
    public static class ChatMessage {
      private final String sender;
      private final String content;
      
      public ChatMessage(String sender, String content) {
        this.sender = sender;
        this.content = content;
      }
      
      public String getSender() {
        return sender;
      }
      
      public String getContent() {
        return content;
      }
    }
    
    /**
     * Request to join a chat session.
     */
    public static class JoinRequest {
      private final String username;
      
      public JoinRequest(String username) {
        this.username = username;
      }
      
      public String getUsername() {
        return username;
      }
    }
    
    /**
     * Stream for join requests.
     * Each message contains a JoinRequest and a stream of messages from the client,
     * as well as a stream for messages to the client.
     */
    public final PromiseStream<Pair<JoinRequest, 
                               Pair<PromiseStream<ChatMessage>, 
                                   PromiseStream<ChatMessage>>>> join;
    
    /**
     * Creates a new ChatServiceInterface.
     */
    public ChatServiceInterface() {
      this.join = new PromiseStream<>();
    }
    
    /**
     * Convenience method to join a chat session.
     *
     * @param username The username to join with
     * @return A pair of streams: one for sending messages and one for receiving messages
     */
    public Pair<PromiseStream<ChatMessage>, FutureStream<ChatMessage>> joinChat(String username) {
      JoinRequest request = new JoinRequest(username);
      
      // Create streams for sending and receiving messages
      PromiseStream<ChatMessage> outgoing = new PromiseStream<>();
      PromiseStream<ChatMessage> incoming = new PromiseStream<>();
      
      // Send the join request with both streams
      join.send(new Pair<>(request, new Pair<>(outgoing, incoming)));
      
      // Return the outgoing stream and the incoming stream's future stream
      return new Pair<>(outgoing, incoming.getFutureStream());
    }
  }
  
  /**
   * Implementation of the chat service.
   */
  public static class ChatServiceImpl {
    
    private final ChatServiceInterface iface;
    
    /**
     * Creates a new ChatServiceImpl.
     */
    public ChatServiceImpl() {
      this.iface = new ChatServiceInterface();
      handleJoinRequests();
    }
    
    /**
     * Starts an actor to handle join requests.
     *
     * @return A future that completes when the actor finishes (should never happen)
     */
    private FlowFuture<Void> handleJoinRequests() {
      return Flow.startActor(() -> {
        try {
          while (true) {
            // Wait for the next join request
            Pair<ChatServiceInterface.JoinRequest, 
                 Pair<PromiseStream<ChatServiceInterface.ChatMessage>, 
                      PromiseStream<ChatServiceInterface.ChatMessage>>> request = 
                Flow.await(iface.join.getFutureStream().nextAsync());
            
            String username = request.first().getUsername();
            
            // Get the client's message stream and the server's message stream
            PromiseStream<ChatServiceInterface.ChatMessage> clientToServer =
                request.second().first();
            PromiseStream<ChatServiceInterface.ChatMessage> serverToClient =
                request.second().second();
            
            // Send a welcome message
            serverToClient.send(new ChatServiceInterface.ChatMessage(
                "Server", "Welcome to the chat, " + username + "!"));
            
            // Start an actor to handle messages from this client
            Flow.startActor(() -> handleClientMessages(username, clientToServer, serverToClient));
          }
        } catch (Exception e) {
          System.err.println("Error in join request handler: " + e.getMessage());
        }
        return null;
      });
    }
    
    /**
     * Handles messages from a client.
     *
     * @param username      The client's username
     * @param clientToServer The stream of messages from the client
     * @param serverToClient The stream of messages to the client
     * @return A future that completes when the actor finishes
     */
    private FlowFuture<Void> handleClientMessages(
        String username, 
        PromiseStream<ChatServiceInterface.ChatMessage> clientToServer,
        PromiseStream<ChatServiceInterface.ChatMessage> serverToClient) {
      
      return Flow.startActor(() -> {
        try {
          // Start a loop to process messages from this client
          while (true) {
            // Wait for the next message from the client
            ChatServiceInterface.ChatMessage message = 
                Flow.await(clientToServer.getFutureStream().nextAsync());
            
            // Process the message (in this simple example, just echo it back)
            String content = message.getContent();
            
            if ("quit".equalsIgnoreCase(content)) {
              // Client wants to quit
              serverToClient.send(new ChatServiceInterface.ChatMessage(
                  "Server", "Goodbye, " + username + "!"));
              serverToClient.close();
              break;
            }
            
            // Echo the message back
            serverToClient.send(new ChatServiceInterface.ChatMessage(
                "Server", "You said: " + content));
          }
        } catch (Exception e) {
          // The client stream was closed
          System.out.println("Client " + username + " disconnected: " + e.getMessage());
        }
        return null;
      });
    }
    
    /**
     * Gets the service interface for this implementation.
     *
     * @return The service interface
     */
    public ChatServiceInterface getInterface() {
      return iface;
    }
  }
  
  /**
   * Example client that connects to the chat service.
   */
  public static class ChatClient {
    
    private final String username;
    private final ChatServiceInterface chatService;
    private PromiseStream<ChatServiceInterface.ChatMessage> outgoingMessages;
    private FutureStream<ChatServiceInterface.ChatMessage> incomingMessages;
    
    /**
     * Creates a new ChatClient.
     *
     * @param username    The client's username
     * @param chatService The chat service to connect to
     */
    public ChatClient(String username, ChatServiceInterface chatService) {
      this.username = username;
      this.chatService = chatService;
    }
    
    /**
     * Connects to the chat service.
     *
     * @return A future that completes when connected
     */
    public FlowFuture<Void> connect() {
      // Join the chat
      Pair<PromiseStream<ChatServiceInterface.ChatMessage>, 
           FutureStream<ChatServiceInterface.ChatMessage>> streams = 
          chatService.joinChat(username);
      
      // Store the streams
      this.outgoingMessages = streams.first();
      this.incomingMessages = streams.second();
      
      // Start an actor to handle incoming messages
      return Flow.startActor(() -> {
        try {
          while (true) {
            // Wait for the next message from the server
            ChatServiceInterface.ChatMessage message = Flow.await(incomingMessages.nextAsync());
            
            // Print the message
            System.out.println(message.getSender() + ": " + message.getContent());
          }
        } catch (Exception e) {
          // The server closed the connection
          System.out.println("Disconnected from server: " + e.getMessage());
        }
        return null;
      });
    }
    
    /**
     * Sends a message to the server.
     *
     * @param content The message content
     */
    public void sendMessage(String content) {
      if (outgoingMessages != null) {
        outgoingMessages.send(new ChatServiceInterface.ChatMessage(username, content));
      }
    }
    
    /**
     * Disconnects from the chat service.
     */
    public void disconnect() {
      if (outgoingMessages != null) {
        sendMessage("quit");
        outgoingMessages.close();
        outgoingMessages = null;
      }
    }
  }
  
  /**
   * Demonstrates how to use the chat service with bi-directional streaming.
   * This would be run in a real application with an actual RPC transport.
   */
  public static void demonstrateChat() {
    // Create a chat service implementation
    ChatServiceImpl serviceImpl = new ChatServiceImpl();
    ChatServiceInterface service = serviceImpl.getInterface();
    
    // Create a client that connects to the service
    ChatClient client = new ChatClient("Alice", service);
    
    // Connect the client
    FlowFuture<Void> connectionFuture = client.connect();
    
    // Send some messages
    client.sendMessage("Hello, server!");
    client.sendMessage("How are you today?");
    client.sendMessage("quit");
    
    // Disconnect the client
    client.disconnect();
    
    // In a real application, we would await the connection future
    // to ensure the client fully disconnects
    // Flow.await(connectionFuture);
  }
}