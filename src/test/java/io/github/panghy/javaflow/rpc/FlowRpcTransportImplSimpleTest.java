package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple tests for {@link FlowRpcTransportImpl}.
 */
public class FlowRpcTransportImplSimpleTest {

  private FlowRpcTransportImpl rpcTransport;
  private EndpointResolver endpointResolver;

  @BeforeEach
  public void setUp() {
    // Ensure FlowSerialization is properly initialized  
    FlowSerialization.setDefaultSerializer(new DefaultSerializer<>());

    rpcTransport = new FlowRpcTransportImpl(new SimulatedFlowTransport());
    endpointResolver = rpcTransport.getEndpointResolver();
  }

  // Simple test interface
  public interface SimpleService {
    String echo(String message);

    int add(int a, int b);
  }

  // Simple implementation
  public static class SimpleServiceImpl implements SimpleService {
    @Override
    public String echo(String message) {
      return "Echo: " + message;
    }

    @Override
    public int add(int a, int b) {
      return a + b;
    }
  }

  @Test
  public void testLocalStubBasicOperations() {
    // Register a loopback service
    EndpointId serviceId = new EndpointId("simple-service");
    SimpleService implementation = new SimpleServiceImpl();
    endpointResolver.registerLoopbackEndpoint(serviceId, implementation);

    // Get local stub
    SimpleService service = rpcTransport.getLocalStub(serviceId, SimpleService.class);
    assertNotNull(service);

    // Test echo method
    String result = service.echo("Hello");
    assertEquals("Echo: Hello", result);

    // Test add method
    int sum = service.add(5, 3);
    assertEquals(8, sum);
  }

  @Test
  public void testLocalStubNotFound() {
    EndpointId unknownId = new EndpointId("unknown");

    assertThrows(IllegalArgumentException.class, () -> {
      rpcTransport.getLocalStub(unknownId, SimpleService.class);
    });
  }

  @Test
  public void testRemoteStubCreation() {
    // Register a remote endpoint
    EndpointId serviceId = new EndpointId("remote-service");
    Endpoint remoteEndpoint = new Endpoint("remote-host", 9090);
    endpointResolver.registerRemoteEndpoint(serviceId, remoteEndpoint);

    // Get remote stub
    SimpleService remoteStub = rpcTransport.getRpcStub(serviceId, SimpleService.class);
    assertNotNull(remoteStub);

    // Verify it's a proxy
    assertInstanceOf(Proxy.class, remoteStub);
  }

  @Test
  public void testMultipleEndpoints() {
    // Register multiple remote endpoints
    EndpointId serviceId = new EndpointId("multi-service");
    List<Endpoint> endpoints = List.of(
        new Endpoint("host1", 9091),
        new Endpoint("host2", 9092),
        new Endpoint("host3", 9093)
    );
    endpointResolver.registerRemoteEndpoints(serviceId, endpoints);

    // Get stub - should work with round-robin
    SimpleService stub = rpcTransport.getRpcStub(serviceId, SimpleService.class);
    assertNotNull(stub);
  }

  @Test
  public void testDirectEndpoint() {
    Endpoint directEndpoint = new Endpoint("direct-host", 8888);
    EndpointId serviceId = new EndpointId("direct-service");

    // Must register the endpoint first
    endpointResolver.registerRemoteEndpoint(serviceId, directEndpoint);

    SimpleService stub = rpcTransport.getRpcStub(directEndpoint, SimpleService.class);
    assertNotNull(stub);
    assertInstanceOf(Proxy.class, stub);
  }

  @Test
  public void testDirectEndpointUnregistered() {
    // Try to create stub for unregistered endpoint
    Endpoint unregisteredEndpoint = new Endpoint("unregistered-host", 9999);

    assertThrows(IllegalArgumentException.class, () -> {
      rpcTransport.getRpcStub(unregisteredEndpoint, SimpleService.class);
    });
  }

  @Test
  public void testTransportClosure() {
    // Close the transport
    FlowFuture<Void> closeFuture = rpcTransport.close();
    assertNotNull(closeFuture);
    assertTrue(closeFuture.isDone());

    // After close, operations should fail
    assertThrows(IllegalStateException.class, () -> {
      rpcTransport.getRpcStub(new EndpointId("test"), SimpleService.class);
    });
  }

