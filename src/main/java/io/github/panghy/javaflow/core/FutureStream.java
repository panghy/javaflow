package io.github.panghy.javaflow.core;

/**
 * The consumer side of a stream in the JavaFlow actor system.
 * This interface extends {@link FlowStream} to provide methods for consuming
 * values from a stream.
 *
 * <p>FutureStream is the consumer counterpart to {@link PromiseStream}, allowing
 * values to be consumed as they are sent to the stream. This separation of concerns
 * enables a clean producer-consumer pattern where the consumer only has access to
 * the read operations, while the producer holds the write capabilities.</p>
 *
 * @param <T> The type of value provided by this stream
 */
public interface FutureStream<T> extends FlowStream<T> {
  // This interface inherits all methods from FlowStream
  // It serves as a type marker for consumer-side stream operations
}