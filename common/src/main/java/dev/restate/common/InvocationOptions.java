// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class InvocationOptions {

  public static final InvocationOptions DEFAULT = new InvocationOptions(null, null);

  private final @Nullable String idempotencyKey;
  private final @Nullable LinkedHashMap<String, String> headers;

  InvocationOptions(
      @Nullable String idempotencyKey, @Nullable LinkedHashMap<String, String> headers) {
    this.idempotencyKey = idempotencyKey;
    this.headers = headers;
  }

  public @Nullable String getIdempotencyKey() {
    return idempotencyKey;
  }

  public @Nullable Map<String, String> getHeaders() {
    return headers;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof InvocationOptions that)) return false;
    return Objects.equals(getIdempotencyKey(), that.getIdempotencyKey())
        && Objects.equals(getHeaders(), that.getHeaders());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getIdempotencyKey(), getHeaders());
  }

  @Override
  public String toString() {
    return "RequestOptions{"
        + "idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", headers="
        + headers
        + '}';
  }

  public static Builder builder() {
    return new Builder(null, null);
  }

  public static Builder idempotencyKey(String idempotencyKey) {
    return new Builder(null, null).idempotencyKey(idempotencyKey);
  }

  public static Builder header(String key, String value) {
    return new Builder(null, null).header(key, value);
  }

  public static Builder headers(Map<String, String> newHeaders) {
    return new Builder(null, null).headers(newHeaders);
  }

  public static final class Builder {
    @Nullable private String idempotencyKey;
    @Nullable private LinkedHashMap<String, String> headers;

    private Builder(
        @Nullable String idempotencyKey, @Nullable LinkedHashMap<String, String> headers) {
      this.idempotencyKey = idempotencyKey;
      this.headers = headers;
    }

    /**
     * @param idempotencyKey Idempotency key to attach in the request.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    /**
     * Append this header to the list of configured headers.
     *
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
     * Append the given header map to the list of headers.
     *
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

    public @Nullable String getIdempotencyKey() {
      return idempotencyKey;
    }

    /**
     * @param idempotencyKey Idempotency key to attach in the request.
     */
    public void setIdempotencyKey(@Nullable String idempotencyKey) {
      idempotencyKey(idempotencyKey);
    }

    public @Nullable Map<String, String> getHeaders() {
      return headers;
    }

    /**
     * @param headers headers to send together with the request. This will overwrite the already
     *     configured headers
     */
    public void setHeaders(@Nullable Map<String, String> headers) {
      headers(headers);
    }

    /**
     * @return build the request
     */
    public InvocationOptions build() {
      return new InvocationOptions(this.idempotencyKey, this.headers);
    }
  }

  public Builder toBuilder() {
    return new Builder(this.idempotencyKey, this.headers);
  }
}
