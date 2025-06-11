package io.github.panghy.javaflow.rpc;

import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for stream handling in {@link FlowRpcTransportImpl}.
 * Tests stream arguments, return values, data transmission, and error handling.
 */
@Timeout(30)
public class FlowRpcTransportImplStreamTest extends AbstractFlowTest {

  private static final Logger LOGGER = Logger.getLogger(FlowRpcTransportImplStreamTest.class.getName());

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
  public interface StreamService {
    PromiseStream<String> getStringStream();
    PromiseStream<Integer> getNumberStream();
    PromiseStream<String> getEmptyStream();
    PromiseStream<String> getErrorStream();
    PromiseStream<String> getDelayedStream();
    
    String processStream(PromiseStream<String> stream);
    FutureStream<String> processFutureStream(FutureStream<String> stream);
    void consumeStream(PromiseStream<String> stream);
  }

  public interface ComplexStreamService {
    PromiseStream<List<String>> getListStream();
    PromiseStream<CustomData> getCustomDataStream();
  }

  public static class CustomData {
    public String name;
    public int value;

    public CustomData() {
    }

    public CustomData(String name, int value) {
      this.name = name;
      this.value = value;
    }
  }

  // Test implementations
  private static class StreamServiceImpl implements StreamService {
    @Override
    public PromiseStream<String> getStringStream() {
      PromiseStream<String> stream = new PromiseStream<>();
      stream.send("Value 1");
      stream.send("Value 2");
      stream.send("Value 3");
      stream.close();
      return stream;
    }

    @Override
    public PromiseStream<Integer> getNumberStream() {
      PromiseStream<Integer> stream = new PromiseStream<>();
      for (int i = 1; i <= 5; i++) {
        stream.send(i);
      }
      stream.close();
      return stream;
    }

    @Override
    public PromiseStream<String> getEmptyStream() {
      PromiseStream<String> stream = new PromiseStream<>();
      stream.close();
      return stream;
    }

    @Override
    public PromiseStream<String> getErrorStream() {
      PromiseStream<String> stream = new PromiseStream<>();
      stream.send("Before error");
      stream.closeExceptionally(new RuntimeException("Stream error"));
      return stream;
    }

    @Override
    public PromiseStream<String> getDelayedStream() {
      PromiseStream<String> stream = new PromiseStream<>();
      startActor(() -> {
        for (int i = 1; i <= 3; i++) {
          await(Flow.delay(0.1));
          stream.send("Delayed " + i);
        }
        stream.close();
        return null;
      });
      return stream;
    }

    @Override
    public String processStream(PromiseStream<String> stream) {
      List<String> values = new ArrayList<>();
      try {
        stream.getFutureStream().forEach(values::add);
        return "Received: " + String.join(", ", values);
      } catch (Exception e) {
        return "Error: " + e.getMessage();
      }
    }

    @Override
    public FutureStream<String> processFutureStream(FutureStream<String> stream) {
      PromiseStream<String> result = new PromiseStream<>();
      stream.forEach(value -> result.send("Processed: " + value));
      stream.onClose().whenComplete((v, error) -> {
        if (error != null) {
          result.closeExceptionally(error);
        } else {
          result.close();
        }
      });
      return result.getFutureStream();
    }

    @Override
    public void consumeStream(PromiseStream<String> stream) {
      // Just consume the stream
      stream.getFutureStream().forEach(value -> {
        // Do nothing, just consume
      });
    }
  }

  private static class ComplexStreamServiceImpl implements ComplexStreamService {
    @Override
    public PromiseStream<List<String>> getListStream() {
      PromiseStream<List<String>> stream = new PromiseStream<>();
      
      List<String> list1 = new ArrayList<>();
      list1.add("A");
      list1.add("B");
      stream.send(list1);
      
      List<String> list2 = new ArrayList<>();
      list2.add("C");
      list2.add("D");
      list2.add("E");
      stream.send(list2);
      
      stream.close();
      return stream;
    }

    @Override
    public PromiseStream<CustomData> getCustomDataStream() {
      PromiseStream<CustomData> stream = new PromiseStream<>();
      stream.send(new CustomData("First", 1));
      stream.send(new CustomData("Second", 2));
      stream.close();
      return stream;
    }
  }

