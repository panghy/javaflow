package io.github.panghy.javaflow.rpc.util;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.rpc.EndpointId;
import io.github.panghy.javaflow.rpc.error.RpcTimeoutException;
import org.junit.jupiter.api.Test;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RpcTimeoutUtil.
 */
public class RpcTimeoutUtilTest extends AbstractFlowTest {

  @Test
  public void testTimeoutUtilDirectly() {
    // Start an actor to test the timeout util
    FlowFuture<Void> testFuture = startActor(() -> {
      // Create a future that never completes
      FlowFuture<String> neverCompletingFuture = new FlowFuture<>();
      
      // Apply timeout
      FlowFuture<String> timeoutFuture = RpcTimeoutUtil.withTimeout(
          neverCompletingFuture, 
          new EndpointId("test"), 
          "testMethod", 
          100);
      
      // This should throw RpcTimeoutException wrapped in ExecutionException
      assertThatThrownBy(() -> await(timeoutFuture))
          .isInstanceOf(java.util.concurrent.ExecutionException.class)
          .hasCauseInstanceOf(RpcTimeoutException.class)
          .satisfies(thrown -> {
            RpcTimeoutException e = (RpcTimeoutException) thrown.getCause();
            assertThat(e.getEndpointId()).isEqualTo(new EndpointId("test"));
            assertThat(e.getMethodName()).isEqualTo("testMethod");
            assertThat(e.getTimeoutMs()).isEqualTo(100);
            assertThat(e.getTimeoutType()).isEqualTo(RpcTimeoutException.TimeoutType.UNARY_RPC);
          });
      
      return null;
    });
    
    // Run until done
    pumpAndAdvanceTimeUntilDone(testFuture);
    
    // Verify the test future completed successfully (the test itself passed)
    assertThat(testFuture.isDone()).isTrue();
    assertThat(testFuture.isCompletedExceptionally()).isFalse();
  }
}