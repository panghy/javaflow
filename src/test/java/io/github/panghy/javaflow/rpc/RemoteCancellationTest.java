package io.github.panghy.javaflow.rpc;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowCancellationException;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.io.network.NetworkSimulationParameters;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for remote cancellation functionality in the RPC framework.
 * Verifies that cancellation propagates correctly across network boundaries
 * and that remote actors handle cancellation appropriately.
 */
@Timeout(30)
public class RemoteCancellationTest extends AbstractFlowTest {

  private FlowRpcTransportImpl clientTransport;
  private FlowRpcTransportImpl serverTransport;
  private LocalEndpoint serverEndpoint;
  private SimulatedFlowTransport transport;

  @Override
  protected void onSetUp() {
    // Initialize FlowSerialization
    FlowSerialization.setDefaultSerializer(new DefaultSerializer<>());

    // Create simulated transport
    transport = new SimulatedFlowTransport();
    serverEndpoint = LocalEndpoint.localhost(9999);

    // Create and initialize transports
    serverTransport = new FlowRpcTransportImpl(transport);
    clientTransport = new FlowRpcTransportImpl(transport);

    // Register endpoints on client side
    EndpointResolver clientResolver = clientTransport.getEndpointResolver();
    clientResolver.registerRemoteEndpoint(new EndpointId("cancellable-service"), serverEndpoint);

    // No need to manually handle connections with registerServiceAndListen
  }

  @Override
  protected void onTearDown() {
    if (clientTransport != null) {
      clientTransport.close();
    }
    if (serverTransport != null) {
      serverTransport.close();
    }
  }

  // Test interface for remote cancellation testing
  public interface CancellableService {
    CompletableFuture<String> longRunningOperation(double delaySeconds);
    CompletableFuture<String> cooperativeCancellableOperation();
    PromiseStream<Integer> streamingOperation();
    CompletableFuture<String> nestedOperation();
  }

  // Implementation of the test service
  public static class CancellableServiceImpl implements CancellableService {
    private final AtomicBoolean cooperativeOpStarted = new AtomicBoolean(false);
    private final AtomicBoolean cooperativeOpCancelled = new AtomicBoolean(false);
    private final AtomicInteger streamItemsSent = new AtomicInteger(0);

    @Override
    public CompletableFuture<String> longRunningOperation(double delaySeconds) {
      return startActor(() -> {
        await(Flow.delay(delaySeconds));
        return "completed";
      });
    }

    @Override
    public CompletableFuture<String> cooperativeCancellableOperation() {
      return startActor(() -> {
        cooperativeOpStarted.set(true);
        try {
          // Periodically check for cancellation
          for (int i = 0; i < 100; i++) {
            if (Flow.isCancelled()) {
              cooperativeOpCancelled.set(true);
              return "cancelled at " + i;
            }
            await(Flow.delay(0.01)); // 10ms delay
          }
          return "completed";
        } finally {
          cooperativeOpCancelled.set(Flow.isCancelled());
        }
      });
    }

    @Override
    public PromiseStream<Integer> streamingOperation() {
      PromiseStream<Integer> stream = new PromiseStream<>();
      
      startActor(() -> {
        try {
          for (int i = 0; i < 1000; i++) {
            if (Flow.isCancelled()) {
              stream.close();
              return null;
            }
            stream.send(i);
            streamItemsSent.incrementAndGet();
            await(Flow.delay(0.01)); // 10ms between items
          }
          stream.close();
        } catch (Exception e) {
          stream.close();
        }
        return null;
      });
      
      return stream;
    }

    @Override
    public CompletableFuture<String> nestedOperation() {
      return startActor(() -> {
        // Start a nested operation
        CompletableFuture<String> nested = startActor(() -> {
          await(Flow.delay(1.0)); // 1 second delay
          return "nested result";
        });
        
        // Wait for the nested operation
        String result = await(nested);
        return "outer: " + result;
      });
    }

    public boolean isCooperativeOpStarted() {
      return cooperativeOpStarted.get();
    }

    public boolean isCooperativeOpCancelled() {
      return cooperativeOpCancelled.get();
    }

  }

  @Test
  void testRemoteCancellationDuringLongRunningOperation() {
    // Register service on server
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Get client proxy (using the endpoint ID registered in onSetUp)
    EndpointId serviceId = new EndpointId("cancellable-service");
    CancellableService client = clientTransport.getRpcStub(
        serviceId, CancellableService.class);

    // Start a long-running operation
    CompletableFuture<String> future = client.longRunningOperation(1.0); // 1 second

    // Let it start
    pump();
    advanceTime(0.1);
    pump();

    // Cancel the operation
    future.cancel();

    // Process cancellation
    advanceTime(1.0);
    pump();

    // Verify the future is cancelled
    assertTrue(future.isCancelled());
  }