  @Test
  public void testStreamOperations() {
    // Test stream data and close messages
    StreamServiceImpl impl = new StreamServiceImpl();
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamService client = transport.getRpcStub(serviceId, StreamService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test string stream
      PromiseStream<String> stringStream = client.getStringStream();
      List<String> stringValues = new ArrayList<>();
      stringStream.getFutureStream().forEach(stringValues::add);
      
      // Wait for stream to close
      await(stringStream.getFutureStream().onClose());
      
      assertEquals(3, stringValues.size());
      assertEquals("Value 1", stringValues.get(0));
      assertEquals("Value 2", stringValues.get(1));
      assertEquals("Value 3", stringValues.get(2));

      // Test number stream
      PromiseStream<Integer> numberStream = client.getNumberStream();
      List<Integer> numberValues = new ArrayList<>();
      numberStream.getFutureStream().forEach(numberValues::add);
      
      await(numberStream.getFutureStream().onClose());
      
      assertEquals(5, numberValues.size());
      for (int i = 0; i < 5; i++) {
        assertEquals(i + 1, numberValues.get(i));
      }

      // Test empty stream
      PromiseStream<String> emptyStream = client.getEmptyStream();
      List<String> emptyValues = new ArrayList<>();
      emptyStream.getFutureStream().forEach(emptyValues::add);
      
      await(emptyStream.getFutureStream().onClose());
      
      assertEquals(0, emptyValues.size());

      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testStreamWithError() {
    // Test stream that ends with error
    StreamServiceImpl impl = new StreamServiceImpl();
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamService client = transport.getRpcStub(serviceId, StreamService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      PromiseStream<String> errorStream = client.getErrorStream();
      List<String> values = new ArrayList<>();
      AtomicBoolean errorReceived = new AtomicBoolean(false);
      
      errorStream.getFutureStream().forEach(values::add);
      
      errorStream.getFutureStream().onClose().whenComplete((v, error) -> {
        if (error != null) {
          errorReceived.set(true);
          assertEquals("Stream error", error.getMessage());
        }
      });
      
      // Wait for stream to complete
      try {
        await(errorStream.getFutureStream().onClose());
        fail("Should have thrown exception");
      } catch (RuntimeException e) {
        assertEquals("Stream error", e.getMessage());
      }
      
      // Should have received one value before error
      assertEquals(1, values.size());
      assertEquals("Before error", values.get(0));
      assertTrue(errorReceived.get());

      return null;
    });

    pumpUntilDone(testFuture);
  }

  // Test removed - required access to private fields

  // Test removed - required access to private fields

  @Test
  public void testPromiseStreamAsMethodArgument() {
    // Test passing a PromiseStream as method argument
    StreamServiceImpl impl = new StreamServiceImpl();
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamService client = transport.getRpcStub(serviceId, StreamService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Create a stream to pass as argument
      PromiseStream<String> inputStream = new PromiseStream<>();
      inputStream.send("Hello");
      inputStream.send("World");
      inputStream.close();
      
      // Pass the stream to the service
      String result = client.processStream(inputStream);
      assertEquals("Received: Hello, World", result);

      // Test with empty stream
      PromiseStream<String> emptyStream = new PromiseStream<>();
      emptyStream.close();
      
      String emptyResult = client.processStream(emptyStream);
      assertEquals("Received: ", emptyResult);

      // Test consuming a stream
      PromiseStream<String> consumeStream = new PromiseStream<>();
      consumeStream.send("Test");
      consumeStream.close();
      
      client.consumeStream(consumeStream);
      // No assertion needed, just verify it doesn't throw

      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testFutureStreamAsMethodArgument() {
    // Test passing a FutureStream as method argument
    StreamServiceImpl impl = new StreamServiceImpl();
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamService client = transport.getRpcStub(serviceId, StreamService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Create a stream to pass as argument
      PromiseStream<String> promiseStream = new PromiseStream<>();
      promiseStream.send("Input1");
      promiseStream.send("Input2");
      promiseStream.close();
      
      FutureStream<String> inputStream = promiseStream.getFutureStream();
      
      // Pass the FutureStream to the service
      FutureStream<String> resultStream = client.processFutureStream(inputStream);
      
      List<String> results = new ArrayList<>();
      resultStream.forEach(results::add);
      
      await(resultStream.onClose());
      
      assertEquals(2, results.size());
      assertEquals("Processed: Input1", results.get(0));
      assertEquals("Processed: Input2", results.get(1));

      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testComplexStreamTypes() {
    // Test streams with complex generic types
    ComplexStreamServiceImpl impl = new ComplexStreamServiceImpl();
    transport.registerServiceAndListen(impl, ComplexStreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("complex-stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ComplexStreamService client = transport.getRpcStub(serviceId, ComplexStreamService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Test list stream
      PromiseStream<List<String>> listStream = client.getListStream();
      List<List<String>> lists = new ArrayList<>();
      listStream.getFutureStream().forEach(lists::add);
      
      await(listStream.getFutureStream().onClose());
      
      assertEquals(2, lists.size());
      assertEquals(2, lists.get(0).size());
      assertEquals("A", lists.get(0).get(0));
      assertEquals("B", lists.get(0).get(1));
      assertEquals(3, lists.get(1).size());
      assertEquals("C", lists.get(1).get(0));
      assertEquals("D", lists.get(1).get(1));
      assertEquals("E", lists.get(1).get(2));

      // Test custom data stream
      PromiseStream<CustomData> dataStream = client.getCustomDataStream();
      List<CustomData> dataList = new ArrayList<>();
      dataStream.getFutureStream().forEach(dataList::add);
      
      await(dataStream.getFutureStream().onClose());
      
      assertEquals(2, dataList.size());
      assertEquals("First", dataList.get(0).name);
      assertEquals(1, dataList.get(0).value);
      assertEquals("Second", dataList.get(1).name);
      assertEquals(2, dataList.get(1).value);

      return null;
    });

    pumpUntilDone(testFuture);
  }

  @Test
  public void testDelayedStreamValues() {
    // Test stream with delayed values
    StreamServiceImpl impl = new StreamServiceImpl();
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamService client = transport.getRpcStub(serviceId, StreamService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      PromiseStream<String> delayedStream = client.getDelayedStream();
      List<String> values = new ArrayList<>();
      AtomicInteger valueCount = new AtomicInteger(0);
      
      delayedStream.getFutureStream().forEach(value -> {
        values.add(value);
        valueCount.incrementAndGet();
      });
      
      // Wait for stream to complete
      await(delayedStream.getFutureStream().onClose());
      
      assertEquals(3, values.size());
      assertEquals("Delayed 1", values.get(0));
      assertEquals("Delayed 2", values.get(1));
      assertEquals("Delayed 3", values.get(2));
      assertEquals(3, valueCount.get());

      return null;
    });

    pumpUntilDone(testFuture);
  }

  // Test removed - required access to private fields

  @Test
  public void testMultipleStreamsInParallel() {
    // Test multiple streams being transmitted in parallel
    StreamServiceImpl impl = new StreamServiceImpl();
    transport.registerServiceAndListen(impl, StreamService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("stream-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    StreamService client = transport.getRpcStub(serviceId, StreamService.class);

    FlowFuture<Void> testFuture = startActor(() -> {
      // Get multiple streams simultaneously
      PromiseStream<String> stream1 = client.getStringStream();
      PromiseStream<Integer> stream2 = client.getNumberStream();
      PromiseStream<String> stream3 = client.getDelayedStream();
      
      List<String> values1 = new ArrayList<>();
      List<Integer> values2 = new ArrayList<>();
      List<String> values3 = new ArrayList<>();
      
      stream1.getFutureStream().forEach(values1::add);
      stream2.getFutureStream().forEach(values2::add);
      stream3.getFutureStream().forEach(values3::add);
      
      // Wait for all streams to complete
      await(stream1.getFutureStream().onClose());
      await(stream2.getFutureStream().onClose());
      await(stream3.getFutureStream().onClose());
      
      // Verify all streams received correct values
      assertEquals(3, values1.size());
      assertEquals(5, values2.size());
      assertEquals(3, values3.size());

      return null;
    });

    pumpUntilDone(testFuture);
  }
}