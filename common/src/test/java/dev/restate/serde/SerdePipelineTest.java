// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.restate.common.Slice;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class SerdePipelineTest {

  private static final Serde<String> STRING_SERDE =
      Serde.using(
          s -> s.getBytes(StandardCharsets.UTF_8), b -> new String(b, StandardCharsets.UTF_8));

  /** Reverses the bytes. */
  private static final PayloadCodec REVERSE_CODEC =
      new PayloadCodec() {
        @Override
        public CompletableFuture<Slice> encode(Slice value) {
          return CompletableFuture.completedFuture(reverse(value));
        }

        @Override
        public CompletableFuture<Slice> decode(Slice value) {
          return CompletableFuture.completedFuture(reverse(value));
        }
      };

  // Serde instances are TypeTags, resolved as-is by the factory
  private static final SerdePipeline PIPELINE = new SerdePipeline(SerdeFactory.NOOP, REVERSE_CODEC);

  private static Slice reverse(Slice value) {
    byte[] bytes = value.toByteArray();
    byte[] reversed = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      reversed[i] = bytes[bytes.length - 1 - i];
    }
    return Slice.wrap(reversed);
  }

  @Test
  void serializeThenEncode() {
    Slice encoded = PIPELINE.encode(STRING_SERDE, "abc").join();

    assertThat(encoded.toByteArray()).isEqualTo("cba".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void decodeThenDeserialize() {
    String decoded = PIPELINE.decode(STRING_SERDE, Slice.wrap("cba")).join();

    assertThat(decoded).isEqualTo("abc");
  }

  @Test
  void serializationFailureFailsTheFuture() {
    Serde<String> failingSerde =
        Serde.using(
            s -> {
              throw new IllegalStateException("boom");
            },
            b -> "");

    assertThatThrownBy(() -> PIPELINE.encode(failingSerde, "abc").get())
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseMessage("boom");
  }

  @Test
  void serdeResolutionFailureFailsTheFuture() {
    // SerdeFactory.NOOP can't resolve classes
    assertThat(PIPELINE.encode(TypeTag.of(String.class), "abc")).isCompletedExceptionally();
    assertThat(PIPELINE.decode(TypeTag.of(String.class), Slice.wrap("abc")))
        .isCompletedExceptionally();
  }

  @Test
  void nullCodecIsNoop() {
    SerdePipeline pipeline = new SerdePipeline(SerdeFactory.NOOP, null);

    assertThat(pipeline.payloadCodec()).isSameAs(PayloadCodec.NOOP);
    assertThat(pipeline.decode(STRING_SERDE, Slice.wrap("abc")).join()).isEqualTo("abc");
    assertThat(PayloadCodec.NOOP.isEncodingDeterministic()).isTrue();
  }
}
