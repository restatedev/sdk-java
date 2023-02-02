package dev.restate.sdk.blocking;

import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An {@link Awakeable} is a special type of {@link Awaitable} which can be arbitrarily completed by
 * another service, by addressing it with its {@link #id()}.
 *
 * <p>It can be used to let a service wait on a specific condition/result, which is fulfilled by
 * another service or by an external system at a later point in time.
 *
 * <p>For example you can send a Kafka record including the {@link Awakeable#id()}, and let another
 * service consume the responses of given external system interaction from another Kafka topic.
 * These responses can then be used to complete this {@link Awakeable} instance by using {@link
 * RestateContext#completeAwakeable(AwakeableIdentifier, TypeTag, Object)}.
 */
@NotThreadSafe
public final class Awakeable<T> extends Awaitable<T> {

  private final AwakeableIdentifier identifier;

  Awakeable(Syscalls syscalls, DeferredResult<T> deferredResult, AwakeableIdentifier identifier) {
    super(syscalls, deferredResult);
    this.identifier = identifier;
  }

  /**
   * @return the unique identifier of this {@link Awakeable} instance.
   */
  public AwakeableIdentifier id() {
    return identifier;
  }
}
