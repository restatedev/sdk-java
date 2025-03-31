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
public interface RequestOptionsBuilder {

  /**
   * @param idempotencyKey Idempotency key to attach in the request.
   * @return this instance, so the builder can be used fluently.
   */
  RequestOptionsBuilder idempotencyKey(String idempotencyKey);

  @Nullable String getIdempotencyKey();

  /**
   * @param idempotencyKey Idempotency key to attach in the request.
   */
  RequestOptionsBuilder setIdempotencyKey(@Nullable String idempotencyKey);

  /**
   * Append this header to the list of configured headers.
   *
   * @param key header key
   * @param value header value
   * @return this instance, so the builder can be used fluently.
   */
  RequestOptionsBuilder header(String key, String value);

  /**
   * Append the given header map to the list of headers.
   *
   * @param newHeaders headers to send together with the request.
   * @return this instance, so the builder can be used fluently.
   */
  RequestOptionsBuilder headers(Map<String, String> newHeaders);

  @Nullable Map<String, String> getHeaders();

  /**
   * @param headers headers to send together with the request. This will overwrite the already
   *     configured headers
   */
  RequestOptionsBuilder setHeaders(@Nullable Map<String, String> headers);
}
