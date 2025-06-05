package io.github.panghy.javaflow.rpc;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Provider for FlowRpcTransport instances in the JavaFlow actor system.
 * This class manages the default transport instance and provides a way
 * to configure which transport implementation to use.
 * 
 * <p>The FlowRpcProvider is responsible for creating and managing the
 * default FlowRpcTransport instance. The transport implementation will
 * automatically use the appropriate underlying network transport (real
 * or simulated) based on the Flow execution mode.</p>
 * 
 * <p>This class is typically not used directly by application code.
 * Instead, use {@link FlowRpcTransport#getInstance()} to get the default
 * transport instance.</p>
 */
public class FlowRpcProvider {

  // Holds the default transport instance
  private static final AtomicReference<FlowRpcTransport> defaultTransport = 
      new AtomicReference<>();
  
  // Private constructor to prevent instantiation
  private FlowRpcProvider() {
  }
  
  /**
   * Gets the default FlowRpcTransport instance.
   * The first call to this method will create the appropriate transport
   * based on the current execution mode.
   *
   * @return The default FlowRpcTransport instance
   */
  public static FlowRpcTransport getDefaultTransport() {
    FlowRpcTransport transport = defaultTransport.get();
    if (transport == null) {
      synchronized (FlowRpcProvider.class) {
        transport = defaultTransport.get();
        if (transport == null) {
          // This will be implemented to create the appropriate transport
          // based on whether we're in simulation mode
          transport = createDefaultTransport();
          defaultTransport.set(transport);
        }
      }
    }
    return transport;
  }
  
  /**
   * Sets the default FlowRpcTransport instance.
   * This is typically used in testing to provide a mock or custom transport.
   *
   * @param transport The transport to set as default
   */
  public static void setDefaultTransport(FlowRpcTransport transport) {
    defaultTransport.set(transport);
  }
  
  
  /**
   * Creates the default transport instance.
   *
   * @return A new FlowRpcTransport instance
   */
  private static FlowRpcTransport createDefaultTransport() {
    // Create the RPC transport implementation
    // The FlowRpcTransportImpl will automatically use the correct
    // underlying network transport based on Flow's execution mode
    return new FlowRpcTransportImpl();
  }
}