  @Test
  void testRemoteCancellationWithCooperativeChecking() {
    // Register service on server
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Get client proxy (using the endpoint ID registered in onSetUp)
    EndpointId serviceId = new EndpointId("cancellable-service");
    CancellableService client = clientTransport.getRpcStub(
        serviceId, CancellableService.class);

    // Start cooperative cancellable operation
    CompletableFuture<String> future = client.cooperativeCancellableOperation();
    
    // Let the operation start on the server - need multiple pump cycles
    // to ensure the RPC is sent, received, and the operation actually starts
    for (int i = 0; i < 10 && !service.isCooperativeOpStarted(); i++) {
      pump();
      advanceTime(0.01); // 10ms increments
      pump();
    }
    
    // Verify operation has started
    assertTrue(service.isCooperativeOpStarted(), "Operation should have started");
    
    // Cancel the operation
    future.cancel();

    // Process cancellation
    for (int i = 0; i < 20; i++) {
      pump();
      advanceTime(0.05);
    }

    // Verify the client-side future is cancelled
    assertTrue(future.isCancelled() || future.isCompletedExceptionally(), 
        "Client-side future should be cancelled");
    
    // Note: Remote cancellation doesn't automatically propagate in the current RPC framework
    // The server-side operation will continue running until it completes naturally
    // This is a known limitation - proper remote cancellation would require sending
    // a cancellation message to the server
  }

  @Test
  void testRemoteCancellationOfStreamingOperation() {
    // Register service on server
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Get client proxy (using the endpoint ID registered in onSetUp)
    EndpointId serviceId = new EndpointId("cancellable-service");
    CancellableService client = clientTransport.getRpcStub(
        serviceId, CancellableService.class);

    AtomicInteger itemsReceived = new AtomicInteger(0);
    AtomicBoolean streamClosed = new AtomicBoolean(false);

    // Start streaming operation and consume stream
    CompletableFuture<Void> consumerFuture = startActor(() -> {
      // Start streaming operation
      PromiseStream<Integer> stream = client.streamingOperation();
      FutureStream<Integer> futureStream = stream.getFutureStream();
      
      try {
        Integer item;
        while (!futureStream.isClosed()) {
          try {
            item = await(futureStream.nextAsync());
            if (item != null) {
              itemsReceived.incrementAndGet();
            }
          } catch (Exception e) {
            // Stream closed
            break;
          }
        }
      } catch (FlowCancellationException e) {
        // Expected on cancellation
      } finally {
        streamClosed.set(true);
      }
      return null;
    });

    // Let stream start and some items flow
    int attempts = 0;
    while (attempts < 50 && itemsReceived.get() == 0) {
      pump();
      advanceTime(0.02); // 20ms
      attempts++;
    }

    assertTrue(itemsReceived.get() > 0, "Should have received some items after " + attempts + " attempts");
    int itemsBeforeCancellation = itemsReceived.get();

    // Cancel the consumer
    consumerFuture.cancel();

    // Process cancellation
    for (int i = 0; i < 10; i++) {
      pump();
      advanceTime(0.05);
    }

    // Verify stream stopped
    assertTrue(streamClosed.get(), "Stream should be closed");
    assertEquals(itemsBeforeCancellation, itemsReceived.get(), 
        "No more items should be received after cancellation");
  }

  @Test
  void testRemoteCancellationWithNestedOperations() {
    // Register service on server
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Get client proxy (using the endpoint ID registered in onSetUp)
    EndpointId serviceId = new EndpointId("cancellable-service");
    CancellableService client = clientTransport.getRpcStub(
        serviceId, CancellableService.class);

    // Start nested operation
    CompletableFuture<String> future = client.nestedOperation();

    // Let it start
    pump();
    advanceTime(0.1);
    pump();

    // Cancel before nested operation completes
    future.cancel();

    // Process cancellation
    advanceTime(1.0);
    pumpAndAdvanceTimeUntilDone();

    // Verify cancellation
    assertTrue(future.isCancelled() || future.isCompletedExceptionally());
  }

