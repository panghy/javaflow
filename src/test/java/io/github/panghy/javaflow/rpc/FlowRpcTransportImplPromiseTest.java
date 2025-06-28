package io.github.panghy.javaflow.rpc;

import java.util.concurrent.CompletableFuture;import io.github.panghy.javaflow.AbstractFlowTest;
import io.github.panghy.javaflow.Flow;
import io.github.panghy.javaflow.core.FutureStream;
import io.github.panghy.javaflow.core.PromiseStream;
import io.github.panghy.javaflow.io.network.LocalEndpoint;
import io.github.panghy.javaflow.io.network.SimulatedFlowTransport;
import io.github.panghy.javaflow.rpc.serialization.DefaultSerializer;
import io.github.panghy.javaflow.rpc.serialization.FlowSerialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.panghy.javaflow.Flow.await;
import static io.github.panghy.javaflow.Flow.startActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for promise and future handling in {@link FlowRpcTransportImpl}.
 * Tests promise arguments, return values, completion handling, and cancellation.
 */
@Timeout(30)
public class FlowRpcTransportImplPromiseTest extends AbstractFlowTest {

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
  public interface AsyncService {
    FlowPromise<String> delayedPromise(double seconds);

    CompletableFuture<String> delayedFuture(double seconds);

    FlowPromise<String> immediatePromise(String value);

    CompletableFuture<String> immediateFuture(String value);

    FlowPromise<String> failingPromise();

    CompletableFuture<String> failingFuture();
  }

  public interface ComplexPromiseService {
    FlowPromise<List<String>> getListPromise();

    FlowPromise<CustomData> getCustomDataPromise();
  }

  public interface PromiseArgumentService {
    String waitForPromise(FlowPromise<String> promise);

    String waitForFuture(CompletableFuture<String> future);

    FlowPromise<String> transformPromise(FlowPromise<String> input);

    CompletableFuture<String> transformFuture(CompletableFuture<String> input);
  }

  public interface FutureProcessingService {
    CompletableFuture<String> processFlowFuture(CompletableFuture<String> future);
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
  private static class AsyncServiceImpl implements AsyncService {
    @Override
    public FlowPromise<String> delayedPromise(double seconds) {
      CompletableFuture<String> future = new CompletableFuture<>();
      startActor(() -> {
        await(Flow.delay(seconds));
        future.getPromise().complete("Delayed result after " + seconds + " seconds");
        return null;
      });
      return future.getPromise();
    }

    @Override
    public CompletableFuture<String> delayedFuture(double seconds) {
      CompletableFuture<String> future = new CompletableFuture<>();
      startActor(() -> {
        await(Flow.delay(seconds));
        future.getPromise().complete("Delayed future result after " + seconds + " seconds");
        return null;
      });
      return future;
    }

    @Override
    public FlowPromise<String> immediatePromise(String value) {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.getPromise().complete("Immediate: " + value);
      return future.getPromise();
    }

    @Override
    public CompletableFuture<String> immediateFuture(String value) {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.getPromise().complete("Immediate future: " + value);
      return future;
    }

    @Override
    public FlowPromise<String> failingPromise() {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.getPromise().completeExceptionally(new RuntimeException("Promise failed"));
      return future.getPromise();
    }

    @Override
    public CompletableFuture<String> failingFuture() {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.getPromise().completeExceptionally(new RuntimeException("Future failed"));
      return future;
    }
  }

  private static class ComplexPromiseServiceImpl implements ComplexPromiseService {
    @Override
    public FlowPromise<List<String>> getListPromise() {
      CompletableFuture<List<String>> future = new CompletableFuture<>();
      List<String> list = new ArrayList<>();
      list.add("Item 1");
      list.add("Item 2");
      list.add("Item 3");
      future.getPromise().complete(list);
      return future.getPromise();
    }

    @Override
    public FlowPromise<CustomData> getCustomDataPromise() {
      CompletableFuture<CustomData> future = new CompletableFuture<>();
      future.getPromise().complete(new CustomData("Test", 42));
      return future.getPromise();
    }
  }

