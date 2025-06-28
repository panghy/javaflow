package io.github.panghy.javaflow.rpc;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.PromiseStream;
import org.junit.jupiter.api.Test;
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
   * A service implementation that overrides the ready method.
   */
  private static class CustomReadyService implements RpcServiceInterface {
    private FlowPromise<Void> readyPromise = new CompletableFuture<Void>().getPromise();

    @Override
    public CompletableFuture<Void> ready() {
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
   * Tests the default implementation of ready().
   */
  @Test
  void testDefaultReady() {
    MinimalService service = new MinimalService();

    // Call ready
    CompletableFuture<Void> readyFuture = service.ready();

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
    CompletableFuture<Void> readyFuture = service.ready();
    assertNotNull(readyFuture);
    assertFalse(readyFuture.isCompleted());

    // Complete the future
    service.completeReady();

    // Verify it's now completed
    assertTrue(readyFuture.isCompleted());
    assertFalse(readyFuture.isCompletedExceptionally());

    // Test with failure
    CustomReadyService service2 = new CustomReadyService();
    CompletableFuture<Void> failingFuture = service2.ready();
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
    CompletableFuture<Void> closeFuture = service.onClose();

    // The default implementation should return a new future that never completes
    assertNotNull(closeFuture);
    assertFalse(closeFuture.isCompleted());

    // Get it again - should be a different future each time
    CompletableFuture<Void> closeFuture2 = service.onClose();
    assertNotNull(closeFuture2);
    assertFalse(closeFuture2.isCompleted());

    // They should be different futures
    assertFalse(closeFuture == closeFuture2);
  }

}