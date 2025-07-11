package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.simulation.SimulationConfiguration;

/**
 * Parameters for simulating file system operations in the JavaFlow system.
 * These parameters control timing, errors, and other behaviors in the simulated file system.
 */
public class SimulationParameters {
  
  // Delay in seconds for various operations
  private double readDelay = 0.001;      // Base delay for reads (1ms)
  private double writeDelay = 0.002;     // Base delay for writes (2ms)
  private double metadataDelay = 0.0005; // Base delay for metadata operations (0.5ms)
  
  // Throughput simulation
  private double readBytesPerSecond = 100_000_000;  // 100MB/s
  private double writeBytesPerSecond = 50_000_000;  // 50MB/s
  
  // Error injection probabilities (0.0 = never, 1.0 = always)
  private double readErrorProbability = 0.0;
  private double writeErrorProbability = 0.0;
  private double metadataErrorProbability = 0.0;
  
  // Additional fault injection parameters
  private double diskFullProbability = 0.0;      // Probability of disk full errors
  private double corruptionProbability = 0.0;    // Probability of data corruption
  
  /**
   * Creates simulation parameters with default values.
   */
  public SimulationParameters() {
    // Use defaults
  }
  
  /**
   * Gets the base delay for read operations in seconds.
   *
   * @return The read delay in seconds
   */
  public double getReadDelay() {
    return readDelay;
  }
  
  /**
   * Sets the base delay for read operations in seconds.
   *
   * @param readDelay The read delay in seconds
   * @return This instance for chaining
   */
  public SimulationParameters setReadDelay(double readDelay) {
    this.readDelay = readDelay;
    return this;
  }
  
  /**
   * Gets the base delay for write operations in seconds.
   *
   * @return The write delay in seconds
   */
  public double getWriteDelay() {
    return writeDelay;
  }
  
  /**
   * Sets the base delay for write operations in seconds.
   *
   * @param writeDelay The write delay in seconds
   * @return This instance for chaining
   */
  public SimulationParameters setWriteDelay(double writeDelay) {
    this.writeDelay = writeDelay;
    return this;
  }
  
  /**
   * Gets the base delay for metadata operations in seconds.
   *
   * @return The metadata delay in seconds
   */
  public double getMetadataDelay() {
    return metadataDelay;
  }
  
  /**
   * Sets the base delay for metadata operations in seconds.
   *
   * @param metadataDelay The metadata delay in seconds
   * @return This instance for chaining
   */
  public SimulationParameters setMetadataDelay(double metadataDelay) {
    this.metadataDelay = metadataDelay;
    return this;
  }
  
  /**
   * Gets the simulated read throughput in bytes per second.
   *
   * @return The read throughput in bytes per second
   */
  public double getReadBytesPerSecond() {
    return readBytesPerSecond;
  }
  
  /**
   * Sets the simulated read throughput in bytes per second.
   *
   * @param readBytesPerSecond The read throughput in bytes per second
   * @return This instance for chaining
   */
  public SimulationParameters setReadBytesPerSecond(double readBytesPerSecond) {
    this.readBytesPerSecond = readBytesPerSecond;
    return this;
  }
  
  /**
   * Gets the simulated write throughput in bytes per second.
   *
   * @return The write throughput in bytes per second
   */
  public double getWriteBytesPerSecond() {
    return writeBytesPerSecond;
  }
  
  /**
   * Sets the simulated write throughput in bytes per second.
   *
   * @param writeBytesPerSecond The write throughput in bytes per second
   * @return This instance for chaining
   */
  public SimulationParameters setWriteBytesPerSecond(double writeBytesPerSecond) {
    this.writeBytesPerSecond = writeBytesPerSecond;
    return this;
  }
  
  /**
   * Gets the probability of read operations failing.
   *
   * @return The read error probability (0.0-1.0)
   */
  public double getReadErrorProbability() {
    return readErrorProbability;
  }
  
  /**
   * Sets the probability of read operations failing.
   *
   * @param readErrorProbability The read error probability (0.0-1.0)
   * @return This instance for chaining
   */
  public SimulationParameters setReadErrorProbability(double readErrorProbability) {
    this.readErrorProbability = readErrorProbability;
    return this;
  }
  
