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
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Interface encapsulating request parameters.
 *
 * @param <Req> the request type
 * @param <Res> the response type
 */
public interface Request<Req, Res> {

  /**
   * Create a new {@link RequestBuilder} for the given {@link Target}, request and response {@link
   * TypeTag} and {@code request} object.
   *
   * <p>When using the annotation processor, you can rely on the generated {@code Handlers} class,
   * instead of manually using this builder. For example, for a service class name {@code Greeter},
   * the corresponding class where all the requests builders are available is named {@code
   * GreeterHandlers}
   */
  static <Req, Res> RequestBuilder<Req, Res> of(
      Target target, TypeTag<Req> reqTypeTag, TypeTag<Res> resTypeTag, Req request) {
    return new RequestImpl.Builder<>(target, reqTypeTag, resTypeTag, request);
  }

  /**
   * Create a new {@link RequestBuilder} for the given {@link Target} and {@code request} byte
   * array.
   *
   * <p>When using the annotation processor, you can rely on the generated {@code Handlers} class,
   * instead of manually using this builder. For example, for a service class name {@code Greeter},
   * the corresponding class where all the requests builders are available is named {@code
   * GreeterHandlers}
   */
  static RequestBuilder<byte[], byte[]> of(Target target, byte[] request) {
    return new RequestImpl.Builder<>(target, TypeTag.of(Serde.RAW), TypeTag.of(Serde.RAW), request);
  }

  /**
   * @return the request target
   */
  Target getTarget();

  /**
   * @return the request type tag
   */
  TypeTag<Req> getRequestTypeTag();

  /**
   * @return the response type tag
   */
  TypeTag<Res> getResponseTypeTag();

  /**
   * @return the request object
   */
  Req getRequest();

  /**
   * @return the idempotency key
   */
  @Nullable String getIdempotencyKey();

  /**
   * @return the request headers
   */
  @Nullable Map<String, String> getHeaders();

  /**
   * @return a builder filled with this request
   */
  RequestBuilder<Req, Res> toBuilder();
}
