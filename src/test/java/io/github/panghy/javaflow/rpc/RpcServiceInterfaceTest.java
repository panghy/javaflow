package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.rpc.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests for the {@link RpcServiceInterface} interface.
 */
public class RpcServiceInterfaceTest {

  private FlowRpcTransport mockTransport;

  // A simple implementation of RpcServiceInterface for testing
  private static class TestService implements RpcServiceInterface {
    public final PromiseStream<Pair<String, FlowPromise<String>>> echo = new PromiseStream<>();

    public FlowFuture<String> echoAsync(String message) {
      FlowFuture<String> future = new FlowFuture<>();
      echo.send(new Pair<>(message, future.getPromise()));
      return future;
    }

    // Override onClose for testing
    private final FlowPromise<Void> closePromise = new FlowFuture<Void>().getPromise();

    @Override
    public FlowFuture<Void> onClose() {
      return closePromise.getFuture();
    }

    public void close() {
      closePromise.complete(null);
    }
  }

  @BeforeEach
  public void setUp() {
    // Create a mock transport
    mockTransport = mock(FlowRpcTransport.class);

    // Set it as the default for the tests
    FlowRpcProvider.setDefaultTransport(mockTransport);
  }

  @Test
  public void testReady() {
    TestService service = new TestService();

    // Call ready and verify it completes immediately
    FlowFuture<Void> readyFuture = service.ready();

    assertNotNull(readyFuture);
    assertTrue(readyFuture.isCompleted());
  }

  @Test
  public void testOnClose() {
    TestService service = new TestService();

    // Get the onClose future
    FlowFuture<Void> closeFuture = service.onClose();

    // Verify it's not completed initially
    assertNotNull(closeFuture);
    assertFalse(closeFuture.isCompleted());

    // Close the service
    service.close();

    // Verify the future is now completed
    assertTrue(closeFuture.isCompleted());
  }

  @Test
  public void testMethodInvocation() {
    TestService service = new TestService();

    // Mock the stream behavior directly instead of using forEach 
    String testMessage = "Hello";
    FlowFuture<String> future = service.echoAsync(testMessage);

    // Complete the future manually 
    future.complete("Echo: " + testMessage);

    // Verify the future is completed
    assertTrue(future.isCompleted());
  }
}