  private static class PromiseArgumentServiceImpl implements PromiseArgumentService {
    @Override
    public String waitForPromise(FlowPromise<String> promise) {
      try {
        return "Received: " + await(promise.getFuture());
      } catch (Exception e) {
        return "Error: " + e.getMessage();
      }
    }

    @Override
    public String waitForFuture(CompletableFuture<String> future) {
      try {
        return "Received: " + await(future);
      } catch (Exception e) {
        return "Error: " + e.getMessage();
      }
    }

    @Override
    public FlowPromise<String> transformPromise(FlowPromise<String> input) {
      CompletableFuture<String> resultFuture = new CompletableFuture<>();
      input.getFuture().whenComplete((value, error) -> {
        if (error != null) {
          resultFuture.getPromise().completeExceptionally(error);
        } else {
          resultFuture.getPromise().complete("Transformed: " + value);
        }
      });
      return resultFuture.getPromise();
    }

    @Override
    public CompletableFuture<String> transformFuture(CompletableFuture<String> input) {
      CompletableFuture<String> resultFuture = new CompletableFuture<>();
      input.whenComplete((value, error) -> {
        if (error != null) {
          resultFuture.getPromise().completeExceptionally(error);
        } else {
          resultFuture.getPromise().complete("Transformed: " + value);
        }
      });
      return resultFuture;
    }
  }

  private static class FutureProcessingServiceImpl implements FutureProcessingService {
    @Override
    public CompletableFuture<String> processFlowFuture(CompletableFuture<String> future) {
      // The future argument will be replaced with a UUID during RPC serialization
      // Return a completed future with our result
      CompletableFuture<String> result = new CompletableFuture<>();
      result.complete("future-processed");
      return result;
    }
  }

