// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A single invocation to send through a {@link Producer} / {@link ExactlyOnceProducer}.
 *
 * <p>Instances are created via {@link #create()}.
 *
 * <p>Passing {@code null} to a setter clears that field. Optional getters return {@code null} when
 * the corresponding field is not set.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public sealed interface Invocation extends InvocationMetadata permits InvocationImpl {

  /** Create a standalone invocation, not bound to any producer, to fill in and send. */
  static Invocation create() {
    return new InvocationImpl();
  }

  /** The invocation payload. */
  Invocation setBody(byte @Nullable [] body);

  byte[] getBody();

  /** Schedule the invocation at an absolute time. */
  Invocation setInvokeTime(@Nullable Instant invokeTime);

  @Nullable Instant getInvokeTime();

  /** W3C {@code traceparent}. */
  Invocation setTraceparent(@Nullable String traceparent);

  @Nullable String getTraceparent();

  /** W3C {@code tracestate}. */
  Invocation setTracestate(@Nullable String tracestate);

  @Nullable String getTracestate();

  /** Idempotency key used by Restate to deduplicate the invocation. */
  Invocation setIdempotencyKey(@Nullable String idempotencyKey);

  @Nullable String getIdempotencyKey();

  @Override
  Invocation setServiceName(@Nullable String serviceName);

  @Override
  Invocation setHandlerName(@Nullable String handlerName);

  @Override
  Invocation setKey(@Nullable String key);

  @Override
  Invocation setScope(@Nullable String scope);

  @Override
  Invocation setLimitKey(@Nullable String limitKey);

  @Override
  Invocation putHeader(String key, String value);

  @Override
  Invocation setHeaders(@Nullable Map<String, String> headers);
}
