package dev.restate.sdk.blocking;

import dev.restate.sdk.core.*;
import dev.restate.sdk.core.function.ThrowingRunnable;
import dev.restate.sdk.core.function.ThrowingSupplier;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.Optional;
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
 * <p>All methods of this interface, and related interfaces, throws either {@link TerminalException}
 * or {@link AbortedExecutionException}, where the former can be caught and acted upon, while the
 * latter MUST NOT be caught, but simply propagated for clean up purposes.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 */
@NotThreadSafe
public interface RestateContext {

  /**
   * Gets the state stored under key, deserializing the raw value using the {@link Serde} in the
   * {@link StateKey}.
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
   * Sets the given value under the given key, serializing the value using the {@link Serde} in the
   * {@link StateKey}.
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
  <T, R> Awaitable<R> call(MethodDescriptor<T, R> methodDescriptor, T parameter);

  /**
   * Create a {@link Channel} to use with generated blocking stubs to invoke other Restate services.
   *
   * <p>The returned {@link Channel} will execute the requests using the {@link
   * #call(MethodDescriptor, Object)} method.
   *
   * <p>Please note that errors will be propagated as {@link TerminalException} and not as {@link
   * io.grpc.StatusRuntimeException}.
   *
   * @return a {@link Channel} to send requests through Restate.
   */
  default Channel grpcChannel() {
    return new GrpcChannelAdapter(this);
  }

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   *     generated `*Grpc` class.
   * @param parameter the invocation request parameter.
   */
  <T> void oneWayCall(MethodDescriptor<T, ?> methodDescriptor, T parameter);

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
  <T> void delayedCall(MethodDescriptor<T, ?> methodDescriptor, T parameter, Duration delay);

  /**
   * Execute a non-deterministic closure, recording the result value in the journal. The result
   * value will be re-played in case of re-invocation (e.g. because of failure recovery or
   * suspension point) without re-executing the closure. Use this feature if you want to perform
   * <b>non-deterministic operations</b>.
   *
   * <p>The closure should tolerate retries, that is Restate might re-execute the closure multiple
   * times until it records a result.
   *
   * <h2>Error handling</h2>
   *
   * Errors occurring within this closure won't be propagated to the caller, unless they are {@link
   * TerminalException}. Consider the following code:
   *
   * <pre>{@code
   * // Bad usage of try-catch outside the side effect
   * try {
   *     ctx.sideEffect(() -> {
   *         throw new IllegalStateException();
   *     });
   * } catch (IllegalStateException e) {
   *     // This will never be executed,
   *     // but the error will be retried by Restate,
   *     // following the invocation retry policy.
   * }
   *
   * // Good usage of try-catch outside the side effect
   * try {
   *     ctx.sideEffect(() -> {
   *         throw new TerminalException("my error");
   *     });
   * } catch (TerminalException e) {
   *     // This is invoked
   * }
   * }</pre>
   *
   * To propagate side effects failures to the side effect call-site, make sure to wrap them in
   * {@link TerminalException}.
   *
   * @param serde the type tag of the return value, used to serialize/deserialize it.
   * @param action to execute for its side effects.
   * @param <T> type of the return value.
   * @return value of the side effect operation.
   */
  <T> T sideEffect(Serde<T> serde, ThrowingSupplier<T> action) throws TerminalException;

  /** Like {@link #sideEffect(Serde, ThrowingSupplier)}, but without returning a value. */
  default void sideEffect(ThrowingRunnable runnable) throws TerminalException {
    sideEffect(
        CoreSerdes.VOID,
        () -> {
          runnable.run();
          return null;
        });
  }

  /**
   * Create an {@link Awakeable}, addressable through {@link Awakeable#id()}.
   *
   * <p>You can use this feature to implement external asynchronous systems interactions, for
   * example you can send a Kafka record including the {@link Awakeable#id()}, and then let another
   * service consume from Kafka the responses of given external system interaction by using {@link
   * #awakeableHandle(String)}.
   *
   * @param serde the response type tag to use for deserializing the {@link Awakeable} result.
   * @return the {@link Awakeable} to await on.
   * @see Awakeable
   */
  <T> Awakeable<T> awakeable(Serde<T> serde);

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(Serde, Object)} or {@link AwakeableHandle#reject(String)} the linked
   * {@link Awakeable}.
   *
   * @see Awakeable
   */
  AwakeableHandle awakeableHandle(String id);

  /**
   * Build a RestateContext from the underlying {@link Syscalls} object.
   *
   * <p>This method is used by code-generation, you should not use it directly but rather use {@link
   * RestateBlockingService#restateContext()}.
   */
  static RestateContext fromSyscalls(Syscalls syscalls) {
    return new RestateContextImpl(syscalls);
  }
}
