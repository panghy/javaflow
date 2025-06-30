package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.FlowTransport;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.rpc.error.RpcTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.delay;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for RPC timeout functionality.
 */
public class FlowRpcTimeoutTest extends AbstractFlowTest {

  private FlowTransport mockTransport;
  private FlowRpcTransportImpl rpcTransport;

  @BeforeEach
  void setUp() {
    mockTransport = mock(FlowTransport.class);
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testUnaryRpcTimeout() {
    // Configure with a short timeout
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .unaryRpcTimeoutMs(100) // 100ms timeout
        .build();

    rpcTransport = new FlowRpcTransportImpl(mockTransport, config);

    // Create a test service interface
    interface TestService {
      String slowMethod();
    }

    EndpointId serviceId = new EndpointId("test-service");
    Endpoint remoteEndpoint = new Endpoint("localhost", 8080);

    // Register as remote endpoint
    rpcTransport.getEndpointResolver().registerRemoteEndpoint(serviceId, remoteEndpoint);

    // Set up mock transport to return a connection that never responds
    FlowConnection mockConnection = mock(FlowConnection.class);
    when(mockConnection.getRemoteEndpoint()).thenReturn(remoteEndpoint);
    when(mockConnection.isOpen()).thenReturn(true);
    when(mockConnection.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    // Never complete the receive - simulating a slow/hung server
    CompletableFuture<ByteBuffer> neverCompletingFuture = new CompletableFuture<>();
    when(mockConnection.receive(anyInt())).thenReturn(neverCompletingFuture);
    when(mockConnection.closeFuture()).thenReturn(new CompletableFuture<>());
    when(mockTransport.connect(any())).thenReturn(CompletableFuture.completedFuture(mockConnection));

    // Get a remote stub and call the slow method
    TestService stub = rpcTransport.getRpcStub(serviceId, TestService.class);

    CompletableFuture<String> resultFuture = startActor(() -> {
      return stub.slowMethod();
    });

    // Run the scheduler - should timeout after 100ms
    // Use pumpUntilDone to ensure all tasks complete
    pumpAndAdvanceTimeUntilDone(resultFuture);

    // Verify timeout exception
    assertThat(resultFuture.isDone()).isTrue();
    assertThatThrownBy(() -> resultFuture.getNow(null))
        .isInstanceOf(java.util.concurrent.CompletionException.class)
        .hasCauseInstanceOf(RpcTimeoutException.class)
        .satisfies(thrown -> {
          RpcTimeoutException e = (RpcTimeoutException) thrown.getCause();
          assertThat(e.getMessage()).contains("timed out after 100ms");
          assertThat(e.getEndpointId()).isEqualTo(serviceId);
        });
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testVoidMethodsDoNotTimeout() {
    // Configure with a short timeout
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .unaryRpcTimeoutMs(100) // 100ms timeout
        .build();

    rpcTransport = new FlowRpcTransportImpl(mockTransport, config);

    // Create a test service interface with void method
    interface TestService {
      void fireAndForget(String message);
    }

    // Register implementation
    TestService impl = message -> {
      // Void method - nothing to do
    };

    EndpointId serviceId = new EndpointId("test-service");
    LocalEndpoint localEndpoint = LocalEndpoint.localhost(8080);

    rpcTransport.getEndpointResolver().registerLocalEndpoint(serviceId, impl, localEndpoint);

    // Set up mock transport
    FlowConnection mockConnection = mock(FlowConnection.class);
    when(mockConnection.getRemoteEndpoint()).thenReturn(new Endpoint("localhost", 8080));
    when(mockConnection.isOpen()).thenReturn(true);
    when(mockConnection.send(any())).thenReturn(startActor(() -> {
      // Simulate slow send
      await(delay(0.5)); // 500ms delay
      return null;
    }));
    when(mockTransport.connect(any())).thenReturn(CompletableFuture.completedFuture(mockConnection));

    // Get a stub and call the void method
    TestService stub = rpcTransport.getRpcStub(serviceId, TestService.class);

    // This should complete immediately without waiting for timeout
    CompletableFuture<Void> voidFuture = new CompletableFuture<>();
    startActor(() -> {
      stub.fireAndForget("test");
      voidFuture.complete(null);
      return null;
    });

    // Should complete quickly without timeout
    testScheduler.advanceTime(0.01);
    testScheduler.pump();

    assertThat(voidFuture).isDone();
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testConnectionTimeout() {
    // Configure with a short connection timeout
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .connectionTimeoutMs(100) // 100ms connection timeout
        .build();

    rpcTransport = new FlowRpcTransportImpl(mockTransport, config);

    // Set up mock transport to never complete connection
    CompletableFuture<FlowConnection> neverCompletingFuture = new CompletableFuture<>();
    when(mockTransport.connect(any())).thenReturn(neverCompletingFuture);

    // Create a test service interface
    interface TestService {
      String testMethod();
    }

    EndpointId serviceId = new EndpointId("test-service");
    Endpoint endpoint = new Endpoint("localhost", 8080);
    rpcTransport.getEndpointResolver().registerRemoteEndpoint(serviceId, endpoint);

    // Get a stub and try to call a method
    TestService stub = rpcTransport.getRpcStub(serviceId, TestService.class);

    CompletableFuture<String> resultFuture = startActor(stub::testMethod);

    // Advance time past connection timeout
    pumpAndAdvanceTimeUntilDone(resultFuture);

    // Should fail with connection timeout
    assertThat(resultFuture.isDone()).isTrue();
    assertThatThrownBy(() -> resultFuture.getNow(null))
        .isInstanceOf(java.util.concurrent.CompletionException.class)
        .hasCauseInstanceOf(RpcTimeoutException.class)
        .satisfies(thrown -> {
          RpcTimeoutException e = (RpcTimeoutException) thrown.getCause();
          assertThat(e.getMessage()).contains("Connection");
          assertThat(e.getMessage()).contains("timed out after 100ms");
          assertThat(e.getTimeoutType()).isEqualTo(RpcTimeoutException.TimeoutType.CONNECTION);
        });
  }
}