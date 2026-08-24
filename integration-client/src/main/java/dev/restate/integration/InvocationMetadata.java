// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Invocation metadata.
 *
 * <p>When used as producer defaults, these will be used for all invocations sent through that
 * producer.
 *
 * <p>Passing {@code null} to a setter clears that field. Getters return {@code null} when the
 * corresponding field is not set.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public sealed interface InvocationMetadata permits Invocation, InvocationMetadataImpl {

  /** Create a standalone metadata object, e.g. to use as producer defaults. */
  static InvocationMetadata create() {
    return new InvocationMetadataImpl();
  }

  /** Target service name. */
  InvocationMetadata setServiceName(@Nullable String serviceName);

  @Nullable String getServiceName();

  /** Target handler name. */
  InvocationMetadata setHandlerName(@Nullable String handlerName);

  @Nullable String getHandlerName();

  /** Target key (required when the target is a Virtual Object or Workflow). */
  InvocationMetadata setKey(@Nullable String key);

  @Nullable String getKey();

  /** Scope. */
  InvocationMetadata setScope(@Nullable String scope);

  @Nullable String getScope();

  /** Rate/concurrency limit key. */
  InvocationMetadata setLimitKey(@Nullable String limitKey);

  @Nullable String getLimitKey();

  /** Idempotency key used by Restate to deduplicate the invocation. */
  InvocationMetadata setIdempotencyKey(@Nullable String idempotencyKey);

  @Nullable String getIdempotencyKey();

  /** Add or replace a single header. */
  InvocationMetadata putHeader(String key, String value);

  /** Replace the whole header map. */
  InvocationMetadata setHeaders(@Nullable Map<String, String> headers);

  Map<String, String> getHeaders();
}
