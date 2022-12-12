package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface RestateContext {

  static RestateContext current() {
    return new RestateContextImpl(Syscalls.current());
  }

  /**
   * Gets the state stored under key, deserializing the raw value using the registered {@link Serde}
   * in the interceptor.
   *
   * @param key identifying the state to get and its type
   * @return an {@link Optional} containing the stored state deserialized or an empty {@link
   *     Optional} if not set yet
   * @throws RuntimeException when the state cannot be deserialized
   */
  <T> Optional<T> get(StateKey<T> key);

  /**
   * Clears the state stored under key.
   *
   * @param key identifying the state to clear
   */
  void clear(StateKey<?> key);

  /**
   * Sets the given value under the given key, serializing the value using the registered {@link
   * Serde} in the interceptor.
   *
   * @param key identifying the value to store and its type
   * @param value to store under the given key
   */
  <T> void set(StateKey<T> key, T value);

  /**
   * Causes the current execution of the function invocation to sleep for the given duration.
   *
   * @param duration for which to sleep
   */
  default void sleep(Duration duration) {
    timer(duration).await();
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link Awaitable#await()}.
   *
   * @param duration for which to sleep
   */
  Awaitable<Void> timer(Duration duration);

  /**
   * Invoke another Restate service method.
   *
   * @return an {@link Awaitable} that wraps the Restate service method result
   */
  <T extends MessageLite, R extends MessageLite> Awaitable<R> call(
      MethodDescriptor<T, R> methodDescriptor, T parameter);

  /** Invoke another Restate service in a fire and forget fashion. */
  <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor, T parameter);

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
   * @param typeTag the type tag of the return value
   * @param action to execute for its side effects
   * @param <T> type of the return value
   * @return value of the side effect operation
   */
  <T> T sideEffect(TypeTag<T> typeTag, Supplier<T> action);

  /** Like {@link #sideEffect(TypeTag, Supplier)}, but without returning a value. */
  default void sideEffect(Runnable runnable) {
    sideEffect(
        Void.class,
        () -> {
          runnable.run();
          return null;
        });
  }

  /** Shorthand for {@link #callback(TypeTag, Consumer)} */
  default <T> Awaitable<T> callback(Class<T> type, Consumer<CallbackIdentifier> caller) {
    return callback(TypeTag.ofClass(type), caller);
  }

  /**
   * This function executes the provided {@code caller} function and suspends this function once
   * {@link Awaitable#await()} is invoked. Another function can then resume this function again by
   * using {@link #completeCallback(CallbackIdentifier, TypeTag, Object)}.
   *
   * <p>You can use this feature to implement external asynchronous systems interactions, for
   * example you can send a Kafka record within the {@code caller}, by including in the record the
   * serialized {@link CallbackIdentifier}, and then let another service consume from Kafka
   * responses of this external system interaction by using {@link
   * #completeCallback(CallbackIdentifier, TypeTag, Object)}.
   *
   * @param typeTag the response type tag to use for deserializing
   * @param caller the function executing the external system interaction
   * @return the result value of the external system interaction, that is the value the wake up-per
   *     passes to {@link #completeCallback(CallbackIdentifier, TypeTag, Object)}
   * @throws StatusRuntimeException if the {@code caller} closure fails. The exception will never
   *     contain the cause, as it's not persisted in the journal, so it can't be deterministically
   *     reproduced.
   */
  <T> Awaitable<T> callback(TypeTag<T> typeTag, Consumer<CallbackIdentifier> caller)
      throws StatusRuntimeException;

  /**
   * Complete the suspended function waiting on the callback identified by the provided {@link
   * CallbackIdentifier}.
   *
   * @param id the id to identify the callback
   * @param payload the payload of the response. This can be either {@code byte[]}, {@link
   *     com.google.protobuf.ByteString}, or any object, which will be serialized by using the
   *     configured {@link Serde}
   * @see #callback(Class, Consumer)
   */
  <T> void completeCallback(CallbackIdentifier id, TypeTag<T> typeTag, T payload);
}
