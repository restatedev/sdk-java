// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.*;
import dev.restate.sdk.common.function.ThrowingRunnable;
import dev.restate.sdk.common.function.ThrowingSupplier;
import dev.restate.sdk.common.syscalls.Syscalls;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * This interface exposes the Restate functionalities to Restate services. It can be used to
 * interact with other Restate services, record side effects, execute timers and synchronize with
 * external systems.
 *
 * <p>All methods of this interface, and related interfaces, throws either {@link TerminalException}
 * or {@link AbortedExecutionException}, where the former can be caught and acted upon, while the
 * latter MUST NOT be caught, but simply propagated for clean up purposes.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 */
@NotThreadSafe
public interface Context {

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
   * Invoke another Restate service method.
   *
   * @param address the address of the callee
   * @param inputSerde Input serde
   * @param outputSerde Output serde
   * @param parameter the invocation request parameter.
   * @return an {@link Awaitable} that wraps the Restate service method result.
   */
  <T, R> Awaitable<R> call(Address address, Serde<T> inputSerde, Serde<R> outputSerde, T parameter);

  /** Like {@link #call(Address, Serde, Serde, Object)} with raw input/output. */
  default Awaitable<byte[]> call(Address address, byte[] parameter) {
    return call(address, CoreSerdes.RAW, CoreSerdes.RAW, parameter);
  }

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
   * @param address the address of the callee
   * @param inputSerde Input serde
   * @param parameter the invocation request parameter.
   */
  <T> void oneWayCall(Address address, Serde<T> inputSerde, T parameter);

  /** Like {@link #oneWayCall(Address, Serde, Object)} with raw input. */
  default void oneWayCall(Address address, byte[] parameter) {
    oneWayCall(address, CoreSerdes.RAW, parameter);
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
   * @param address the address of the callee
   * @param inputSerde Input serde
   * @param parameter the invocation request parameter.
   * @param delay time to wait before executing the call.
   */
  <T> void delayedCall(Address address, Serde<T> inputSerde, T parameter, Duration delay);

  /** Like {@link #delayedCall(Address, Serde, Object, Duration)} with raw input. */
  default void delayedCall(Address address, byte[] parameter, Duration delay) {
    delayedCall(address, CoreSerdes.RAW, parameter, delay);
  }

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
   * @see RestateRandom
   */
  RestateRandom random();

  /**
   * Create a {@link KeyedContext}. This will look up the thread-local/async-context storage for the
   * underlying context implementation, so make sure to call it always from the same context where
   * the service is executed.
   */
  static Context current() {
    return fromSyscalls(Syscalls.current());
  }

  /** Build a RestateContext from the underlying {@link Syscalls} object. */
  static Context fromSyscalls(Syscalls syscalls) {
    return new ContextImpl(syscalls);
  }
}
