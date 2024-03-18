// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import com.google.protobuf.ByteString;
import java.util.Map;
import java.util.Objects;

public final class Request {

  private final InvocationId invocationId;
  private final ByteString body;
  private final Map<String, String> headers;

  public Request(InvocationId invocationId, ByteString body, Map<String, String> headers) {
    this.invocationId = invocationId;
    this.body = body;
    this.headers = headers;
  }

  public InvocationId invocationId() {
    return invocationId;
  }

  public byte[] body() {
    return body.toByteArray();
  }

  public ByteString bodyBuffer() {
    return body;
  }

  public Map<String, String> headers() {
    return headers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Request request = (Request) o;

    if (!Objects.equals(invocationId, request.invocationId)) return false;
    if (!Objects.equals(body, request.body)) return false;
    return Objects.equals(headers, request.headers);
  }

  @Override
  public int hashCode() {
    int result = invocationId != null ? invocationId.hashCode() : 0;
    result = 31 * result + (body != null ? body.hashCode() : 0);
    result = 31 * result + (headers != null ? headers.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Request{"
        + "invocationId="
        + invocationId
        + ", body="
        + body
        + ", headers="
        + headers
        + '}';
  }
}
