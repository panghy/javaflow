package io.github.panghy.javaflow.rpc;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.error.RpcTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.delay;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RPC stream timeout functionality.
 */
public class FlowRpcStreamTimeoutTest extends AbstractFlowTest {

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testStreamInactivityTimeout() {
    // Configure with a short stream inactivity timeout
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .streamInactivityTimeoutMs(200) // 200ms inactivity timeout
        .build();

    SimulatedFlowTransport transport = new SimulatedFlowTransport();

    // Create server transport
    FlowRpcTransportImpl serverTransport = new FlowRpcTransportImpl(
        transport, config);

    // Create a test service interface with streaming method
    interface TestService {
      PromiseStream<String> streamData();
    }

    // Register implementation that sends data with delays
    TestService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      startActor(() -> {
        // Send first value immediately
        stream.send("value1");

        // Wait 100ms (within timeout)
        await(delay(0.1));
        stream.send("value2");

        // Wait 1s (exceeds timeout) - stream should timeout before this
        // Don't close the stream - keep it open so forEach keeps waiting
        await(delay(1.0));
        stream.send("value3"); // This should not be received due to timeout

        // Keep the stream open indefinitely to ensure timeout triggers
        await(delay(60.0)); // Very long delay
        stream.close();
        return null;
      });
      return stream;
    };

    EndpointId serviceId = new EndpointId("test-service");
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(12345); // Use fixed port

    // Register and start listening
    serverTransport.registerServiceAndListen(serviceId, impl, TestService.class, serverEndpoint);

    // Create client transport with same config
    FlowRpcTransportImpl clientTransport = new FlowRpcTransportImpl(
        transport, config);

    // Register remote endpoint in client
    clientTransport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    // Get a remote stub and call the streaming method
    TestService stub = clientTransport.getRpcStub(serviceId, TestService.class);

    List<String> receivedValues = new ArrayList<>();

    // Get the stream from the stub (this needs to be in an actor)
    CompletableFuture<PromiseStream<String>> streamFuture = startActor(() -> stub.streamData());

    // Wait for the stream future to complete (this needs time advancement for connection establishment)
    pumpAndAdvanceTimeUntilDone(streamFuture);

    assertThat(streamFuture.isDone()).isTrue();

    // Check if it completed exceptionally and print the exception if so
    if (streamFuture.isCompletedExceptionally()) {
      try {
        streamFuture.getNow();
      } catch (Exception e) {
        System.err.println("Stream future failed with exception: " + e);
        e.printStackTrace(System.err);
      }
    }

    assertThat(streamFuture.isCompletedExceptionally()).isFalse();

    PromiseStream<String> stream;
    try {
      stream = streamFuture.getNow();
    } catch (Exception e) {
      throw new RuntimeException("Failed to get stream", e);
    }

    // Use forEach to start processing the stream
    CompletableFuture<Void> forEachFuture = stream.getFutureStream().forEach(value -> {
      System.out.println("Received value: " + value);
      receivedValues.add(value);
    });

    // Process tasks to receive the first value (should happen immediately)
    pumpUntilDone();
    assertThat(receivedValues).containsExactly("value1");

    // Advance time by 100ms and process - should receive value2
    advanceTime(0.1);
    // Account for simulated network delays.
    advanceTime(0.05);
    pumpUntilDone();
    assertThat(receivedValues).containsExactly("value1", "value2");

    // Now advance time by 300ms to trigger the timeout (exceeds 200ms inactivity)
    advanceTime(0.3);

    // Process until the timeout occurs - this should trigger the timeout and complete the forEach with exception
    pumpUntilDone();

    // Create a separate test to verify the timeout works by making a direct hasNextAsync call
    // that should fail with timeout exception after the stream is timed out
    CompletableFuture<Boolean> hasNextFuture = startActor(() -> {
      // Try to get the next value - this should fail with timeout exception
      return Flow.await(stream.getFutureStream().hasNextAsync());
    });

