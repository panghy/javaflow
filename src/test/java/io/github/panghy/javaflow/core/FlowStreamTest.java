package io.github.panghy.javaflow.core;

import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FlowStream} interface.
 */
public class FlowStreamTest extends AbstractFlowTest {

  @Test
  public void testOnCloseDefaultImplementation() {
    // Test the default onClose() method implementation
    FlowStream<String> stream = new TestFlowStream<>();
    
    CompletableFuture<Void> onCloseFuture = stream.onClose();
    assertNotNull(onCloseFuture);
    // The default implementation just returns a new FlowFuture that won't complete
    assertTrue(!onCloseFuture.isDone());
  }

  /**
   * Test implementation of FlowStream for testing the interface methods.
   */
  private static class TestFlowStream<T> implements FlowStream<T> {
    
    @Override
    public CompletableFuture<T> nextAsync() {
      return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Boolean> hasNextAsync() {
      return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Void> closeExceptionally(Throwable exception) {
      return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Void> close() {
      return new CompletableFuture<>();
    }

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public CompletableFuture<Void> onClose() {
      return new CompletableFuture<>();
    }

    @Override
    public <R> FlowStream<R> map(Function<? super T, ? extends R> mapper) {
      return new TestFlowStream<>();
    }

    @Override
    public FlowStream<T> filter(Predicate<? super T> predicate) {
      return this;
    }

    @Override
    public CompletableFuture<Void> forEach(Consumer<? super T> action) {
      return new CompletableFuture<>();
    }
  }
}