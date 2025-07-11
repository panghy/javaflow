package io.github.panghy.javaflow.rpc;

import java.util.concurrent.CompletableFuture;
import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link RpcServiceInterface} usage patterns.
 */
@Timeout(30)
public class RpcServiceInterfaceExtraTest extends AbstractFlowTest {

  private FlowRpcTransportImpl transport;
  private LocalEndpoint serverEndpoint;

  @BeforeEach
  public void setUp() {
    FlowSerialization.setDefaultSerializer(new DefaultSerializer<>());
    SimulatedFlowTransport networkTransport = new SimulatedFlowTransport();
    transport = new FlowRpcTransportImpl(networkTransport);
    serverEndpoint = LocalEndpoint.localhost(9876);
  }

  // Test service interface
  public interface TestService extends RpcServiceInterface {
    String echo(String message);
    CompletableFuture<String> asyncEcho(String message);
    CompletableFuture<String> promiseEcho(String message);
    PromiseStream<String> streamEcho(String message);
    void voidMethod();
  }

  // Implementation
  private static class TestServiceImpl implements TestService {
    @Override
    public String echo(String message) {
      return "Echo: " + message;
    }

    @Override
    public CompletableFuture<String> asyncEcho(String message) {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.complete("Async: " + message);
      return future;
    }

    @Override
    public CompletableFuture<String> promiseEcho(String message) {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.complete("Promise: " + message);
      return future;
    }

    @Override
    public PromiseStream<String> streamEcho(String message) {
      PromiseStream<String> stream = new PromiseStream<>();
      stream.send("Stream: " + message);
      stream.close();
      return stream;
    }

    @Override
    public void voidMethod() {
      // Do nothing
    }
  }

  @Test
  public void testRpcServiceInterface() throws Exception {
    // Setup service
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    // Pump to ensure server is ready
    pump();
    
    // Test basic string method
    CompletableFuture<String> echoResult = startActor(() -> stub.echo("test"));
    pumpAndAdvanceTimeUntilDone(echoResult);
    assertEquals("Echo: test", echoResult.getNow(null));
    
    // Test async future method
    CompletableFuture<String> asyncResult = stub.asyncEcho("async");
    pumpAndAdvanceTimeUntilDone(asyncResult);
    assertEquals("Async: async", asyncResult.getNow(null));
    
    // Test void method
    CompletableFuture<Void> voidResult = startActor(() -> {
      stub.voidMethod();
      return null;
    });
    pumpAndAdvanceTimeUntilDone(voidResult);
    
    // Test promise method
    CompletableFuture<CompletableFuture<String>> promiseResultF = startActor(() -> stub.promiseEcho("promise"));
    pumpAndAdvanceTimeUntilDone(promiseResultF);
    CompletableFuture<String> promise = promiseResultF.getNow(null);
    assertNotNull(promise);
    
    CompletableFuture<String> promiseValue = promise;
    pumpAndAdvanceTimeUntilDone(promiseValue);
    assertEquals("Promise: promise", promiseValue.getNow(null));
  }

  @Test
  public void testRpcServiceInterfaceDefaultMethods() throws Exception {
    // Test the default methods of RpcServiceInterface
    TestService service = new TestServiceImpl();
    
    // Test ready() method
    CompletableFuture<Void> readyFuture = service.ready();
    assertNotNull(readyFuture);
    pumpAndAdvanceTimeUntilDone(readyFuture);
    
    // Test onClose() method  
    CompletableFuture<Void> closeFuture = service.onClose();
    assertNotNull(closeFuture);
    // onClose() returns a future that doesn't complete automatically
    assertEquals(false, closeFuture.isDone());
  }


  @Test
  public void testRegisterAsLocal() throws Exception {
    // Set the transport as default
    FlowRpcProvider.setDefaultTransport(transport);
    
    TestService service = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("local-service");
    LocalEndpoint localEndpoint = LocalEndpoint.localhost(9877);
    
    // Register as local
    service.registerAsLocal(endpointId, localEndpoint);
    
    // Verify we can get a stub for the local service
    TestService stub = transport.getRpcStub(endpointId, TestService.class);
    assertNotNull(stub);
    
    // Test that it works
    CompletableFuture<String> result = startActor(() -> stub.echo("local"));
    pumpAndAdvanceTimeUntilDone(result);
    assertEquals("Echo: local", result.getNow(null));
  }
}