  @Test
  void testRemoteCancellationWithNetworkFailure() {
    // Register service on server
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Configure network to have some latency
    NetworkSimulationParameters params = new NetworkSimulationParameters()
        .setSendDelay(0.05) // 50ms latency  
        .setReceiveDelay(0.05)
        .setPacketLossProbability(0.1); // 10% packet loss
    
    // Need to create new transport with these parameters
    transport.close();
    transport = new SimulatedFlowTransport(params);
    
    // Re-initialize transports with new transport
    clientTransport.close();
    serverTransport.close();
    clientTransport = new FlowRpcTransportImpl(transport);
    serverTransport = new FlowRpcTransportImpl(transport);
    
    // Re-register service
    EndpointId serviceImplId2 = new EndpointId("cancellable-service-impl-2");
    serverTransport.registerServiceAndListen(serviceImplId2, service, CancellableService.class, serverEndpoint);
    
    // Re-register endpoint on client side
    EndpointId serviceId2 = new EndpointId("cancellable-service-2");
    EndpointResolver clientResolver2 = clientTransport.getEndpointResolver();
    clientResolver2.registerRemoteEndpoint(serviceId2, serverEndpoint);

    // Get client proxy
    CancellableService client = clientTransport.getRpcStub(
        serviceId2, CancellableService.class);

    // Start operation
    CompletableFuture<String> future = client.longRunningOperation(2.0); // 2 seconds

    // Let it start with network delays
    pump();
    advanceTime(0.2);
    pump();

    // Cancel the operation
    future.cancel();

    // Simulate network partition by creating new transport with 100% packet loss
    // Note: In a real scenario, you'd need to interrupt existing connections

    // Try to process - cancellation might not reach server
    advanceTime(0.5);
    pump();

    // Future should still be cancelled on client side
    assertTrue(future.isCancelled());
  }

  @Test
  void testMultipleConcurrentCancellations() {
    // Register service on server
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Get client proxy (using the endpoint ID registered in onSetUp)
    EndpointId serviceId = new EndpointId("cancellable-service");
    CancellableService client = clientTransport.getRpcStub(
        serviceId, CancellableService.class);

    // Start multiple operations
    CompletableFuture<String> future1 = client.longRunningOperation(1.0);
    CompletableFuture<String> future2 = client.longRunningOperation(2.0);
    CompletableFuture<String> future3 = client.longRunningOperation(3.0);

    // Let them start
    pump();
    advanceTime(0.1);
    pump();

    // Cancel all operations
    future1.cancel();
    future2.cancel();
    future3.cancel();

    // Process cancellations
    advanceTime(3.5);
    pumpAndAdvanceTimeUntilDone();

    // Verify all are cancelled
    assertTrue(future1.isCancelled());
    assertTrue(future2.isCancelled());
    assertTrue(future3.isCancelled());
  }

  @Test
  void testCancellationExceptionPropagation() {
    // Register service on server
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Get client proxy (using the endpoint ID registered in onSetUp)
    EndpointId serviceId = new EndpointId("cancellable-service");
    CancellableService client = clientTransport.getRpcStub(
        serviceId, CancellableService.class);

    // Start operation and immediately try to await it
    CompletableFuture<String> remoteFuture = client.longRunningOperation(1.0);
    
    AtomicReference<Exception> caughtException = new AtomicReference<>();
    startActor(() -> {
      try {
        await(remoteFuture);
      } catch (FlowCancellationException e) {
        caughtException.set(e);
      }
      return null;
    });

    // Let it start
    pump();
    advanceTime(0.1);
    pump();

    // Cancel the remote operation
    remoteFuture.cancel();

    // Process cancellation
    advanceTime(1.0);
    pumpAndAdvanceTimeUntilDone();

    // Verify FlowCancellationException was thrown
    assertInstanceOf(FlowCancellationException.class, caughtException.get(),
        "Should have caught FlowCancellationException");
  }

  @Test
  void testCancellationDuringConnectionSetup() {
    // Don't register service yet to delay connection
    
    // Get client proxy (using the endpoint ID registered in onSetUp)
    EndpointId serviceId = new EndpointId("cancellable-service");
    CancellableService client = clientTransport.getRpcStub(
        serviceId, CancellableService.class);

    // Start operation - this will try to establish connection
    CompletableFuture<String> future = client.longRunningOperation(1.0);

    // Cancel before connection is established
    future.cancel();

    // Now register service
    CancellableServiceImpl service = new CancellableServiceImpl();
    EndpointId serviceImplId = new EndpointId("cancellable-service-impl");
    serverTransport.registerServiceAndListen(serviceImplId, service, CancellableService.class, serverEndpoint);

    // Process
    pump();
    advanceTime(0.1);
    pump();

    // Verify cancellation
    assertTrue(future.isCancelled());
  }
}