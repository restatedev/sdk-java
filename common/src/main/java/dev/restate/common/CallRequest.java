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
import dev.restate.serde.TypeTag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CallRequest<Req, Res> {

  private final Target target;
  private final TypeTag<Req> reqTypeTag;
  private final TypeTag<Res> resTypeTag;
  private final Req request;
  @Nullable private final String idempotencyKey;
  @Nullable private final LinkedHashMap<String, String> headers;

  private CallRequest(
      Target target,
      TypeTag<Req> reqTypeTag,
      TypeTag<Res> resTypeTag,
      Req request,
      @Nullable String idempotencyKey,
      @Nullable LinkedHashMap<String, String> headers) {
    this.target = target;
    this.reqTypeTag = reqTypeTag;
    this.resTypeTag = resTypeTag;
    this.request = request;
    this.idempotencyKey = idempotencyKey;
    this.headers = headers;
  }

  public Target target() {
    return target;
  }

  public TypeTag<Req> requestSerdeInfo() {
    return reqTypeTag;
  }

  public TypeTag<Res> responseSerdeInfo() {
    return resTypeTag;
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

  public static <Req, Res> Builder<Req, Res> of(
      Target target, TypeTag<Req> reqTypeTag, TypeTag<Res> resTypeTag, Req request) {
    return new Builder<>(target, reqTypeTag, resTypeTag, request);
  }

  public static <Res> Builder<Void, Res> withNoRequestBody(Target target, TypeTag<Res> resTypeTag) {
    return new Builder<>(target, Serde.VOID, resTypeTag, null);
  }

  public static <Req> Builder<Req, Void> withNoResponseBody(
      Target target, TypeTag<Req> reqTypeTag, Req request) {
    return new Builder<>(target, reqTypeTag, Serde.VOID, request);
  }

  public static Builder<byte[], byte[]> ofRaw(Target target, byte[] request) {
    return new Builder<>(target, TypeTag.of(Serde.RAW), TypeTag.of(Serde.RAW), request);
  }

  public static final class Builder<Req, Res> {
    private final Target target;
    private final TypeTag<Req> reqTypeTag;
    private final TypeTag<Res> resTypeTag;
    private final Req request;
    @Nullable private String idempotencyKey;
    @Nullable private LinkedHashMap<String, String> headers;

    private Builder(Target target, TypeTag<Req> reqTypeTag, TypeTag<Res> resTypeTag, Req request) {
      this.target = target;
      this.reqTypeTag = reqTypeTag;
      this.resTypeTag = resTypeTag;
      this.request = request;
    }

    /**
     * @param idempotencyKey Idempotency key to attach in the request.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder<Req, Res> idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    /**
     * @param key header key
     * @param value header value
     * @return this instance, so the builder can be used fluently.
     */
    public Builder<Req, Res> header(String key, String value) {
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
    public Builder<Req, Res> headers(Map<String, String> newHeaders) {
      if (this.headers == null) {
        this.headers = new LinkedHashMap<>();
      }
      this.headers.putAll(newHeaders);
      return this;
    }

    public @Nullable String getIdempotencyKey() {
      return idempotencyKey;
    }

    public Builder<Req, Res> setIdempotencyKey(@Nullable String idempotencyKey) {
      return idempotencyKey(idempotencyKey);
    }

    public @Nullable Map<String, String> getHeaders() {
      return headers;
    }

    public Builder<Req, Res> setHeaders(@Nullable Map<String, String> headers) {
      return headers(headers);
    }

    public CallRequest<Req, Res> build() {
      return new CallRequest<>(
          this.target,
          this.reqTypeTag,
          this.resTypeTag,
          this.request,
          this.idempotencyKey,
          this.headers);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CallRequest<?, ?> that)) return false;
    return Objects.equals(target, that.target)
        && Objects.equals(reqTypeTag, that.reqTypeTag)
        && Objects.equals(resTypeTag, that.resTypeTag)
        && Objects.equals(request, that.request)
        && Objects.equals(idempotencyKey, that.idempotencyKey)
        && Objects.equals(headers, that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(target, reqTypeTag, resTypeTag, request, idempotencyKey, headers);
  }

  @Override
  public String toString() {
    return "CallRequest{"
        + "target="
        + target
        + ", reqSerdeInfo="
        + reqTypeTag
        + ", resSerdeInfo="
        + resTypeTag
        + ", request="
        + request
        + ", idempotencyKey='"
        + idempotencyKey
        + '\''
        + ", headers="
        + headers
        + '}';
  }
}
