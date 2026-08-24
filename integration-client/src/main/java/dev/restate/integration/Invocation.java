// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * A single invocation to send through a {@link Producer} / {@link ExactlyOnceProducer}.
 *
 * <p>Instances are created via {@link #create()}.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public sealed interface Invocation extends InvocationMetadata permits InvocationImpl {

  /** Create a standalone invocation, not bound to any producer, to fill in and send. */
  static Invocation create() {
    return new InvocationImpl();
  }

  /** The invocation payload. */
  Invocation setBody(byte[] body);

  byte[] getBody();

  /** Schedule the invocation after a delay. Mutually exclusive with {@link #setInvokeTime}. */
  Invocation setDelay(Duration delay);

  Duration getDelay();

  /** Schedule the invocation at an absolute time. Mutually exclusive with {@link #setDelay}. */
  Invocation setInvokeTime(Instant invokeTime);

  Instant getInvokeTime();

  /** W3C {@code traceparent}. */
  Invocation setTraceparent(String traceparent);

  String getTraceparent();

  /** W3C {@code tracestate}. */
  Invocation setTracestate(String tracestate);

  String getTracestate();

  @Override
  Invocation setServiceName(String serviceName);

  @Override
  Invocation setHandlerName(String handlerName);

  @Override
  Invocation setKey(String key);

  @Override
  Invocation setScope(String scope);

  @Override
  Invocation setLimitKey(String limitKey);

  @Override
  Invocation setIdempotencyKey(String idempotencyKey);

  @Override
  Invocation putHeader(String key, String value);

  @Override
  Invocation setHeaders(Map<String, String> headers);
}
