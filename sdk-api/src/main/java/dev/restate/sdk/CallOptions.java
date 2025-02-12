// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CallOptions {

  public static final CallOptions DEFAULT = new CallOptions(null, null);

  @Nullable private final String idempotencyKey;
  @Nullable private final LinkedHashMap<String, String> headers;

  private CallOptions(
      @Nullable String idempotencyKey, @Nullable LinkedHashMap<String, String> headers) {
    this.idempotencyKey = idempotencyKey;
    this.headers = headers;
  }

  public @Nullable String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Map<String, String> getHeaders() {
    if (headers == null) {
      return Collections.emptyMap();
    }
    return headers;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * @param idempotencyKey Idempotency key to attach in the request.
   */
  public static Builder withIdempotencyKey(String idempotencyKey) {
    return builder().idempotencyKey(idempotencyKey);
  }

  public static final class Builder {
    @Nullable private String idempotencyKey;
    @Nullable private LinkedHashMap<String, String> headers;

    private Builder() {}

    /**
     * @param idempotencyKey Idempotency key to attach in the request.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    /**
     * @param key header key
     * @param value header value
     * @return this instance, so the builder can be used fluently.
     */
    public Builder header(String key, String value) {
      if (this.headers == null) {
        this.headers = new LinkedHashMap<>();
      }
      this.headers.put(key, value);
      return this;
    }

    /**
     * @param newHeaders headers to send together with the request.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder headers(Map<String, String> newHeaders) {
      if (this.headers == null) {
        this.headers = new LinkedHashMap<>();
      }
      this.headers.putAll(newHeaders);
      return this;
    }

    public CallOptions build() {
      return new CallOptions(this.idempotencyKey, this.headers);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CallOptions that)) return false;
    return Objects.equals(idempotencyKey, that.idempotencyKey)
        && Objects.equals(headers, that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idempotencyKey, headers);
  }

  @Override
  public String toString() {
    return "CallOptions{"
        + "idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", headers="
        + headers
        + '}';
  }
}
