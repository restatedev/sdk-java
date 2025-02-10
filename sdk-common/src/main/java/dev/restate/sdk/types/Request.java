// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.types;

import dev.restate.common.Slice;
import io.opentelemetry.context.Context;
import java.nio.ByteBuffer;
import java.util.Map;

/** The Request object represents the incoming request to an handler. */
public record Request(
    InvocationId invocationId, Context otelContext, Slice body, Map<String, String> headers) {
  public byte[] bodyAsByteArray() {
    return body.toByteArray();
  }

  public ByteBuffer bodyAsBodyBuffer() {
    return body.asReadOnlyByteBuffer();
  }
}
