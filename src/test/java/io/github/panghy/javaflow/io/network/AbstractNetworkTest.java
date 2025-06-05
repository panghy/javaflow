package io.github.panghy.javaflow.io.network;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for network tests that provides common utility methods
 * for both simulated and real network testing.
 * 
 * <p>This class extends AbstractFlowTest for simulated network tests while also
 * providing utility methods for real network tests.</p>
 */
public abstract class AbstractNetworkTest extends AbstractFlowTest {

  /**
   * Creates a ByteBuffer from a UTF-8 encoded string.
   *
   * @param message The message to convert to a ByteBuffer
   * @return A ByteBuffer containing the UTF-8 encoded message
   */
  protected ByteBuffer createBuffer(String message) {
    return ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Converts a ByteBuffer to a UTF-8 encoded string.
   *
   * @param buffer The ByteBuffer to convert
   * @return A string decoded from the ByteBuffer using UTF-8 encoding
   */
  protected String bufferToString(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Waits for a future to complete, using the appropriate method based on whether
   * we're in a simulated or real environment.
   *
   * @param <T> The type of the future's result
   * @param future The future to wait for
   * @param simulated Whether we're in a simulated environment
   * @param timeout The timeout for real network operations (ignored in simulation)
   * @param unit The time unit for the timeout (ignored in simulation)
   * @return The result of the future
   * @throws Exception If the future does not complete or completes with an exception
   */
  protected <T> T awaitFuture(FlowFuture<T> future, boolean simulated, long timeout, TimeUnit unit) 
      throws Exception {
    if (simulated) {
      pumpUntilDone(future);
      if (!future.isDone()) {
        throw new AssertionError("Future did not complete after simulation");
      }
      return future.getNow();
    } else {
      return future.toCompletableFuture().get(timeout, unit);
    }
  }

  /**
   * Waits for a future to complete, using the appropriate method based on whether
   * we're in a simulated or real environment, with a default timeout of 5 seconds.
   *
   * @param <T> The type of the future's result
   * @param future The future to wait for
   * @param simulated Whether we're in a simulated environment
   * @return The result of the future
   * @throws Exception If the future does not complete or completes with an exception
   */
  protected <T> T awaitFuture(FlowFuture<T> future, boolean simulated) throws Exception {
    return awaitFuture(future, simulated, 5, TimeUnit.SECONDS);
  }

  /**
   * Creates a unique port number for tests based on the test method name's hashcode.
   * This helps avoid port conflicts between concurrent test runs.
   *
   * @param basePort The base port number (e.g., 10000)
   * @param testName The test method name, typically from getClass().getSimpleName()
   * @return A port number derived from the base port and test name
   */
  protected int uniqueTestPort(int basePort, String testName) {
    int portOffset = Math.abs(testName.hashCode() % 10000);
    return basePort + portOffset;
  }
}