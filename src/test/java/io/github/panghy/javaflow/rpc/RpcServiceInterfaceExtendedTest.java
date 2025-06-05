package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.Endpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional tests for the {@link RpcServiceInterface} interface
 * to improve code coverage.
 */
public class RpcServiceInterfaceExtendedTest extends AbstractFlowTest {

  /**
   * A minimal test implementation of RpcServiceInterface that doesn't override
   * any default methods.
   */
  private static class MinimalService implements RpcServiceInterface {
    public final PromiseStream<String> eventStream = new PromiseStream<>();
  }

  /**
   * A service implementation that overrides the registerAsLoopback method.
   */
  private static class CustomRegistrationService implements RpcServiceInterface {
    private EndpointId lastRegisteredId;
    private boolean registerCalled = false;

    @Override
    public void registerAsLoopback(EndpointId endpointId) {
      lastRegisteredId = endpointId;
      registerCalled = true;
    }

    public EndpointId getLastRegisteredId() {
      return lastRegisteredId;
    }

    public boolean wasRegisterCalled() {
      return registerCalled;
    }
  }

  /**
   * A service implementation that overrides the ready method.
   */
  private static class CustomReadyService implements RpcServiceInterface {
    private FlowPromise<Void> readyPromise = new FlowFuture<Void>().getPromise();

    @Override
    public FlowFuture<Void> ready() {
      return readyPromise.getFuture();
    }

    public void completeReady() {
      readyPromise.complete(null);
    }

    public void failReady(Throwable t) {
      readyPromise.completeExceptionally(t);
    }
  }

  /**
   * Tests a custom implementation of registerAsLoopback.
   */
  @Test
  void testCustomRegisterAsLoopback() {
    CustomRegistrationService service = new CustomRegistrationService();

    // Call registerAsLoopback
    EndpointId testId = new EndpointId("custom-service");
    service.registerAsLoopback(testId);

    // Verify our implementation was called
    assertTrue(service.wasRegisterCalled());
    assertEquals(testId, service.getLastRegisteredId());
  }


  /**
   * Tests the default implementation of ready().
   */
  @Test
  void testDefaultReady() {
    MinimalService service = new MinimalService();

    // Call ready
    FlowFuture<Void> readyFuture = service.ready();

    // Verify it returned a completed future
    assertNotNull(readyFuture);
    assertTrue(readyFuture.isCompleted());
    assertFalse(readyFuture.isCompletedExceptionally());
  }

  /**
   * Tests a custom implementation of ready().
   */
  @Test
  void testCustomReady() {
    CustomReadyService service = new CustomReadyService();

    // Call ready and verify it's not completed yet
    FlowFuture<Void> readyFuture = service.ready();
    assertNotNull(readyFuture);
    assertFalse(readyFuture.isCompleted());

    // Complete the future
    service.completeReady();

    // Verify it's now completed
    assertTrue(readyFuture.isCompleted());
    assertFalse(readyFuture.isCompletedExceptionally());

    // Test with failure
    CustomReadyService service2 = new CustomReadyService();
    FlowFuture<Void> failingFuture = service2.ready();
    service2.failReady(new RuntimeException("Test exception"));

    // Verify it completed exceptionally
    assertTrue(failingFuture.isCompleted());
    assertTrue(failingFuture.isCompletedExceptionally());
  }

  /**
   * Tests the default implementation of onClose().
   */
  @Test
  void testDefaultOnClose() {
    MinimalService service = new MinimalService();

    // Get the default onClose future
    FlowFuture<Void> closeFuture = service.onClose();

    // The default implementation should return a new future that never completes
    assertNotNull(closeFuture);
    assertFalse(closeFuture.isCompleted());

    // Get it again - should be a different future each time
    FlowFuture<Void> closeFuture2 = service.onClose();
    assertNotNull(closeFuture2);
    assertFalse(closeFuture2.isCompleted());

    // They should be different futures
    assertFalse(closeFuture == closeFuture2);
  }

  /**
   * A simple mock implementation of FlowRpcTransport for testing.
   */
  private static class MockFlowRpcTransport implements FlowRpcTransport {
    private EndpointId lastRegisteredId;
    private Object lastRegisteredService;
    private boolean registerEndpointCalled = false;

    @Override
    public EndpointResolver getEndpointResolver() {
      // Not used in these tests
      return null;
    }

    @Override
    public <T> T getRpcStub(EndpointId id, Class<T> interfaceClass) {
      // Not used in these tests
      return null;
    }

    @Override
    public <T> T getRpcStub(Endpoint endpoint, Class<T> interfaceClass) {
      // Not used in these tests
      return null;
    }

    @Override
    public <T> T getLocalStub(EndpointId id, Class<T> interfaceClass) {
      // Not used in these tests
      return null;
    }

    @Override
    public FlowFuture<Void> close() {
      // Not used in these tests
      return new FlowFuture<>();
    }

    public EndpointId getLastRegisteredId() {
      return lastRegisteredId;
    }

    public Object getLastRegisteredService() {
      return lastRegisteredService;
    }

    public boolean wasRegisterEndpointCalled() {
      return registerEndpointCalled;
    }
  }
}