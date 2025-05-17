package io.github.panghy.javaflow.io.network;

/**
 * Parameters for simulating network operations in the JavaFlow system.
 * These parameters control timing, errors, and other behaviors in the simulated network.
 * 
 * <p>NetworkSimulationParameters provides a comprehensive set of configuration options
 * for controlling the behavior of simulated network operations. This enables realistic
 * and deterministic testing of network code without actual network hardware, allowing
 * for the simulation of various network conditions such as:</p>
 * 
 * <ul>
 *   <li>Network latency and throughput limitations</li>
 *   <li>Random connection failures</li>
 *   <li>Random send/receive errors</li>
 *   <li>Connection drops and disconnects</li>
 * </ul>
 * 
 * <p>By adjusting these parameters, developers can test how their code behaves under
 * different network conditions, including error cases that would be difficult to
 * reliably reproduce with real network hardware.</p>
 * 
 * <p>The simulation parameters use a fluent interface for easy configuration:</p>
 * 
 * <pre>{@code
 * // Create parameters with high latency and intermittent errors
 * NetworkSimulationParameters params = new NetworkSimulationParameters()
 *     .setConnectDelay(0.5)             // 500ms connection delay
 *     .setSendBytesPerSecond(1_000_000) // 1MB/s throughput
 *     .setSendErrorProbability(0.1)     // 10% chance of send errors
 *     .setDisconnectProbability(0.01);  // 1% chance of disconnects
 * 
 * // Create a transport with these parameters
 * SimulatedFlowTransport transport = new SimulatedFlowTransport(params);
 * }</pre>
 * 
 * <p>Default values are provided for all parameters to simulate a reasonably
 * fast local network with no errors. These defaults can be overridden as needed.</p>
 * 
 * @see SimulatedFlowTransport
 * @see SimulatedFlowConnection
 */
public class NetworkSimulationParameters {

  // Delay in seconds for various operations
  private double connectDelay = 0.01;      // Base delay for connection establishment (10ms)
  private double sendDelay = 0.001;        // Base delay for send operations (1ms)
  private double receiveDelay = 0.001;     // Base delay for receive operations (1ms)
  
  // Throughput simulation
  private double sendBytesPerSecond = 10_000_000;  // 10MB/s
  private double receiveBytesPerSecond = 10_000_000;  // 10MB/s
  
  // Error injection probabilities (0.0 = never, 1.0 = always)
  private double connectErrorProbability = 0.0;
  private double sendErrorProbability = 0.0;
  private double receiveErrorProbability = 0.0;
  private double disconnectProbability = 0.0;
  
  /**
   * Creates simulation parameters with default values.
   */
  public NetworkSimulationParameters() {
    // Use defaults
  }
  
  /**
   * Gets the base delay for connection establishment in seconds.
   *
   * @return The connect delay in seconds
   */
  public double getConnectDelay() {
    return connectDelay;
  }
  
  /**
   * Sets the base delay for connection establishment in seconds.
   *
   * @param connectDelay The connect delay in seconds
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setConnectDelay(double connectDelay) {
    this.connectDelay = connectDelay;
    return this;
  }
  
  /**
   * Gets the base delay for send operations in seconds.
   *
   * @return The send delay in seconds
   */
  public double getSendDelay() {
    return sendDelay;
  }
  
  /**
   * Sets the base delay for send operations in seconds.
   *
   * @param sendDelay The send delay in seconds
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setSendDelay(double sendDelay) {
    this.sendDelay = sendDelay;
    return this;
  }
  
  /**
   * Gets the base delay for receive operations in seconds.
   *
   * @return The receive delay in seconds
   */
  public double getReceiveDelay() {
    return receiveDelay;
  }
  
  /**
   * Sets the base delay for receive operations in seconds.
   *
   * @param receiveDelay The receive delay in seconds
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setReceiveDelay(double receiveDelay) {
    this.receiveDelay = receiveDelay;
    return this;
  }
  
  /**
   * Gets the simulated send throughput in bytes per second.
   *
   * @return The send throughput in bytes per second
   */
  public double getSendBytesPerSecond() {
    return sendBytesPerSecond;
  }
  
  /**
   * Sets the simulated send throughput in bytes per second.
   *
   * @param sendBytesPerSecond The send throughput in bytes per second
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setSendBytesPerSecond(double sendBytesPerSecond) {
    this.sendBytesPerSecond = sendBytesPerSecond;
    return this;
  }
  
  /**
   * Gets the simulated receive throughput in bytes per second.
   *
   * @return The receive throughput in bytes per second
   */
  public double getReceiveBytesPerSecond() {
    return receiveBytesPerSecond;
  }
  
  /**
   * Sets the simulated receive throughput in bytes per second.
   *
   * @param receiveBytesPerSecond The receive throughput in bytes per second
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setReceiveBytesPerSecond(double receiveBytesPerSecond) {
    this.receiveBytesPerSecond = receiveBytesPerSecond;
    return this;
  }
  
  /**
   * Gets the probability of connection operations failing.
   *
   * @return The connect error probability (0.0-1.0)
   */
  public double getConnectErrorProbability() {
    return connectErrorProbability;
  }
  
  /**
   * Sets the probability of connection operations failing.
   *
   * @param connectErrorProbability The connect error probability (0.0-1.0)
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setConnectErrorProbability(double connectErrorProbability) {
    this.connectErrorProbability = connectErrorProbability;
    return this;
  }
  
  /**
   * Gets the probability of send operations failing.
   *
   * @return The send error probability (0.0-1.0)
   */
  public double getSendErrorProbability() {
    return sendErrorProbability;
  }
  
  /**
   * Sets the probability of send operations failing.
   *
   * @param sendErrorProbability The send error probability (0.0-1.0)
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setSendErrorProbability(double sendErrorProbability) {
    this.sendErrorProbability = sendErrorProbability;
    return this;
  }
  
  /**
   * Gets the probability of receive operations failing.
   *
   * @return The receive error probability (0.0-1.0)
   */
  public double getReceiveErrorProbability() {
    return receiveErrorProbability;
  }
  
  /**
   * Sets the probability of receive operations failing.
   *
   * @param receiveErrorProbability The receive error probability (0.0-1.0)
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setReceiveErrorProbability(double receiveErrorProbability) {
    this.receiveErrorProbability = receiveErrorProbability;
    return this;
  }
  
  /**
   * Gets the probability of a connection randomly disconnecting during an operation.
   *
   * @return The disconnect probability (0.0-1.0)
   */
  public double getDisconnectProbability() {
    return disconnectProbability;
  }
  
  /**
   * Sets the probability of a connection randomly disconnecting during an operation.
   *
   * @param disconnectProbability The disconnect probability (0.0-1.0)
   * @return This instance for chaining
   */
  public NetworkSimulationParameters setDisconnectProbability(double disconnectProbability) {
    this.disconnectProbability = disconnectProbability;
    return this;
  }
  
  /**
   * Calculates the simulated delay for a send operation of the given size.
   *
   * @param sizeBytes The number of bytes being sent
   * @return The simulated delay in seconds
   */
  public double calculateSendDelay(int sizeBytes) {
    return sendDelay + (sizeBytes / sendBytesPerSecond);
  }
  
  /**
   * Calculates the simulated delay for a receive operation of the given size.
   *
   * @param sizeBytes The number of bytes being received
   * @return The simulated delay in seconds
   */
  public double calculateReceiveDelay(int sizeBytes) {
    return receiveDelay + (sizeBytes / receiveBytesPerSecond);
  }
}