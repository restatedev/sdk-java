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

/**
 * Payload codec.
 *
 * <p>This allows to transform journal payloads after they're serialized, before they're written to
 * the wire, and before they're deserialized after they're read from the wire. A typical use case is
 * service side journal encryption.
 *
 * <p>Payloads that are passed through the codec:
 *
 * <ul>
 *   <li>Handlers input and success output
 *   <li>{@code ctx.run} success results
 *   <li>Call/send parameters and call success results
 *   <li>Awakeables, durable promises and signals success results
 *   <li>State values
 * </ul>
 *
 * <p>Failures (e.g. terminal error messages), state keys, headers and other metadata are <b>not</b>
 * passed through the codec.
 *
 * <p>The encoding/decoding doesn't need to be deterministic, see {@link
 * #isEncodingDeterministic()}.
 *
 * <p>The codec can be configured in the endpoint with {@code
 * Endpoint.Builder#withPayloadCodec(PayloadCodec)}, and in the ingress client with {@code
 * Client.connect(String, SerdeFactory, PayloadCodec, RequestOptions)}.
 *
 * <p>Implementations of this class <b>MUST be thread safe and asynchronous</b>, as they might block
 * the I/O thread.
 *
 * <p><b>NOTE:</b> Using this class in a service requires the native state machine, available on JDK
 * 23+.
 */
public interface PayloadCodec {

  /**
   * Encodes the given payload. This is applied <i>after</i> serialization.
   *
   * @param payload the payload to encode. Empty slices must be handled as well.
   * @return a future completed with the encoded payload
   */
  CompletableFuture<Slice> encode(Slice payload);

  /**
   * Decodes the given payload. This is applied <i>before</i> deserialization.
   *
   * @param payload the payload to decode.
   * @return a future completed with the decoded payload
   */
  CompletableFuture<Slice> decode(Slice payload);

  /**
   * Whether {@link #encode(Slice)} is deterministic, that is, whether encoding the same payload
   * twice always produces the same bytes.
   *
   * <p>When replaying an invocation, the SDK re-executes the handler code and, by default, checks
   * that the payloads it produces (e.g. state values, call parameters, handler output) are equal to
   * the payloads recorded in the journal by the previous execution. If they differ, the invocation
   * fails with a journal mismatch error, as this usually signals non-deterministic handler code.
   *
   * <p>This check compares <i>encoded</i> payloads. If the encoding is not deterministic, e.g. when
   * encrypting with a random IV/nonce as AES-GCM requires, the same payload encodes to different
   * bytes on every execution, and the check would fail on every replay. Return {@code false} in
   * that case, and the SDK will skip the comparison of payloads when replaying. Every other journal
   * check (e.g. the sequence of operations, the call targets, the state keys) stays enabled.
   *
   * @return {@code true} if encoding the same payload always produces the same bytes.
   */
  default boolean isEncodingDeterministic() {
    return false;
  }

  /** Noop codec. */
  PayloadCodec NOOP =
      new PayloadCodec() {
        @Override
        public CompletableFuture<Slice> encode(Slice payload) {
          return CompletableFuture.completedFuture(payload);
        }

        @Override
        public CompletableFuture<Slice> decode(Slice payload) {
          return CompletableFuture.completedFuture(payload);
        }

        @Override
        public boolean isEncodingDeterministic() {
          return true;
        }

        @Override
        public String toString() {
          return "PayloadCodec.NOOP";
        }
      };
}
