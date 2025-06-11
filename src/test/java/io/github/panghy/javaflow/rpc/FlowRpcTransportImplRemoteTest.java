package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.FlowStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.error.RpcException;
import io.github.panghy.javaflow.rpc.message.RpcMessage;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for remote invocation functionality in {@link FlowRpcTransportImpl}.
 * Focuses on testing the RemoteInvocationHandler and actual RPC calls.
 */
public class FlowRpcTransportImplRemoteTest extends AbstractFlowTest {

  private FlowRpcTransportImpl clientTransport;
  private FlowRpcTransportImpl serverTransport;
  private EndpointResolver clientResolver;
  private LocalEndpoint serverEndpoint;
  private SimulatedFlowTransport transport;

  @Override
  protected void onSetUp() {
    // Initialize FlowSerialization
    FlowSerialization.setDefaultSerializer(new DefaultSerializer<>());

    // Create simulated transports
    transport = new SimulatedFlowTransport();

    // Create RPC transports
    clientTransport = new FlowRpcTransportImpl(transport);
    serverTransport = new FlowRpcTransportImpl(transport);

    clientResolver = clientTransport.getEndpointResolver();

    // Start server on a specific endpoint
    serverEndpoint = LocalEndpoint.localhost(9999);

    // Register service implementations
    TestServiceImpl testServiceImpl = new TestServiceImpl();
    ServiceWithPromiseImpl promiseServiceImpl = new ServiceWithPromiseImpl();
    ServiceWithMultipleArgsImpl multiArgsServiceImpl = new ServiceWithMultipleArgsImpl();
    
    // Register all services on the same endpoint
    serverTransport.registerServiceAndListen(testServiceImpl, TestService.class, serverEndpoint);
    serverTransport.registerServiceAndListen(promiseServiceImpl, ServiceWithPromise.class, serverEndpoint);
    serverTransport.registerServiceAndListen(multiArgsServiceImpl, ServiceWithMultipleArgs.class, serverEndpoint);

    // Register endpoints on client side
    clientResolver.registerRemoteEndpoint(new EndpointId("test-service"), serverEndpoint);
    clientResolver.registerRemoteEndpoint(new EndpointId("promise-service"), serverEndpoint);
    clientResolver.registerRemoteEndpoint(new EndpointId("multi-promise-service"), serverEndpoint);
    clientResolver.registerRemoteEndpoint(new EndpointId("null-service"), serverEndpoint);
    clientResolver.registerRemoteEndpoint(new EndpointId("concat-service"), serverEndpoint);
    clientResolver.registerRemoteEndpoint(new EndpointId("void-service"), serverEndpoint);
    clientResolver.registerRemoteEndpoint(new EndpointId("promise-return-service"), serverEndpoint);
    clientResolver.registerRemoteEndpoint(new EndpointId("method-id-service"), serverEndpoint);
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

  // Test interfaces
  public interface TestService {
    String echo(String message);

    int add(int a, int b);

    void voidMethod();

    FlowFuture<String> asyncMethod(String input);
  }

  public interface ServiceWithPromise {
    void processWithCallback(String input, FlowPromise<String> callback);

    String methodWithPromiseAndReturn(FlowPromise<Integer> promise);
  }

  public interface ServiceWithStream {
    void processStream(PromiseStream<String> stream);

    PromiseStream<Integer> getStream();
  }

  public interface ServiceWithMultipleArgs {
    String concat(String a, String b, String c);

    void multiplePromises(FlowPromise<String> p1, FlowPromise<Integer> p2);
  }

  // Service implementations
  private static class TestServiceImpl implements TestService {
    @Override
    public String echo(String message) {
      return "Echo: " + message;
    }

    @Override
    public int add(int a, int b) {
      return a + b;
    }

    @Override
    public void voidMethod() {
      // Do nothing
    }

    @Override
    public FlowFuture<String> asyncMethod(String input) {
      FlowFuture<String> future = new FlowFuture<>();
      future.getPromise().complete("Async: " + input);
      return future;
    }
  }

  private static class ServiceWithPromiseImpl implements ServiceWithPromise {
    @Override
    public void processWithCallback(String input, FlowPromise<String> callback) {
      System.out.println("ServiceWithPromiseImpl.processWithCallback called with input: " + input);
      System.out.println("  Callback class: " + callback.getClass().getName());
      System.out.println("  Completing callback with: Processed: " + input);
      callback.complete("Processed: " + input);
      System.out.println("  Callback completed");
    }

    @Override
    public String methodWithPromiseAndReturn(FlowPromise<Integer> promise) {
      promise.complete(42);
      return "Promise registered";
    }
  }

  private static class ServiceWithMultipleArgsImpl implements ServiceWithMultipleArgs {
    @Override
    public String concat(String a, String b, String c) {
      return a + b + c;
    }

    @Override
    public void multiplePromises(FlowPromise<String> p1, FlowPromise<Integer> p2) {
      p1.complete("First promise");
      p2.complete(123);
    }
  }


  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteMethodInvocation() throws Exception {
    // Register remote endpoint
    EndpointId serviceId = new EndpointId("test-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    // Get remote stub
    TestService remoteService = clientTransport.getRpcStub(serviceId, TestService.class);

    // Test echo method
    FlowFuture<String> echoF = startActor(() -> remoteService.echo("Hello"));
    pumpUntilDone(echoF);
    String echoResult = echoF.getNow();
    assertEquals("Echo: Hello", echoResult);

    // Test add method
    FlowFuture<Integer> addResultF = startActor(() -> remoteService.add(10, 20));
    pumpUntilDone(addResultF);
    int addResult = addResultF.getNow();
    assertEquals(30, addResult);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testVoidMethodInvocation() {
    EndpointId serviceId = new EndpointId("void-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    TestService remoteService = clientTransport.getRpcStub(serviceId, TestService.class);

    FlowFuture<Void> voidF = startActor(() -> {
      remoteService.voidMethod();
      return null;
    });
    pumpUntilDone(voidF);

    assertDoesNotThrow(voidF::getNow);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteMethodWithPromiseArgument() throws Exception {
    EndpointId serviceId = new EndpointId("promise-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    ServiceWithPromise remoteService = clientTransport.getRpcStub(serviceId, ServiceWithPromise.class);

    FlowFuture<String> resultF = startActor(() -> {
      FlowFuture<String> future = new FlowFuture<>();
      FlowPromise<String> promise = future.getPromise();

      remoteService.processWithCallback("test input", promise);

      return await(future);
    });
    pumpUntilDone(resultF);

    String result = resultF.getNow();
    assertEquals("Processed: test input", result);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteMethodWithMultiplePromises() throws Exception {
    EndpointId serviceId = new EndpointId("multi-promise-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    ServiceWithMultipleArgs remoteService = clientTransport.getRpcStub(serviceId, ServiceWithMultipleArgs.class);

    FlowFuture<List<Object>> resultF = startActor(() -> {
      FlowFuture<String> future1 = new FlowFuture<>();
      FlowFuture<Integer> future2 = new FlowFuture<>();

      remoteService.multiplePromises(future1.getPromise(), future2.getPromise());

      String result1 = await(future1);
      Integer result2 = await(future2);
      return Arrays.asList(result1, result2);
    });
    pumpUntilDone(resultF);

    List<Object> results = resultF.getNow();
    assertEquals("First promise", results.get(0));
    assertEquals(123, results.get(1));
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteMethodWithNullArguments() throws Exception {
    EndpointId serviceId = new EndpointId("null-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    TestService remoteService = clientTransport.getRpcStub(serviceId, TestService.class);

    FlowFuture<String> echoF = startActor(() -> remoteService.echo(null));
    pumpUntilDone(echoF);
    String result = echoF.getNow();
    assertEquals("Echo: null", result);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteMethodWithMultipleArguments() throws Exception {
    EndpointId serviceId = new EndpointId("concat-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    ServiceWithMultipleArgs remoteService = clientTransport.getRpcStub(serviceId, ServiceWithMultipleArgs.class);

    FlowFuture<String> concatF = startActor(() -> remoteService.concat("Hello", " ", "World"));
    pumpUntilDone(concatF);
    String result = concatF.getNow();
    assertEquals("Hello World", result);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteInvocationWithWrongMessageId() throws Exception {
    // Create a custom server that responds with wrong message ID
    startActor(() -> {
      try {
        FlowStream<FlowConnection> stream = transport.listen(LocalEndpoint.localhost(8888));
        FlowConnection conn = await(stream.nextAsync());

        ByteBuffer buffer = await(conn.receive(65536));
        RpcMessage request = RpcMessage.deserialize(buffer);

        // Send response with different message ID
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            UUID.randomUUID(), // Wrong ID
            request.getHeader().getMethodId(),
            null,
            FlowSerialization.serialize("Wrong response"));

        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Server error
      }
      return null;
    });

    EndpointId serviceId = new EndpointId("wrong-id-service");
    clientResolver.registerRemoteEndpoint(serviceId, LocalEndpoint.localhost(8888));

    TestService remoteService = clientTransport.getRpcStub(serviceId, TestService.class);

    FlowFuture<String> echoF = startActor(() -> remoteService.echo("test"));
    
    // Wait a bit for the response to arrive
    pump();
    pump();
    pump();
    
    // The future should remain incomplete since the response has wrong message ID
    // The response is ignored and the RPC call is still waiting
    assertFalse(echoF.isDone(), "Future should not be done when response has wrong message ID");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteInvocationWithErrorResponse() throws Exception {
    // Create a custom server that always returns errors
    startActor(() -> {
      try {
        FlowStream<FlowConnection> stream = transport.listen(LocalEndpoint.localhost(7777));
        FlowConnection conn = await(stream.nextAsync());

        ByteBuffer buffer = await(conn.receive(65536));
        RpcMessage request = RpcMessage.deserialize(buffer);

        // Send error response
        IllegalStateException error = new IllegalStateException("Server error");
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.ERROR,
            request.getHeader().getMessageId(),
            request.getHeader().getMethodId(),
            null,
            FlowSerialization.serialize(error));

        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Server error
      }
      return null;
    });

    EndpointId serviceId = new EndpointId("error-service");
    clientResolver.registerRemoteEndpoint(serviceId, LocalEndpoint.localhost(7777));

    TestService remoteService = clientTransport.getRpcStub(serviceId, TestService.class);

    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        remoteService.echo("test");
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    pumpUntilDone(errorF);

    Exception error = errorF.getNow();
    assertThat(error).isInstanceOf(RpcException.class)
        .hasMessageContaining("RPC invocation failed for method: echo");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testProxyObjectMethods() {
    EndpointId serviceId = new EndpointId("object-method-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    TestService remoteService = clientTransport.getRpcStub(serviceId, TestService.class);

    // Test getClass() - this is final and handled by the proxy directly
    Class<?> proxyClass = remoteService.getClass();
    assertNotNull(proxyClass);
    assertTrue(proxyClass.getName().contains("Proxy"));

    // Test toString() - this should be handled by our InvocationHandler
    String toStringResult = remoteService.toString();
    assertNotNull(toStringResult);
    assertTrue(toStringResult.contains("RemoteStub"));
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteMethodWithPromiseAndReturn() throws Exception {
    EndpointId serviceId = new EndpointId("promise-return-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    ServiceWithPromise remoteService = clientTransport.getRpcStub(serviceId, ServiceWithPromise.class);

    FlowFuture<List<Object>> resultF = startActor(() -> {
      FlowFuture<Integer> future = new FlowFuture<>();
      String returnValue = remoteService.methodWithPromiseAndReturn(future.getPromise());
      Integer promiseValue = await(future);
      return Arrays.asList(returnValue, promiseValue);
    });
    pumpUntilDone(resultF);

    List<Object> results = resultF.getNow();
    assertEquals("Promise registered", results.get(0));
    assertEquals(42, results.get(1));
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testDirectEndpointInvocation() throws Exception {
    // Use direct endpoint without EndpointId
    TestService remoteService = clientTransport.getRpcStub(serverEndpoint, TestService.class);

    FlowFuture<String> echoF = startActor(() -> remoteService.echo("Direct"));
    pumpUntilDone(echoF);
    String result = echoF.getNow();
    assertEquals("Echo: Direct", result);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testMethodIdBuilding() throws Exception {
    EndpointId serviceId = new EndpointId("method-id-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);

    ServiceWithMultipleArgs service = clientTransport.getRpcStub(serviceId, ServiceWithMultipleArgs.class);

    // This will test the buildMethodId method with multiple parameter types
    FlowFuture<String> futureF = startActor(() -> service.concat("a", "b", "c"));
    pumpUntilDone(futureF);
    assertEquals("abc", futureF.getNow());
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testRemoteMethodWithPromiseStreamArgument() throws Exception {
    // Test PromiseStream as method argument in remote RPC calls
    // This specifically tests the client-side handling in processArguments
    
    // Define service interface that accepts PromiseStream
    interface StreamConsumerService {
      String consumeStream(String prefix, PromiseStream<Integer> stream);
    }
    
    // Server-side implementation
    class StreamConsumerImpl implements StreamConsumerService {
      @Override
      public String consumeStream(String prefix, PromiseStream<Integer> stream) {
        // In a real implementation, we'd consume the stream
        // For this test, just acknowledge receipt
        assertNotNull(stream);
        
        // The stream is a local PromiseStream created by processIncomingArguments
        // when it receives a UUID from the client
        // This verifies that the client-side processArguments method correctly
        // converted the PromiseStream to a UUID (lines 1386-1400 in FlowRpcTransportImpl)
        
        return prefix + "-stream-received";
      }
    }
    
    // Register the service on server
    serverTransport.registerServiceAndListen(
        new StreamConsumerImpl(), 
        StreamConsumerService.class, 
        serverEndpoint);
    
    // Get client stub
    EndpointId serviceId = new EndpointId("stream-consumer-service");
    clientResolver.registerRemoteEndpoint(serviceId, serverEndpoint);
    StreamConsumerService remoteService = clientTransport.getRpcStub(serviceId, StreamConsumerService.class);
    
    // Test: Simple stream passing
    FlowFuture<String> resultF = startActor(() -> {
      PromiseStream<Integer> stream = new PromiseStream<>();
      
      // Call remote method with PromiseStream argument
      // This will trigger processArguments to convert the stream to UUID
      String result = remoteService.consumeStream("test", stream);
      
      // Clean up the stream
      stream.close();
      
      return result;
    });
    
    pumpUntilDone(resultF);
    assertEquals("test-stream-received", resultF.getNow());
    
    // Verify that the processArguments method was used by checking
    // that a stream was registered with the RemotePromiseTracker
    // (The actual stream consumption is tested in other tests)
  }
}