package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.Serde;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * This interface exposes the Restate functionalities to Restate services. It can be used to access
 * the service instance key-value state storage, interact with other Restate services, record side
 * effects, execute timers and synchronize with external systems.
 *
 * <p>To use it within your Restate service, implement {@link RestateBlockingService} and get an
 * instance with {@link RestateBlockingService#restateContext()}.
 *
 * <p>NOTE: This interface should never be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 */
@NotThreadSafe
public interface RestateContext {

  /**
   * Gets the state stored under key, deserializing the raw value using the registered {@link Serde}
   * in the interceptor.
   *
   * @param key identifying the state to get and its type.
   * @return an {@link Optional} containing the stored state deserialized or an empty {@link
   *     Optional} if not set yet.
   * @throws RuntimeException when the state cannot be deserialized.
   */
  <T> Optional<T> get(StateKey<T> key);

  /**
   * Clears the state stored under key.
   *
   * @param key identifying the state to clear.
   */
  void clear(StateKey<?> key);

  /**
   * Sets the given value under the given key, serializing the value using the registered {@link
   * Serde} in the interceptor.
   *
   * @param key identifying the value to store and its type.
   * @param value to store under the given key. MUST NOT be null.
   */
  <T> void set(StateKey<T> key, @Nonnull T value);

  /**
   * Causes the current execution of the function invocation to sleep for the given duration.
   *
   * @param duration for which to sleep.
   */
  default void sleep(Duration duration) {
    timer(duration).await();
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link Awaitable#await()}.
   *
   * @param duration for which to sleep.
   */
  Awaitable<Void> timer(Duration duration);

  /**
   * Invoke another Restate service method.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   *     generated `*Grpc` class.
   * @param parameter the invocation request parameter.
   * @return an {@link Awaitable} that wraps the Restate service method result.
   */
  <T extends MessageLite, R extends MessageLite> Awaitable<R> call(
      MethodDescriptor<T, R> methodDescriptor, T parameter);

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   *     generated `*Grpc` class.
   * @param parameter the invocation request parameter.
   */
  <T extends MessageLite> void oneWayCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor, T parameter);

  /**
   * Invoke another Restate service without waiting for the response after the provided {@code
   * delay} has elapsed.
   *
   * <p>This method returns immediately, as the timer is executed and awaited on Restate.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   *     generated {@code *Grpc} class.
   * @param parameter the invocation request parameter.
   * @param delay time to wait before executing the call.
   */
  <T extends MessageLite> void delayedCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor, T parameter, Duration delay);

  /** Shorthand for {@link #sideEffect(TypeTag, Supplier)}. */
  default <T> T sideEffect(Class<T> clazz, Supplier<T> action) {
    return sideEffect(TypeTag.ofClass(clazz), action);
  }

  /**
   * Registers side effects that will be re-played in case of re-invocation (e.g. because of failure
   * recovery or suspension point).
   *
   * <p>Use this function if you want to perform non-deterministic operations.
   *
   * @param typeTag the type tag of the return value, used to serialize/deserialize it.
   * @param action to execute for its side effects.
   * @param <T> type of the return value.
   * @return value of the side effect operation.
   */
  <T> T sideEffect(TypeTag<T> typeTag, Supplier<T> action);

  /** Like {@link #sideEffect(TypeTag, Supplier)}, but without returning a value. */
  default void sideEffect(Runnable runnable) {
    sideEffect(
        TypeTag.VOID,
        () -> {
          runnable.run();
          return null;
        });
  }

  /** Shorthand for {@link #awakeable(TypeTag)} */
  default <T> Awakeable<T> awakeable(Class<T> type) {
    return awakeable(TypeTag.ofClass(type));
  }

  /**
   * Create an {@link Awakeable}, addressable through {@link Awakeable#id()}.
   *
   * <p>You can use this feature to implement external asynchronous systems interactions, for
   * example you can send a Kafka record including the {@link Awakeable#id()}, and then let another
   * service consume from Kafka the responses of given external system interaction by using {@link
   * #awakeableHandle(String)}.
   *
   * @param typeTag the response type tag to use for deserializing the {@link Awakeable} result.
   * @return the {@link Awakeable} to await on.
   * @see Awakeable
   */
  <T> Awakeable<T> awakeable(TypeTag<T> typeTag);

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(TypeTag, Object)} or {@link AwakeableHandle#reject(String)} the linked
   * {@link Awakeable}.
   *
   * @see Awakeable
   */
  AwakeableHandle awakeableHandle(String id);
}