  @Test
  public void testPromiseAsReturnValueAlreadyCompleted() throws Exception {
    // Test returning an already completed FlowPromise
    AsyncServiceImpl impl = new AsyncServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, AsyncService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("async-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    AsyncService client = transport.getRpcStub(serviceId, AsyncService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test immediate promise
      FlowPromise<String> promise = client.immediatePromise("test");
      assertNotNull(promise);
      String result = await(promise.getFuture());
      assertEquals("Immediate: test", result);

      // Test immediate future
      CompletableFuture<String> future = client.immediateFuture("test");
      assertNotNull(future);
      String futureResult = await(future);
      assertEquals("Immediate future: test", futureResult);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testPromiseAsReturnValueDelayed() throws Exception {
    // Test returning a FlowPromise that completes later
    AsyncServiceImpl impl = new AsyncServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, AsyncService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("async-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    AsyncService client = transport.getRpcStub(serviceId, AsyncService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test delayed promise
      FlowPromise<String> promise = client.delayedPromise(0.1);
      assertNotNull(promise);
      String result = await(promise.getFuture());
      assertEquals("Delayed result after 0.1 seconds", result);

      // Test delayed future
      CompletableFuture<String> future = client.delayedFuture(0.1);
      assertNotNull(future);
      String futureResult = await(future);
      assertEquals("Delayed future result after 0.1 seconds", futureResult);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testExceptionalPromiseReturn() throws Exception {
    // Test returning a promise that completes exceptionally
    AsyncServiceImpl impl = new AsyncServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, AsyncService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("async-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    AsyncService client = transport.getRpcStub(serviceId, AsyncService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test failing promise
      FlowPromise<String> promise = client.failingPromise();
      assertNotNull(promise);
      try {
        await(promise.getFuture());
        fail("Should have thrown exception");
      } catch (RuntimeException e) {
        assertEquals("Promise failed", e.getMessage());
      }

      // Test failing future
      CompletableFuture<String> future = client.failingFuture();
      assertNotNull(future);
      try {
        await(future);
        fail("Should have thrown exception");
      } catch (RuntimeException e) {
        assertEquals("Future failed", e.getMessage());
      }
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  // Test removed - required access to private fields

  @Test
  public void testPromiseCompletionLambda() throws Exception {
    // Test the lambda in sendResponse for promise completion handler
    interface AsyncService {
      FlowPromise<String> asyncMethod();
    }

    class AsyncServiceImpl implements AsyncService {
      FlowPromise<String> resultPromise;

      @Override
      public FlowPromise<String> asyncMethod() {
        CompletableFuture<String> future = new CompletableFuture<>();
        resultPromise = future.getPromise();
        // Return promise that will complete later
        return resultPromise;
      }
    }

    AsyncServiceImpl impl = new AsyncServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, AsyncService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("async-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    AsyncService client = transport.getRpcStub(serviceId, AsyncService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Call method to get promise
      FlowPromise<String> clientPromise = client.asyncMethod();

      // Complete the promise on server side
      startActor(() -> {
        await(Flow.delay(0.1));
        impl.resultPromise.complete("Async result");
        return null;
      });

      // Client should receive the result
      String result = await(clientPromise.getFuture());
      assertEquals("Async result", result);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testPromiseCompletionExceptionallyLambda() throws Exception {
    // Test promise completion with error - lambda error path
    interface AsyncService {
      FlowPromise<String> asyncMethod();
    }

    class AsyncServiceImpl implements AsyncService {
      FlowPromise<String> resultPromise;

      @Override
      public FlowPromise<String> asyncMethod() {
        CompletableFuture<String> future = new CompletableFuture<>();
        resultPromise = future.getPromise();
        // Return promise that will complete later
        return resultPromise;
      }
    }

    AsyncServiceImpl impl = new AsyncServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, AsyncService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("async-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    AsyncService client = transport.getRpcStub(serviceId, AsyncService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Call method to get promise
      FlowPromise<String> clientPromise = client.asyncMethod();

      // Complete the promise exceptionally on server side
      startActor(() -> {
        await(Flow.delay(0.1));
        impl.resultPromise.completeExceptionally(new RuntimeException("Async error"));
        return null;
      });

      // Client should receive the error
      try {
        await(clientPromise.getFuture());
        fail("Should have thrown exception");
      } catch (RuntimeException e) {
        assertEquals("Async error", e.getMessage());
      }
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testPromiseAsMethodArgument() {
    // Test passing a promise as a method argument
    PromiseArgumentServiceImpl impl = new PromiseArgumentServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, PromiseArgumentService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("promise-arg-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    PromiseArgumentService client = transport.getRpcStub(serviceId, PromiseArgumentService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test 1: Pass a completed promise
      CompletableFuture<String> completedFuture = new CompletableFuture<>();
      completedFuture.complete("completed value");

      String result1 = client.waitForPromise(completedFuture.getPromise());
      assertEquals("Received: completed value", result1);

      // Test 2: Pass an incomplete promise
      CompletableFuture<String> incompleteFuture = new CompletableFuture<>();

      // Start actor to complete promise after delay
      startActor(() -> {
        await(Flow.delay(0.1));
        incompleteFuture.complete("delayed value");
        return null;
      });

      String result2 = client.waitForPromise(incompleteFuture.getPromise());
      assertEquals("Received: delayed value", result2);

      // Test 3: Transform a promise
      CompletableFuture<String> inputFuture = new CompletableFuture<>();
      inputFuture.complete("input");

      FlowPromise<String> transformed = client.transformPromise(inputFuture.getPromise());
      String transformedResult = await(transformed.getFuture());
      assertEquals("Transformed: input", transformedResult);

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testProcessFlowFutureAsMethodArgument() {
    // Test FlowFuture as method argument
    // This covers lines 1360-1369 in FlowRpcTransportImpl

    // Test an interface that accepts FlowFuture
    FutureProcessingServiceImpl impl = new FutureProcessingServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, FutureProcessingService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("future-processing-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    FutureProcessingService client = transport.getRpcStub(serviceId, FutureProcessingService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test 1: Call method with completed FlowFuture argument
      CompletableFuture<String> completedFuture = new CompletableFuture<>();
      completedFuture.complete("completed-value");

      // This will trigger the FlowFuture processing path in processArguments (lines 1360-1369)
      CompletableFuture<String> result1 = client.processFlowFuture(completedFuture);
      assertEquals("future-processed", await(result1));

      // Test 2: Call method with uncompleted FlowFuture argument
      CompletableFuture<String> uncompletedFuture = new CompletableFuture<>();

      CompletableFuture<String> result2 = client.processFlowFuture(uncompletedFuture);

      // Complete the future after method call
      startActor(() -> {
        await(Flow.delay(0.1));
        uncompletedFuture.complete("delayed-value");
        return null;
      });

      assertEquals("future-processed", await(result2));
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testFutureAsMethodArgument() {
    // Test passing a future as a method argument
    PromiseArgumentServiceImpl impl = new PromiseArgumentServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, PromiseArgumentService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("promise-arg-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    PromiseArgumentService client = transport.getRpcStub(serviceId, PromiseArgumentService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test 1: Pass a completed future
      CompletableFuture<String> completedFuture = new CompletableFuture<>();
      completedFuture.complete("completed value");

      String result1 = client.waitForFuture(completedFuture);
      assertEquals("Received: completed value", result1);

      // Test 2: Transform a future
      CompletableFuture<String> inputFuture = new CompletableFuture<>();
      inputFuture.complete("input");

      CompletableFuture<String> transformed = client.transformFuture(inputFuture);
      String transformedResult = await(transformed);
      assertEquals("Transformed: input", transformedResult);

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testExtractPromiseTypeFromInstance() throws Exception {
    // Test extracting type from FlowPromise with complex generic types
    interface ComplexPromiseService {
      FlowPromise<List<String>> getListPromise();
    }

    ComplexPromiseServiceImpl impl = new ComplexPromiseServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, ComplexPromiseService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("complex-promise-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    ComplexPromiseService client = transport.getRpcStub(serviceId, ComplexPromiseService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test getting list promise
      FlowPromise<List<String>> listPromise = client.getListPromise();
      List<String> result = await(listPromise.getFuture());
      assertEquals(3, result.size());
      assertEquals("Item 1", result.get(0));
      assertEquals("Item 2", result.get(1));
      assertEquals("Item 3", result.get(2));

      // Test getting custom data promise
      FlowPromise<CustomData> dataPromise = impl.getCustomDataPromise();
      CustomData data = await(dataPromise.getFuture());
      assertEquals("Test", data.name);
      assertEquals(42, data.value);
      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }

  @Test
  public void testGenericTypeExtractionFromInterface() throws Exception {
    // Test type extraction from method parameters
    interface GenericService {
      void processPromise(FlowPromise<String> promise);

      void processFuture(CompletableFuture<Integer> future);

      void processStream(PromiseStream<Double> stream);

      void processFutureStream(FutureStream<Boolean> stream);

      void processPlainObject(String obj);
    }

    // Use reflection to test type extraction
    Method processPromiseMethod = GenericService.class.getMethod("processPromise", FlowPromise.class);
    Method processFutureMethod = GenericService.class.getMethod("processFuture", FlowFuture.class);
    Method processStreamMethod = GenericService.class.getMethod("processStream", PromiseStream.class);
    Method processFutureStreamMethod = GenericService.class.getMethod("processFutureStream", FutureStream.class);
    Method processPlainMethod = GenericService.class.getMethod("processPlainObject", String.class);

    // Get the RemoteInvocationHandler for testing
    FlowRpcTransportImpl.RemoteInvocationHandler handler = transport.new RemoteInvocationHandler(
        new EndpointId("test"), null, null, null, FlowRpcConfiguration.defaultConfig());

    // Test extracting types from parameterized types
    Type promiseType = processPromiseMethod.getGenericParameterTypes()[0];
    assertNotNull(promiseType);
    assertTrue(promiseType instanceof ParameterizedType);

    Type futureType = processFutureMethod.getGenericParameterTypes()[0];
    assertNotNull(futureType);
    assertTrue(futureType instanceof ParameterizedType);

    Type streamType = processStreamMethod.getGenericParameterTypes()[0];
    assertNotNull(streamType);
    assertTrue(streamType instanceof ParameterizedType);

    Type futureStreamType = processFutureStreamMethod.getGenericParameterTypes()[0];
    assertNotNull(futureStreamType);
    assertTrue(futureStreamType instanceof ParameterizedType);

    // Plain type should not be parameterized
    Type plainType = processPlainMethod.getGenericParameterTypes()[0];
    assertNotNull(plainType);
    assertFalse(plainType instanceof ParameterizedType);
  }

  // Test removed - required access to private fields

  @Test
  public void testSendCancellationMethod() throws Exception {
    // Test sendCancellation method in MessageSender
    AtomicBoolean cancellationSent = new AtomicBoolean(false);
    AtomicReference<UUID> cancelledPromiseId = new AtomicReference<>();

    // Create a custom message sender to track cancellation
    RemotePromiseTracker.MessageSender messageSender = new RemotePromiseTracker.MessageSender() {
      @Override
      public <T> void sendResult(io.github.panghy.javaflow.io.network.Endpoint destination, UUID promiseId, T result) {
      }

      @Override
      public void sendError(io.github.panghy.javaflow.io.network.Endpoint destination, UUID promiseId,
                            Throwable error) {
        if (error instanceof IllegalStateException && error.getMessage().equals("Promise was cancelled")) {
          cancellationSent.set(true);
          cancelledPromiseId.set(promiseId);
        }
      }

      @Override
      public void sendCancellation(io.github.panghy.javaflow.io.network.Endpoint source, UUID promiseId) {
        sendError(source, promiseId, new IllegalStateException("Promise was cancelled"));
      }

      @Override
      public <T> void sendStreamValue(io.github.panghy.javaflow.io.network.Endpoint destination,
                                      UUID streamId, T value) {
      }

      @Override
      public void sendStreamClose(io.github.panghy.javaflow.io.network.Endpoint destination, UUID streamId) {
      }

      @Override
      public void sendStreamError(io.github.panghy.javaflow.io.network.Endpoint destination, UUID streamId,
                                  Throwable error) {
      }
    };

    RemotePromiseTracker tracker = new RemotePromiseTracker(messageSender);
    UUID promiseId = UUID.randomUUID();

    // Simulate sending cancellation
    tracker.sendCancellationToEndpoint(serverEndpoint, promiseId);

    assertTrue(cancellationSent.get());
    assertEquals(promiseId, cancelledPromiseId.get());
  }

  @Test
  public void testNestedFutureHandling() {
    // Test handling of nested FlowFuture from mapResponse
    AsyncServiceImpl impl = new AsyncServiceImpl();
    EndpointId endpointId = new EndpointId("test-service-" + System.nanoTime());

    transport.registerServiceAndListen(endpointId, impl, AsyncService.class, serverEndpoint);

    EndpointId serviceId = new EndpointId("async-service");
    transport.getEndpointResolver().registerRemoteEndpoint(serviceId, serverEndpoint);

    AsyncService client = transport.getRpcStub(serviceId, AsyncService.class);

    CompletableFuture<Void> testFuture = startActor(() -> {
      // Test immediate future (which should flatten properly)
      CompletableFuture<String> future = client.immediateFuture("test");
      String result = await(future);
      assertEquals("Immediate future: test", result);

      // Test delayed future
      CompletableFuture<String> delayedFuture = client.delayedFuture(0.1);
      String delayedResult = await(delayedFuture);
      assertEquals("Delayed future result after 0.1 seconds", delayedResult);

      return null;
    });

    pumpAndAdvanceTimeUntilDone(testFuture);
  }
}