// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

import dev.restate.serde.Serde;
import dev.restate.serde.SerdeInfo;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class SendRequest<Req> {

  private final Target target;
  private final SerdeInfo<Req> reqSerdeInfo;
  private final Req request;
  @Nullable private final String idempotencyKey;
  @Nullable private final LinkedHashMap<String, String> headers;
  @Nullable private final Duration delay;

  private SendRequest(
      Target target,
      SerdeInfo<Req> reqSerdeInfo,
      Req request,
      @Nullable String idempotencyKey,
      @Nullable LinkedHashMap<String, String> headers,
      @Nullable Duration delay) {
    this.target = target;
    this.reqSerdeInfo = reqSerdeInfo;
    this.request = request;
    this.idempotencyKey = idempotencyKey;
    this.headers = headers;
    this.delay = delay;
  }

  public Target target() {
    return target;
  }

  public SerdeInfo<Req> requestSerdeInfo() {
    return reqSerdeInfo;
  }

  public Req request() {
    return request;
  }

  public @Nullable String idempotencyKey() {
    return idempotencyKey;
  }

  public Map<String, String> headers() {
    if (headers == null) {
      return Map.of();
    }
    return headers;
  }

  public @Nullable Duration delay() {
    return delay;
  }

  public static <Req> Builder<Req> of(Target target, SerdeInfo<Req> reqSerdeInfo, Req request) {
    return new Builder<>(target, reqSerdeInfo, request);
  }

  public static Builder<Void> withNoRequestBody(Target target) {
    return new Builder<>(target, Serde.VOID, null);
  }

  public static Builder<byte[]> ofRaw(Target target, byte[] request) {
    return new Builder<>(target, SerdeInfo.of(Serde.RAW), request);
  }

  public static final class Builder<Req> {
    private final Target target;
    private final SerdeInfo<Req> reqSerdeInfo;
    private final Req request;
    @Nullable private String idempotencyKey;
    @Nullable private LinkedHashMap<String, String> headers;
    @Nullable private Duration delay;

    private Builder(Target target, SerdeInfo<Req> reqSerdeInfo, Req request) {
      this.target = target;
      this.reqSerdeInfo = reqSerdeInfo;
      this.request = request;
    }

    /**
     * @param idempotencyKey Idempotency key to attach in the request.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder<Req> idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    /**
     * @param key header key
     * @param value header value
     * @return this instance, so the builder can be used fluently.
     */
    public Builder<Req> header(String key, String value) {
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
    public Builder<Req> headers(Map<String, String> newHeaders) {
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
    public Builder<Req> delay(Duration delay) {
      this.delay = delay;
      return this;
    }

    public SendRequest<Req> build() {
      return new SendRequest<>(
          this.target,
          this.reqSerdeInfo,
          this.request,
          this.idempotencyKey,
          this.headers,
          this.delay);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SendRequest<?> that)) return false;
    return Objects.equals(target, that.target)
        && Objects.equals(reqSerdeInfo, that.reqSerdeInfo)
        && Objects.equals(request, that.request)
        && Objects.equals(idempotencyKey, that.idempotencyKey)
        && Objects.equals(headers, that.headers)
        && Objects.equals(delay, that.delay);
  }

  @Override
  public int hashCode() {
    return Objects.hash(target, reqSerdeInfo, request, idempotencyKey, headers, delay);
  }

  @Override
  public String toString() {
    return "SendRequest{"
        + "target="
        + target
        + ", reqSerdeInfo="
        + reqSerdeInfo
        + ", request="
        + request
        + ", idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", headers="
        + headers
        + ", delay="
        + delay
        + '}';
  }
}
