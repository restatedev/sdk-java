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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Object encapsulating request parameters.
 *
 * @param <Req> the request type
 * @param <Res> the response type
 */
public sealed class Request<Req, Res> permits SendRequest {

  private final Target target;
  private final TypeTag<Req> reqTypeTag;
  private final TypeTag<Res> resTypeTag;
  private final Req request;
  @Nullable private final String idempotencyKey;
  @Nullable private final LinkedHashMap<String, String> headers;

  Request(
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

  public TypeTag<Req> requestTypeTag() {
    return reqTypeTag;
  }

  public TypeTag<Res> responseTypeTag() {
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

  /**
   * Create a new {@link Builder} for the given {@link Target}, request and response {@link TypeTag}
   * and {@code request} object.
   *
   * <p>When using the annotation processor, you can rely on the generated {@code Client} class or
   * the generated {@code Requests} class, instead of manually using this builder.
   */
  public static <Req, Res> Builder<Req, Res> of(
      Target target, TypeTag<Req> reqTypeTag, TypeTag<Res> resTypeTag, Req request) {
    return new Builder<>(target, reqTypeTag, resTypeTag, request);
  }

  /**
   * Create a new {@link Builder} for the given {@link Target} and {@code request} byte array.
   *
   * <p>When using the annotation processor, you can rely on the generated {@code Client} class or
   * the generated {@code Requests} class, instead of manually using this builder.
   */
  public static Builder<byte[], byte[]> of(Target target, byte[] request) {
    return new Builder<>(target, TypeTag.of(Serde.RAW), TypeTag.of(Serde.RAW), request);
  }

  public static final class Builder<Req, Res> implements RequestOptionsBuilder {
    private final Target target;
    private final TypeTag<Req> reqTypeTag;
    private final TypeTag<Res> resTypeTag;
    private final Req request;
    @Nullable private String idempotencyKey;
    @Nullable private LinkedHashMap<String, String> headers;

    public Builder(
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

    private Builder(Target target, TypeTag<Req> reqTypeTag, TypeTag<Res> resTypeTag, Req request) {
      this.target = target;
      this.reqTypeTag = reqTypeTag;
      this.resTypeTag = resTypeTag;
      this.request = request;
    }

    /** {@inheritDoc} */
    @Override
    public Builder<Req, Res> idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public Builder<Req, Res> header(String key, String value) {
      if (this.headers == null) {
        this.headers = new LinkedHashMap<>();
      }
      this.headers.put(key, value);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public Builder<Req, Res> headers(Map<String, String> newHeaders) {
      if (this.headers == null) {
        this.headers = new LinkedHashMap<>();
      }
      this.headers.putAll(newHeaders);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getIdempotencyKey() {
      return idempotencyKey;
    }

    /** {@inheritDoc} */
    @Override
    public Builder<Req, Res> setIdempotencyKey(@Nullable String idempotencyKey) {
      return idempotencyKey(idempotencyKey);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Map<String, String> getHeaders() {
      return headers;
    }

    /** {@inheritDoc} */
    @Override
    public Builder<Req, Res> setHeaders(@Nullable Map<String, String> headers) {
      return headers(headers);
    }

    /**
     * @return build the request as send request.
     */
    public SendRequest<Req, Res> asSend() {
      return new SendRequest<>(
          target, reqTypeTag, resTypeTag, request, idempotencyKey, headers, null);
    }

    /**
     * @return build the request as send request delayed by the given {@code delay}.
     */
    public SendRequest<Req, Res> asSendDelayed(Duration delay) {
      return new SendRequest<>(
          target, reqTypeTag, resTypeTag, request, idempotencyKey, headers, delay);
    }

    /**
     * @return build the request as send request.
     */
    public Request<Req, Res> build() {
      return new Request<>(
          this.target,
          this.reqTypeTag,
          this.resTypeTag,
          this.request,
          this.idempotencyKey,
          this.headers);
    }
  }

  public Builder<Req, Res> toBuilder() {
    return new Builder<>(
        this.target,
        this.reqTypeTag,
        this.resTypeTag,
        this.request,
        this.idempotencyKey,
        this.headers);
  }

  /**
   * @return copy this request as send.
   */
  public SendRequest<Req, Res> asSend() {
    return new SendRequest<>(
        target, reqTypeTag, resTypeTag, request, idempotencyKey, headers, null);
  }

  /**
   * @return copy this request as send request delayed by the given {@code delay}.
   */
  public SendRequest<Req, Res> asSendDelayed(Duration delay) {
    return new SendRequest<>(
        target, reqTypeTag, resTypeTag, request, idempotencyKey, headers, delay);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Request<?, ?> that)) return false;
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