    // Wait for this future to complete 
    pumpAndAdvanceTimeUntilDone(hasNextFuture);

    System.out.println("After timeout - hasNextFuture.isDone(): " + hasNextFuture.isDone());
    System.out.println("After timeout - hasNextFuture.isCompletedExceptionally(): " +
                       hasNextFuture.isCompletedExceptionally());

    // The hasNextAsync should fail with timeout exception
    assertThat(hasNextFuture.isDone()).isTrue();
    assertThat(hasNextFuture.isCompletedExceptionally()).isTrue();

    // Check the exception from hasNextAsync
    assertThatThrownBy(hasNextFuture::getNow)
        .isInstanceOf(java.util.concurrent.ExecutionException.class)
        .satisfies(thrown -> {
          // The exception might be wrapped in multiple ExecutionExceptions
          Throwable cause = thrown.getCause();
          while (cause instanceof java.util.concurrent.ExecutionException) {
            cause = cause.getCause();
          }
          assertThat(cause).isInstanceOf(RpcTimeoutException.class);
          RpcTimeoutException e = (RpcTimeoutException) cause;
          assertThat(e.getTimeoutType()).isEqualTo(RpcTimeoutException.TimeoutType.STREAM_INACTIVITY);
          assertThat(e.getTimeoutMs()).isEqualTo(200);
        });

    // Verify we received only the first two values before timeout
    assertThat(receivedValues).containsExactly("value1", "value2");

    // Clean up
    serverTransport.close();
    clientTransport.close();
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void testStreamWithNoTimeout() {
    // Configure with no stream timeout
    FlowRpcConfiguration config = FlowRpcConfiguration.builder()
        .streamInactivityTimeoutMs(0) // No timeout
        .build();

    SimulatedFlowTransport transport = new SimulatedFlowTransport();

    // Create server transport
    FlowRpcTransportImpl serverTransport = new FlowRpcTransportImpl(
        transport, config);

    // Create a test service interface with streaming method
    interface TestService {
      PromiseStream<Integer> streamNumbers();
    }

    // Register implementation
    TestService impl = () -> {
      PromiseStream<Integer> stream = new PromiseStream<>();
      startActor(() -> {
        for (int i = 1; i <= 3; i++) {
          stream.send(i);
          await(delay(0.1)); // 100ms between values
        }
        stream.close();
        return null;
      });
      return stream;
    };

    EndpointId serviceId = new EndpointId("test-service");
    LocalEndpoint serverEndpoint = LocalEndpoint.localhost(12346); // Use different fixed port

    // Register and start listening
    serverTransport.registerServiceAndListen(serviceId, impl, TestService.class, serverEndpoint);

    // Create client transport
    FlowRpcTransportImpl clientTransport = new FlowRpcTransportImpl(
        transport, config);

    // Register remote endpoint
    clientTransport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    // Get a remote stub and call the streaming method
    TestService stub = clientTransport.getRpcStub(serviceId, TestService.class);

    List<Integer> receivedValues = new ArrayList<>();

    // Create a future that will execute the stream operation inside a flow task
    CompletableFuture<Void> testFuture = startActor(() -> {
      // Get the stream from the stub
      PromiseStream<Integer> stream = stub.streamNumbers();

      // Process the stream - should complete without timeout
      CompletableFuture<Void> forEachFuture = stream.getFutureStream().forEach(receivedValues::add);

      // Wait for all values to be received
      await(forEachFuture);

      return null;
    });

    // Run the test - should complete successfully
    pumpAndAdvanceTimeUntilDone(testFuture);

    // Verify the future completed successfully (no exception)
    assertThat(testFuture.isDone()).isTrue();
    assertThat(testFuture.isCompletedExceptionally()).isFalse();

    // Verify all values were received
    assertThat(receivedValues).containsExactly(1, 2, 3);

    // Clean up
    serverTransport.close();
    clientTransport.close();
  }
}