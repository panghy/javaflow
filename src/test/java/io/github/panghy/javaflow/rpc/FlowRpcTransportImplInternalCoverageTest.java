package io.github.panghy.javaflow.rpc;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.message.RpcMessage;
import io.github.panghy.javaflow.rpc.message.RpcMessageHeader;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Internal coverage tests for {@link FlowRpcTransportImpl}.
 * Tests error handling paths and edge cases that are difficult to test through the public API.
 * These tests restore coverage that was lost when tests accessing private classes were removed.
 */
@Timeout(30)
public class FlowRpcTransportImplInternalCoverageTest extends AbstractFlowTest {

  private static final Logger LOGGER = Logger.getLogger(
      FlowRpcTransportImplInternalCoverageTest.class.getName());

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

    CompletableFuture<String> futureMethod();

    FlowPromise<String> promiseMethod();

    PromiseStream<String> streamMethod();
  }

  public interface NumericService {
    byte getByte();

    short getShort();

    int getInt();

    long getLong();

    float getFloat();

    double getDouble();

    String processByte(byte value);

    String processShort(short value);

    String processInt(int value);

    String processLong(long value);

    String processFloat(float value);

    String processDouble(double value);
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
    public CompletableFuture<String> futureMethod() {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.getPromise().complete("Future result");
      return future;
    }

    @Override
    public FlowPromise<String> promiseMethod() {
      CompletableFuture<String> future = new CompletableFuture<>();
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
      return 1234;
    }

    @Override
    public int getInt() {
      return 123456;
    }

    @Override
    public long getLong() {
      return 123456789L;
    }

    @Override
    public float getFloat() {
      return 3.14f;
    }

    @Override
    public double getDouble() {
      return 3.14159;
    }

    @Override
    public String processByte(byte value) {
      return "Byte: " + value;
    }

    @Override
    public String processShort(short value) {
      return "Short: " + value;
    }

    @Override
    public String processInt(int value) {
      return "Int: " + value;
    }

    @Override
    public String processLong(long value) {
      return "Long: " + value;
    }

    @Override
    public String processFloat(float value) {
      return "Float: " + value;
    }

    @Override
    public String processDouble(double value) {
      return "Double: " + value;
    }
  }

  @Test
  public void testNumericTypeConversions() {
    // Test numeric type conversions in arguments and return values
    NumericServiceImpl impl = new NumericServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, NumericService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("numeric-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    NumericService client = transport.getRpcStub(serviceId, NumericService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test return value conversions
      assertEquals(42, client.getByte());
      assertEquals(1234, client.getShort());
      assertEquals(123456, client.getInt());
      assertEquals(123456789L, client.getLong());
      assertEquals(3.14f, client.getFloat(), 0.001);
      assertEquals(3.14159, client.getDouble(), 0.00001);

      // Test argument conversions
      assertEquals("Byte: 42", client.processByte((byte) 42));
      assertEquals("Short: 1234", client.processShort((short) 1234));
      assertEquals("Int: 123456", client.processInt(123456));
      assertEquals("Long: 123456789", client.processLong(123456789L));
      assertEquals("Float: 3.14", client.processFloat(3.14f));
      assertEquals("Double: 3.14159", client.processDouble(3.14159));

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testNumericOverflowInArguments() {
    // Test numeric overflow handling in arguments
    interface OverflowService {
      String processByte(byte value);

      String processShort(short value);
    }

    OverflowService impl = new OverflowService() {
      @Override
      public String processByte(byte value) {
        return "Byte: " + value;
      }

      @Override
      public String processShort(short value) {
        return "Short: " + value;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, OverflowService.class, serverEndpoint);

    // Create a service that sends values that overflow when converted
    interface WideNumericService {
      String processByte(long value);

      String processShort(long value);
    }

    EndpointId serviceId = new EndpointId("overflow-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    WideNumericService client = transport.getRpcStub(serviceId, WideNumericService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test byte overflow
      try {
        client.processByte(256L); // Too large for byte
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("cannot be converted to Byte"));
      }

      // Test short overflow
      try {
        client.processShort(65536L); // Too large for short
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("cannot be converted to Short"));
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testNumericOverflowInReturnValue() {
    // Test numeric overflow handling in return values
    NumericService overflowService = new NumericService() {
      @Override
      public byte getByte() {
        // This will be sent as Long over wire
        return 42;
      }

      @Override
      public short getShort() {
        // This will be sent as Long over wire  
        return 1234;
      }

      @Override
      public int getInt() {
        return 123456;
      }

      @Override
      public long getLong() {
        return Long.MAX_VALUE;
      }

      @Override
      public float getFloat() {
        return 3.14f;
      }

      @Override
      public double getDouble() {
        return 3.14159;
      }

      @Override
      public String processByte(byte value) {
        return "Byte: " + value;
      }

      @Override
      public String processShort(short value) {
        return "Short: " + value;
      }

      @Override
      public String processInt(int value) {
        return "Int: " + value;
      }

      @Override
      public String processLong(long value) {
        return "Long: " + value;
      }

      @Override
      public String processFloat(float value) {
        return "Float: " + value;
      }

      @Override
      public String processDouble(double value) {
        return "Double: " + value;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, overflowService, NumericService.class, serverEndpoint);

    // Create a service interface that expects narrower types
    interface NarrowNumericService {
      int getByte(); // Expects int but server returns byte

      int getShort(); // Expects int but server returns short
    }

    EndpointId serviceId = new EndpointId("overflow-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    NarrowNumericService client = transport.getRpcStub(serviceId, NarrowNumericService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // These should work because byte/short fit in int
      assertEquals(42, client.getByte());
      assertEquals(1234, client.getShort());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testUnknownMessageType() {
    // Test handling unknown message type by sending raw message
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));

        // Create a message with unknown type (not in MessageType enum)
        ByteBuffer header = ByteBuffer.allocate(25);
        header.put((byte) 99); // Unknown message type
        header.putLong(UUID.randomUUID().getMostSignificantBits());
        header.putLong(UUID.randomUUID().getLeastSignificantBits());
        header.putInt(0); // No method ID length
        header.putInt(0); // No promise IDs
        header.flip();

        ByteBuffer message = ByteBuffer.allocate(header.remaining() + 4);
        message.putInt(header.remaining());
        message.put(header);
        message.flip();

        connection.send(message);

        // Give server time to process
        await(Flow.delay(0.1));

        // Connection should still be open (error was logged but not fatal)
        assertTrue(connection.isOpen());

        return null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testCorruptedMessageDeserialization() {
    // Test handling completely corrupted messages
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));

        // Send corrupted data that will fail deserialization
        ByteBuffer corruptedData = ByteBuffer.allocate(20);
        corruptedData.putInt(16); // Length
        corruptedData.put((byte) 255); // Invalid type
        corruptedData.putLong(0); // Invalid UUID high
        corruptedData.putLong(0); // Invalid UUID low
        corruptedData.flip();

        connection.send(corruptedData);

        // Give server time to process
        await(Flow.delay(0.1));

        // Connection should still be open
        assertTrue(connection.isOpen());

        return null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testStreamDataWithUnknownStreamId() {
    // Test handling stream data for unknown stream ID
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));

        // Send STREAM_DATA for unknown stream
        UUID unknownStreamId = UUID.randomUUID();
        ByteBuffer payload = FlowSerialization.serialize("test data");
        RpcMessage streamDataMsg = new RpcMessage(
            RpcMessageHeader.MessageType.STREAM_DATA,
            unknownStreamId,
            null,
            null,
            payload);

        connection.send(streamDataMsg.serialize());

        // Give server time to process
        await(Flow.delay(0.1));

        // Connection should still be open
        assertTrue(connection.isOpen());

        return null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testPromiseCompletionForUnknownPromise() {
    // Test handling promise completion for unknown promise ID
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));

        // Send PROMISE_COMPLETE for unknown promise
        UUID unknownPromiseId = UUID.randomUUID();
        ByteBuffer payload = FlowSerialization.serialize("result");
        RpcMessage promiseMsg = new RpcMessage(
            RpcMessageHeader.MessageType.PROMISE_COMPLETE,
            unknownPromiseId,
            null,
            null,
            payload);

        connection.send(promiseMsg.serialize());

        // Give server time to process
        await(Flow.delay(0.1));

        // Connection should still be open
        assertTrue(connection.isOpen());

        return null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testRequestWithEmptyPayload() {
    // Test REQUEST message with empty payload (no arguments)
    interface NoArgsService {
      String noArgs();
    }

    NoArgsService impl = () -> "no args result";

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, NoArgsService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("no-args-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    NoArgsService client = transport.getRpcStub(serviceId, NoArgsService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      String result = client.noArgs();
      assertEquals("no args result", result);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testSendErrorForUnserializablePromiseResult() {
    // Test MessageSender.sendError when promise result serialization fails
    interface UnserializablePromiseService {
      FlowPromise<Object> getUnserializablePromise();
    }

    UnserializablePromiseService impl = () -> {
      CompletableFuture<Object> future = new CompletableFuture<>();
      // Complete with non-serializable object
      startActor(() -> {
        await(Flow.delay(0.1));
        future.complete(new Object() {
          private final Thread thread = Thread.currentThread();

          @Override
          public String toString() {
            return "Unserializable";
          }
        });
        return null;
      });
      return future.getPromise();
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, UnserializablePromiseService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("unserializable-promise-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    UnserializablePromiseService client = transport.getRpcStub(serviceId,
        UnserializablePromiseService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      FlowPromise<Object> promise = client.getUnserializablePromise();
      try {
        await(promise.getFuture());
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Should get serialization error
        assertNotNull(e);
      }
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testSerializationErrorForStreamValue() {
    // Test stream value serialization error
    interface UnserializableStreamService {
      PromiseStream<Object> getUnserializableStream();
    }

    UnserializableStreamService impl = () -> {
      PromiseStream<Object> stream = new PromiseStream<>();
      startActor(() -> {
        await(Flow.delay(0.1));
        // Send non-serializable object
        stream.send(new Object() {
          private final Thread thread = Thread.currentThread();
        });
        return null;
      });
      return stream;
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, UnserializableStreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("unserializable-stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    UnserializableStreamService client = transport.getRpcStub(serviceId,
        UnserializableStreamService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      PromiseStream<Object> stream = client.getUnserializableStream();
      AtomicBoolean errorReceived = new AtomicBoolean(false);
      stream.getFutureStream().forEach(value -> {
        fail("Should not receive value");
      });
      stream.getFutureStream().onClose().whenComplete((v, error) -> {
        if (error != null) {
          errorReceived.set(true);
        }
      });

      // Wait for stream to close with error
      try {
        await(stream.getFutureStream().onClose());
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertTrue(errorReceived.get());
      }
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testConnectionCloseWhileProcessingMessages() {
    // Test connection closing while actively processing messages
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));

        // Send multiple requests
        List<UUID> messageIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
          UUID messageId = UUID.randomUUID();
          messageIds.add(messageId);
          String methodId = "io.github.panghy.javaflow.rpc." +
                            "FlowRpcTransportImplInternalCoverageTest$TestService.echo(java.lang.String)";
          RpcMessage request = new RpcMessage(
              RpcMessageHeader.MessageType.REQUEST,
              messageId,
              methodId,
              null,
              FlowSerialization.serialize(new Object[]{"Message " + i}));
          connection.send(request.serialize());
        }

        // Close connection immediately
        await(connection.close());

        // Give time for server to process
        await(Flow.delay(0.1));

        return null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testInvokeMethodWithInvalidArguments() {
    // Test invoking method with wrong argument types
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      try {
        var connection = await(networkTransport.connect(serverEndpoint));

        // Send request with wrong argument type
        UUID messageId = UUID.randomUUID();
        RpcMessage request = new RpcMessage(
            RpcMessageHeader.MessageType.REQUEST,
            messageId,
            "io.github.panghy.javaflow.rpc.FlowRpcTransportImplInternalCoverageTest$TestService.addNumbers(int,int)",
            null,
            FlowSerialization.serialize(new Object[]{"not", "numbers"})); // Wrong types

        connection.send(request.serialize());

        // Read the error response
        ByteBuffer response = await(connection.receive(65536));
        RpcMessage responseMsg = RpcMessage.deserialize(response);
        assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());

        return null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testPromiseWithNullPayload() {
    // Test promise completion with null payload
    interface NullPromiseService {
      FlowPromise<String> getNullPromise();
    }

    NullPromiseService impl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.complete(null);
      return future.getPromise();
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, NullPromiseService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("null-promise-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    NullPromiseService client = transport.getRpcStub(serviceId, NullPromiseService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      FlowPromise<String> promise = client.getNullPromise();
      String result = await(promise.getFuture());
      assertEquals(null, result);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testMultipleNumericConversions() {
    // Test multiple numeric conversions in a single call
    interface MultiNumericService {
      String processMultiple(byte b, short s, int i, long l, float f, double d);
    }

    MultiNumericService impl = (b, s, i, l, f, d) -> {
      return String.format("b=%d,s=%d,i=%d,l=%d,f=%.2f,d=%.2f", b, s, i, l, f, d);
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, MultiNumericService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("multi-numeric-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    MultiNumericService client = transport.getRpcStub(serviceId, MultiNumericService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      String result = client.processMultiple(
          (byte) 1, (short) 2, 3, 4L, 5.0f, 6.0);
      assertEquals("b=1,s=2,i=3,l=4,f=5.00,d=6.00", result);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testMethodInvocationException() {
    // Test when method invocation throws exception
    interface ExceptionService {
      void throwException() throws Exception;

      String throwRuntimeException();
    }

    ExceptionService impl = new ExceptionService() {
      @Override
      public void throwException() throws Exception {
        throw new Exception("Checked exception");
      }

      @Override
      public String throwRuntimeException() {
        throw new RuntimeException("Unchecked exception");
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, ExceptionService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("exception-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ExceptionService client = transport.getRpcStub(serviceId, ExceptionService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test checked exception
      try {
        client.throwException();
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("Checked exception"));
      }

      // Test runtime exception
      try {
        client.throwRuntimeException();
        fail("Should have thrown exception");
      } catch (RuntimeException e) {
        assertTrue(e.getMessage().contains("Unchecked exception"));
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testFutureAlreadyCompleted() {
    // Test returning already completed futures
    interface CompletedFutureService {
      CompletableFuture<String> getCompletedFuture();

      CompletableFuture<String> getExceptionalFuture();
    }

    CompletedFutureService impl = new CompletedFutureService() {
      @Override
      public CompletableFuture<String> getCompletedFuture() {
        return FlowFuture.completed("Already done");
      }

      @Override
      public CompletableFuture<String> getExceptionalFuture() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.getPromise().completeExceptionally(new RuntimeException("Already failed"));
        return future;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, CompletedFutureService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("completed-future-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    CompletedFutureService client = transport.getRpcStub(serviceId, CompletedFutureService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test already completed future
      CompletableFuture<String> completedFuture = client.getCompletedFuture();
      String result = await(completedFuture);
      assertEquals("Already done", result);

      // Test already failed future
      try {
        CompletableFuture<String> failedFuture = client.getExceptionalFuture();
        await(failedFuture);
        fail("Should have thrown exception");
      } catch (RuntimeException e) {
        assertEquals("Already failed", e.getMessage());
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testWideningNumericConversions() {
    // Test numeric conversions that widen types
    interface WideningService {
      long getInt();

      double getFloat();

      double getInt2();
    }

    WideningService impl = new WideningService() {
      @Override
      public long getInt() {
        return 42L;
      }

      @Override
      public double getFloat() {
        return 3.14;
      }

      @Override
      public double getInt2() {
        return 100.0;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, WideningService.class, serverEndpoint);

    // Client expects narrower types
    interface NarrowingClient {
      int getInt();

      float getFloat();

      int getInt2();
    }

    EndpointId serviceId = new EndpointId("widening-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    NarrowingClient client = transport.getRpcStub(serviceId, NarrowingClient.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // These should work with numeric conversion
      assertEquals(42, client.getInt());
      assertEquals(3.14f, client.getFloat(), 0.001);
      assertEquals(100, client.getInt2());
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testStreamCloseWithError() {
    // Test stream closing with error payload
    interface ErrorStreamService {
      PromiseStream<String> getErrorStream();
    }

    ErrorStreamService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      startActor(() -> {
        stream.send("Value 1");
        await(Flow.delay(0.1));
        stream.closeExceptionally(new RuntimeException("Stream error"));
        return null;
      });
      return stream;
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, ErrorStreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("error-stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ErrorStreamService client = transport.getRpcStub(serviceId, ErrorStreamService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      PromiseStream<String> stream = client.getErrorStream();
      List<String> values = new ArrayList<>();
      AtomicReference<Throwable> errorRef = new AtomicReference<>();

      stream.getFutureStream().forEach(values::add);
      stream.getFutureStream().onClose().whenComplete((v, error) -> {
        errorRef.set(error);
      });

      try {
        await(stream.getFutureStream().onClose());
        fail("Should have thrown exception");
      } catch (RuntimeException e) {
        assertEquals("Stream error", e.getMessage());
      }

      assertEquals(1, values.size());
      assertEquals("Value 1", values.getFirst());
      assertNotNull(errorRef.get());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testConnectionHandlerStartMessageReader() {
    // Test that message reader starts properly and handles connection close
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Connect and immediately close to test reader error handling
      var connection = await(networkTransport.connect(serverEndpoint));

      // Send a valid request to trigger reader start
      UUID messageId = UUID.randomUUID();
      RpcMessage request = new RpcMessage(
          RpcMessageHeader.MessageType.REQUEST,
          messageId,
          "io.github.panghy.javaflow.rpc.FlowRpcTransportImplInternalCoverageTest$TestService.echo(java.lang.String)",
          null,
          FlowSerialization.serialize(new Object[]{"test"}));

      connection.send(request.serialize());

      // Close connection to trigger error handling
      await(connection.close());

      // Give time for error handling
      await(Flow.delay(0.1));

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testMessageSenderFailedSerialization() {
    // Test sendStreamError when error serialization fails
    interface StreamErrorService {
      PromiseStream<String> getStream();
    }

    StreamErrorService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      startActor(() -> {
        await(Flow.delay(0.1));
        // Close with an error that fails to serialize
        stream.closeExceptionally(new Exception() {
          @Override
          public String getMessage() {
            throw new RuntimeException("Can't serialize me!");
          }
        });
        return null;
      });
      return stream;
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, StreamErrorService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-error-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamErrorService client = transport.getRpcStub(serviceId, StreamErrorService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      PromiseStream<String> stream = client.getStream();

      try {
        await(stream.getFutureStream().onClose());
        // Stream should close (even if error couldn't be serialized)
      } catch (Exception e) {
        // Expected - stream closed with error
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testHandleResponseWithInvalidPayload() {
    // Test handling response with payload that can't be deserialized
    LocalEndpoint customEndpoint = LocalEndpoint.localhost(8889);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Start a custom server that sends invalid response
      var serverStream = networkTransport.listen(customEndpoint);

      startActor(() -> {
        var conn = await(serverStream.nextAsync());

        // Receive request
        ByteBuffer request = await(conn.receive(65536));
        RpcMessage requestMsg = RpcMessage.deserialize(request);

        // Send response with corrupted payload
        ByteBuffer corruptedPayload = ByteBuffer.allocate(100);
        corruptedPayload.put(new byte[100]); // Random bytes
        corruptedPayload.flip();

        RpcMessage response = new RpcMessage(
            RpcMessageHeader.MessageType.RESPONSE,
            requestMsg.getHeader().getMessageId(),
            null,
            null,
            corruptedPayload);

        conn.send(response.serialize());
        return null;
      });

      // Create client and make a call
      EndpointId serviceId = new EndpointId("test-service");
      transport.getEndpointResolver().registerRemoteEndpoint(serviceId, customEndpoint);

      interface SimpleService {
        String getValue();
      }

      SimpleService client = transport.getRpcStub(serviceId, SimpleService.class);

      try {
        client.getValue();
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Expected - deserialization should fail
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testSendMessageToClosedConnection() {
    // Test sending message when connection is already closed
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Connect then close
      var connection = await(networkTransport.connect(serverEndpoint));
      await(connection.close());

      // Now try to send a request through the transport
      EndpointId serviceId = new EndpointId("test-service");
      transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

      TestService client = transport.getRpcStub(serviceId, TestService.class);

      try {
        client.echo("test");
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Expected - connection is closed
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testFloatToDoubleConversion() {
    // Test Float to Double conversion in processIncomingArguments
    interface FloatDoubleService {
      String processDouble(double value);
    }

    FloatDoubleService impl = value -> "Double: " + value;

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, FloatDoubleService.class, serverEndpoint);

    // Client sends float, server expects double
    interface FloatClient {
      String processDouble(float value);
    }

    EndpointId serviceId = new EndpointId("float-double-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    FloatClient client = transport.getRpcStub(serviceId, FloatClient.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      String result = client.processDouble(3.14f);
      assertTrue(result.startsWith("Double: 3.14"));
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testIntegerToDoubleConversion() {
    // Test Integer to Double conversion
    interface IntDoubleService {
      String processDouble(double value);
    }

    IntDoubleService impl = value -> "Double: " + value;

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, IntDoubleService.class, serverEndpoint);

    // Client sends int, server expects double
    interface IntClient {
      String processDouble(int value);
    }

    EndpointId serviceId = new EndpointId("int-double-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    IntClient client = transport.getRpcStub(serviceId, IntClient.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      String result = client.processDouble(42);
      assertEquals("Double: 42.0", result);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testLongToDoubleConversion() {
    // Test Long to Double conversion
    interface LongDoubleService {
      String processDouble(double value);
    }

    LongDoubleService impl = value -> "Double: " + value;

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, LongDoubleService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("long-double-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    LongDoubleService client = transport.getRpcStub(serviceId, LongDoubleService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      String result = client.processDouble(123456789L);
      assertEquals("Double: 1.23456789E8", result);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testSendCancellationToRemotePromise() {
    // Test cancellation of remote promises
    interface CancellableService {
      FlowPromise<String> getCancellablePromise();
    }

    AtomicReference<FlowPromise<String>> promiseRef = new AtomicReference<>();

    CancellableService impl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      promiseRef.set(future.getPromise());
      // Never complete it
      return future.getPromise();
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, CancellableService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("cancellable-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    CancellableService client = transport.getRpcStub(serviceId, CancellableService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      FlowPromise<String> promise = client.getCancellablePromise();

      // Wait a bit then close transport to trigger cancellation
      await(Flow.delay(0.1));
      await(transport.close());

      // Promise should be cancelled
      try {
        await(promise.getFuture());
        fail("Should have been cancelled");
      } catch (Exception e) {
        // Expected - promise was cancelled
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testSendErrorWhenSerializationFails() {
    // Test sendError when serialization of the error itself fails
    interface ErrorService {
      FlowPromise<String> getPromise();
    }

    AtomicReference<FlowPromise<String>> promiseRef = new AtomicReference<>();

    ErrorService impl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      promiseRef.set(future.getPromise());
      return future.getPromise();
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, ErrorService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("error-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ErrorService client = transport.getRpcStub(serviceId, ErrorService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      FlowPromise<String> promise = client.getPromise();

      // Complete with an error that can't be serialized
      startActor(() -> {
        await(Flow.delay(0.1));
        promiseRef.get().completeExceptionally(new Exception() {
          @Override
          public String getMessage() {
            throw new RuntimeException("Can't serialize me!");
          }
        });
        return null;
      });

      // Client should still get an error (even if not the exact one)
      try {
        await(promise.getFuture());
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testConnectionFailureInSendMessageToEndpoint() {
    // Test when connectionManager.getConnectionToEndpoint fails
    LocalEndpoint badEndpoint = LocalEndpoint.localhost(19999);

    // Create a service that returns a promise
    interface RemotePromiseService {
      FlowPromise<String> getRemotePromise();
    }

    RemotePromiseService impl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      // Complete after delay
      startActor(() -> {
        await(Flow.delay(0.1));
        future.complete("result");
        return null;
      });
      return future.getPromise();
    };

    // Register service on bad endpoint (won't actually listen)
    EndpointId serviceId = new EndpointId("remote-promise-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, badEndpoint);

    RemotePromiseService client = transport.getRpcStub(serviceId, RemotePromiseService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // This should fail to connect
      try {
        client.getRemotePromise();
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Expected - connection should fail
        assertTrue(e.getMessage().contains("Connection refused") ||
                   e.getMessage().contains("Failed to connect"));
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testMessageReaderExceptionHandling() {
    // Test exception handling in ConnectionMessageHandler's message reader
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Connect and start a request
      var connection = await(networkTransport.connect(serverEndpoint));

      // Send a valid request to start the reader
      UUID messageId = UUID.randomUUID();
      RpcMessage request = new RpcMessage(
          RpcMessageHeader.MessageType.REQUEST,
          messageId,
          "io.github.panghy.javaflow.rpc.FlowRpcTransportImplInternalCoverageTest$TestService.echo(java.lang.String)",
          null,
          FlowSerialization.serialize(new Object[]{"test"}));

      connection.send(request.serialize());

      // Wait for response
      ByteBuffer response = await(connection.receive(65536));
      RpcMessage responseMsg = RpcMessage.deserialize(response);
      assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());

      // Now send corrupted data to trigger exception in reader
      ByteBuffer corruptedData = ByteBuffer.allocate(10);
      corruptedData.put(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
      corruptedData.flip();

      connection.send(corruptedData);

      // Close connection to trigger cleanup
      await(connection.close());

      // Give time for error handling
      await(Flow.delay(0.1));

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testConnectionAlreadyClosedInSendMessage() {
    // Test sending message when connection exists but is closed
    interface PromiseReturningService {
      FlowPromise<String> getDelayedValue();
    }

    PromiseReturningService impl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      // Complete promise after connection is closed
      startActor(() -> {
        await(Flow.delay(0.3));
        future.complete("delayed result");
        return null;
      });
      return future.getPromise();
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, PromiseReturningService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      EndpointId serviceId = new EndpointId("promise-service");
      transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);
      PromiseReturningService client = transport.getRpcStub(serviceId, PromiseReturningService.class);

      // Get the promise
      FlowPromise<String> promise = client.getDelayedValue();

      // Force close all connections
      await(Flow.delay(0.1));
      await(networkTransport.close());

      // Promise completion should still try to send result
      try {
        await(promise.getFuture());
        fail("Should have thrown exception due to closed connection");
      } catch (Exception e) {
        // Expected - connection was closed
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testSendStreamErrorWhenSerializationFails() {
    // Test sendStreamError when error serialization fails - should fall back to sendStreamClose
    interface StreamErrorService {
      PromiseStream<String> getStream();
    }

    AtomicReference<PromiseStream<String>> streamRef = new AtomicReference<>();

    StreamErrorService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      streamRef.set(stream);
      return stream;
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, StreamErrorService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-error-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamErrorService client = transport.getRpcStub(serviceId, StreamErrorService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      PromiseStream<String> stream = client.getStream();

      // Close stream with unserializable error
      startActor(() -> {
        await(Flow.delay(0.1));
        streamRef.get().closeExceptionally(new Exception() {
          private Object writeReplace() {
            throw new RuntimeException("Can't serialize!");
          }
        });
        return null;
      });

      // Stream should still close (even without error details)
      try {
        await(stream.getFutureStream().onClose());
      } catch (Exception e) {
        // May or may not get an exception
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testNullConnectionInSendMessageToEndpoint() {
    // Test connection failure scenarios
    interface FailingConnectionService {
      String getValue();
    }

    FailingConnectionService impl = () -> "value";

    // Create a bad endpoint that we can't actually connect to
    LocalEndpoint badEndpoint = LocalEndpoint.localhost(29999);

    // Register on a real endpoint first
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, FailingConnectionService.class, serverEndpoint);

    // But tell the client it's on the bad endpoint
    EndpointId serviceId = new EndpointId("failing-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, badEndpoint);

    FailingConnectionService client = transport.getRpcStub(serviceId, FailingConnectionService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // This should fail to connect
      try {
        client.getValue();
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Expected - connection should fail
        assertTrue(e.getMessage().contains("Connection refused") ||
                   e.getMessage().contains("Failed to connect") ||
                   e.getMessage().contains("No connection available"));
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testHandleIncomingMessageWithInvalidMessageType() {
    // Test handling message with invalid type value
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      var connection = await(networkTransport.connect(serverEndpoint));

      // Send a message with invalid type in header but valid structure
      ByteBuffer buffer = ByteBuffer.allocate(100);
      buffer.putInt(25); // Message length
      buffer.put((byte) 127); // Invalid message type (not in enum)

      // Add UUID (16 bytes)
      buffer.putLong(UUID.randomUUID().getMostSignificantBits());
      buffer.putLong(UUID.randomUUID().getLeastSignificantBits());

      // Method ID length and promise count
      buffer.putInt(0); // No method ID
      buffer.putInt(0); // No promise IDs
      buffer.flip();

      connection.send(buffer);

      // Give time to process
      await(Flow.delay(0.1));

      // Connection should remain open despite invalid message
      assertTrue(connection.isOpen());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testMultiplePendingCallsWithConnectionError() {
    // Test that all pending calls are completed with error when connection fails
    interface SlowService {
      CompletableFuture<String> slowMethod1();

      CompletableFuture<String> slowMethod2();

      CompletableFuture<String> slowMethod3();
    }

    SlowService impl = new SlowService() {
      @Override
      public CompletableFuture<String> slowMethod1() {
        return startActor(() -> {
          await(Flow.delay(1.0));
          return "result1";
        });
      }

      @Override
      public CompletableFuture<String> slowMethod2() {
        return startActor(() -> {
          await(Flow.delay(1.0));
          return "result2";
        });
      }

      @Override
      public CompletableFuture<String> slowMethod3() {
        return startActor(() -> {
          await(Flow.delay(1.0));
          return "result3";
        });
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, SlowService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("slow-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    SlowService client = transport.getRpcStub(serviceId, SlowService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Start multiple concurrent calls
      CompletableFuture<String> future1 = client.slowMethod1();
      CompletableFuture<String> future2 = client.slowMethod2();
      CompletableFuture<String> future3 = client.slowMethod3();

      // Close the network transport to trigger connection errors
      await(Flow.delay(0.1));
      await(networkTransport.close());

      // All futures should complete exceptionally
      try {
        await(future1);
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertNotNull(e);
      }

      try {
        await(future2);
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertNotNull(e);
      }

      try {
        await(future3);
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testHandleStreamCloseWithRemainingErrorBytes() {
    // Test stream close message with error payload that has remaining bytes
    interface ErrorStreamService {
      PromiseStream<String> getStream();
    }

    ErrorStreamService impl = () -> {
      PromiseStream<String> stream = new PromiseStream<>();
      startActor(() -> {
        stream.send("value1");
        await(Flow.delay(0.1));
        // Close with specific error
        stream.closeExceptionally(new IllegalStateException("Stream error with details"));
        return null;
      });
      return stream;
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, ErrorStreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("error-stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ErrorStreamService client = transport.getRpcStub(serviceId, ErrorStreamService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      PromiseStream<String> stream = client.getStream();
      List<String> values = new ArrayList<>();
      AtomicReference<Throwable> errorRef = new AtomicReference<>();

      stream.getFutureStream().forEach(values::add);
      stream.getFutureStream().onClose().whenComplete((v, error) -> {
        errorRef.set(error);
      });

      try {
        await(stream.getFutureStream().onClose());
        fail("Should have thrown exception");
      } catch (IllegalStateException e) {
        assertEquals("Stream error with details", e.getMessage());
      }

      assertEquals(1, values.size());
      assertNotNull(errorRef.get());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testMessageSenderWithExistingConnection() {
    // Test MessageSender using existing connection instead of creating new one
    interface BidirectionalService {
      void startBidirectionalCommunication(EndpointId callbackEndpointId);
    }

    interface CallbackService {
      FlowPromise<String> getCallback();
    }

    AtomicReference<FlowPromise<String>> callbackPromiseRef = new AtomicReference<>();

    CallbackService callbackImpl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      callbackPromiseRef.set(future.getPromise());
      return future.getPromise();
    };

    // Register callback service
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, callbackImpl, CallbackService.class, serverEndpoint);
    EndpointId callbackEndpointId = new EndpointId("callback-service");
    transport.getEndpointResolver().registerLocalEndpoint(callbackEndpointId, callbackImpl, serverEndpoint);

    BidirectionalService bidirectionalImpl = (EndpointId callbackId) -> {
      // Get callback client using the existing connection
      CallbackService callbackClient = transport.getRpcStub(callbackId, CallbackService.class);

      startActor(() -> {
        // Get promise from callback
        FlowPromise<String> promise = callbackClient.getCallback();

        // This will complete later
        return null;
      });
    };

    EndpointId serviceEndpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(serviceEndpointId, bidirectionalImpl, 
        BidirectionalService.class, serverEndpoint);
    EndpointId bidirectionalId = new EndpointId("bidirectional-service");
    transport.getEndpointResolver().registerRemoteEndpoint(bidirectionalId, serverEndpoint);

    BidirectionalService client = transport.getRpcStub(bidirectionalId, BidirectionalService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Start bidirectional communication
      client.startBidirectionalCommunication(callbackEndpointId);

      // Wait for callback promise to be created
      await(Flow.delay(0.1));

      // Complete the callback promise - this should use existing connection
      if (callbackPromiseRef.get() != null) {
        callbackPromiseRef.get().complete("callback result");
      }

      // Give time for message to be sent
      await(Flow.delay(0.1));

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testHandleRequestMethodWithException() {
    // Test handleRequest when method invocation throws different exceptions
    interface ExceptionThrowingService {
      void throwChecked() throws Exception;

      void throwUnchecked();

      void throwError();
    }

    ExceptionThrowingService impl = new ExceptionThrowingService() {
      @Override
      public void throwChecked() throws Exception {
        throw new Exception("Checked exception");
      }

      @Override
      public void throwUnchecked() {
        throw new IllegalArgumentException("Unchecked exception");
      }

      @Override
      public void throwError() {
        throw new Error("Error thrown");
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, ExceptionThrowingService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("exception-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ExceptionThrowingService client = transport.getRpcStub(serviceId, ExceptionThrowingService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test checked exception
      try {
        client.throwChecked();
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertEquals("Checked exception", e.getMessage());
      }

      // Test unchecked exception
      try {
        client.throwUnchecked();
        fail("Should have thrown exception");
      } catch (IllegalArgumentException e) {
        assertEquals("Unchecked exception", e.getMessage());
      }

      // Test error
      try {
        client.throwError();
        fail("Should have thrown error");
      } catch (Error e) {
        assertEquals("Error thrown", e.getMessage());
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testSendResponseWithNullResult() {
    // Test sending response with null result value
    interface NullReturningService {
      String getNullString();

      Integer getNullInteger();

      List<String> getNullList();
    }

    NullReturningService impl = new NullReturningService() {
      @Override
      public String getNullString() {
        return null;
      }

      @Override
      public Integer getNullInteger() {
        return null;
      }

      @Override
      public List<String> getNullList() {
        return null;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, NullReturningService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("null-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    NullReturningService client = transport.getRpcStub(serviceId, NullReturningService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test null string
      assertNull(client.getNullString());

      // Test null integer
      assertNull(client.getNullInteger());

      // Test null list
      assertNull(client.getNullList());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testProcessIncomingArgumentsEdgeCases() {
    // Test processIncomingArguments with edge cases
    interface EdgeCaseService {
      String processNull(String arg);

      String processPrimitive(int value);

      String processArray(String[] array);

      String processVarArgs(String... args);
    }

    EdgeCaseService impl = new EdgeCaseService() {
      @Override
      public String processNull(String arg) {
        return "Got: " + arg;
      }

      @Override
      public String processPrimitive(int value) {
        return "Value: " + value;
      }

      @Override
      public String processArray(String[] array) {
        return array == null ? "null" : "Length: " + array.length;
      }

      @Override
      public String processVarArgs(String... args) {
        return "Args: " + args.length;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, EdgeCaseService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("edge-case-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    EdgeCaseService client = transport.getRpcStub(serviceId, EdgeCaseService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test null argument
      assertEquals("Got: null", client.processNull(null));

      // Test primitive
      assertEquals("Value: 42", client.processPrimitive(42));

      // Test null array
      assertEquals("null", client.processArray(null));

      // Test empty array
      assertEquals("Length: 0", client.processArray(new String[0]));

      // Test varargs
      assertEquals("Args: 0", client.processVarArgs());
      assertEquals("Args: 3", client.processVarArgs("a", "b", "c"));

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testConnectionFailureWithError() {
    // Test connectionFuture.whenComplete with error != null
    interface RemoteService {
      String getValue();
    }

    RemoteService impl = () -> "value";

    // Create an endpoint that will cause connection to fail
    LocalEndpoint unreachableEndpoint = LocalEndpoint.localhost(19876);

    // Try to connect to an endpoint that's not listening
    CompletableFuture<Void> testFuture = startActor(() -> {
      try {
        // This should trigger connection failure
        var connection = await(networkTransport.connect(unreachableEndpoint));
        fail("Should have failed to connect");
      } catch (Exception e) {
        // Expected - connection refused
        assertTrue(e.getMessage().contains("Connection refused") ||
                   e.getMessage().contains("Failed to connect"));
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testPromiseWithExistingClosedConnection() {
    // Test that promise completion finds existing connection but it's closed
    interface DelayedPromiseService {
      FlowPromise<String> getDelayedPromise();
    }

    AtomicReference<FlowPromise<String>> serverPromiseRef = new AtomicReference<>();

    DelayedPromiseService impl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      serverPromiseRef.set(future.getPromise());
      return future.getPromise();
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, DelayedPromiseService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("delayed-promise-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    DelayedPromiseService client = transport.getRpcStub(serviceId, DelayedPromiseService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Get the promise - this establishes a connection
      FlowPromise<String> clientPromise = client.getDelayedPromise();

      // Wait a bit for connection to be established
      await(Flow.delay(0.1));

      // Force close the transport to simulate connection issues
      await(networkTransport.close());

      // Wait a bit
      await(Flow.delay(0.1));

      // Now complete the promise on server side
      // This will try to send result but connection is gone
      if (serverPromiseRef.get() != null) {
        serverPromiseRef.get().complete("delayed result");
      }

      // Give time for completion attempt
      await(Flow.delay(0.2));

      // Client should get an error due to closed connection
      try {
        await(clientPromise.getFuture());
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Expected - connection was closed
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testHandleStreamDataForUnregisteredStream() {
    // Test receiving STREAM_DATA for a stream that was never registered
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      var connection = await(networkTransport.connect(serverEndpoint));

      // Send STREAM_DATA for a completely unknown stream ID
      UUID randomStreamId = UUID.randomUUID();
      ByteBuffer payload = FlowSerialization.serialize("stream data");

      RpcMessage streamDataMsg = new RpcMessage(
          RpcMessageHeader.MessageType.STREAM_DATA,
          randomStreamId,
          null,
          null,
          payload);

      connection.send(streamDataMsg.serialize());

      // Give time to process
      await(Flow.delay(0.1));

      // Connection should still be open
      assertTrue(connection.isOpen());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testHandlePromiseCompleteForUnregisteredPromise() {
    // Test receiving PROMISE_COMPLETE for a promise that was never registered
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      var connection = await(networkTransport.connect(serverEndpoint));

      // Send PROMISE_COMPLETE for unknown promise
      UUID randomPromiseId = UUID.randomUUID();
      ByteBuffer payload = FlowSerialization.serialize("promise result");

      RpcMessage promiseMsg = new RpcMessage(
          RpcMessageHeader.MessageType.PROMISE_COMPLETE,
          randomPromiseId,
          null,
          null,
          payload);

      connection.send(promiseMsg.serialize());

      // Give time to process
      await(Flow.delay(0.1));

      // Connection should still be open
      assertTrue(connection.isOpen());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testPromiseCancellationWithSendCancellationMethod() {
    // Test the sendCancellation method by using RemotePromiseTracker with reflection
    // This test directly triggers the sendCancellation code path

    // Set up a service that creates a connection
    interface SimpleService {
      String echo(String msg);
    }

    SimpleService impl = msg -> msg;
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, SimpleService.class, serverEndpoint);

    // Track messages sent
    AtomicReference<ByteBuffer> lastSentMessage = new AtomicReference<>();
    AtomicBoolean cancellationLogged = new AtomicBoolean(false);

    // Use a custom logger handler to verify the cancellation log message
    Logger logger = Logger.getLogger(FlowRpcTransportImpl.class.getName());
    java.util.logging.Handler testHandler = new java.util.logging.Handler() {
      @Override
      public void publish(java.util.logging.LogRecord record) {
        if (record.getMessage() != null &&
            record.getMessage().contains("Sending cancellation to") &&
            record.getMessage().contains("for promise")) {
          cancellationLogged.set(true);
        }
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() throws SecurityException {
      }
    };

    logger.addHandler(testHandler);
    logger.setLevel(java.util.logging.Level.INFO);

    try {
      CompletableFuture<Void> testFuture = startActor(() -> {
        // Create a connection to the server
        var conn = await(networkTransport.connect(serverEndpoint));

        // Use reflection to access RemotePromiseTracker and MessageSender
        try {
          // Get the RemotePromiseTracker field from FlowRpcTransportImpl
          java.lang.reflect.Field trackerField = FlowRpcTransportImpl.class.getDeclaredField("promiseTracker");
          trackerField.setAccessible(true);
          RemotePromiseTracker tracker = (RemotePromiseTracker) trackerField.get(transport);

          // Create a local promise with sendResultBack=true using the 3-argument version
          UUID promiseId = UUID.randomUUID();
          FlowPromise<String> localPromise = tracker.createLocalPromiseForRemote(
              promiseId, serverEndpoint, new io.github.panghy.javaflow.rpc.serialization.TypeDescription(String.class));

          // Cancel the promise - this should trigger sendCancellation
          boolean cancelled = localPromise.getFuture().cancel();
          assertTrue(cancelled, "Should be able to cancel the promise");

          // Wait for the cancellation to be processed
          await(Flow.delay(0.2));

          // Verify that the cancellation was logged
          assertTrue(cancellationLogged.get(), "Cancellation should have been logged");

        } catch (Exception e) {
          fail("Failed to access internal fields: " + e);
        }

        conn.close();
        return null;
      });

      pumpAndAdvanceTimeUntilDone(testFuture);

    } finally {
      logger.removeHandler(testHandler);
    }
  }

  @Test
  public void testRemotePromiseCompletionWithConnectionFailure() {
    // Test promise completion when new connection fails
    interface AsyncPromiseService {
      FlowPromise<String> getAsyncPromise();
    }

    AtomicReference<FlowPromise<String>> serverPromiseRef = new AtomicReference<>();
    AtomicReference<EndpointId> clientEndpointRef = new AtomicReference<>();

    AsyncPromiseService impl = () -> {
      CompletableFuture<String> future = new CompletableFuture<>();
      serverPromiseRef.set(future.getPromise());
      return future.getPromise();
    };

    // Register service on a special endpoint
    LocalEndpoint specialEndpoint = LocalEndpoint.localhost(19877);
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, AsyncPromiseService.class, specialEndpoint);

    // Create a second transport that will act as client
    SimulatedFlowTransport clientNetworkTransport = new SimulatedFlowTransport();
    FlowRpcTransportImpl clientTransport = new FlowRpcTransportImpl(clientNetworkTransport);

    EndpointId serviceId = new EndpointId("async-promise-service");
    clientTransport.getEndpointResolver().registerRemoteEndpoint(serviceId, specialEndpoint);

    AsyncPromiseService client = clientTransport.getRpcStub(serviceId, AsyncPromiseService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Get promise from server
      FlowPromise<String> clientPromise = client.getAsyncPromise();

      // Wait for promise to be created on server
      await(Flow.delay(0.1));

      // Close the client transport to simulate network failure
      await(clientTransport.close());

      // Now complete the promise on server side
      // This will try to send result back but connection is gone
      if (serverPromiseRef.get() != null) {
        startActor(() -> {
          await(Flow.delay(0.1));
          serverPromiseRef.get().complete("async result");
          return null;
        });
      }

      // Give time for completion attempt
      await(Flow.delay(0.3));

      // The promise should fail on client side
      try {
        await(clientPromise.getFuture());
        fail("Should have thrown exception");
      } catch (Exception e) {
        // Expected - transport was closed
        assertNotNull(e);
      }

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);

    // Clean up
    CompletableFuture<Void> cleanupFuture = transport.close();
    pumpAndAdvanceTimeUntilDone(cleanupFuture);
  }

  @Test
  public void testHandleErrorMessageDeserialization() {
    // Test handling ERROR message with deserialization issues
    TestServiceImpl impl = new TestServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, TestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      var connection = await(networkTransport.connect(serverEndpoint));

      // First send a valid request to register pending call
      UUID messageId = UUID.randomUUID();
      RpcMessage request = new RpcMessage(
          RpcMessageHeader.MessageType.REQUEST,
          messageId,
          "io.github.panghy.javaflow.rpc.FlowRpcTransportImplInternalCoverageTest$TestService.echo(java.lang.String)",
          null,
          FlowSerialization.serialize(new Object[]{"test"}));

      connection.send(request.serialize());

      // Wait a bit
      await(Flow.delay(0.05));

      // Now send ERROR response with corrupted payload
      ByteBuffer corruptedPayload = ByteBuffer.allocate(50);
      corruptedPayload.put(new byte[50]); // Random bytes that won't deserialize
      corruptedPayload.flip();

      RpcMessage errorMsg = new RpcMessage(
          RpcMessageHeader.MessageType.ERROR,
          messageId,
          null,
          null,
          corruptedPayload);

      connection.send(errorMsg.serialize());

      // Give time to process
      await(Flow.delay(0.1));

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testHandleResponseNumericOverflow() {
    // Test handling RESPONSE with numeric overflow scenarios
    interface NumericOverflowService {
      byte getByteValue();

      short getShortValue();
    }

    // We'll send back values that are too large for the expected types
    NumericOverflowService impl = new NumericOverflowService() {
      @Override
      public byte getByteValue() {
        return 42;
      }

      @Override
      public short getShortValue() {
        return 1234;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, NumericOverflowService.class, serverEndpoint);

    // Create interface that expects wider types
    interface WideNumericService {
      long getByteValue(); // Expects long but server returns byte

      long getShortValue(); // Expects long but server returns short
    }

    EndpointId serviceId = new EndpointId("numeric-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    WideNumericService client = transport.getRpcStub(serviceId, WideNumericService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // These should work with numeric conversion
      assertEquals(42L, client.getByteValue());
      assertEquals(1234L, client.getShortValue());

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  // Interface for numeric conversion tests
  public interface NumericTestService {
    // For testing Long to smaller types
    String processByte(byte value);

    String processShort(short value);

    String processInt(int value);

    String processDouble(double value);

    // For testing Integer conversions
    String processLongFromInt(long value);

    String processDoubleFromInt(double value);

    // For testing Float to Double
    String processDoubleFromFloat(double value);
  }

  @Test
  public void testNumericConversionsComprehensive() {
    // Comprehensive test for all numeric conversions in convertArgumentType method
    NumericTestService impl = new NumericTestService() {
      @Override
      public String processByte(byte value) {
        return "Byte: " + value;
      }

      @Override
      public String processShort(short value) {
        return "Short: " + value;
      }

      @Override
      public String processInt(int value) {
        return "Int: " + value;
      }

      @Override
      public String processDouble(double value) {
        return "Double: " + value;
      }

      @Override
      public String processLongFromInt(long value) {
        return "Long: " + value;
      }

      @Override
      public String processDoubleFromInt(double value) {
        return "DoubleFromInt: " + value;
      }

      @Override
      public String processDoubleFromFloat(double value) {
        return "DoubleFromFloat: " + value;
      }
    };

    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());


    transport.registerServiceAndListen(endpointId, impl, NumericTestService.class, serverEndpoint);

    CompletableFuture<Void> testFuture = startActor(() -> {
      var connection = await(networkTransport.connect(serverEndpoint));

      // Test Long -> Byte conversions (lines 813-837)
      testLongToByteConversions(connection);

      // Test Long -> Short conversions (lines 822-829)
      testLongToShortConversions(connection);

      // Test Long -> Integer conversions (lines 814-821)
      testLongToIntegerConversions(connection);

      // Test Long -> Double conversion (lines 838-841)
      testLongToDoubleConversion(connection);

      // Test Integer -> Long conversion (lines 844-846)
      testIntegerToLongConversion(connection);

      // Test Integer -> Double conversion (lines 847-850)
      testIntegerToDoubleConversion(connection);

      // Test Float -> Double conversion (lines 852-856)
      testFloatToDoubleConversion(connection);

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  private void testLongToByteConversions(io.github.panghy.javaflow.io.network.FlowConnection connection)
      throws Exception {
    String methodId = "io.github.panghy.javaflow.rpc." +
                      "FlowRpcTransportImplInternalCoverageTest$NumericTestService.processByte(byte)";

    // Valid conversions
    testNumericConversion(connection, methodId, 0L, "Byte: 0");
    testNumericConversion(connection, methodId, 42L, "Byte: 42");
    testNumericConversion(connection, methodId, -42L, "Byte: -42");
    testNumericConversion(connection, methodId, (long) Byte.MAX_VALUE, "Byte: 127");
    testNumericConversion(connection, methodId, (long) Byte.MIN_VALUE, "Byte: -128");

    // Overflow cases
    testNumericOverflow(connection, methodId, (long) Byte.MAX_VALUE + 1, "cannot be converted to Byte");
    testNumericOverflow(connection, methodId, (long) Byte.MIN_VALUE - 1, "cannot be converted to Byte");
    testNumericOverflow(connection, methodId, 1000L, "cannot be converted to Byte");
    testNumericOverflow(connection, methodId, -1000L, "cannot be converted to Byte");
  }

  private void testLongToShortConversions(io.github.panghy.javaflow.io.network.FlowConnection connection)
      throws Exception {
    String methodId = "io.github.panghy.javaflow.rpc." +
                      "FlowRpcTransportImplInternalCoverageTest$NumericTestService.processShort(short)";

    // Valid conversions
    testNumericConversion(connection, methodId, 0L, "Short: 0");
    testNumericConversion(connection, methodId, 1000L, "Short: 1000");
    testNumericConversion(connection, methodId, -1000L, "Short: -1000");
    testNumericConversion(connection, methodId, (long) Short.MAX_VALUE, "Short: 32767");
    testNumericConversion(connection, methodId, (long) Short.MIN_VALUE, "Short: -32768");

    // Overflow cases
    testNumericOverflow(connection, methodId, (long) Short.MAX_VALUE + 1, "cannot be converted to Short");
    testNumericOverflow(connection, methodId, (long) Short.MIN_VALUE - 1, "cannot be converted to Short");
    testNumericOverflow(connection, methodId, 100000L, "cannot be converted to Short");
  }

  private void testLongToIntegerConversions(io.github.panghy.javaflow.io.network.FlowConnection connection)
      throws Exception {
    String methodId = "io.github.panghy.javaflow.rpc." +
                      "FlowRpcTransportImplInternalCoverageTest$NumericTestService.processInt(int)";

    // Valid conversions
    testNumericConversion(connection, methodId, 0L, "Int: 0");
    testNumericConversion(connection, methodId, 123456L, "Int: 123456");
    testNumericConversion(connection, methodId, -123456L, "Int: -123456");
    testNumericConversion(connection, methodId, (long) Integer.MAX_VALUE, "Int: 2147483647");
    testNumericConversion(connection, methodId, (long) Integer.MIN_VALUE, "Int: -2147483648");

    // Overflow cases
    testNumericOverflow(connection, methodId, (long) Integer.MAX_VALUE + 1, "cannot be converted to Integer");
    testNumericOverflow(connection, methodId, (long) Integer.MIN_VALUE - 1, "cannot be converted to Integer");
  }

  private void testLongToDoubleConversion(io.github.panghy.javaflow.io.network.FlowConnection connection)
      throws Exception {
    String methodId = "io.github.panghy.javaflow.rpc." +
                      "FlowRpcTransportImplInternalCoverageTest$NumericTestService.processDouble(double)";

    // All Long to Double conversions should work
    testNumericConversion(connection, methodId, 0L, "Double: 0.0");
    testNumericConversion(connection, methodId, 123456789L, "Double: 1.23456789E8");
    testNumericConversion(connection, methodId, -123456789L, "Double: -1.23456789E8");
    testNumericConversion(connection, methodId, Long.MAX_VALUE, "Double: 9.223372036854776E18");
  }

  private void testIntegerToLongConversion(io.github.panghy.javaflow.io.network.FlowConnection connection)
      throws Exception {
    String methodId = "io.github.panghy.javaflow.rpc." +
                      "FlowRpcTransportImplInternalCoverageTest$NumericTestService.processLongFromInt(long)";

    // All Integer to Long conversions should work (widening)
    testNumericConversion(connection, methodId, 0, "Long: 0");
    testNumericConversion(connection, methodId, 42, "Long: 42");
    testNumericConversion(connection, methodId, -42, "Long: -42");
    testNumericConversion(connection, methodId, Integer.MAX_VALUE, "Long: 2147483647");
    testNumericConversion(connection, methodId, Integer.MIN_VALUE, "Long: -2147483648");
  }

  private void testIntegerToDoubleConversion(io.github.panghy.javaflow.io.network.FlowConnection connection)
      throws Exception {
    String methodId = "io.github.panghy.javaflow.rpc." +
                      "FlowRpcTransportImplInternalCoverageTest$NumericTestService.processDoubleFromInt(double)";

    // All Integer to Double conversions should work
    testNumericConversion(connection, methodId, 0, "DoubleFromInt: 0.0");
    testNumericConversion(connection, methodId, 42, "DoubleFromInt: 42.0");
    testNumericConversion(connection, methodId, -42, "DoubleFromInt: -42.0");
    testNumericConversion(connection, methodId, Integer.MAX_VALUE, "DoubleFromInt: 2.147483647E9");
  }

  private void testFloatToDoubleConversion(io.github.panghy.javaflow.io.network.FlowConnection connection)
      throws Exception {
    String methodId = "io.github.panghy.javaflow.rpc." +
                      "FlowRpcTransportImplInternalCoverageTest$NumericTestService.processDoubleFromFloat(double)";

    // All Float to Double conversions should work (widening)
    testNumericConversion(connection, methodId, 0.0f, "DoubleFromFloat: 0.0");
    testNumericConversion(connection, methodId, 3.14f, "DoubleFromFloat: 3.140000104904175");
    testNumericConversion(connection, methodId, -3.14f, "DoubleFromFloat: -3.140000104904175");
    testNumericConversion(connection, methodId, Float.MAX_VALUE, "DoubleFromFloat: 3.4028234663852886E38");
  }

  private void testNumericConversion(io.github.panghy.javaflow.io.network.FlowConnection connection,
                                     String methodId, Object value, String expectedResult) throws Exception {
    UUID messageId = UUID.randomUUID();
    Object[] args = new Object[]{value};
    RpcMessage request = new RpcMessage(
        RpcMessageHeader.MessageType.REQUEST,
        messageId,
        methodId,
        null,
        FlowSerialization.serialize(args));

    connection.send(request.serialize());

    ByteBuffer response = Flow.await(connection.receive(65536));
    RpcMessage responseMsg = RpcMessage.deserialize(response);
    assertEquals(RpcMessageHeader.MessageType.RESPONSE, responseMsg.getHeader().getType());
    String result = (String) FlowSerialization.deserialize(responseMsg.getPayload());
    assertEquals(expectedResult, result);
  }

  private void testNumericOverflow(io.github.panghy.javaflow.io.network.FlowConnection connection,
                                   String methodId, Object value, String expectedError) throws Exception {
    UUID messageId = UUID.randomUUID();
    Object[] args = new Object[]{value};
    RpcMessage request = new RpcMessage(
        RpcMessageHeader.MessageType.REQUEST,
        messageId,
        methodId,
        null,
        FlowSerialization.serialize(args));

    connection.send(request.serialize());

    ByteBuffer response = Flow.await(connection.receive(65536));
    RpcMessage responseMsg = RpcMessage.deserialize(response);
    assertEquals(RpcMessageHeader.MessageType.ERROR, responseMsg.getHeader().getType());
    Exception error = (Exception) FlowSerialization.deserialize(responseMsg.getPayload());
    assertTrue(error.getMessage().contains(expectedError));
  }

}