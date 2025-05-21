package io.github.panghy.javaflow.rpc;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Provider for FlowRpcTransport instances in the JavaFlow actor system.
 * This class manages the default transport instance and provides a way
 * to configure which transport implementation to use.
 * 
 * <p>The FlowRpcProvider is responsible for creating and managing the
 * appropriate FlowRpcTransport instances. It automatically selects between
 * real and simulated transports based on the current execution mode.</p>
 * 
 * <p>This class is typically not used directly by application code.
 * Instead, use {@link FlowRpcTransport#getInstance()} to get the default
 * transport instance.</p>
 */
public class FlowRpcProvider {

  // Holds the default transport instance
  private static final AtomicReference<FlowRpcTransport> defaultTransport = 
      new AtomicReference<>();
  
  // Whether we're in simulation mode
  private static boolean simulationMode = false;
  
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
   * Sets whether the system is running in simulation mode.
   * This affects which transport implementation is used by default.
   *
   * @param simulation true if simulation mode is enabled, false otherwise
   */
  public static void setSimulationMode(boolean simulation) {
    simulationMode = simulation;
    // Clear the default transport so it will be recreated
    defaultTransport.set(null);
  }
  
  /**
   * Checks if the system is running in simulation mode.
   *
   * @return true if simulation mode is enabled, false otherwise
   */
  public static boolean isSimulationMode() {
    return simulationMode;
  }
  
  /**
   * Creates the default transport based on the current execution mode.
   *
   * @return A new FlowRpcTransport instance
   */
  private static FlowRpcTransport createDefaultTransport() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}