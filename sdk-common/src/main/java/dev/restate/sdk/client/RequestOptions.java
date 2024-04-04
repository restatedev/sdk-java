// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RequestOptions {

  public static final RequestOptions DEFAULT = new RequestOptions();

  private String idempotencyKey;
  private final Map<String, String> additionalHeaders = new HashMap<>();

  public RequestOptions withIdempotency(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    return this;
  }

  public RequestOptions withHeader(String name, String value) {
    this.additionalHeaders.put(name, value);
    return this;
  }

  public RequestOptions withHeaders(Map<String, String> additionalHeaders) {
    this.additionalHeaders.putAll(additionalHeaders);
    return this;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Map<String, String> getAdditionalHeaders() {
    return additionalHeaders;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RequestOptions that = (RequestOptions) o;

    if (!Objects.equals(idempotencyKey, that.idempotencyKey)) return false;
    return additionalHeaders.equals(that.additionalHeaders);
  }

  @Override
  public int hashCode() {
    int result = idempotencyKey != null ? idempotencyKey.hashCode() : 0;
    result = 31 * result + additionalHeaders.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "RequestOptions{"
        + "idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", additionalHeaders="
        + additionalHeaders
        + '}';
  }
}
