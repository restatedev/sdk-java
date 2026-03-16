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

  public static final InvocationOptions DEFAULT = new InvocationOptions(null, null, null);

  private final @Nullable String idempotencyKey;
  private final @Nullable String limitKey;
  private final @Nullable LinkedHashMap<String, String> headers;

  InvocationOptions(
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable LinkedHashMap<String, String> headers) {
    this.idempotencyKey = idempotencyKey;
    this.limitKey = limitKey;
    this.headers = headers;
  }

  public @Nullable String getIdempotencyKey() {
    return idempotencyKey;
  }

  /**
   * Returns the limit key for this invocation.
   *
   * <p>The limit key is used to limit the concurrency of invocations sharing the same key within a
   * scope. It must be used together with a scoped invocation target (see {@link
   * Target#scoped(String)}), and requires Restate >= 1.7.
   */
  public @Nullable String getLimitKey() {
    return limitKey;
  }

  public @Nullable Map<String, String> getHeaders() {
    return headers;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof InvocationOptions that)) return false;
    return Objects.equals(getIdempotencyKey(), that.getIdempotencyKey())
        && Objects.equals(getLimitKey(), that.getLimitKey())
        && Objects.equals(getHeaders(), that.getHeaders());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getIdempotencyKey(), getLimitKey(), getHeaders());
  }

  @Override
  public String toString() {
    return "RequestOptions{"
        + "idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", limitKey='"
        + limitKey
        + '\''
        + ", headers="
        + headers
        + '}';
  }

  public static Builder builder() {
    return new Builder(null, null, null);
  }

  public static Builder idempotencyKey(String idempotencyKey) {
    return new Builder(null, null, null).idempotencyKey(idempotencyKey);
  }

  /**
   * Create a builder with the given limit key.
   *
   * <p>The limit key is used to limit the concurrency of invocations sharing the same key within a
   * scope. It must be used together with a scoped invocation target (see {@link
   * Target#scoped(String)}), and requires Restate >= 1.7.
   */
  public static Builder limitKey(String limitKey) {
    return new Builder(null, null, null).limitKey(limitKey);
  }

  public static Builder header(String key, String value) {
    return new Builder(null, null, null).header(key, value);
  }

  public static Builder headers(Map<String, String> newHeaders) {
    return new Builder(null, null, null).headers(newHeaders);
  }

  public static final class Builder {
    @Nullable private String idempotencyKey;
    @Nullable private String limitKey;
    @Nullable private LinkedHashMap<String, String> headers;

    private Builder(
        @Nullable String idempotencyKey,
        @Nullable String limitKey,
        @Nullable LinkedHashMap<String, String> headers) {
      this.idempotencyKey = idempotencyKey;
      this.limitKey = limitKey;
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
     * Set the limit key for this invocation.
     *
     * <p>The limit key is used to limit the concurrency of invocations sharing the same key within
     * a scope. It must be used together with a scoped invocation target (see {@link
     * Target#scoped(String)}), and requires Restate >= 1.7.
     *
     * @param limitKey Limit key to attach in the request.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder limitKey(String limitKey) {
      this.limitKey = limitKey;
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

    public @Nullable String getLimitKey() {
      return limitKey;
    }

    /**
     * @param limitKey Limit key to attach in the request.
     */
    public void setLimitKey(@Nullable String limitKey) {
      this.limitKey = limitKey;
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
      return new InvocationOptions(this.idempotencyKey, this.limitKey, this.headers);
    }
  }

  public Builder toBuilder() {
    return new Builder(this.idempotencyKey, this.limitKey, this.headers);
  }
}
