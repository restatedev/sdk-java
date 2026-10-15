// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde;

import dev.restate.common.Slice;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Combines a {@link SerdeFactory} and a {@link PayloadCodec}: resolves the {@link Serde} from the
 * {@link TypeTag}, then serializes and encodes, or decodes and deserializes, the values.
 */
@ApiStatus.Internal
public final class SerdePipeline {

  private final SerdeFactory serdeFactory;
  private final PayloadCodec payloadCodec;

  /**
   * @param payloadCodec the codec to use, or {@code null} to use {@link PayloadCodec#NOOP}.
   */
  public SerdePipeline(SerdeFactory serdeFactory, @Nullable PayloadCodec payloadCodec) {
    this.serdeFactory = serdeFactory;
    this.payloadCodec = payloadCodec != null ? payloadCodec : PayloadCodec.NOOP;
  }

  /** Resolve the serde, serialize the value, then encode it. */
  public <T> CompletableFuture<Slice> encode(TypeTag<T> typeTag, T value) {
    try {
      return payloadCodec.encode(serdeFactory.create(typeTag).serialize(value));
    } catch (Throwable e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  /** Decode the value, then resolve the serde and deserialize it. */
  public <T> CompletableFuture<T> decode(TypeTag<T> typeTag, Slice value) {
    Serde<T> serde;
    CompletableFuture<Slice> decoded;
    try {
      serde = serdeFactory.create(typeTag);
      decoded = payloadCodec.decode(value);
    } catch (Throwable e) {
      return CompletableFuture.failedFuture(e);
    }
    return decoded.thenApply(serde::deserialize);
  }

  public SerdeFactory serdeFactory() {
    return serdeFactory;
  }

  public PayloadCodec payloadCodec() {
    return payloadCodec;
  }
}
