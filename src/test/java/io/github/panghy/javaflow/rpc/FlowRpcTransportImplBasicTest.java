package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.logging.Logger;

import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Basic functionality tests for {@link FlowRpcTransportImpl}.
 * Tests service registration, basic method invocation, and object method handling.
 */
@Timeout(30)
public class FlowRpcTransportImplBasicTest extends AbstractFlowTest {

  private static final Logger LOGGER = Logger.getLogger(FlowRpcTransportImplBasicTest.class.getName());

  private FlowRpcTransportImpl transport;
  private SimulatedFlowTransport networkTransport;
  private LocalEndpoint serverEndpoint;

  @BeforeEach
  public void setUp() {
    FlowSerialization.setDefaultSerializer(new DefaultSerializer<>());
    networkTransport = new SimulatedFlowTransport();
    transport = new FlowRpcTransportImpl(networkTransport);
    serverEndpoint = LocalEndpoint.localhost(9876);
  }

  // Test interfaces
  public interface TestService {
    String echo(String message);

    int addNumbers(int a, int b);

    void voidMethod();

    FlowFuture<String> futureMethod();

    FlowPromise<String> promiseMethod();

    PromiseStream<String> streamMethod();
  }

  public interface SecondService {
    String getName();
  }

  public interface ServiceWithObjectLikeMethods {
    String hashCode(String param);

    String toString(int param);

    boolean equals(String a, String b);
  }

  // Test implementations
  private static class TestServiceImpl implements TestService {
    @Override
    public String echo(String message) {
      return "Echo: " + message;
    }

    @Override
    public int addNumbers(int a, int b) {
      return a + b;
    }

    @Override
    public void voidMethod() {
      // Do nothing
    }

    @Override
    public FlowFuture<String> futureMethod() {
      FlowFuture<String> future = new FlowFuture<>();
      future.getPromise().complete("Future result");
      return future;
    }

    @Override
    public FlowPromise<String> promiseMethod() {
      FlowFuture<String> future = new FlowFuture<>();
      future.getPromise().complete("Promise result");
      return future.getPromise();
    }

    @Override
    public PromiseStream<String> streamMethod() {
      PromiseStream<String> stream = new PromiseStream<>();
      stream.send("Stream value 1");
      stream.send("Stream value 2");
      stream.close();
      return stream;
    }
  }

  private static class SecondServiceImpl implements SecondService {
    @Override
    public String getName() {
      return "SecondService";
    }
  }

  private static class ServiceWithObjectLikeMethodsImpl implements ServiceWithObjectLikeMethods {
    @Override
    public String hashCode(String param) {
      return "hashCode called with: " + param;
    }

    @Override
    public String toString(int param) {
      return "toString called with: " + param;
    }

    @Override
    public boolean equals(String a, String b) {
      return a.equals(b);
    }
  }