  /**
   * Gets the probability of write operations failing.
   *
   * @return The write error probability (0.0-1.0)
   */
  public double getWriteErrorProbability() {
    return writeErrorProbability;
  }
  
  /**
   * Sets the probability of write operations failing.
   *
   * @param writeErrorProbability The write error probability (0.0-1.0)
   * @return This instance for chaining
   */
  public SimulationParameters setWriteErrorProbability(double writeErrorProbability) {
    this.writeErrorProbability = writeErrorProbability;
    return this;
  }
  
  /**
   * Gets the probability of metadata operations failing.
   *
   * @return The metadata error probability (0.0-1.0)
   */
  public double getMetadataErrorProbability() {
    return metadataErrorProbability;
  }
  
  /**
   * Sets the probability of metadata operations failing.
   *
   * @param metadataErrorProbability The metadata error probability (0.0-1.0)
   * @return This instance for chaining
   */
  public SimulationParameters setMetadataErrorProbability(double metadataErrorProbability) {
    this.metadataErrorProbability = metadataErrorProbability;
    return this;
  }
  
  /**
   * Calculates the simulated delay for a read operation of the given size.
   *
   * @param sizeBytes The number of bytes being read
   * @return The simulated delay in seconds
   */
  public double calculateReadDelay(int sizeBytes) {
    return readDelay + (sizeBytes / readBytesPerSecond);
  }
  
  /**
   * Calculates the simulated delay for a write operation of the given size.
   *
   * @param sizeBytes The number of bytes being written
   * @return The simulated delay in seconds
   */
  public double calculateWriteDelay(int sizeBytes) {
    return writeDelay + (sizeBytes / writeBytesPerSecond);
  }
  
  /**
   * Gets the probability of disk full errors.
   *
   * @return The disk full probability (0.0-1.0)
   */
  public double getDiskFullProbability() {
    return diskFullProbability;
  }
  
  /**
   * Sets the probability of disk full errors.
   *
   * @param diskFullProbability The disk full probability (0.0-1.0)
   * @return This instance for chaining
   */
  public SimulationParameters setDiskFullProbability(double diskFullProbability) {
    this.diskFullProbability = diskFullProbability;
    return this;
  }
  
  /**
   * Gets the probability of data corruption.
   *
   * @return The corruption probability (0.0-1.0)
   */
  public double getCorruptionProbability() {
    return corruptionProbability;
  }
  
  /**
   * Sets the probability of data corruption.
   *
   * @param corruptionProbability The corruption probability (0.0-1.0)
   * @return This instance for chaining
   */
  public SimulationParameters setCorruptionProbability(double corruptionProbability) {
    this.corruptionProbability = corruptionProbability;
    return this;
  }
  
  /**
   * Creates SimulationParameters from a SimulationConfiguration.
   * This factory method integrates file system simulation with the unified configuration.
   *
   * @param config The simulation configuration (may be null)
   * @return SimulationParameters configured from the SimulationConfiguration
   */
  public static SimulationParameters fromSimulationConfig(SimulationConfiguration config) {
    SimulationParameters params = new SimulationParameters();
    
    if (config != null) {
      // Apply file system-related configuration
      params.setReadDelay(config.getDiskReadDelay());
      params.setWriteDelay(config.getDiskWriteDelay());
      params.setMetadataDelay(config.getDiskReadDelay() * 0.5); // Metadata is typically faster
      
      // Apply throughput settings
      params.setReadBytesPerSecond(config.getDiskBytesPerSecond());
      params.setWriteBytesPerSecond(config.getDiskBytesPerSecond() * 0.5); // Writes typically slower
      
      // Apply error probabilities
      params.setReadErrorProbability(config.getDiskFailureProbability());
      params.setWriteErrorProbability(config.getDiskFailureProbability());
      params.setMetadataErrorProbability(config.getDiskFailureProbability() * 0.5);
      
      // Apply additional fault injection
      params.setDiskFullProbability(config.getDiskFullProbability());
      params.setCorruptionProbability(config.getDiskCorruptionProbability());
    }
    
    return params;
  }
}