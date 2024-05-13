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

public final class CallRequestOptions extends RequestOptions {

  public static final CallRequestOptions DEFAULT = new CallRequestOptions();

  private final String idempotencyKey;

  public CallRequestOptions() {
    this(new HashMap<>(), null);
  }

  public CallRequestOptions(Map<String, String> additionalHeaders, String idempotencyKey) {
    super(additionalHeaders);
    this.idempotencyKey = idempotencyKey;
  }

  public CallRequestOptions withIdempotency(String idempotencyKey) {
    return new CallRequestOptions(new HashMap<>(this.additionalHeaders), idempotencyKey);
  }

  @Override
  public CallRequestOptions withHeader(String name, String value) {
    CallRequestOptions newOptions = this.copy();
    newOptions.additionalHeaders.put(name, value);
    return newOptions;
  }

  @Override
  public CallRequestOptions withHeaders(Map<? extends String, ? extends String> additionalHeaders) {
    CallRequestOptions newOptions = this.copy();
    newOptions.additionalHeaders.putAll(additionalHeaders);
    return newOptions;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  @Override
  public CallRequestOptions copy() {
    return new CallRequestOptions(new HashMap<>(this.additionalHeaders), this.idempotencyKey);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    CallRequestOptions that = (CallRequestOptions) o;
    return Objects.equals(idempotencyKey, that.idempotencyKey);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(idempotencyKey);
    return result;
  }

  @Override
  public String toString() {
    return "CallRequestOptions{"
        + "idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", additionalHeaders="
        + additionalHeaders
        + '}';
  }
}
