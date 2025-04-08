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

/** This class encapsulates the inputs to a handler. */
public final class HandlerRequest {
  private final InvocationId invocationId;
  private final Context otelContext;
  private final Slice body;
  private final Map<String, String> headers;

  public HandlerRequest(
      InvocationId invocationId, Context otelContext, Slice body, Map<String, String> headers) {
    this.invocationId = invocationId;
    this.otelContext = otelContext;
    this.body = body;
    this.headers = headers;
  }

  public InvocationId getInvocationId() {
    return invocationId;
  }

  public Context getOpenTelemetryContext() {
    return otelContext;
  }

  public Slice getBody() {
    return body;
  }

  public byte[] getBodyAsByteArray() {
    return body.toByteArray();
  }

  public ByteBuffer getBodyAsBodyBuffer() {
    return body.asReadOnlyByteBuffer();
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (HandlerRequest) obj;
    return Objects.equals(this.invocationId, that.invocationId)
        && Objects.equals(this.otelContext, that.otelContext)
        && Objects.equals(this.body, that.body)
        && Objects.equals(this.headers, that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(invocationId, otelContext, body, headers);
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
        + ']';
  }
}