  @Test
  public void testRegisterServiceDirectly() {
    // Test the registerService method directly
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerService(impl, TestService.class);

    // The service should be registered but not listening yet
    // Start listening
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    // Now create a client and test
    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerLocalEndpoint(serviceId, impl, serverEndpoint);

    TestService client = transport.getRpcStub(serviceId, TestService.class);
    
    FlowFuture<Void> testFuture = startActor(() -> {
      assertEquals("Echo: Hello", client.echo("Hello"));
      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testMultipleServicesOnSameEndpoint() {
    // Register multiple services on the same endpoint
    TestServiceImpl testImpl = new TestServiceImpl();
    SecondServiceImpl secondImpl = new SecondServiceImpl();

    transport.registerServiceAndListen(testImpl, TestService.class, serverEndpoint);
    transport.registerServiceAndListen(secondImpl, SecondService.class, serverEndpoint);

    // Register endpoints
    EndpointId testId = new EndpointId("test-service");
    EndpointId secondId = new EndpointId("second-service");
    transport.getEndpointResolver().registerLocalEndpoint(testId, testImpl, serverEndpoint);
    transport.getEndpointResolver().registerLocalEndpoint(secondId, secondImpl, serverEndpoint);

    // Create clients
    TestService testClient = transport.getRpcStub(testId, TestService.class);
    SecondService secondClient = transport.getRpcStub(secondId, SecondService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test both services
      assertEquals("Echo: Test", testClient.echo("Test"));
      assertEquals("SecondService", secondClient.getName());
      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testUnsupportedObjectMethods() {
    // Test unsupported Object methods in remote proxy
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    TestService client = transport.getRpcStub(serviceId, TestService.class);

    // Test that proxy properly handles Object methods
    assertNotNull(client.toString());
    assertTrue(client.toString().contains("RemoteStub"));
    assertEquals(client, client);
    assertFalse(client.equals(new Object()));
    assertTrue(client.hashCode() != 0);

    // Test trying to call a method name that looks like an Object method but isn't
    try {
      // Use reflection to try to call wait()
      client.getClass().getMethod("wait").invoke(client);
      fail("Should have thrown exception");
    } catch (Exception e) {
      // Expected - wait() is not supported
    }
  }

  @Test
  public void testRemoteStubToStringVariations() {
    // Test different toString variations for remote stubs
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    // Test with round-robin (no specific endpoint)
    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    TestService roundRobinClient = transport.getRpcStub(serviceId, TestService.class);
    assertTrue(roundRobinClient.toString().contains("round-robin"));

    // Test with direct endpoint
    TestService directClient = transport.getRpcStub(serverEndpoint, TestService.class);
    assertTrue(directClient.toString().contains(serverEndpoint.toString()));
  }

  @Test
  public void testLocalStubUnsupportedObjectMethods() {
    // Test unsupported Object methods in local stub
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerLocalEndpoint(serviceId, impl, serverEndpoint);

    TestService localStub = transport.getLocalStub(serviceId, TestService.class);

    // Test that we can't call wait(), notify(), etc.
    try {
      localStub.getClass().getMethod("wait").invoke(localStub);
      fail("Should have thrown exception");
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  public void testMethodNotFoundInRegistry() {
    // Test when a method is not found in the service registry
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    // Create a service interface with a method that doesn't exist in the implementation
    interface ExtendedTestService extends TestService {
      String nonExistentMethod();
    }

    // Try to call a method that doesn't exist
    FlowFuture<Void> testFuture = startActor(() -> {
      try {
        ExtendedTestService client = transport.getRpcStub(serviceId, ExtendedTestService.class);
        client.nonExistentMethod();
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Expected - the method doesn't exist
        assertTrue(e.getMessage().contains("Method not found") ||
                   e.getCause() != null && e.getCause().getMessage().contains("Method not found"));
      }
      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testServiceWithObjectLikeMethods() {
    // Test a service that has methods with names like Object methods but different signatures
    ServiceWithObjectLikeMethodsImpl impl = new ServiceWithObjectLikeMethodsImpl();
    transport.registerServiceAndListen(impl, ServiceWithObjectLikeMethods.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("object-like-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ServiceWithObjectLikeMethods client = transport.getRpcStub(serviceId, ServiceWithObjectLikeMethods.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // These should work because they have different signatures
      assertEquals("hashCode called with: test", client.hashCode("test"));
      assertEquals("toString called with: 42", client.toString(42));
      assertTrue(client.equals("hello", "hello"));
      assertFalse(client.equals("hello", "world"));
      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testVoidMethodInvocation() {
    // Test void method invocation
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    TestService client = transport.getRpcStub(serviceId, TestService.class);

    // Void method should complete without error
    FlowFuture<Void> voidFuture = startActor(() -> {
      client.voidMethod();
      return null;
    });

    pumpUntilDone(voidFuture);
    assertTrue(voidFuture.isDone());
    assertFalse(voidFuture.isCompletedExceptionally());
  }

  @Test
  public void testBasicMethodInvocation() {
    // Test basic method invocation with return value
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    TestService client = transport.getRpcStub(serviceId, TestService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test echo method
      assertEquals("Echo: Hello World", client.echo("Hello World"));

      // Test addNumbers method
      assertEquals(15, client.addNumbers(10, 5));
      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testDirectEndpointConnection() {
    // Test creating a stub with direct endpoint connection
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    // Register the service
    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    // Get stub using direct endpoint
    TestService directClient = transport.getRpcStub(serverEndpoint, TestService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test that it works
      assertEquals("Echo: Direct", directClient.echo("Direct"));
      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testClosedTransport() {
    // Test that operations fail after transport is closed
    FlowFuture<Void> closeFuture = transport.close();
    pumpUntilDone(closeFuture);

    // Try to get a stub after closing
    try {
      transport.getRpcStub(new EndpointId("test"), TestService.class);
      fail("Should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertEquals("RPC transport is closed", e.getMessage());
    }

    // Try to get a local stub after closing
    try {
      transport.getLocalStub(new EndpointId("test"), TestService.class);
      fail("Should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertEquals("RPC transport is closed", e.getMessage());
    }

    // Try to get a stub with endpoint after closing
    try {
      transport.getRpcStub(serverEndpoint, TestService.class);
      fail("Should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertEquals("RPC transport is closed", e.getMessage());
    }
  }

  @Test
  public void testNullEndpointHandling() {
    // Test null endpoint handling
    try {
      transport.getRpcStub((EndpointId) null, TestService.class);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("Endpoint cannot be null", e.getMessage());
    }
  }

  @Test
  public void testUnregisteredEndpoint() {
    // Test getting stub for unregistered endpoint
    LocalEndpoint unregisteredEndpoint = LocalEndpoint.localhost(9999);
    
    try {
      transport.getRpcStub(unregisteredEndpoint, TestService.class);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Endpoint not registered"));
    }
  }
}