// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.types;

import io.opentelemetry.context.Context;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/** The Request object represents the incoming request to an handler. */
public final class Request {

  private final InvocationId invocationId;
  private final Context otelContext;
  private final Slice body;
  private final Map<String, String> headers;

  public Request(
      InvocationId invocationId,
      Context otelContext,
      Slice body,
      Map<String, String> headers) {
    this.invocationId = invocationId;
    this.otelContext = otelContext;
    this.body = body;
    this.headers = headers;
  }

  /**
   * @return this invocation id.
   */
  public InvocationId invocationId() {
    return invocationId;
  }

  /**
   * @return the attached OpenTelemetry {@link Context}.
   */
  public Context otelContext() {
    return otelContext;
  }

  public byte[] body() {
    return body.toByteArray();
  }

  public ByteBuffer bodyBuffer() {
    return body.asReadOnlyByteBuffer();
  }

  /**
   * @return the request headers, as received at the ingress.
   */
  public Map<String, String> headers() {
    return headers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Request request = (Request) o;
    return Objects.equals(invocationId, request.invocationId)
        && Objects.equals(otelContext, request.otelContext)
        && Objects.equals(body, request.body)
        && Objects.equals(headers, request.headers);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(invocationId);
    result = 31 * result + Objects.hashCode(otelContext);
    result = 31 * result + Objects.hashCode(body);
    result = 31 * result + Objects.hashCode(headers);
    return result;
  }

  @Override
  public String toString() {
    return "Request{" + "invocationId=" + invocationId + ", headers=" + headers + '}';
  }
}
