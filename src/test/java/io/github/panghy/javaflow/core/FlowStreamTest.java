package io.github.panghy.javaflow.core;

import io.github.panghy.javaflow.AbstractFlowTest;
import org.junit.jupiter.api.Test;

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
    
    FlowFuture<Void> onCloseFuture = stream.onClose();
    assertNotNull(onCloseFuture);
    // The default implementation just returns a new FlowFuture that won't complete
    assertTrue(!onCloseFuture.isDone());
  }

  /**
   * Test implementation of FlowStream for testing the interface methods.
   */
  private static class TestFlowStream<T> implements FlowStream<T> {
    
    @Override
    public FlowFuture<T> nextAsync() {
      return new FlowFuture<>();
    }

    @Override
    public FlowFuture<Boolean> hasNextAsync() {
      return new FlowFuture<>();
    }

    @Override
    public FlowFuture<Void> closeExceptionally(Throwable exception) {
      return new FlowFuture<>();
    }

    @Override
    public FlowFuture<Void> close() {
      return new FlowFuture<>();
    }

    @Override
    public boolean isClosed() {
      return false;
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
    public FlowFuture<Void> forEach(Consumer<? super T> action) {
      return new FlowFuture<>();
    }
  }
}