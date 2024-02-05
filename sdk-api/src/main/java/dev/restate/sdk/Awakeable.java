// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import com.google.protobuf.ByteString;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.syscalls.Deferred;
import dev.restate.sdk.common.syscalls.Result;
import dev.restate.sdk.common.syscalls.Syscalls;
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
 * {@link KeyedContext#awakeableHandle(String)}.
 */
@NotThreadSafe
public final class Awakeable<T> extends Awaitable.MappedAwaitable<ByteString, T> {

  private final String identifier;

  Awakeable(Syscalls syscalls, Deferred<ByteString> deferred, Serde<T> serde, String identifier) {
    super(
        Awaitable.single(syscalls, deferred),
        res -> {
          if (res.isSuccess()) {
            return Result.success(
                Util.deserializeWrappingException(syscalls, serde, res.getValue()));
          }
          //noinspection unchecked
          return (Result<T>) res;
        });
    this.identifier = identifier;
  }

  /**
   * @return the unique identifier of this {@link Awakeable} instance.
   */
  public String id() {
    return identifier;
  }
}
