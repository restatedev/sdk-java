// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import dev.restate.common.Slice;
import io.opentelemetry.context.Context;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** This class encapsulates the inputs to a handler. */
public final class HandlerRequest {
  private final InvocationId invocationId;
  private final Context otelContext;
  private final Slice body;
  private final Map<String, String> headers;
  private final @Nullable String scope;
  private final @Nullable String limitKey;
  private final @Nullable String idempotencyKey;

  public HandlerRequest(
      InvocationId invocationId,
      Context otelContext,
      Slice body,
      Map<String, String> headers,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable String idempotencyKey) {
    this.invocationId = invocationId;
    this.otelContext = otelContext;
    this.body = body;
    this.headers = headers;
    this.scope = scope;
    this.limitKey = limitKey;
    this.idempotencyKey = idempotencyKey;
  }

  public HandlerRequest(
      InvocationId invocationId, Context otelContext, Slice body, Map<String, String> headers) {
    this(invocationId, otelContext, body, headers, null, null, null);
  }

  public InvocationId invocationId() {
    return invocationId;
  }

  public Context openTelemetryContext() {
    return otelContext;
  }

  public Slice body() {
    return body;
  }

  public byte[] bodyAsByteArray() {
    return body.toByteArray();
  }

  public ByteBuffer bodyAsBodyBuffer() {
    return body.asReadOnlyByteBuffer();
  }

  public Map<String, String> headers() {
    return headers;
  }

  /** If this invocation was called within a scope, returns the scope. */
  public @Nullable String scope() {
    return scope;
  }

  /** If this invocation was called with a limit key, returns the limit key. */
  public @Nullable String limitKey() {
    return limitKey;
  }

  /** If this invocation was called with an idempotency key, returns the idempotency key. */
  public @Nullable String idempotencyKey() {
    return idempotencyKey;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (HandlerRequest) obj;
    return Objects.equals(this.invocationId, that.invocationId)
        && Objects.equals(this.otelContext, that.otelContext)
        && Objects.equals(this.body, that.body)
        && Objects.equals(this.headers, that.headers)
        && Objects.equals(this.scope, that.scope)
        && Objects.equals(this.limitKey, that.limitKey)
        && Objects.equals(this.idempotencyKey, that.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(invocationId, otelContext, body, headers, scope, limitKey, idempotencyKey);
  }

  @Override
  public String toString() {
    return "HandlerRequest["
        + "invocationId="
        + invocationId
        + ", "
        + "otelContext="
        + otelContext
        + ", "
        + "body="
        + body
        + ", "
        + "headers="
        + headers
        + ", "
        + "scope="
        + scope
        + ", "
        + "limitKey="
        + limitKey
        + ", "
        + "idempotencyKey="
        + idempotencyKey
        + ']';
  }
}
