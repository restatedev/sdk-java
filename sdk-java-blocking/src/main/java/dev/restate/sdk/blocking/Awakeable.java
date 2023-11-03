package dev.restate.sdk.blocking;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.Serde;
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
 * <p>For example, you can send a Kafka record including the {@link Awakeable#id()}, and then let
 * another service consume from Kafka the responses of given external system interaction by using
 * {@link RestateContext#awakeableHandle(String)}.
 */
@NotThreadSafe
public final class Awakeable<T> extends Awaitable<T> {

  private final String identifier;

  Awakeable(
      Syscalls syscalls,
      DeferredResult<ByteString> deferredResult,
      Serde<T> serde,
      String identifier) {
    super(syscalls, deferredResult, serde::deserialize);
    this.identifier = identifier;
  }

  /**
   * @return the unique identifier of this {@link Awakeable} instance.
   */
  public String id() {
    return identifier;
  }
}
