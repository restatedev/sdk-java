// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Options for requests to Restate services. */
public interface RequestBuilder<Req, Res> extends Request<Req, Res> {

  /**
   * @param idempotencyKey Idempotency key to attach in the request.
   * @return this instance, so the builder can be used fluently.
   */
  RequestBuilder<Req, Res> idempotencyKey(@Nullable String idempotencyKey);

  /**
   * @param idempotencyKey Idempotency key to attach in the request.
   */
  RequestBuilder<Req, Res> setIdempotencyKey(@Nullable String idempotencyKey);

  /**
   * Append this header to the list of configured headers.
   *
   * @param key header key
   * @param value header value
   * @return this instance, so the builder can be used fluently.
   */
  RequestBuilder<Req, Res> header(String key, String value);

  /**
   * Append the given header map to the list of headers.
   *
   * @param newHeaders headers to send together with the request.
   * @return this instance, so the builder can be used fluently.
   */
  RequestBuilder<Req, Res> headers(@Nullable Map<String, String> newHeaders);

  /**
   * @param headers headers to send together with the request. This will overwrite the already
   *     configured headers
   */
  RequestBuilder<Req, Res> setHeaders(@Nullable Map<String, String> headers);

  /**
   * @return the built request
   */
  Request<Req, Res> build();
}
