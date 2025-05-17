package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.Flow;
import java.io.IOException;

/**
 * Provider for the default FlowTransport implementation.
 * This class is responsible for returning the appropriate transport
 * implementation based on the current mode (real or simulation).
 * 
 * <p>TransportProvider serves as a factory for {@link FlowTransport} instances,
 * automatically selecting the appropriate implementation based on whether
 * JavaFlow is running in simulation mode or not. This allows application code
 * to be written once and run in either mode without changes.</p>
 * 
 * <p>In normal operation, it returns a {@link RealFlowTransport} that performs
 * actual network I/O operations. In simulation mode, it returns a {@link SimulatedFlowTransport}
 * that simulates network behavior in memory with configurable parameters.</p>
 * 
 * <p>The provider maintains a singleton instance of the transport for each mode,
 * ensuring that all code uses the same transport instance. This allows for consistent
 * behavior and resource management across an application.</p>
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * // Get the default transport for the current mode
 * FlowTransport transport = TransportProvider.getDefaultTransport();
 * 
 * // For testing, you can set a specific transport
 * TransportProvider.setDefaultTransport(customTransport);
 * }</pre>
 * 
 * <p>While this class can be used directly, most code should use
 * {@link FlowTransport#getDefault()} instead, which delegates to this provider.</p>
 * 
 * @see FlowTransport
 * @see RealFlowTransport
 * @see SimulatedFlowTransport
 */
public final class TransportProvider {

  private static volatile FlowTransport defaultTransport;

  private TransportProvider() {
    // Utility class should not be instantiated
  }

  /**
   * Gets the default FlowTransport instance.
   * In normal operation, this returns a real transport implementation.
   * In simulation mode, this returns a simulated transport.
   *
   * @return The default FlowTransport instance
   */
  public static FlowTransport getDefaultTransport() {
    if (defaultTransport == null) {
      synchronized (TransportProvider.class) {
        if (defaultTransport == null) {
          defaultTransport = createDefaultTransport();
        }
      }
    }
    return defaultTransport;
  }

  /**
   * Sets the default FlowTransport instance.
   * This is primarily for testing purposes.
   *
   * @param transport The transport to use as the default
   */
  public static void setDefaultTransport(FlowTransport transport) {
    synchronized (TransportProvider.class) {
      defaultTransport = transport;
    }
  }

  /**
   * Creates the default FlowTransport implementation based on the current mode.
   *
   * @return A FlowTransport implementation
   */
  private static FlowTransport createDefaultTransport() {
    if (Flow.isSimulated()) {
      return new SimulatedFlowTransport();
    } else {
      try {
        return new RealFlowTransport();
      } catch (IOException e) {
        throw new RuntimeException("Failed to create real transport implementation", e);
      }
    }
  }
}