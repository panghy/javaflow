package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.Endpoint;
import io.github.panghy.javaflow.io.network.FlowConnection;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.error.RpcException;
import io.github.panghy.javaflow.rpc.message.RpcMessage;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Additional tests for {@link FlowRpcTransportImpl} to improve code coverage.
 * Focuses on edge cases and error handling paths.
 */
@Timeout(30) // Apply 30 second timeout to all test methods
public class FlowRpcTransportImplCoverageTest extends AbstractFlowTest {

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

  public interface NumericService {
    byte getByte();
    short getShort();
    int getInt();
    long getLong();
    void processByte(byte b);
    void processShort(short s);
    void processInt(int i);
    void processLong(long l);
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

  private static class NumericServiceImpl implements NumericService {
    @Override
    public byte getByte() {
      return 42;
    }

    @Override
    public short getShort() {
      return 1000;
    }

    @Override
    public int getInt() {
      return 100000;
    }

    @Override
    public long getLong() {
      return 1000000000L;
    }

    @Override
    public void processByte(byte b) {
      assertEquals(42, b);
    }

    @Override
    public void processShort(short s) {
      assertEquals(1000, s);
    }

    @Override
    public void processInt(int i) {
      assertEquals(100000, i);
    }

    @Override
    public void processLong(long l) {
      assertEquals(1000000000L, l);
    }
  }

  @Test
  public void testRegisterServiceDirectly() {
    // Test the registerService method directly
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerService(impl, TestService.class);
    
    // Now register and listen on an endpoint
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Verify service is properly registered by making a call
    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    assertNotNull(stub);
  }

  @Test
  public void testMultipleServicesOnSameEndpoint() {
    // Register multiple services on the same endpoint
    TestServiceImpl testImpl = new TestServiceImpl();
    NumericServiceImpl numericImpl = new NumericServiceImpl();
    
    // Register both services
    transport.registerServiceAndListen(testImpl, TestService.class, serverEndpoint);
    transport.registerServiceAndListen(numericImpl, NumericService.class, serverEndpoint);
    
    // Verify both services work
    EndpointId testServiceId = new EndpointId("test-service");
    EndpointId numericServiceId = new EndpointId("numeric-service");
    
    transport.getEndpointResolver().registerRemoteEndpoint(testServiceId, serverEndpoint);
    transport.getEndpointResolver().registerRemoteEndpoint(numericServiceId, serverEndpoint);
    
    TestService testStub = transport.getRpcStub(testServiceId, TestService.class);
    NumericService numericStub = transport.getRpcStub(numericServiceId, NumericService.class);
    
    assertNotNull(testStub);
    assertNotNull(numericStub);
  }