  @Test
  public void testLocalStubWithException() {
    // Service that throws exception
    SimpleService errorService = new SimpleService() {
      @Override
      public String echo(String message) {
        throw new IllegalArgumentException("Test exception");
      }

      @Override
      public int add(int a, int b) {
        return 0;
      }
    };

    EndpointId serviceId = new EndpointId("error-service");
    endpointResolver.registerLoopbackEndpoint(serviceId, errorService);

    SimpleService localStub = rpcTransport.getLocalStub(serviceId, SimpleService.class);

    // Method invocation should throw wrapped exception
    RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
      localStub.echo("test");
    });

    assertTrue(thrown.getMessage().contains("Local invocation failed"));
  }

  @Test
  public void testObjectMethods() {
    EndpointId serviceId = new EndpointId("test-service");
    SimpleService implementation = new SimpleServiceImpl();
    endpointResolver.registerLoopbackEndpoint(serviceId, implementation);

    SimpleService localStub = rpcTransport.getLocalStub(serviceId, SimpleService.class);

    // Test toString
    String toStringResult = localStub.toString();
    assertTrue(toStringResult.contains("LocalStub"));

    // Test equals
    assertEquals(localStub, localStub);
    assertNotEquals(localStub, implementation);

    // Test hashCode
    assertDoesNotThrow(localStub::hashCode);
  }

  @Test
  public void testNullArguments() {
    EndpointId serviceId = new EndpointId("test-service");
    SimpleService implementation = new SimpleServiceImpl();
    endpointResolver.registerLoopbackEndpoint(serviceId, implementation);

    SimpleService localStub = rpcTransport.getLocalStub(serviceId, SimpleService.class);

    // Test with null argument
    String result = localStub.echo(null);
    assertEquals("Echo: null", result);
  }

  @Test
  public void testLocalEndpointRegistration() {
    EndpointId serviceId = new EndpointId("local-service");
    SimpleService implementation = new SimpleServiceImpl();

    // Register as local endpoint with network exposure
    endpointResolver.registerLocalEndpoint(serviceId, implementation,
        new Endpoint("localhost", 8080));

    // Should be treated as local when getting RPC stub
    SimpleService stub = rpcTransport.getRpcStub(serviceId, SimpleService.class);
    assertNotNull(stub);

    // Local stub should also work
    SimpleService localStub = rpcTransport.getLocalStub(serviceId, SimpleService.class);
    assertNotNull(localStub);
  }

  @Test
  public void testPromiseArgument() {
    // Interface with promise parameter
    interface PromiseService {
      void processWithCallback(String input, FlowPromise<String> callback);
    }

    PromiseService implementation = (input, callback) -> {
      callback.complete("Processed: " + input);
    };

    EndpointId serviceId = new EndpointId("promise-service");
    endpointResolver.registerLoopbackEndpoint(serviceId, implementation);

    PromiseService service = rpcTransport.getLocalStub(serviceId, PromiseService.class);

    FlowFuture<String> future = new FlowFuture<>();
    FlowPromise<String> promise = future.getPromise();

    service.processWithCallback("test", promise);

    assertTrue(future.isDone());
    try {
      assertEquals("Processed: test", Flow.await(future));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testDefaultConstructor() {
    // Test the default constructor
    FlowRpcTransportImpl defaultTransport = new FlowRpcTransportImpl();
    assertNotNull(defaultTransport);
    assertNotNull(defaultTransport.getEndpointResolver());

    // Clean up
    FlowFuture<Void> closeFuture = defaultTransport.close();
    assertTrue(closeFuture.isDone());
  }

  @Test
  public void testGetRpcStubWithNullEndpoint() {
    assertThrows(IllegalArgumentException.class, () -> {
      rpcTransport.getRpcStub((Endpoint) null, SimpleService.class);
    });
  }

  @Test
  public void testLocalStubWithWrongInterface() {
    EndpointId serviceId = new EndpointId("wrong-interface-service");
    SimpleService implementation = new SimpleServiceImpl();
    endpointResolver.registerLoopbackEndpoint(serviceId, implementation);

    // Try to get stub with incompatible interface
    interface WrongInterface {
      void wrongMethod();
    }

    assertThrows(ClassCastException.class, () -> {
      rpcTransport.getLocalStub(serviceId, WrongInterface.class);
    });
  }

  @Test
  public void testClosedTransportOperations() {
    // Close the transport
    FlowFuture<Void> closeFuture = rpcTransport.close();
    assertTrue(closeFuture.isDone());

    // Try to close again (should still complete)
    FlowFuture<Void> secondClose = rpcTransport.close();
    assertTrue(secondClose.isDone());

    EndpointId serviceId = new EndpointId("test");
    Endpoint endpoint = new Endpoint("localhost", 8080);

    // All operations should throw IllegalStateException when closed
    assertThrows(IllegalStateException.class, () -> {
      rpcTransport.getRpcStub(serviceId, SimpleService.class);
    });

    assertThrows(IllegalStateException.class, () -> {
      rpcTransport.getRpcStub(endpoint, SimpleService.class);
    });

    assertThrows(IllegalStateException.class, () -> {
      rpcTransport.getLocalStub(serviceId, SimpleService.class);
    });
  }

  @Test
  public void testLocalInvocationHandlerWithReflectionException() {
    // Test case where method.invoke throws an exception other than InvocationTargetException
    EndpointId serviceId = new EndpointId("reflection-error-service");

    // Create a mock implementation that will cause reflection issues
    SimpleService faultyImplementation = new SimpleService() {
      @Override
      public String echo(String message) {
        // This will throw IllegalAccessError when accessed via reflection with wrong permissions
        throw new IllegalAccessError("Simulated reflection error");
      }

      @Override
      public int add(int a, int b) {
        return 0;
      }
    };

    endpointResolver.registerLoopbackEndpoint(serviceId, faultyImplementation);
    SimpleService localStub = rpcTransport.getLocalStub(serviceId, SimpleService.class);

    // The invocation should throw RuntimeException wrapping the original error
    RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
      localStub.echo("test");
    });

    assertTrue(thrown.getMessage().contains("Local invocation failed"));
  }

  @Test
  public void testRemoteStubWithRoundRobin() {
    // Test round-robin behavior with multiple endpoints
    EndpointId serviceId = new EndpointId("round-robin-service");
    List<Endpoint> endpoints = List.of(
        new Endpoint("host1", 9091),
        new Endpoint("host2", 9092),
        new Endpoint("host3", 9093)
    );
    endpointResolver.registerRemoteEndpoints(serviceId, endpoints);

    // Get multiple stubs - should use round-robin
    SimpleService stub1 = rpcTransport.getRpcStub(serviceId, SimpleService.class);
    SimpleService stub2 = rpcTransport.getRpcStub(serviceId, SimpleService.class);
    SimpleService stub3 = rpcTransport.getRpcStub(serviceId, SimpleService.class);

    assertNotNull(stub1);
    assertNotNull(stub2);
    assertNotNull(stub3);

    // All should be different proxy instances
    assertNotEquals(stub1, stub2);
    assertNotEquals(stub2, stub3);
    assertNotEquals(stub1, stub3);
  }

  @Test
  public void testDirectEndpointStub() {
    // Test creating stub with direct endpoint (no EndpointId)
    Endpoint directEndpoint = new Endpoint("direct-host", 7777);
    EndpointId serviceId = new EndpointId("direct-endpoint-service");

    // Must register the endpoint first
    endpointResolver.registerRemoteEndpoint(serviceId, directEndpoint);

    SimpleService stub = rpcTransport.getRpcStub(directEndpoint, SimpleService.class);
    assertNotNull(stub);
    assertInstanceOf(Proxy.class, stub);

    // Verify toString includes the endpoint info
    String stubString = stub.toString();
    assertTrue(stubString.contains("RemoteStub"));
    assertTrue(stubString.contains(serviceId.toString()));
    assertTrue(stubString.contains(directEndpoint.toString()));
  }

  @Test
  public void testRemoteStubObjectMethods() {
    EndpointId serviceId = new EndpointId("remote-object-methods");
    Endpoint endpoint = new Endpoint("remote-host", 9999);
    endpointResolver.registerRemoteEndpoint(serviceId, endpoint);

    SimpleService remoteStub = rpcTransport.getRpcStub(serviceId, SimpleService.class);

    // Test toString
    String toStringResult = remoteStub.toString();
    assertTrue(toStringResult.contains("RemoteStub"));
    assertTrue(toStringResult.contains(serviceId.toString()));
    assertTrue(toStringResult.contains("round-robin"));

    // Test equals
    assertEquals(remoteStub, remoteStub);
    SimpleService anotherStub = rpcTransport.getRpcStub(serviceId, SimpleService.class);
    assertNotEquals(remoteStub, anotherStub);
    assertNotEquals(null, remoteStub);
    assertNotEquals("not a stub", remoteStub);

    // Test hashCode
    int hashCode = remoteStub.hashCode();
    assertEquals(hashCode, remoteStub.hashCode()); // Should be consistent
    assertNotEquals(hashCode, anotherStub.hashCode()); // Different instances have different hashes
  }
}