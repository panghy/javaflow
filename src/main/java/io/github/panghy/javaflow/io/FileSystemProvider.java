package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.Flow;

/**
 * Provider for file system implementations. Manages which implementation (real or simulated)
 * should be used based on the current mode.
 */
public class FileSystemProvider {
  // Singleton instances
  private static FlowFileSystem defaultFileSystem;
  private static FlowFileSystem realFileSystem;
  private static FlowFileSystem simulatedFileSystem;
  
  // Lock for lazy initialization
  private static final Object LOCK = new Object();
  
  private FileSystemProvider() {
    // Prevent instantiation
  }
  
  /**
   * Gets the default file system based on the current mode (real or simulation).
   *
   * @return The default file system implementation
   */
  public static FlowFileSystem getDefaultFileSystem() {
    if (defaultFileSystem == null) {
      synchronized (LOCK) {
        if (defaultFileSystem == null) {
          if (Flow.isSimulated()) {
            defaultFileSystem = getSimulatedFileSystem();
          } else {
            defaultFileSystem = getRealFileSystem();
          }
        }
      }
    }
    return defaultFileSystem;
  }
  
  /**
   * Gets the real file system implementation.
   *
   * @return The real file system implementation
   */
  public static FlowFileSystem getRealFileSystem() {
    if (realFileSystem == null) {
      synchronized (LOCK) {
        if (realFileSystem == null) {
          // This class will be implemented as part of the real file system implementation
          realFileSystem = new RealFlowFileSystem();
        }
      }
    }
    return realFileSystem;
  }
  
  /**
   * Gets the simulated file system implementation.
   *
   * @return The simulated file system implementation
   */
  public static FlowFileSystem getSimulatedFileSystem() {
    if (simulatedFileSystem == null) {
      synchronized (LOCK) {
        if (simulatedFileSystem == null) {
          // This class will be implemented as part of the simulated file system implementation
          simulatedFileSystem = new SimulatedFlowFileSystem(new SimulationParameters());
        }
      }
    }
    return simulatedFileSystem;
  }
  
  /**
   * Resets the file system provider, clearing any cached instances.
   * This is primarily used for testing purposes.
   */
  public static void reset() {
    synchronized (LOCK) {
      defaultFileSystem = null;
      realFileSystem = null;
      simulatedFileSystem = null;
    }
  }
  
  /**
   * Sets the default file system implementation.
   * This is primarily used for testing purposes.
   *
   * @param fileSystem The file system implementation to use
   */
  public static void setDefaultFileSystem(FlowFileSystem fileSystem) {
    synchronized (LOCK) {
      defaultFileSystem = fileSystem;
    }
  }
}