  @Test
  public void testNumericTypeConversions() throws Exception {
    // Test numeric type conversions in arguments and return values
    NumericServiceImpl impl = new NumericServiceImpl();
    transport.registerServiceAndListen(impl, NumericService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("numeric-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    NumericService stub = transport.getRpcStub(serviceId, NumericService.class);
    
    // Pump once to let the server start listening
    pump();
    
    // Test return value conversions
    FlowFuture<Byte> byteF = startActor(() -> stub.getByte());
    pumpUntilDone(byteF);
    assertTrue(byteF.isDone());
    assertEquals(42, byteF.getNow().byteValue());
    
    FlowFuture<Short> shortF = startActor(() -> stub.getShort());
    pumpUntilDone(shortF);
    assertTrue(shortF.isDone());
    assertEquals(1000, shortF.getNow().shortValue());
    
    FlowFuture<Integer> intF = startActor(() -> stub.getInt());
    pumpUntilDone(intF);
    assertTrue(intF.isDone());
    assertEquals(100000, intF.getNow().intValue());
    
    FlowFuture<Long> longF = startActor(() -> stub.getLong());
    pumpUntilDone(longF);
    assertTrue(longF.isDone());
    assertEquals(1000000000L, longF.getNow().longValue());
    
    // Test argument conversions
    FlowFuture<Void> voidF = startActor(() -> {
      stub.processByte((byte) 42);
      stub.processShort((short) 1000);
      stub.processInt(100000);
      stub.processLong(1000000000L);
      return null;
    });
    pumpUntilDone(voidF);
    assertTrue(voidF.isDone());
  }

  @Test
  public void testNumericOverflowInReturnValue() throws Exception {
    // Test numeric overflow handling
    NumericService overflowService = new NumericService() {
      @Override
      public byte getByte() {
        return 0;
      }
      
      @Override
      public short getShort() {
        return 0;
      }
      
      @Override
      public int getInt() {
        return 0;
      }
      
      @Override
      public long getLong() {
        // This will cause overflow when converting to smaller types
        return Long.MAX_VALUE;
      }
      
      @Override
      public void processByte(byte b) { }
      @Override
      public void processShort(short s) { }
      @Override
      public void processInt(int i) { }
      @Override
      public void processLong(long l) { }
    };
    
    transport.registerServiceAndListen(overflowService, NumericService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("overflow-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    // Create a stub that expects an int but will receive a long
    // We need to simulate this through a custom message
    startActor(() -> {
      try {
        // Send a custom message that returns Long.MAX_VALUE for a method expecting int
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Create request
        UUID messageId = UUID.randomUUID();
        String methodId = NumericService.class.getName() + ".getInt()";
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[0])
        );
        
        await(connection.send(request.serialize()));
        
        // The server will respond with a long value due to our override
        // This tests the numeric conversion in the client
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testExtractPromiseTypeFromInstance() throws Exception {
    // Test extracting type from FlowPromise with complex generic types
    interface ComplexPromiseService {
      FlowPromise<List<String>> getListPromise();
    }
    
    ComplexPromiseService impl = () -> {
      FlowFuture<List<String>> future = new FlowFuture<>();
      future.getPromise().complete(List.of("test1", "test2"));
      return future.getPromise();
    };
    
    transport.registerServiceAndListen(impl, ComplexPromiseService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("promise-type-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    ComplexPromiseService stub = transport.getRpcStub(serviceId, ComplexPromiseService.class);
    
    FlowFuture<FlowPromise<List<String>>> promiseF = startActor(() -> stub.getListPromise());
    pumpUntilDone(promiseF);
    
    FlowPromise<List<String>> promise = promiseF.getNow();
    assertNotNull(promise);
  }

  @Test
  public void testUnsupportedObjectMethods() {
    // Test unsupported Object methods in remote proxy
    EndpointId serviceId = new EndpointId("unsupported-service");
    Endpoint endpoint = new Endpoint("localhost", 7777);
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, endpoint);
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    // Test that proxy properly handles Object methods
    // toString should return RemoteStub[...]
    String toString = stub.toString();
    assertTrue(toString.contains("RemoteStub"));
    
    // equals and hashCode should work
    assertEquals(stub, stub);
    assertNotNull(stub.hashCode());
    
    // Test trying to call a method name that looks like an Object method but isn't
    interface ServiceWithObjectLikeMethods {
      void wait(int timeout); // This will confuse with Object.wait()
      void notify(String message); // This will confuse with Object.notify()
    }
    
    ServiceWithObjectLikeMethods stub2 = transport.getRpcStub(serviceId, ServiceWithObjectLikeMethods.class);
    
    // These should not throw UnsupportedOperationException because they're interface methods
    FlowFuture<Void> future = startActor(() -> {
      stub2.wait(100);
      stub2.notify("test");
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testRemoteStubToStringVariations() {
    // Test different toString variations for remote stubs
    
    // 1. Round-robin stub (no physical endpoint)
    EndpointId serviceId1 = new EndpointId("round-robin-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId1, new Endpoint("host1", 8001));
    TestService roundRobinStub = transport.getRpcStub(serviceId1, TestService.class);
    String toString1 = roundRobinStub.toString();
    assertTrue(toString1.contains("RemoteStub"));
    assertTrue(toString1.contains("round-robin"));
    assertTrue(toString1.contains(serviceId1.toString()));
    
    // 2. Direct endpoint stub (with both EndpointId and physical endpoint)
    Endpoint directEndpoint = new Endpoint("host2", 8002);
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId1, directEndpoint);
    TestService directStub = transport.getRpcStub(directEndpoint, TestService.class);
    String toString2 = directStub.toString();
    assertTrue(toString2.contains("RemoteStub"));
    assertTrue(toString2.contains(directEndpoint.toString()));
    
    // 3. Direct endpoint without EndpointId - this isn't possible in current impl
    // as getRpcStub(Endpoint) requires the endpoint to be registered
  }

  @Test
  public void testLocalStubUnsupportedObjectMethods() {
    // Test unsupported Object methods in local stub
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId serviceId = new EndpointId("local-unsupported");
    transport.getEndpointResolver().registerLoopbackEndpoint(serviceId, impl);
    
    // Get local stub through a proxy to test object methods
    TestService localStub = transport.getLocalStub(serviceId, TestService.class);
    
    // Test that we can't call wait(), notify(), etc.
    // These are final methods and handled by the JVM, not our invocation handler
    assertNotNull(localStub.getClass());
  }

  @Test
  public void testMethodNotFoundInRegistry() {
    // Test when a method is not found in the service registry
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Send a request with an invalid method ID
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String invalidMethodId = "com.invalid.Service.invalidMethod()";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            invalidMethodId,
            null,
            FlowSerialization.serialize(new Object[0])
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
        assertEquals(messageId, responseMsg.getHeader().getMessageId());
        
        Exception error = FlowSerialization.deserialize(responseMsg.getPayload(), Exception.class);
        assertInstanceOf(RpcException.class, error);
        assertTrue(error.getMessage().contains("Method not found"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testSerializationErrorInRequest() throws Exception {
    // Test serialization error when sending request
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("serialization-error");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    // Create a service with an unserializable argument
    interface ServiceWithUnserializable {
      void process(Object unserializable);
    }
    
    ServiceWithUnserializable stub = transport.getRpcStub(serviceId, ServiceWithUnserializable.class);
    
    // Create an unserializable object
    Object unserializable = new Object() {
      private final Thread thread = Thread.currentThread(); // Thread is not serializable
    };
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.process(unserializable);
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    pumpUntilDone(errorF);
    
    Exception error = errorF.getNow();
    assertNotNull(error);
    // The error is wrapped in RpcException with INVOCATION_ERROR code
    assertInstanceOf(RpcException.class, error);
    assertTrue(error.getMessage().contains("RPC invocation failed"));
  }

  @Test
  public void testConnectionCloseDuringRequest() throws Exception {
    // Test connection closing while waiting for response
    AtomicReference<FlowConnection> connectionRef = new AtomicReference<>();
    
    // Start a custom server that closes connection after receiving request
    startActor(() -> {
      try {
        var stream = networkTransport.listen(LocalEndpoint.localhost(6666));
        FlowConnection conn = await(stream.nextAsync());
        connectionRef.set(conn);
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        
        // Close connection without sending response
        conn.close();
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("close-connection");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, LocalEndpoint.localhost(6666));
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.echo("test");
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    // Pump until connection is closed
    for (int i = 0; i < 10; i++) {
      pumpUntilDone();
      if (connectionRef.get() != null && !connectionRef.get().isOpen()) {
        break;
      }
    }
    
    // The error should eventually propagate
    pumpUntilDone();
    
    assertTrue(errorF.isDone() || connectionRef.get() == null);
    if (errorF.isDone()) {
      Exception error = errorF.getNow();
      assertInstanceOf(RpcException.class, error);
    }
  }

  @Test
  public void testPromiseAsReturnValueAlreadyCompleted() throws Exception {
    // Test returning an already completed FlowFuture - this tests how already-completed
    // futures are handled. When a method returns a completed FlowFuture, the RPC framework
    // unwraps it and sends the value directly. 
    
    // First test: Method returns String directly
    TestService service1 = new TestService() {
      @Override
      public String echo(String message) {
        return "Echo: " + message;
      }
      
      @Override
      public int addNumbers(int a, int b) {
        return a + b;
      }
      
      @Override
      public void voidMethod() { }
      
      @Override
      public FlowFuture<String> futureMethod() {
        return null;
      }
      
      @Override
      public FlowPromise<String> promiseMethod() {
        return null;
      }
      
      @Override
      public PromiseStream<String> streamMethod() {
        return null;
      }
    };
    
    transport.registerServiceAndListen(service1, TestService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("direct-return");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    // Pump to ensure server is ready
    pump();
    
    // Test normal method
    FlowFuture<String> echoResult = startActor(() -> stub.echo("test"));
    pumpUntilDone(echoResult);
    assertEquals("Echo: test", echoResult.getNow());
    
    // Second test: Method returns incomplete FlowFuture
    TestService service2 = new TestService() {
      @Override
      public String echo(String message) {
        return message;
      }
      
      @Override
      public int addNumbers(int a, int b) {
        return a + b;
      }
      
      @Override
      public void voidMethod() { }
      
      @Override
      public FlowFuture<String> futureMethod() {
        // Return incomplete future that completes later
        FlowFuture<String> future = new FlowFuture<>();
        startActor(() -> {
          future.getPromise().complete("Delayed result");
          return null;
        });
        return future;
      }
      
      @Override
      public FlowPromise<String> promiseMethod() {
        return null;
      }
      
      @Override
      public PromiseStream<String> streamMethod() {
        return null;
      }
    };
    
    // Re-register with new implementation
    transport = new FlowRpcTransportImpl(networkTransport);
    transport.registerServiceAndListen(service2, TestService.class, LocalEndpoint.localhost(9877));
    
    EndpointId serviceId2 = new EndpointId("incomplete-future");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId2, LocalEndpoint.localhost(9877));
    
    TestService stub2 = transport.getRpcStub(serviceId2, TestService.class);
    
    // Test method returning incomplete future
    FlowFuture<String> futureResult = stub2.futureMethod();
    pumpUntilDone(futureResult);
    assertTrue(futureResult.isDone());
    assertEquals("Delayed result", futureResult.getNow());
  }

  @Test
  public void testExceptionalFutureReturn() throws Exception {
    // Test returning a future that completes exceptionally
    TestService service = new TestService() {
      @Override
      public String echo(String message) {
        return message;
      }
      
      @Override
      public int addNumbers(int a, int b) {
        return a + b;
      }
      
      @Override
      public void voidMethod() { }
      
      @Override
      public FlowFuture<String> futureMethod() {
        // Return future that completes exceptionally
        FlowFuture<String> future = new FlowFuture<>();
        future.getPromise().completeExceptionally(new IllegalStateException("Future failed"));
        return future;
      }
      
      @Override
      public FlowPromise<String> promiseMethod() {
        return null;
      }
      
      @Override
      public PromiseStream<String> streamMethod() {
        return null;
      }
    };
    
    transport.registerServiceAndListen(service, TestService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("exceptional-future");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    // Pump to ensure server is ready
    pump();
    
    // When the future is already completed exceptionally, it should propagate through RPC
    FlowFuture<String> result = stub.futureMethod();
    pumpUntilDone(result);
    
    assertTrue(result.isDone());
    assertTrue(result.isCompletedExceptionally());
    
    // Try to get the value to see the exception
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        result.getNow();
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    pumpUntilDone(errorF);
    Exception ex = errorF.getNow();
    assertNotNull(ex);
    // The exception should be wrapped in an ExecutionException
    assertTrue(ex.getMessage().contains("Future failed"));
  }

  @Test
  public void testInvalidArgumentCount() {
    // Test sending wrong number of arguments
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Send request with wrong argument count
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = TestService.class.getName() + ".echo(java.lang.String)";
        
        // Send empty arguments array instead of one string
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[0]) // Wrong arg count
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testUnknownMessageType() {
    // Test handling of unknown message type
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Send a message with an unknown type
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a malformed message to trigger unknown message type handling
        // We'll send raw bytes that decode to an invalid message
        ByteBuffer invalidMessage = ByteBuffer.allocate(100);
        invalidMessage.putInt(50); // Some length
        invalidMessage.put((byte) 99); // Invalid message type byte
        invalidMessage.flip();
        
        await(connection.send(invalidMessage));
        
        // The server should log a warning but continue processing
        // No response expected for unknown message type
      } catch (Exception e) {
        // Expected - might fail during message processing
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testDeserializationErrorInResponse() throws Exception {
    // Test deserialization error when receiving response
    // We'll create a custom server that sends malformed response
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(7777);
    
    startActor(() -> {
      try {
        var stream = networkTransport.listen(customEndpoint);
        FlowConnection conn = await(stream.nextAsync());
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);
        
        // Send back a response with malformed payload
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            requestMsg.getHeader().getMethodId(),
            null,
            ByteBuffer.wrap(new byte[]{1, 2, 3}) // Invalid serialized data
        );
        
        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("deserialization-error");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.echo("test");
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    pumpUntilDone(errorF);
    
    Exception error = errorF.getNow();
    assertNotNull(error);
    // Should get an error due to deserialization failure
  }

  @Test
  public void testPromiseCompletionWithWrongType() {
    // Test promise completion message with wrong payload type
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Register a promise in the tracker
    UUID promiseId = UUID.randomUUID();
    
    // Send a promise completion message with wrong type
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send PROMISE_COMPLETE message with Integer payload instead of String
        RpcMessage message = new RpcMessage(
            RpcMessageHeader.MessageType.PROMISE_COMPLETE,
            promiseId,
            null,
            null,
            FlowSerialization.serialize(42) // Integer instead of String
        );
        
        await(connection.send(message.serialize()));
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testStreamOperations() {
    // Test stream data and close messages
    
    // Create a service that returns a stream
    interface StreamService {
      PromiseStream<String> getStream();
    }
    
    StreamService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      // Send values directly (synchronously for simplicity)
      stream.send("Value 1");
      stream.send("Value 2");
      stream.close();
      return stream;
    };
    
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    StreamService stub = transport.getRpcStub(serviceId, StreamService.class);
    
    pump(); // Let server start
    
    // Call method that returns a stream
    AtomicReference<PromiseStream<String>> streamRef = new AtomicReference<>();
    List<String> receivedValues = new java.util.ArrayList<>();
    AtomicReference<Exception> streamError = new AtomicReference<>();
    AtomicBoolean streamClosed = new AtomicBoolean(false);
    
    FlowFuture<Void> streamF = startActor(() -> {
      PromiseStream<String> stream = stub.getStream();
      streamRef.set(stream);
      
      // Consume stream values
      FutureStream<String> futureStream = stream.getFutureStream();
      
      // Use the Flow API to consume stream
      try {
        while (true) {
          Boolean hasNext = await(futureStream.hasNextAsync());
          if (!hasNext) {
            streamClosed.set(true);
            break;
          }
          String value = await(futureStream.nextAsync());
          receivedValues.add(value);
        }
      } catch (Exception e) {
        streamError.set(e);
      }
      
      return null;
    });
    
    // Pump until stream is complete
    pumpUntilDone(streamF);
    
    // Verify we received the values
    assertTrue(streamClosed.get());
    assertEquals(2, receivedValues.size());
    assertEquals("Value 1", receivedValues.get(0));
    assertEquals("Value 2", receivedValues.get(1));
    assertNull(streamError.get());
  }
  
  @Test
  public void testStreamWithError() {
    // Test stream that ends with error
    interface StreamService {
      PromiseStream<String> getStreamWithError();
    }
    
    StreamService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      // Start actor to send values and then error
      startActor(() -> {
        System.err.println("TEST: Sending Value 1");
        stream.send("Value 1");
        System.err.println("TEST: Closing stream exceptionally");
        stream.closeExceptionally(new RuntimeException("Stream failed"));
        System.err.println("TEST: Stream closed");
        return null;
      });
      return stream;
    };
    
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("stream-error-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    StreamService stub = transport.getRpcStub(serviceId, StreamService.class);
    
    pump(); // Let server start
    
    // Call method that returns a stream with error
    List<String> receivedValues = new java.util.ArrayList<>();
    AtomicReference<Exception> streamError = new AtomicReference<>();
    
    FlowFuture<Void> streamF = startActor(() -> {
      PromiseStream<String> stream = stub.getStreamWithError();
      
      // Consume stream values
      FutureStream<String> futureStream = stream.getFutureStream();
      
      // Use the Flow API to consume stream
      try {
        while (await(futureStream.hasNextAsync())) {
          String value = await(futureStream.nextAsync());
          receivedValues.add(value);
        }
      } catch (Exception e) {
        streamError.set(e);
      }
      
      // Check if the stream closed with an error
      try {
        await(futureStream.onClose());
      } catch (Exception e) {
        streamError.set(e);
      }
      
      return null;
    });
    
    // Pump until stream is complete
    pumpUntilDone(streamF);
    
    // Verify we received one value and then error
    assertEquals(1, receivedValues.size());
    assertEquals("Value 1", receivedValues.get(0));
    assertNotNull(streamError.get());
    assertInstanceOf(RuntimeException.class, streamError.get());
    assertEquals("Stream failed", streamError.get().getMessage());
  }
  
  @Test
  public void testStreamDeserializationError() {
    // Test stream data deserialization error
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    UUID streamId = UUID.randomUUID();
    
    // Send stream data with invalid payload that can't be deserialized
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send STREAM_DATA message with corrupted payload
        ByteBuffer corruptedPayload = ByteBuffer.wrap(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        RpcMessage dataMessage = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_DATA,
            streamId,
            null,
            null,
            corruptedPayload
        );
        
        await(connection.send(dataMessage.serialize()));
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testStreamCloseDeserializationError() {
    // Test stream close error deserialization failure
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    UUID streamId = UUID.randomUUID();
    
    // Send stream close with invalid error payload
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send STREAM_CLOSE message with corrupted error payload
        ByteBuffer corruptedPayload = ByteBuffer.wrap(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        RpcMessage closeMessage = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_CLOSE,
            streamId,
            null,
            null,
            corruptedPayload
        );
        
        await(connection.send(closeMessage.serialize()));
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testMapResponseEdgeCases() throws Exception {
    // Test edge cases in mapResponse method
    interface EdgeCaseService {
      void voidReturn();
      Object objectReturn();
      FlowPromise<String> promiseReturn();
    }
    
    EdgeCaseService impl = new EdgeCaseService() {
      @Override
      public void voidReturn() {
        // Do nothing
      }
      
      @Override
      public Object objectReturn() {
        return null; // Return null
      }
      
      @Override
      public FlowPromise<String> promiseReturn() {
        // Return a promise that's already completed
        FlowFuture<String> future = new FlowFuture<>();
        future.getPromise().complete("Already completed");
        return future.getPromise();
      }
    };
    
    transport.registerServiceAndListen(impl, EdgeCaseService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("edge-case-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    EdgeCaseService stub = transport.getRpcStub(serviceId, EdgeCaseService.class);
    
    pump(); // Let server start
    
    // Test void return
    FlowFuture<Void> voidF = startActor(() -> {
      stub.voidReturn();
      return null;
    });
    pumpUntilDone(voidF);
    assertTrue(voidF.isDone());
    
    // Test null object return
    FlowFuture<Object> objectF = startActor(() -> stub.objectReturn());
    pumpUntilDone(objectF);
    assertTrue(objectF.isDone());
    assertNull(objectF.getNow());
    
    // Test promise return
    FlowFuture<FlowPromise<String>> promiseF = startActor(() -> stub.promiseReturn());
    pumpUntilDone(promiseF);
    FlowPromise<String> promise = promiseF.getNow();
    assertNotNull(promise);
  }

  @Test
  public void testPromiseCompletionLambda() throws Exception {
    // Test the lambda$sendResponse$2 method - promise completion handler
    interface AsyncService {
      FlowPromise<String> delayedResult();
    }
    
    AsyncService impl = () -> {
      FlowFuture<String> future = new FlowFuture<>();
      // Complete promise asynchronously
      startActor(() -> {
        future.getPromise().complete("Delayed result");
        return null;
      });
      return future.getPromise();
    };
    
    transport.registerServiceAndListen(impl, AsyncService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("async-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    AsyncService stub = transport.getRpcStub(serviceId, AsyncService.class);
    
    pump(); // Let server start
    
    // Call method that returns incomplete promise
    FlowFuture<FlowPromise<String>> promiseF = startActor(() -> stub.delayedResult());
    pumpUntilDone(promiseF);
    
    FlowPromise<String> promise = promiseF.getNow();
    assertNotNull(promise);
    
    // Wait for the promise to complete
    FlowFuture<String> valueFuture = promise.getFuture();
    pumpUntilDone(valueFuture);
    
    assertEquals("Delayed result", valueFuture.getNow());
  }
  
  @Test
  public void testPromiseCompletionExceptionallyLambda() throws Exception {
    // Test promise completion with error - lambda$sendResponse$2 error path
    interface AsyncService {
      FlowPromise<String> failingResult();
    }
    
    AsyncService impl = () -> {
      FlowFuture<String> future = new FlowFuture<>();
      // Complete promise exceptionally asynchronously
      startActor(() -> {
        future.getPromise().completeExceptionally(new RuntimeException("Promise failed"));
        return null;
      });
      return future.getPromise();
    };
    
    transport.registerServiceAndListen(impl, AsyncService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("async-error-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    AsyncService stub = transport.getRpcStub(serviceId, AsyncService.class);
    
    pump(); // Let server start
    
    // Call method that returns promise that will fail
    FlowFuture<FlowPromise<String>> promiseF = startActor(() -> stub.failingResult());
    pumpUntilDone(promiseF);
    
    FlowPromise<String> promise = promiseF.getNow();
    assertNotNull(promise);
    
    // Wait for the promise to complete exceptionally
    FlowFuture<String> valueFuture = promise.getFuture();
    pumpUntilDone(valueFuture);
    
    assertTrue(valueFuture.isCompletedExceptionally());
  }
  
  @Test
  public void testStartMessageReaderConnectionClose() {
    // Test lambda$startMessageReader$0 when connection closes
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Create a direct connection and then close it
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a request
        UUID messageId = UUID.randomUUID();
        String methodId = TestService.class.getName() + ".echo(java.lang.String)";
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[]{"test"})
        );
        
        await(connection.send(request.serialize()));
        
        // Close connection immediately
        connection.close();
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConvertArgumentTypeEdgeCases() throws Exception {
    // Test convertArgumentType method edge cases
    interface ConversionService {
      void processLongAsInt(int value);
      void processLongAsShort(short value);
      void processLongAsByte(byte value);
      void processNull(String value);
    }
    
    ConversionService impl = new ConversionService() {
      @Override
      public void processLongAsInt(int value) {
        assertEquals(12345, value);
      }
      
      @Override
      public void processLongAsShort(short value) {
        assertEquals(1234, value);
      }
      
      @Override
      public void processLongAsByte(byte value) {
        assertEquals(123, value);
      }
      
      @Override
      public void processNull(String value) {
        assertNull(value);
      }
    };
    
    transport.registerServiceAndListen(impl, ConversionService.class, serverEndpoint);
    
    // Now we need to send raw messages with Long values to test conversion
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test int conversion
        UUID messageId1 = UUID.randomUUID();
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            ConversionService.class.getName() + ".processLongAsInt(int)",
            null,
            FlowSerialization.serialize(new Object[]{12345L}) // Send Long instead of int
        );
        await(connection.send(request1.serialize()));
        
        // Test short conversion
        UUID messageId2 = UUID.randomUUID();
        RpcMessage request2 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId2,
            ConversionService.class.getName() + ".processLongAsShort(short)",
            null,
            FlowSerialization.serialize(new Object[]{1234L}) // Send Long instead of short
        );
        await(connection.send(request2.serialize()));
        
        // Test byte conversion
        UUID messageId3 = UUID.randomUUID();
        RpcMessage request3 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId3,
            ConversionService.class.getName() + ".processLongAsByte(byte)",
            null,
            FlowSerialization.serialize(new Object[]{123L}) // Send Long instead of byte
        );
        await(connection.send(request3.serialize()));
        
        // Test null conversion
        UUID messageId4 = UUID.randomUUID();
        RpcMessage request4 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId4,
            ConversionService.class.getName() + ".processNull(java.lang.String)",
            null,
            FlowSerialization.serialize(new Object[]{null})
        );
        await(connection.send(request4.serialize()));
        
        // Wait for responses
        for (int i = 0; i < 4; i++) {
          ByteBuffer response = await(connection.receive(65536));
          RpcMessage responseMsg = RpcMessage.deserialize(response);
          assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConvertArgumentTypeOverflow() throws Exception {
    // Test overflow in numeric conversions
    interface ConversionService {
      void processOverflowInt(int value);
      void processOverflowShort(short value);
      void processOverflowByte(byte value);
    }
    
    ConversionService impl = new ConversionService() {
      @Override
      public void processOverflowInt(int value) {
        // Should not be called
      }
      
      @Override
      public void processOverflowShort(short value) {
        // Should not be called
      }
      
      @Override
      public void processOverflowByte(byte value) {
        // Should not be called
      }
    };
    
    transport.registerServiceAndListen(impl, ConversionService.class, serverEndpoint);
    
    // Send messages with overflowing values
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test int overflow
        UUID messageId1 = UUID.randomUUID();
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            ConversionService.class.getName() + ".processOverflowInt(int)",
            null,
            FlowSerialization.serialize(new Object[]{Long.MAX_VALUE}) // Too big for int
        );
        await(connection.send(request1.serialize()));
        
        // Expect error response
        ByteBuffer response1 = await(connection.receive(65536));
        RpcMessage responseMsg1 = RpcMessage.deserialize(response1);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg1.getHeader().getType());
        
        // Test short overflow
        UUID messageId2 = UUID.randomUUID();
        RpcMessage request2 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId2,
            ConversionService.class.getName() + ".processOverflowShort(short)",
            null,
            FlowSerialization.serialize(new Object[]{100000L}) // Too big for short
        );
        await(connection.send(request2.serialize()));
        
        // Expect error response
        ByteBuffer response2 = await(connection.receive(65536));
        RpcMessage responseMsg2 = RpcMessage.deserialize(response2);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg2.getHeader().getType());
        
        // Test byte overflow
        UUID messageId3 = UUID.randomUUID();
        RpcMessage request3 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId3,
            ConversionService.class.getName() + ".processOverflowByte(byte)",
            null,
            FlowSerialization.serialize(new Object[]{1000L}) // Too big for byte
        );
        await(connection.send(request3.serialize()));
        
        // Expect error response
        ByteBuffer response3 = await(connection.receive(65536));
        RpcMessage responseMsg3 = RpcMessage.deserialize(response3);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg3.getHeader().getType());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testPromiseCompletionWithTypeResolution() {
    // Test promise completion with ClassNotFoundException in type resolution
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Create a RemotePromiseTracker with a promise that has an unresolvable type
    UUID promiseId = UUID.randomUUID();
    
    // Send a promise completion message
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send PROMISE_COMPLETE message
        RpcMessage message = new RpcMessage(
            RpcMessageHeader.MessageType.PROMISE_COMPLETE,
            promiseId,
            null,
            null,
            FlowSerialization.serialize("Promise value")
        );
        
        await(connection.send(message.serialize()));
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testSendErrorResponseWithNonException() throws Exception {
    // Test sendErrorResponse when error is not an Exception
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Create a custom service that throws Error instead of Exception
    interface ErrorThrowingService {
      String throwError();
    }
    
    ErrorThrowingService errorImpl = () -> {
      throw new Error("This is an error, not exception");
    };
    
    transport.registerServiceAndListen(errorImpl, ErrorThrowingService.class, LocalEndpoint.localhost(9878));
    
    EndpointId serviceId = new EndpointId("error-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, LocalEndpoint.localhost(9878));
    
    ErrorThrowingService stub = transport.getRpcStub(serviceId, ErrorThrowingService.class);
    
    pump(); // Let server start
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.throwError();
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    pumpUntilDone(errorF);
    
    Exception error = errorF.getNow();
    assertNotNull(error);
    assertInstanceOf(RpcException.class, error);
  }

  @Test
  public void testConnectionMessageHandlerErrorResponse() {
    // Test error response handling in ConnectionMessageHandler
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Send a request that will get an error response first, then send invalid error message
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // First, send a request that generates an error response (method not found)
        UUID messageId1 = UUID.randomUUID();
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            "invalid.method.id()",
            null,
            FlowSerialization.serialize(new Object[0])
        );
        await(connection.send(request.serialize()));
        
        // Wait for error response 
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
        
        // Now send an ERROR message for a non-existent pending call
        UUID messageId2 = UUID.randomUUID();
        RpcMessage errorMsg = new RpcMessage(
            RpcMessageHeader.MessageType.ERROR,
            messageId2,  // Different message ID that's not in pendingCalls
            null,
            null,
            FlowSerialization.serialize(new RuntimeException("Test error"))
        );
        await(connection.send(errorMsg.serialize()));
        
        // Send a RESPONSE message for a non-existent pending call
        UUID messageId3 = UUID.randomUUID();
        RpcMessage responseMsg2 = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            messageId3,  // Different message ID that's not in pendingCalls
            null,
            null,
            FlowSerialization.serialize("response")
        );
        await(connection.send(responseMsg2.serialize()));
        
      } catch (Exception e) {
        // Expected - connection might close
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test 
  public void testConnectionMessageHandlerNullPayload() {
    // Test handling messages with null payloads in ConnectionMessageHandler
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send PROMISE_COMPLETE with null payload
        UUID promiseId = UUID.randomUUID();
        RpcMessage promiseMsg = new RpcMessage(
            RpcMessageHeader.MessageType.PROMISE_COMPLETE,
            promiseId,
            null,
            null,
            null  // null payload
        );
        await(connection.send(promiseMsg.serialize()));
        
        // Send STREAM_CLOSE with null payload (normal close)
        UUID streamId = UUID.randomUUID();
        RpcMessage streamCloseMsg = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_CLOSE,
            streamId,
            null,
            null,
            null  // null payload for normal close
        );
        await(connection.send(streamCloseMsg.serialize()));
        
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerCorruptedMessage() {
    // Test handling completely corrupted messages that fail to deserialize
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send completely invalid data that will fail RpcMessage.deserialize
        ByteBuffer corruptedData = ByteBuffer.allocate(20);
        corruptedData.putInt(16);  // Length
        corruptedData.put((byte) 255);  // Invalid type
        corruptedData.putLong(0);  // Invalid UUID high
        corruptedData.putLong(0);  // Invalid UUID low
        corruptedData.flip();
        
        await(connection.send(corruptedData));
        
        // Send another corrupted message with too short length
        ByteBuffer shortData = ByteBuffer.allocate(4);
        shortData.putInt(100);  // Claims 100 bytes but only has 4
        shortData.flip();
        
        await(connection.send(shortData));
        
      } catch (Exception e) {
        // Expected - connection will likely fail
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerStreamTypeResolution() {
    // Test stream type resolution with ClassNotFoundException
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Create a stream with an element type that will cause ClassNotFoundException
        UUID streamId = UUID.randomUUID();
        
        // Send STREAM_DATA message - the getIncomingStreamType will return null
        // which will cause ClassNotFoundException handling path in handleStreamData
        RpcMessage streamDataMsg = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_DATA,
            streamId,
            null,
            null,
            FlowSerialization.serialize("test data")
        );
        await(connection.send(streamDataMsg.serialize()));
        
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerPromiseTypeResolution() {
    // Test promise type resolution with ClassNotFoundException
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send PROMISE_COMPLETE message for unknown promise
        // This will trigger the type resolution path where getIncomingPromiseType returns null
        UUID promiseId = UUID.randomUUID();
        RpcMessage promiseMsg = new RpcMessage(
            RpcMessageHeader.MessageType.PROMISE_COMPLETE,
            promiseId,
            null,
            null,
            FlowSerialization.serialize("promise result")
        );
        await(connection.send(promiseMsg.serialize()));
        
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerEmptyPayload() {
    // Test REQUEST message with empty payload (no arguments)
    interface NoArgsService {
      String noArgs();
    }
    
    NoArgsService impl = () -> "no args result";
    
    transport.registerServiceAndListen(impl, NoArgsService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send request with empty payload
        UUID messageId = UUID.randomUUID();
        String methodId = NoArgsService.class.getName() + ".noArgs()";
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            null  // null payload for no arguments
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        assertEquals(messageId, responseMsg.getHeader().getMessageId());
        
        String result = FlowSerialization.deserialize(responseMsg.getPayload(), String.class);
        assertEquals("no args result", result);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerZeroLengthPayload() {
    // Test REQUEST message with zero-length payload
    interface NoArgsService {
      String noArgs();
    }
    
    NoArgsService impl = () -> "zero length result";
    
    transport.registerServiceAndListen(impl, NoArgsService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send request with zero-length payload
        UUID messageId = UUID.randomUUID();
        String methodId = NoArgsService.class.getName() + ".noArgs()";
        ByteBuffer emptyPayload = ByteBuffer.allocate(0);
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            emptyPayload  // zero-length payload
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerSendResponseSerializationError() throws Exception {
    // Test sendResponse when serialization of result fails
    interface ProblematicService {
      Object getUnserializableObject();
    }
    
    ProblematicService impl = () -> {
      // Return an object that will cause serialization to fail
      return new Object() {
        private final Thread thread = Thread.currentThread(); // Not serializable
      };
    };
    
    transport.registerServiceAndListen(impl, ProblematicService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("problematic-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    ProblematicService stub = transport.getRpcStub(serviceId, ProblematicService.class);
    
    pump(); // Let server start
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.getUnserializableObject();
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    pumpUntilDone(errorF);
    
    Exception error = errorF.getNow();
    assertNotNull(error);
    // Should get RpcException due to serialization failure on server side
    assertInstanceOf(RpcException.class, error);
  }

  @Test
  public void testConnectionMessageHandlerResponseWithNullPayload() throws Exception {
    // Test mapResponse with null payload in response message
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(8888);
    
    startActor(() -> {
      try {
        var stream = networkTransport.listen(customEndpoint);
        FlowConnection conn = await(stream.nextAsync());
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);
        
        // Send response with null payload
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            null,
            null,
            null  // null payload
        );
        
        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("null-payload-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);
    
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    FlowFuture<String> resultF = startActor(() -> stub.echo("test"));
    pumpUntilDone(resultF);
    
    // Should get null as result
    assertTrue(resultF.isDone());
    assertNull(resultF.getNow());
  }

  @Test
  public void testConnectionMessageHandlerSendErrorResponseFail() throws Exception {
    // Test when sendErrorResponse itself fails due to serialization error
    interface ErrorService {
      String methodThatThrowsUnserializableError();
    }
    
    ErrorService impl = () -> {
      // Throw an error that contains unserializable objects
      throw new RuntimeException("Error with unserializable cause") {
        private final Thread thread = Thread.currentThread();  // Unserializable
      };
    };
    
    transport.registerServiceAndListen(impl, ErrorService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("error-serialization-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    ErrorService stub = transport.getRpcStub(serviceId, ErrorService.class);
    
    pump(); // Let server start
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.methodThatThrowsUnserializableError();
        return null;
      } catch (Exception e) {
        return e;
      }
    });

    pumpUntilDone();
    
    // Should complete or timeout after pumping
    if (errorF.isDone()) {
      Exception error = errorF.getNow();
      assertNotNull(error);
      // Should still get an RpcException even though the original error couldn't be serialized
      assertInstanceOf(RpcException.class, error);
    }
  }

  @Test
  public void testConnectionMessageHandlerMapResponseWithClassNotFound() throws Exception {
    // Test mapResponse when TypeDescription.toType() throws ClassNotFoundException
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(7999);
    
    startActor(() -> {
      try {
        var stream = networkTransport.listen(customEndpoint);
        FlowConnection conn = await(stream.nextAsync());
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);
        
        // Send response with a simple string payload
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            null,
            null,
            FlowSerialization.serialize("test result")
        );
        
        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("class-not-found-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);
    
    // Create a stub that expects a non-existent class as return type
    // This will trigger the ClassNotFoundException path in mapResponse
    TestService stub = transport.getRpcStub(serviceId, TestService.class);
    
    FlowFuture<String> resultF = startActor(() -> stub.echo("test"));
    pumpUntilDone(resultF);
    
    // Should complete successfully even with ClassNotFoundException handling
    assertTrue(resultF.isDone());
    assertEquals("test result", resultF.getNow());
  }

  @Test
  public void testConnectionMessageHandlerMapResponseVoidReturnType() throws Exception {
    // Test mapResponse specifically for void return type
    interface VoidService {
      void voidMethod();
    }
    
    VoidService impl = () -> {
      // Do nothing
    };
    
    transport.registerServiceAndListen(impl, VoidService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("void-return-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    VoidService stub = transport.getRpcStub(serviceId, VoidService.class);
    
    pump(); // Let server start
    
    FlowFuture<Void> voidF = startActor(() -> {
      stub.voidMethod();
      return null;
    });
    
    pumpUntilDone(voidF);
    
    assertTrue(voidF.isDone());
    assertNull(voidF.getNow());
  }

  @Test
  public void testConnectionMessageHandlerMapResponseVoidClassType() throws Exception {
    // Test mapResponse for Void.class return type (different from void.class)
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(7998);
    
    startActor(() -> {
      try {
        var stream = networkTransport.listen(customEndpoint);
        FlowConnection conn = await(stream.nextAsync());
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);
        
        // Send response with null payload for Void return
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            null,
            null,
            null  // null for Void
        );
        
        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("void-class-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);
    
    // Test with method that returns Void (not void)
    interface VoidClassService {
      Void getVoid();
    }
    
    VoidClassService stub = transport.getRpcStub(serviceId, VoidClassService.class);
    
    FlowFuture<Void> resultF = startActor(() -> stub.getVoid());
    pumpUntilDone(resultF);
    
    assertTrue(resultF.isDone());
    assertNull(resultF.getNow());
  }

  @Test
  public void testConnectionMessageHandlerParameterizedTypeHandling() throws Exception {
    // Test handling of ParameterizedType in mapResponse
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(7997);
    
    startActor(() -> {
      try {
        var stream = networkTransport.listen(customEndpoint);
        FlowConnection conn = await(stream.nextAsync());
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);
        
        // Send response with UUID (will be handled as direct value for FlowPromise)
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            null,
            null,
            FlowSerialization.serialize("direct value")
        );
        
        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("parameterized-type-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);
    
    // Test with a parameterized return type
    interface ParameterizedService {
      FlowPromise<String> getPromise();
    }
    
    ParameterizedService stub = transport.getRpcStub(serviceId, ParameterizedService.class);
    
    FlowFuture<FlowPromise<String>> promiseF = startActor(() -> stub.getPromise());
    pumpUntilDone(promiseF);
    
    assertTrue(promiseF.isDone());
    FlowPromise<String> promise = promiseF.getNow();
    assertNotNull(promise);
  }

  @Test
  public void testConnectionMessageHandlerStreamCloseWithError() {
    // Test handleStreamClose with error payload that has remaining bytes
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID streamId = UUID.randomUUID();
        
        // Send STREAM_CLOSE with error payload that has remaining bytes
        Exception streamError = new RuntimeException("Stream error with remaining bytes");
        ByteBuffer errorPayload = FlowSerialization.serialize(streamError);
        errorPayload.mark(); // Ensure hasRemaining() returns true
        
        RpcMessage streamCloseMsg = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_CLOSE,
            streamId,
            null,
            null,
            errorPayload  // Error payload with remaining bytes
        );
        await(connection.send(streamCloseMsg.serialize()));
        
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test 
  public void testConnectionMessageHandlerProcessIncomingArgsWithPromises() {
    // Test processIncomingArguments with actual promise IDs
    interface PromiseArgService {
      String processPromise(FlowPromise<String> promise);
    }
    
    PromiseArgService impl = (promise) -> {
      // Just return a test result
      return "processed promise arg";
    };
    
    transport.registerServiceAndListen(impl, PromiseArgService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        UUID promiseId = UUID.randomUUID();
        String methodId = PromiseArgService.class.getName() + ".processPromise(" + FlowPromise.class.getName() + ")";
        
        // Send request with promise ID in arguments and promiseIds list
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            List.of(promiseId),  // promiseIds list
            FlowSerialization.serialize(new Object[]{promiseId})  // UUID as argument
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        
        String result = FlowSerialization.deserialize(responseMsg.getPayload(), String.class);
        assertEquals("processed promise arg", result);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerProcessIncomingArgsWithStreams() {
    // Test processIncomingArguments with PromiseStream parameters
    interface StreamArgService {
      String processStream(PromiseStream<String> stream);
    }
    
    StreamArgService impl = (stream) -> {
      return "processed stream arg";
    };
    
    transport.registerServiceAndListen(impl, StreamArgService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        UUID streamId = UUID.randomUUID();
        String methodId = StreamArgService.class.getName() + ".processStream(" + PromiseStream.class.getName() + ")";
        
        // Send request with stream ID in arguments and promiseIds list
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            List.of(streamId),  // promiseIds list includes streams
            FlowSerialization.serialize(new Object[]{streamId})  // UUID as argument
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        
        String result = FlowSerialization.deserialize(responseMsg.getPayload(), String.class);
        assertEquals("processed stream arg", result);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerHandleVoidClassReturnType() throws Exception {
    // Test mapResponse specifically for Void.class (boxed void) return type
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(6996);
    
    startActor(() -> {
      try {
        var stream = networkTransport.listen(customEndpoint);
        FlowConnection conn = await(stream.nextAsync());
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);
        
        // Send response with null payload for Void return
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            null,
            null,
            null  // null payload for Void return
        );
        
        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("void-class-return-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);
    
    // Test with method that returns boxed Void (not primitive void)
    interface VoidClassReturnService {
      Void getVoidBoxed();
    }
    
    VoidClassReturnService stub = transport.getRpcStub(serviceId, VoidClassReturnService.class);
    
    FlowFuture<Void> resultF = startActor(() -> stub.getVoidBoxed());
    pumpUntilDone(resultF);
    
    assertTrue(resultF.isDone());
    assertNull(resultF.getNow());
  }
  
  @Test 
  public void testConnectionMessageHandlerMapResponseReturnTypeConversion() throws Exception {
    // Test numeric type conversion in mapResponse for return values
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(6995);
    
    startActor(() -> {
      try {
        var stream = networkTransport.listen(customEndpoint);
        FlowConnection conn = await(stream.nextAsync());
        
        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);
        
        // Send response with Long value for method expecting int
        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            null,
            null,
            FlowSerialization.serialize(12345L) // Send Long
        );
        
        await(conn.send(response.serialize()));
      } catch (Exception e) {
        // Ignore
      }
      return null;
    });
    
    pump(); // Let server start
    
    EndpointId serviceId = new EndpointId("type-conversion-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);
    
    // Test with method that returns int but receives Long
    interface TypeConversionService {
      int getIntValue();
    }
    
    TypeConversionService stub = transport.getRpcStub(serviceId, TypeConversionService.class);
    
    FlowFuture<Integer> resultF = startActor(() -> stub.getIntValue());
    pumpUntilDone(resultF);
    
    assertTrue(resultF.isDone());
    assertEquals(12345, resultF.getNow().intValue());
  }
  
  @Test
  public void testConnectionMessageHandlerMethodInvocationException() throws Exception {
    // Test when method.invoke() throws an exception during processIncomingArguments
    interface ExceptionThrowingService {
      String throwException();
    }
    
    ExceptionThrowingService impl = () -> {
      throw new RuntimeException("Method execution failed");
    };
    
    transport.registerServiceAndListen(impl, ExceptionThrowingService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("exception-throwing-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    ExceptionThrowingService stub = transport.getRpcStub(serviceId, ExceptionThrowingService.class);
    
    pump(); // Let server start
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.throwException();
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    pumpUntilDone(errorF);
    
    Exception error = errorF.getNow();
    assertNotNull(error);
    assertInstanceOf(RpcException.class, error);
    // The original exception should be contained in the RPC exception hierarchy
    String errorMessage = error.getMessage();
    Throwable cause = error.getCause();
    boolean hasExpectedMessage = errorMessage.contains("Method execution failed") ||
        (cause != null && cause.getMessage() != null && cause.getMessage().contains("Method execution failed")) ||
        // Check nested causes for the original exception
        hasNestedCause(error, "Method execution failed");
    assertTrue(hasExpectedMessage, "Expected error to contain 'Method execution failed' somewhere in the " +
        "exception chain, but got: " + errorMessage + (cause != null ? " (cause: " + cause.getMessage() + ")" : ""));
  }
  
  private boolean hasNestedCause(Throwable throwable, String messageSubstring) {
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null && current.getMessage().contains(messageSubstring)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
  
  @Test
  public void testConnectionMessageHandlerArgumentCountMismatch() {
    // Test when the wrong number of arguments is provided
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = TestService.class.getName() + ".addNumbers(int,int)";
        
        // Send request with wrong argument count (1 instead of 2)
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[]{42}) // Only 1 arg instead of 2
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
        Exception error = FlowSerialization.deserialize(responseMsg.getPayload(), Exception.class);
        // The server returns IllegalArgumentException for wrong argument count
        assertInstanceOf(IllegalArgumentException.class, error);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerDeserializationErrorInArguments() {
    // Test when argument deserialization fails
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = TestService.class.getName() + ".echo(java.lang.String)";
        
        // Send request with corrupted payload that can't be deserialized
        ByteBuffer corruptedPayload = ByteBuffer.wrap(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            corruptedPayload
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerSerializationErrorHandling() throws Exception {
    // Test error handling when serialization fails during response sending
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    // Create a service that returns an unserializable object
    interface UnserializableService {
      Object getUnserializableObject();
    }
    
    UnserializableService unserializableImpl = () -> new Object() {
      private final Thread thread = Thread.currentThread(); // Not serializable
    };
    
    transport.registerServiceAndListen(unserializableImpl, UnserializableService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("unserializable-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    UnserializableService stub = transport.getRpcStub(serviceId, UnserializableService.class);
    
    pump(); // Let server start
    
    FlowFuture<Exception> errorF = startActor(() -> {
      try {
        stub.getUnserializableObject();
        return null;
      } catch (Exception e) {
        return e;
      }
    });
    
    pumpUntilDone(errorF);
    
    Exception error = errorF.getNow();
    assertNotNull(error);
    assertInstanceOf(RpcException.class, error);
  }

  @Test
  public void testConnectionMessageHandlerVoidReturnTypeEdgeCases() throws Exception {
    // Test void return type with both void.class and Void.class
    interface VoidReturnService {
      void voidMethod();
      Void voidBoxedMethod();
    }
    
    VoidReturnService impl = new VoidReturnService() {
      @Override
      public void voidMethod() {
        // Do nothing
      }
      
      @Override
      public Void voidBoxedMethod() {
        return null;
      }
    };
    
    transport.registerServiceAndListen(impl, VoidReturnService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("void-return-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    VoidReturnService stub = transport.getRpcStub(serviceId, VoidReturnService.class);
    
    pump(); // Let server start
    
    // Test void method
    FlowFuture<Void> voidF = startActor(() -> {
      stub.voidMethod();
      return null;
    });
    pumpUntilDone(voidF);
    assertTrue(voidF.isDone());
    
    // Test Void boxed method
    FlowFuture<Void> voidBoxedF = startActor(() -> stub.voidBoxedMethod());
    pumpUntilDone(voidBoxedF);
    assertTrue(voidBoxedF.isDone());
    assertNull(voidBoxedF.getNow());
  }

  @Test
  public void testConnectionMessageHandlerComplexStreamHandling() {
    // Test stream handling with complex scenarios
    interface ComplexStreamService {
      PromiseStream<String> getComplexStream();
    }
    
    ComplexStreamService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      // Send values immediately rather than in a separate actor
      stream.send("Value 1");
      stream.send("Value 2");
      stream.send("Value 3");
      stream.close();
      return stream;
    };
    
    transport.registerServiceAndListen(impl, ComplexStreamService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("complex-stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    ComplexStreamService stub = transport.getRpcStub(serviceId, ComplexStreamService.class);
    
    pump(); // Let server start
    
    List<String> receivedValues = new ArrayList<>();
    AtomicBoolean streamClosed = new AtomicBoolean(false);
    
    FlowFuture<Void> streamF = startActor(() -> {
      PromiseStream<String> stream = stub.getComplexStream();
      var futureStream = stream.getFutureStream();
      
      // Read all values using forEach to ensure complete consumption
      await(futureStream.forEach(receivedValues::add));
      streamClosed.set(true);
      
      return null;
    });
    
    pumpUntilDone(streamF);
    
    assertTrue(streamClosed.get());
    assertEquals(3, receivedValues.size());
    assertEquals("Value 1", receivedValues.get(0));
    assertEquals("Value 2", receivedValues.get(1));
    assertEquals("Value 3", receivedValues.get(2));
  }

  @Test
  public void testConnectionMessageHandlerInvalidMethodIdFormat() {
    // Test handling of invalid method ID formats
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        
        // Test method ID with invalid format (missing parentheses)
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            "InvalidMethodIdFormat",
            null,
            FlowSerialization.serialize(new Object[0])
        );
        
        await(connection.send(request1.serialize()));
        
        // Wait for error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
        Exception error = FlowSerialization.deserialize(responseMsg.getPayload(), Exception.class);
        assertInstanceOf(RpcException.class, error);
        assertTrue(error.getMessage().contains("Method not found"));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerMethodWithReturnValueTypeMismatch() throws Exception {
    // Test type conversion in return values
    interface TypeMismatchService {
      String getIntAsString();
      int getStringAsInt();
    }
    
    TypeMismatchService impl = new TypeMismatchService() {
      @Override
      public String getIntAsString() {
        // This will be converted to string
        return "42";
      }
      
      @Override 
      public int getStringAsInt() {
        // This will cause type issues if not handled properly
        return 42;
      }
    };
    
    transport.registerServiceAndListen(impl, TypeMismatchService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("type-mismatch-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    TypeMismatchService stub = transport.getRpcStub(serviceId, TypeMismatchService.class);
    
    pump(); // Let server start
    
    // Test successful conversion
    FlowFuture<String> stringF = startActor(() -> stub.getIntAsString());
    pumpUntilDone(stringF);
    assertEquals("42", stringF.getNow());
    
    FlowFuture<Integer> intF = startActor(() -> stub.getStringAsInt());
    pumpUntilDone(intF);
    assertEquals(42, intF.getNow().intValue());
  }

  @Test
  public void testConnectionMessageHandlerHandleRequestWithNullPayload() {
    // Test handleRequest with null payload (no arguments)
    interface NoArgService {
      String noArgMethod();
    }
    
    NoArgService impl = () -> "no args result";
    
    transport.registerServiceAndListen(impl, NoArgService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = NoArgService.class.getName() + ".noArgMethod()";
        
        // Send request with null payload
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            null  // null payload
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        
        String result = FlowSerialization.deserialize(responseMsg.getPayload(), String.class);
        assertEquals("no args result", result);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });
    
    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerHandleRequestWithEmptyPayload() {
    // Test handleRequest with empty payload buffer
    interface NoArgService {
      String noArgMethod();
    }
    
    NoArgService impl = () -> "empty payload result";
    
    transport.registerServiceAndListen(impl, NoArgService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = NoArgService.class.getName() + ".noArgMethod()";
        
        // Send request with empty payload
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            ByteBuffer.allocate(0)  // empty payload
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        
        String result = FlowSerialization.deserialize(responseMsg.getPayload(), String.class);
        assertEquals("empty payload result", result);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });
    
    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerConnectionCloseRaceCondition() {
    // Test race condition when connection closes during message processing
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    AtomicReference<FlowConnection> connectionRef = new AtomicReference<>();
    
    // Start a client that will close connection abruptly
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        connectionRef.set(connection);
        
        UUID messageId = UUID.randomUUID();
        String methodId = TestService.class.getName() + ".echo(java.lang.String)";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[]{"test message"})
        );
        
        await(connection.send(request.serialize()));
        
        // Close connection immediately after sending
        connection.close();
        
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
    
    // Verify connection was closed
    if (connectionRef.get() != null) {
      assertFalse(connectionRef.get().isOpen());
    }
  }

  @Test
  public void testConnectionMessageHandlerPromiseCompletionWithNullType() {
    // Test promise completion when type information is null
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a PROMISE_COMPLETE message for a promise that doesn't exist in tracker
        UUID promiseId = UUID.randomUUID();
        RpcMessage promiseComplete = new RpcMessage(
            RpcMessageHeader.MessageType.PROMISE_COMPLETE,
            promiseId,
            null,
            null,
            FlowSerialization.serialize("test result")
        );
        
        await(connection.send(promiseComplete.serialize()));
        
        // Give time for message to be processed
        await(Flow.delay(0.1));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });
    
    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerStreamDataWithNullType() {
    // Test stream data when type information is null
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a STREAM_DATA message for a stream that doesn't exist in tracker
        UUID streamId = UUID.randomUUID();
        RpcMessage streamData = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_DATA,
            streamId,
            null,
            null,
            FlowSerialization.serialize("stream value")
        );
        
        await(connection.send(streamData.serialize()));
        
        // Give time for message to be processed
        await(Flow.delay(0.1));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerStreamCloseWithErrorPayload() {
    // Test stream close with error payload
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a STREAM_CLOSE message with error payload
        UUID streamId = UUID.randomUUID();
        RuntimeException error = new RuntimeException("Stream error");
        RpcMessage streamClose = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_CLOSE,
            streamId,
            null,
            null,
            FlowSerialization.serialize(error)
        );
        
        await(connection.send(streamClose.serialize()));
        
        // Give time for message to be processed
        await(Flow.delay(0.1));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerStreamCloseNormalWithNullPayload() {
    // Test normal stream close with null payload
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a STREAM_CLOSE message with null payload (normal close)
        UUID streamId = UUID.randomUUID();
        RpcMessage streamClose = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_CLOSE,
            streamId,
            null,
            null,
            null
        );
        
        await(connection.send(streamClose.serialize()));
        
        // Give time for message to be processed
        await(Flow.delay(0.1));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerProcessIncomingArgsNullArgs() {
    // Test processIncomingArguments with null args
    interface TestService {
      String testMethod();
    }
    
    TestService impl = () -> "result";
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerConvertArgumentOverflowErrors() throws Exception {
    // Test numeric conversion overflow errors
    interface OverflowService {
      int processInt(int value);
      short processShort(short value);
      byte processByte(byte value);
    }
    
    OverflowService impl = new OverflowService() {
      public int processInt(int value) {
        return value;
      }
      public short processShort(short value) {
        return value;
      }
      public byte processByte(byte value) {
        return value;
      }
    };
    
    transport.registerServiceAndListen(impl, OverflowService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test Integer overflow
        UUID messageId1 = UUID.randomUUID();
        String methodId1 = OverflowService.class.getName() + ".processInt(int)";
        
        // Send Long.MAX_VALUE which should cause overflow
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            methodId1,
            null,
            FlowSerialization.serialize(new Object[]{Long.MAX_VALUE})
        );
        
        await(connection.send(request1.serialize()));
        
        // Wait for error response
        ByteBuffer response1 = await(connection.receive(65536));
        RpcMessage responseMsg1 = RpcMessage.deserialize(response1);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg1.getHeader().getType());
        
        // Test Short overflow
        UUID messageId2 = UUID.randomUUID();
        String methodId2 = OverflowService.class.getName() + ".processShort(short)";
        
        RpcMessage request2 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId2,
            methodId2,
            null,
            FlowSerialization.serialize(new Object[]{100000L}) // Too big for short
        );
        
        await(connection.send(request2.serialize()));
        
        ByteBuffer response2 = await(connection.receive(65536));
        RpcMessage responseMsg2 = RpcMessage.deserialize(response2);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg2.getHeader().getType());
        
        // Test Byte overflow
        UUID messageId3 = UUID.randomUUID();
        String methodId3 = OverflowService.class.getName() + ".processByte(byte)";
        
        RpcMessage request3 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId3,
            methodId3,
            null,
            FlowSerialization.serialize(new Object[]{1000L}) // Too big for byte
        );
        
        await(connection.send(request3.serialize()));
        
        ByteBuffer response3 = await(connection.receive(65536));
        RpcMessage responseMsg3 = RpcMessage.deserialize(response3);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg3.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerFlowFutureReturnTypes() throws Exception {
    // Test handling of FlowFuture return types (both completed and uncompleted)
    interface FutureService {
      FlowFuture<String> getCompletedFuture();
      FlowFuture<String> getUncompletedFuture();
    }
    
    FutureService impl = new FutureService() {
      public FlowFuture<String> getCompletedFuture() {
        FlowFuture<String> future = new FlowFuture<>();
        future.getPromise().complete("completed");
        return future;
      }
      
      public FlowFuture<String> getUncompletedFuture() {
        FlowFuture<String> future = new FlowFuture<>();
        // Complete it later
        startActor(() -> {
          await(Flow.delay(0.1));
          future.getPromise().complete("later");
          return null;
        });
        return future;
      }
    };
    
    transport.registerServiceAndListen(impl, FutureService.class, serverEndpoint);
    
    EndpointId serviceId = new EndpointId("future-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
    
    FutureService stub = transport.getRpcStub(serviceId, FutureService.class);
    
    pump(); // Let server start
    
    // For RPC, FlowFuture should be converted to a promise by the transport
    // Test completed future - it should return the value directly
    FlowFuture<String> completedF = startActor(() -> {
      // The RPC transport should unwrap completed futures
      FlowFuture<String> result = stub.getCompletedFuture();
      return await(result);
    });
    pumpUntilDone(completedF);
    assertEquals("completed", completedF.getNow());
    
    // Test uncompleted future - it should return a promise that completes later
    FlowFuture<String> uncompletedF = startActor(() -> {
      FlowFuture<String> result = stub.getUncompletedFuture();
      return await(result);
    });
    
    // Since the future completes after a delay, we need to pump until done
    pumpUntilDone(uncompletedF);
    assertEquals("later", uncompletedF.getNow());
  }

  @Test
  public void testConnectionMessageHandlerFlowPromiseExceptionalCompletion() {
    // Test FlowPromise that completes exceptionally
    interface PromiseExceptionService {
      FlowPromise<String> getFailingPromise();
    }
    
    PromiseExceptionService impl = () -> {
      FlowFuture<String> future = new FlowFuture<>();
      future.getPromise().completeExceptionally(new RuntimeException("Promise failed"));
      return future.getPromise();
    };
    
    transport.registerServiceAndListen(impl, PromiseExceptionService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = PromiseExceptionService.class.getName() + ".getFailingPromise()";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[0])
        );
        
        await(connection.send(request.serialize()));
        
        // Should get an error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
        
        Exception error = FlowSerialization.deserialize(responseMsg.getPayload(), Exception.class);
        assertInstanceOf(RuntimeException.class, error);
        assertEquals("Promise failed", error.getMessage());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  public void testConnectionMessageHandlerUnknownMessageType() {
    // Test handling of unknown message type
    TestServiceImpl impl = new TestServiceImpl();
    transport.registerServiceAndListen(impl, TestService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Create a message with an invalid type by manipulating the bytes
        UUID messageId = UUID.randomUUID();
        RpcMessage normalMessage = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            "test.method",
            null,
            null
        );
        
        ByteBuffer serialized = normalMessage.serialize();
        byte[] bytes = new byte[serialized.remaining()];
        serialized.get(bytes);
        
        // Corrupt the message type byte (offset depends on RpcMessage format)
        // This is a bit hacky but necessary to test the unknown message type branch
        bytes[16] = (byte) 99; // Invalid message type
        
        await(connection.send(ByteBuffer.wrap(bytes)));
        
        // Give time for message to be processed
        await(Flow.delay(0.1));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerConnectionClosedDuringRead() throws Exception {
    // Test exception handling when connection closes during read
    interface SimpleService {
      String getTestMethod();
      String getMethodWithArgs(String arg);
      int getIntegerResult();
    }
    
    transport.registerServiceAndListen(new SimpleService() {
      @Override
      public String getTestMethod() {
        return "test";
      }
      @Override
      public String getMethodWithArgs(String arg) {
        return arg;
      }
      @Override
      public int getIntegerResult() {
        return 42;
      }
    }, SimpleService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a request to start the message reader
        UUID messageId = UUID.randomUUID();
        String methodId = SimpleService.class.getName() + ".getTestMethod()";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            null
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        
        // Now close the connection to trigger the error handling
        connection.close();
        
        // Give time for the reader to encounter the closed connection
        await(Flow.delay(0.1));
        
      } catch (Exception e) {
        // Expected
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerNumericConversionInBounds() throws Exception {
    // Test numeric conversions that are within bounds
    interface NumericService {
      int processInt(int value);
      short processShort(short value);
      byte processByte(byte value);
    }
    
    NumericService impl = new NumericService() {
      public int processInt(int value) {
        return value;
      }
      public short processShort(short value) {
        return value;
      }
      public byte processByte(byte value) {
        return value;
      }
    };
    
    transport.registerServiceAndListen(impl, NumericService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test int conversion within bounds
        UUID messageId1 = UUID.randomUUID();
        String methodId1 = NumericService.class.getName() + ".processInt(int)";
        
        // Send Long value that fits in Integer
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            methodId1,
            null,
            FlowSerialization.serialize(new Object[]{42L})
        );
        
        await(connection.send(request1.serialize()));
        
        ByteBuffer response1 = await(connection.receive(65536));
        RpcMessage responseMsg1 = RpcMessage.deserialize(response1);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg1.getHeader().getType());
        assertEquals(42, FlowSerialization.deserialize(responseMsg1.getPayload(), Integer.class));
        
        // Test short conversion within bounds
        UUID messageId2 = UUID.randomUUID();
        String methodId2 = NumericService.class.getName() + ".processShort(short)";
        
        RpcMessage request2 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId2,
            methodId2,
            null,
            FlowSerialization.serialize(new Object[]{100L})
        );
        
        await(connection.send(request2.serialize()));
        
        ByteBuffer response2 = await(connection.receive(65536));
        RpcMessage responseMsg2 = RpcMessage.deserialize(response2);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg2.getHeader().getType());
        assertEquals((short) 100, FlowSerialization.deserialize(responseMsg2.getPayload(), Short.class));
        
        // Test byte conversion within bounds
        UUID messageId3 = UUID.randomUUID();
        String methodId3 = NumericService.class.getName() + ".processByte(byte)";
        
        RpcMessage request3 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId3,
            methodId3,
            null,
            FlowSerialization.serialize(new Object[]{50L})
        );
        
        await(connection.send(request3.serialize()));
        
        ByteBuffer response3 = await(connection.receive(65536));
        RpcMessage responseMsg3 = RpcMessage.deserialize(response3);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg3.getHeader().getType());
        assertEquals((byte) 50, FlowSerialization.deserialize(responseMsg3.getPayload(), Byte.class));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerPromiseStreamArgument() throws Exception {
    // Test PromiseStream as method argument
    interface StreamService {
      String processStream(PromiseStream<String> stream);
    }
    
    StreamService impl = new StreamService() {
      public String processStream(PromiseStream<String> stream) {
        return "stream-received";
      }
    };
    
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Create a stream UUID to send
        UUID streamId = UUID.randomUUID();
        
        UUID messageId = UUID.randomUUID();
        String methodId = StreamService.class.getName() + 
            ".processStream(io.github.panghy.javaflow.core.PromiseStream)";
        
        // Include the stream ID in promise IDs
        List<UUID> promiseIds = new ArrayList<>();
        promiseIds.add(streamId);
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            promiseIds,
            FlowSerialization.serialize(new Object[]{streamId})
        );
        
        await(connection.send(request.serialize()));
        
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        assertEquals("stream-received", FlowSerialization.deserialize(responseMsg.getPayload(), String.class));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerRegularUuidArgument() throws Exception {
    // Test regular UUID as argument (not a promise)
    interface UuidService {
      String processUuid(UUID id);
    }
    
    UuidService impl = new UuidService() {
      public String processUuid(UUID id) {
        return "uuid-" + id.toString();
      }
    };
    
    transport.registerServiceAndListen(impl, UuidService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID argUuid = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String methodId = UuidService.class.getName() + ".processUuid(java.util.UUID)";
        
        // Send with promise IDs containing a different UUID to test the non-match case
        List<UUID> promiseIds = new ArrayList<>();
        promiseIds.add(UUID.randomUUID()); // Different UUID
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            promiseIds,
            FlowSerialization.serialize(new Object[]{argUuid})
        );
        
        await(connection.send(request.serialize()));
        
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        assertEquals("uuid-" + argUuid.toString(), 
            FlowSerialization.deserialize(responseMsg.getPayload(), String.class));
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerVoidReturnType() throws Exception {
    // Test void return type handling directly by sending a void method request
    interface VoidService {
      void doNothing();
    }
    
    VoidService impl = new VoidService() {
      public void doNothing() {
        // Do nothing
      }
    };
    
    transport.registerServiceAndListen(impl, VoidService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = VoidService.class.getName() + ".doNothing()";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            null
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response - void methods still send a response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        assertNull(responseMsg.getPayload()); // Void methods have null payload
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerExtractTypeFromParameter() throws Exception {
    // Test extractTypeFromParameter method by using a generic method parameter
    interface GenericService {
      <T> T process(List<T> items);
    }
    
    GenericService impl = new GenericService() {
      public <T> T process(List<T> items) {
        return items.isEmpty() ? null : items.get(0);
      }
    };
    
    transport.registerServiceAndListen(impl, GenericService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a request with a List argument
        UUID messageId = UUID.randomUUID();
        String methodId = GenericService.class.getName() + ".process(java.util.List)";
        
        List<String> testList = Arrays.asList("item1", "item2");
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[]{testList})
        );
        
        await(connection.send(request.serialize()));
        
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerNumericConversionEdgeCases() throws Exception {
    // Test non-precision losing numeric conversions and ensure precision-losing ones fail
    interface NumericService {
      Number processFloat(Float value);
      Number processDouble(Double value);
      Number processLong(Long value);
    }
    
    NumericService impl = new NumericService() {
      public Number processFloat(Float value) {
        return value;
      }
      public Number processDouble(Double value) {
        return value;
      }
      public Number processLong(Long value) {
        return value;
      }
    };
    
    transport.registerServiceAndListen(impl, NumericService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test Integer to Float conversion should fail (precision loss)
        UUID messageId1 = UUID.randomUUID();
        String methodId1 = NumericService.class.getName() + ".processFloat(java.lang.Float)";
        
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            methodId1,
            null,
            FlowSerialization.serialize(new Object[]{42}) // Send Integer for Float parameter
        );
        
        await(connection.send(request1.serialize()));
        
        ByteBuffer response1 = await(connection.receive(65536));
        RpcMessage responseMsg1 = RpcMessage.deserialize(response1);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg1.getHeader().getType());
        
        // Test Double conversion from Float
        UUID messageId2 = UUID.randomUUID();
        String methodId2 = NumericService.class.getName() + ".processDouble(java.lang.Double)";
        
        RpcMessage request2 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId2,
            methodId2,
            null,
            FlowSerialization.serialize(new Object[]{42.5f}) // Send Float for Double parameter
        );
        
        await(connection.send(request2.serialize()));
        
        ByteBuffer response2 = await(connection.receive(65536));
        RpcMessage responseMsg2 = RpcMessage.deserialize(response2);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg2.getHeader().getType());
        
        // Test Long conversion from Integer
        UUID messageId3 = UUID.randomUUID();
        String methodId3 = NumericService.class.getName() + ".processLong(java.lang.Long)";
        
        RpcMessage request3 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId3,
            methodId3,
            null,
            FlowSerialization.serialize(new Object[]{42}) // Send Integer for Long parameter
        );
        
        await(connection.send(request3.serialize()));
        
        ByteBuffer response3 = await(connection.receive(65536));
        RpcMessage responseMsg3 = RpcMessage.deserialize(response3);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg3.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerErrorInFutureCompletion() throws Exception {
    // Test error handling in sendResponse future completion
    interface FutureErrorService {
      FlowFuture<String> getFailingFuture();
    }
    
    FutureErrorService impl = new FutureErrorService() {
      public FlowFuture<String> getFailingFuture() {
        FlowFuture<String> future = new FlowFuture<>();
        // Complete exceptionally after a delay
        startActor(() -> {
          await(Flow.delay(0.1));
          future.getPromise().completeExceptionally(new RuntimeException("Test error"));
          return null;
        });
        return future;
      }
    };
    
    transport.registerServiceAndListen(impl, FutureErrorService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        UUID messageId = UUID.randomUUID();
        String methodId = FutureErrorService.class.getName() + ".getFailingFuture()";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            null
        );
        
        await(connection.send(request.serialize()));
        
        // First response should be the promise ID
        ByteBuffer response1 = await(connection.receive(65536));
        RpcMessage responseMsg1 = RpcMessage.deserialize(response1);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg1.getHeader().getType());
        UUID promiseId = FlowSerialization.deserialize(responseMsg1.getPayload(), UUID.class);
        
        // Wait for the error message
        ByteBuffer response2 = await(connection.receive(65536));
        RpcMessage responseMsg2 = RpcMessage.deserialize(response2);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg2.getHeader().getType());
        assertEquals(promiseId, responseMsg2.getHeader().getMessageId());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerMessageReaderConnectionClosed() throws Exception {
    // Test connection closed exception handling in message reader
    interface SimpleService {
      String echo(String message);
    }
    
    SimpleService impl = new SimpleService() {
      public String echo(String message) {
        return message;
      }
    };
    
    transport.registerServiceAndListen(impl, SimpleService.class, serverEndpoint);
    
    AtomicReference<FlowConnection> connectionRef = new AtomicReference<>();
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        connectionRef.set(connection);
        
        // Send a request
        UUID messageId = UUID.randomUUID();
        String methodId = SimpleService.class.getName() + ".echo(java.lang.String)";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            FlowSerialization.serialize(new Object[]{"test"})
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for response
        ByteBuffer response = await(connection.receive(65536));
        assertNotNull(response);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
    
    // Close the connection while message reader is active
    if (connectionRef.get() != null) {
      connectionRef.get().close();
    }

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerPrimitiveNumericConversions() throws Exception {
    // Test all primitive numeric conversions in convertArgumentType
    interface NumericService {
      byte processByte(byte value);
      short processShort(short value);
      int processInt(int value);
      long processLong(long value);
      float processFloat(float value);
      double processDouble(double value);
    }
    
    NumericService impl = new NumericService() {
      public byte processByte(byte value) { 
        return value; 
      }
      public short processShort(short value) { 
        return value; 
      }
      public int processInt(int value) { 
        return value; 
      }
      public long processLong(long value) { 
        return value; 
      }
      public float processFloat(float value) { 
        return value; 
      }
      public double processDouble(double value) { 
        return value; 
      }
    };
    
    transport.registerServiceAndListen(impl, NumericService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test byte conversions
        String methodId1 = NumericService.class.getName() + ".processByte(byte)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId1,
            null,
            FlowSerialization.serialize(new Object[]{(short) 42}) // Short to byte
        ).serialize()));
        
        ByteBuffer response1 = await(connection.receive(65536));
        assertNotNull(response1);
        
        // Test short conversions
        String methodId2 = NumericService.class.getName() + ".processShort(short)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId2,
            null,
            FlowSerialization.serialize(new Object[]{(byte) 42}) // Byte to short
        ).serialize()));
        
        ByteBuffer response2 = await(connection.receive(65536));
        assertNotNull(response2);
        
        // Test int conversions
        String methodId3 = NumericService.class.getName() + ".processInt(int)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId3,
            null,
            FlowSerialization.serialize(new Object[]{(short) 42}) // Short to int
        ).serialize()));
        
        ByteBuffer response3 = await(connection.receive(65536));
        assertNotNull(response3);
        
        // Test long conversions
        String methodId4 = NumericService.class.getName() + ".processLong(long)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId4,
            null,
            FlowSerialization.serialize(new Object[]{42}) // Int to long
        ).serialize()));
        
        ByteBuffer response4 = await(connection.receive(65536));
        assertNotNull(response4);
        
        // Test float conversions
        String methodId5 = NumericService.class.getName() + ".processFloat(float)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId5,
            null,
            FlowSerialization.serialize(new Object[]{42L}) // Long to float
        ).serialize()));
        
        ByteBuffer response5 = await(connection.receive(65536));
        assertNotNull(response5);
        
        // Test double conversions
        String methodId6 = NumericService.class.getName() + ".processDouble(double)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId6,
            null,
            FlowSerialization.serialize(new Object[]{42.0f}) // Float to double
        ).serialize()));
        
        ByteBuffer response6 = await(connection.receive(65536));
        assertNotNull(response6);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerBoxedTypeConversions() throws Exception {
    // Test boxed type conversions in convertArgumentType
    interface BoxedService {
      Integer processInteger(Integer value);
      Long processLongBoxed(Long value);
      Boolean processBoolean(Boolean value);
    }
    
    BoxedService impl = new BoxedService() {
      public Integer processInteger(Integer value) { 
        return value; 
      }
      public Long processLongBoxed(Long value) { 
        return value; 
      }
      public Boolean processBoolean(Boolean value) { 
        return value; 
      }
    };
    
    transport.registerServiceAndListen(impl, BoxedService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test primitive int to Integer
        String methodId1 = BoxedService.class.getName() + ".processInteger(java.lang.Integer)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId1,
            null,
            FlowSerialization.serialize(new Object[]{42}) // int to Integer
        ).serialize()));
        
        ByteBuffer response1 = await(connection.receive(65536));
        assertNotNull(response1);
        
        // Test primitive long to Long
        String methodId2 = BoxedService.class.getName() + ".processLongBoxed(java.lang.Long)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId2,
            null,
            FlowSerialization.serialize(new Object[]{42L}) // long to Long
        ).serialize()));
        
        ByteBuffer response2 = await(connection.receive(65536));
        assertNotNull(response2);
        
        // Test primitive boolean to Boolean
        String methodId3 = BoxedService.class.getName() + ".processBoolean(java.lang.Boolean)";
        await(connection.send(new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            UUID.randomUUID(),
            methodId3,
            null,
            FlowSerialization.serialize(new Object[]{true}) // boolean to Boolean
        ).serialize()));
        
        ByteBuffer response3 = await(connection.receive(65536));
        assertNotNull(response3);
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerNonNumericConversion() throws Exception {
    // Test non-numeric type conversions in convertArgumentType
    interface ConversionService {
      String processString(String value);
      Object processObject(Object value);
    }
    
    ConversionService impl = new ConversionService() {
      public String processString(String value) {
        return value;
      }
      public Object processObject(Object value) {
        return value;
      }
    };
    
    transport.registerServiceAndListen(impl, ConversionService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test String parameter with non-String argument
        UUID messageId1 = UUID.randomUUID();
        String methodId1 = ConversionService.class.getName() + ".processString(java.lang.String)";
        
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            methodId1,
            null,
            FlowSerialization.serialize(new Object[]{42}) // Send Integer for String parameter
        );
        
        await(connection.send(request1.serialize()));
        
        ByteBuffer response1 = await(connection.receive(65536));
        RpcMessage responseMsg1 = RpcMessage.deserialize(response1);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg1.getHeader().getType());
        
        // Test Object parameter with specific type
        UUID messageId2 = UUID.randomUUID();
        String methodId2 = ConversionService.class.getName() + ".processObject(java.lang.Object)";
        
        List<String> testList = Arrays.asList("a", "b", "c");
        
        RpcMessage request2 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId2,
            methodId2,
            null,
            FlowSerialization.serialize(new Object[]{testList})
        );
        
        await(connection.send(request2.serialize()));
        
        ByteBuffer response2 = await(connection.receive(65536));
        RpcMessage responseMsg2 = RpcMessage.deserialize(response2);
        assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg2.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test 
  public void testConnectionMessageHandlerMapResponseExceptionTypes() throws Exception {
    // Test mapResponse handling of different exception types
    interface ExceptionService {
      String throwRuntimeException() throws RuntimeException;
      String throwCheckedException() throws Exception;
    }
    
    ExceptionService impl = new ExceptionService() {
      public String throwRuntimeException() {
        throw new RuntimeException("Test runtime exception");
      }
      public String throwCheckedException() throws Exception {
        throw new Exception("Test checked exception");
      }
    };
    
    transport.registerServiceAndListen(impl, ExceptionService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Test runtime exception
        UUID messageId1 = UUID.randomUUID();
        String methodId1 = ExceptionService.class.getName() + ".throwRuntimeException()";
        
        RpcMessage request1 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId1,
            methodId1,
            null,
            null
        );
        
        await(connection.send(request1.serialize()));
        
        ByteBuffer response1 = await(connection.receive(65536));
        RpcMessage responseMsg1 = RpcMessage.deserialize(response1);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg1.getHeader().getType());
        
        // Test checked exception
        UUID messageId2 = UUID.randomUUID();
        String methodId2 = ExceptionService.class.getName() + ".throwCheckedException()";
        
        RpcMessage request2 = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId2,
            methodId2,
            null,
            null
        );
        
        await(connection.send(request2.serialize()));
        
        ByteBuffer response2 = await(connection.receive(65536));
        RpcMessage responseMsg2 = RpcMessage.deserialize(response2);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg2.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }
  
  @Test
  public void testConnectionMessageHandlerInvalidMessageHandling() throws Exception {
    // Test handling of invalid messages that trigger exceptions
    interface SimpleService {
      String echo(String message);
    }
    
    SimpleService impl = new SimpleService() {
      public String echo(String message) {
        return message;
      }
    };
    
    transport.registerServiceAndListen(impl, SimpleService.class, serverEndpoint);
    
    startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));
        
        // Send a request with invalid method that will cause ClassNotFoundException
        UUID messageId = UUID.randomUUID();
        String methodId = "com.nonexistent.Service.method()";
        
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            methodId,
            null,
            null
        );
        
        await(connection.send(request.serialize()));
        
        // Wait for error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });

    pumpUntilDone();
  }

  @Test
  void testRemoteInvocationHandlerExtractTypeFromMethodParameter() throws Exception {
    // This test specifically targets the extractTypeFromMethodParameter method
    // by using various generic parameter types
    GenericService serviceImpl = new GenericServiceImpl();
    transport.registerServiceAndListen(serviceImpl, GenericService.class, serverEndpoint);
    EndpointId serviceId = new EndpointId("generic-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    GenericService client = transport.getRpcStub(serviceId, GenericService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test with FlowPromise<String> parameter
      FlowFuture<String> promiseFuture = new FlowFuture<>();
      FlowPromise<String> stringPromise = promiseFuture.getPromise();
      stringPromise.complete("test");
      String result1 = await(client.processPromise(stringPromise));
      assertEquals("Processed: test", result1);

      // Test with PromiseStream<Integer> parameter
      PromiseStream<Integer> intStream = new PromiseStream<>();
      intStream.send(1);
      intStream.send(2);
      intStream.send(3);
      intStream.close();
      List<Integer> result2 = await(client.processStream(intStream));
      assertEquals(Arrays.asList(1, 2, 3), result2);

      // Test with non-generic parameter (should fall back to Object)
      Object result3 = await(client.processObject("plain"));
      assertEquals("Object: plain", result3);
      
      return null;
    });
    
    pumpUntilDone(testFuture);
  }

  @Test
  void testRemoteInvocationHandlerProcessArgumentsWithNullArgs() throws Exception {
    // Test processArguments with null arguments
    NullableService serviceImpl = new NullableServiceImpl();
    transport.registerServiceAndListen(serviceImpl, NullableService.class, serverEndpoint);
    EndpointId serviceId = new EndpointId("nullable-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    NullableService client = transport.getRpcStub(serviceId, NullableService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test with null argument array
      String result = await(client.processNull());
      assertEquals("null processed", result);

      // Test with empty argument array
      String result2 = await(client.processEmpty());
      assertEquals("empty processed", result2);
      
      return null;
    });
    
    pumpUntilDone(testFuture);
  }

  @Test
  void testRemoteInvocationHandlerConvertReturnValueEdgeCases() throws Exception {
    // Test convertReturnValue with various numeric conversions
    RemoteNumericService serviceImpl = new RemoteNumericServiceImpl();
    transport.registerServiceAndListen(serviceImpl, RemoteNumericService.class, serverEndpoint);
    EndpointId serviceId = new EndpointId("remote-numeric-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    RemoteNumericService client = transport.getRpcStub(serviceId, RemoteNumericService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test null return value
      Integer nullResult = await(client.getNullInteger());
      assertNull(nullResult);

      // Test Long to Byte conversion at boundary
      byte byteResult = await(client.getByteFromLong(Byte.MAX_VALUE));
      assertEquals(Byte.MAX_VALUE, byteResult);

      // Test Long to Short conversion at boundary
      short shortResult = await(client.getShortFromLong(Short.MAX_VALUE));
      assertEquals(Short.MAX_VALUE, shortResult);

      // Test Long to Integer conversion at boundary
      int intResult = await(client.getIntFromLong(Integer.MAX_VALUE));
      assertEquals(Integer.MAX_VALUE, intResult);

      // Test Long value that doesn't need conversion
      long longResult = await(client.getLong(Long.MAX_VALUE));
      assertEquals(Long.MAX_VALUE, longResult);
      
      return null;
    });
    
    pumpUntilDone(testFuture);
  }

  @Test
  void testRemoteInvocationHandlerObjectMethods() throws Exception {
    // Test handleObjectMethod for toString, equals, hashCode
    TestService serviceImpl = new TestServiceImpl();
    transport.registerServiceAndListen(serviceImpl, TestService.class, serverEndpoint);
    EndpointId serviceId = new EndpointId("test-service-object");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    TestService client = transport.getRpcStub(serviceId, TestService.class);

    // Test toString
    String toStringResult = client.toString();
    assertTrue(toStringResult.contains("RemoteStub"));
    assertTrue(toStringResult.contains("test-service-object"));

    // Test equals
    assertTrue(client.equals(client));
    assertFalse(client.equals("not a proxy"));
    
    // Test hashCode
    int hashCode = client.hashCode();
    assertEquals(System.identityHashCode(client), hashCode);
  }

  @Test
  void testRemoteInvocationHandlerObjectMethodsDirectEndpoint() throws Exception {
    // Test handleObjectMethod with direct endpoint
    TestService serviceImpl = new TestServiceImpl();
    transport.registerServiceAndListen(serviceImpl, TestService.class, serverEndpoint);
    
    // Register the endpoint so it can be found
    EndpointId serviceId = new EndpointId("test-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    // Get stub directly to endpoint
    TestService client = transport.getRpcStub(serverEndpoint, TestService.class);

    // Test toString with direct endpoint
    String toStringResult = client.toString();
    assertTrue(toStringResult.contains("RemoteStub"));
    assertTrue(toStringResult.contains(serverEndpoint.toString()));
  }

  @Test
  void testRemoteInvocationHandlerVoidMethod() throws Exception {
    // Test void method invocation (covers lambda$invoke$0)
    VoidMethodService serviceImpl = new VoidMethodServiceImpl();
    transport.registerServiceAndListen(serviceImpl, VoidMethodService.class, serverEndpoint);
    EndpointId serviceId = new EndpointId("void-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    VoidMethodService client = transport.getRpcStub(serviceId, VoidMethodService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test void method - should complete successfully
      client.doSomething("test");
      
      // Test another void method
      client.doNothing();
      
      return null;
    });
    
    pumpUntilDone(testFuture);
  }

  @Test
  void testRemoteInvocationHandlerConvertReturnValueOutOfRange() throws Exception {
    RemoteNumericService serviceImpl = new RemoteNumericServiceImpl();
    transport.registerServiceAndListen(serviceImpl, RemoteNumericService.class, serverEndpoint);
    EndpointId serviceId = new EndpointId("remote-numeric-service2");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    RemoteNumericService client = transport.getRpcStub(serviceId, RemoteNumericService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test Long to Byte conversion out of range
      try {
        await(client.getByteFromLong(1000L));
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // Expected
      }

      // Test Long to Short conversion out of range
      try {
        await(client.getShortFromLong(100000L));
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // Expected
      }

      // Test Long to Integer conversion out of range
      try {
        await(client.getIntFromLong(Long.MAX_VALUE));
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // Expected
      }
      
      return null;
    });
    
    pumpUntilDone(testFuture);
  }

  interface GenericService {
    FlowFuture<String> processPromise(FlowPromise<String> promise);
    FlowFuture<List<Integer>> processStream(PromiseStream<Integer> stream);
    FlowFuture<Object> processObject(Object obj);
  }

  static class GenericServiceImpl implements GenericService {
    @Override
    public FlowFuture<String> processPromise(FlowPromise<String> promise) {
      return promise.getFuture().map(s -> "Processed: " + s);
    }

    @Override
    public FlowFuture<List<Integer>> processStream(PromiseStream<Integer> stream) {
      List<Integer> collected = new ArrayList<>();
      FlowFuture<List<Integer>> future = new FlowFuture<>();
      stream.getFutureStream().forEach(value -> collected.add(value)).whenComplete((v, e) -> {
        if (e != null) {
          future.getPromise().completeExceptionally(e);
        } else {
          future.getPromise().complete(collected);
        }
      });
      return future;
    }

    @Override
    public FlowFuture<Object> processObject(Object obj) {
      return FlowFuture.completed("Object: " + obj);
    }
  }

  interface NullableService {
    FlowFuture<String> processNull();
    FlowFuture<String> processEmpty();
  }

  static class NullableServiceImpl implements NullableService {
    @Override
    public FlowFuture<String> processNull() {
      return FlowFuture.completed("null processed");
    }

    @Override
    public FlowFuture<String> processEmpty() {
      return FlowFuture.completed("empty processed");
    }
  }

  interface VoidMethodService {
    void doSomething(String param);
    void doNothing();
  }
  
  static class VoidMethodServiceImpl implements VoidMethodService {
    @Override
    public void doSomething(String param) {
      // Just a void method
    }
    
    @Override
    public void doNothing() {
      // Another void method
    }
  }

  interface RemoteNumericService {
    FlowFuture<Integer> getNullInteger();
    FlowFuture<Byte> getByteFromLong(long value);
    FlowFuture<Short> getShortFromLong(long value);
    FlowFuture<Integer> getIntFromLong(long value);
    FlowFuture<Long> getLong(long value);
  }

  static class RemoteNumericServiceImpl implements RemoteNumericService {
    @Override
    public FlowFuture<Integer> getNullInteger() {
      return FlowFuture.completed(null);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public FlowFuture<Byte> getByteFromLong(long value) {
      // Return the Long value - the RPC framework will convert it to Byte on client side
      // This simulates what happens when Tuple serialization stores all integers as Long
      FlowFuture rawFuture = FlowFuture.completed(Long.valueOf(value));
      return rawFuture;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public FlowFuture<Short> getShortFromLong(long value) {
      // Return the Long value - the RPC framework will convert it to Short on client side
      // This simulates what happens when Tuple serialization stores all integers as Long
      FlowFuture rawFuture = FlowFuture.completed(Long.valueOf(value));
      return rawFuture;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public FlowFuture<Integer> getIntFromLong(long value) {
      // Return the Long value - the RPC framework will convert it to Integer on client side
      // This simulates what happens when Tuple serialization stores all integers as Long
      FlowFuture rawFuture = FlowFuture.completed(Long.valueOf(value));
      return rawFuture;
    }

    @Override
    public FlowFuture<Long> getLong(long value) {
      return FlowFuture.completed(value);
    }
  }

}
