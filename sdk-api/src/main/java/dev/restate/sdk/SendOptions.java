// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class SendOptions {

  public static final SendOptions DEFAULT = new SendOptions(null, null, null);

  @Nullable private final String idempotencyKey;
  @Nullable private final LinkedHashMap<String, String> headers;
  @Nullable private final Duration delay;

  private SendOptions(
      @Nullable String idempotencyKey,
      @Nullable LinkedHashMap<String, String> headers,
      @Nullable Duration delay) {
    this.idempotencyKey = idempotencyKey;
    this.headers = headers;
    this.delay = delay;
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

  public @Nullable Duration getDelay() {
    return delay;
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

  /**
   * @param delay time to wait before executing the call. The time is waited by Restate, and not by
   *     this service.
   */
  public static Builder withDelay(Duration delay) {
    return builder().delay(delay);
  }

  public static final class Builder {
    @Nullable private String idempotencyKey;
    @Nullable private LinkedHashMap<String, String> headers;
    @Nullable private Duration delay;

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

    /**
     * @param delay time to wait before executing the call. The time is waited by Restate, and not
     *     by this service.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder delay(Duration delay) {
      this.delay = delay;
      return this;
    }

    public SendOptions build() {
      return new SendOptions(this.idempotencyKey, this.headers, this.delay);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SendOptions that)) return false;
    return Objects.equals(idempotencyKey, that.idempotencyKey)
        && Objects.equals(headers, that.headers)
        && Objects.equals(delay, that.delay);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idempotencyKey, headers, delay);
  }

  @Override
  public String toString() {
    return "SendOptions{"
        + "idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", headers="
        + headers
        + ", delay="
        + delay
        + '}';
  }
}
