// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.micrometer;

import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.endpoint.HeadersAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import org.jspecify.annotations.Nullable;

/**
 * Shared helpers for building Micrometer observations that mirror the spans produced by {@code
 * sdk-interceptor-opentelemetry}: same span names and the same attribute set ({@code
 * restate.invocation.id}, {@code restate.invocation.target}, {@code restate.run.name}).
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class MicrometerHelpers {
  private MicrometerHelpers() {}

  public static final String INVOCATION_OBSERVATION = "restate.invocation";
  public static final String RUN_OBSERVATION = "restate.run";

  public static final String INVOCATION_ID = "restate.invocation.id";
  public static final String INVOCATION_TARGET = "restate.invocation.target";
  public static final String RUN_NAME = "restate.run.name";

  /** Build a {@link ReceiverContext} wrapping the attempt headers for W3C / B3 extraction. */
  public static ReceiverContext<HeadersAccessor> headersReceiverContext(HeadersAccessor headers) {
    ReceiverContext<HeadersAccessor> recvCtx = new ReceiverContext<>(HeadersAccessor::get);
    recvCtx.setCarrier(headers);
    return recvCtx;
  }

  public static Observation startHandlerObservation(
      ObservationRegistry registry,
      ReceiverContext<HeadersAccessor> recvCtx,
      HandlerRequest request) {
    String target = request.serviceName() + "/" + request.handlerName();
    return Observation.createNotStarted(INVOCATION_OBSERVATION, () -> recvCtx, registry)
        .contextualName("attempt " + target)
        .highCardinalityKeyValue(INVOCATION_ID, request.invocationId().toString())
        .highCardinalityKeyValue(INVOCATION_TARGET, target)
        .start();
  }

  public static Observation startRunObservation(
      ObservationRegistry registry, @Nullable String runName) {
    String name = runName != null ? runName : "";
    return Observation.createNotStarted(RUN_OBSERVATION, registry)
        .contextualName("run (" + name + ")")
        .highCardinalityKeyValue(RUN_NAME, name)
        .start();
  }
}
