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
  private final String serviceName;
  private final String handlerName;

  public HandlerRequest(
      InvocationId invocationId,
      Context otelContext,
      Slice body,
      Map<String, String> headers,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable String idempotencyKey,
      String serviceName,
      String handlerName) {
    this.invocationId = invocationId;
    this.otelContext = otelContext;
    this.body = body;
    this.headers = headers;
    this.scope = scope;
    this.limitKey = limitKey;
    this.idempotencyKey = idempotencyKey;
    this.serviceName = serviceName;
    this.handlerName = handlerName;
  }

  public HandlerRequest(
      InvocationId invocationId, Context otelContext, Slice body, Map<String, String> headers) {
    this(invocationId, otelContext, body, headers, null, null, null, null, null);
  }

  public InvocationId invocationId() {
    return invocationId;
  }

  /**
   * @deprecated Use the new {@code sdk-interceptor-opentelemetry} module instead
   */
  @Deprecated(forRemoval = true)
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

  /**
   * <b>PREVIEW:</b> The scope key with which this invocation was submitted, if any.
   *
   * @see dev.restate.common.Target#scoped(String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public @Nullable String scope() {
    return scope;
  }

  /** <b>PREVIEW:</b> The limit key with which this invocation was submitted, if any. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public @Nullable String limitKey() {
    return limitKey;
  }

  /** The idempotency key with which this invocation was submitted, if any. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public @Nullable String idempotencyKey() {
    return idempotencyKey;
  }

  /** Name of the service being invoked. */
  public String serviceName() {
    return serviceName;
  }

  /** Name of the handler being invoked. */
  public String handlerName() {
    return handlerName;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof HandlerRequest that)) return false;
    return Objects.equals(invocationId, that.invocationId)
        && Objects.equals(otelContext, that.otelContext)
        && Objects.equals(body, that.body)
        && Objects.equals(headers, that.headers)
        && Objects.equals(scope, that.scope)
        && Objects.equals(limitKey, that.limitKey)
        && Objects.equals(idempotencyKey, that.idempotencyKey)
        && Objects.equals(serviceName, that.serviceName)
        && Objects.equals(handlerName, that.handlerName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        invocationId,
        otelContext,
        body,
        headers,
        scope,
        limitKey,
        idempotencyKey,
        serviceName,
        handlerName);
  }

  @Override
  public String toString() {
    return "HandlerRequest{"
        + "invocationId="
        + invocationId
        + ", otelContext="
        + otelContext
        + ", body="
        + body
        + ", headers="
        + headers
        + ", scope='"
        + scope
        + '\''
        + ", limitKey='"
        + limitKey
        + '\''
        + ", idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", handlerName='"
        + handlerName
        + '\''
        + '}';
  }
}
