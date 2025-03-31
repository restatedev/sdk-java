// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

import dev.restate.serde.TypeTag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class RequestImpl<Req, Res> implements WorkflowRequest<Req, Res> {

  private final Target target;
  private final TypeTag<Req> reqTypeTag;
  private final TypeTag<Res> resTypeTag;
  private final Req request;
  @Nullable private final String idempotencyKey;
  @Nullable private final LinkedHashMap<String, String> headers;

  RequestImpl(
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

  @Override
  public Target getTarget() {
    return target;
  }

  @Override
  public TypeTag<Req> getRequestTypeTag() {
    return reqTypeTag;
  }

  @Override
  public TypeTag<Res> getResponseTypeTag() {
    return resTypeTag;
  }

  @Override
  public Req getRequest() {
    return request;
  }

  @Override
  public @Nullable String getIdempotencyKey() {
    return idempotencyKey;
  }

  @Override
  public Map<String, String> getHeaders() {
    if (this.headers == null) {
      return Map.of();
    }
    return this.headers;
  }

  static final class Builder<Req, Res> implements WorkflowRequestBuilder<Req, Res> {
    private final Target target;
    private final TypeTag<Req> reqTypeTag;
    private final TypeTag<Res> resTypeTag;
    private final Req request;
    @Nullable private String idempotencyKey;
    @Nullable private LinkedHashMap<String, String> headers;

    Builder(
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

    Builder(Target target, TypeTag<Req> reqTypeTag, TypeTag<Res> resTypeTag, Req request) {
      this.target = target;
      this.reqTypeTag = reqTypeTag;
      this.resTypeTag = resTypeTag;
      this.request = request;
    }

    @Override
    public Target getTarget() {
      return target;
    }

    @Override
    public TypeTag<Req> getRequestTypeTag() {
      return reqTypeTag;
    }

    @Override
    public TypeTag<Res> getResponseTypeTag() {
      return resTypeTag;
    }

    @Override
    public Req getRequest() {
      return request;
    }

    /**
     * @param idempotencyKey Idempotency key to attach in the request.
     * @return this instance, so the builder can be used fluently.
     */
    @Override
    public Builder<Req, Res> idempotencyKey(String idempotencyKey) {
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
    @Override
    public Builder<Req, Res> header(String key, String value) {
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
    @Override
    public Builder<Req, Res> headers(Map<String, String> newHeaders) {
      if (this.headers == null) {
        this.headers = new LinkedHashMap<>();
      }
      this.headers.putAll(newHeaders);
      return this;
    }

    @Override
    public @Nullable String getIdempotencyKey() {
      return idempotencyKey;
    }

    /**
     * @param idempotencyKey Idempotency key to attach in the request.
     */
    @Override
    public Builder<Req, Res> setIdempotencyKey(@Nullable String idempotencyKey) {
      return idempotencyKey(idempotencyKey);
    }

    @Override
    public @Nullable Map<String, String> getHeaders() {
      return headers;
    }

    /**
     * @param headers headers to send together with the request. This will overwrite the already
     *     configured headers
     */
    @Override
    public Builder<Req, Res> setHeaders(@Nullable Map<String, String> headers) {
      return headers(headers);
    }

    /**
     * @return build the request
     */
    public RequestImpl<Req, Res> build() {
      return new RequestImpl<>(
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

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Request<?, ?> that)) return false;
    return Objects.equals(target, that.getTarget())
        && Objects.equals(reqTypeTag, that.getRequestTypeTag())
        && Objects.equals(resTypeTag, that.getResponseTypeTag())
        && Objects.equals(request, that.getRequest())
        && Objects.equals(idempotencyKey, that.getIdempotencyKey())
        && Objects.equals(headers, that.getHeaders());
  }

  @Override
  public int hashCode() {
    return Objects.hash(target, reqTypeTag, resTypeTag, request, idempotencyKey, headers);
  }

  @Override
  public String toString() {
    return "Request{"
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
