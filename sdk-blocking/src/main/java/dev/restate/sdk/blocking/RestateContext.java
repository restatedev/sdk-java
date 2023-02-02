package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import dev.restate.generated.core.AwakeableIdentifier;
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

  RestateInstanceStorage storage();

  RestateServices services();

  RestateSynchronization sync();

  // TODO RestatePubSub pubSub();

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
}
