// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry;

import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.endpoint.HeadersAccessor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Standard OpenTelemetry attribute keys used by the Restate interceptors. */
@org.jetbrains.annotations.ApiStatus.Internal
public final class OpenTelemetryHelpers {
  private OpenTelemetryHelpers() {}

  public static final String INSTRUMENTATION_NAME = "dev.restate.sdk.interceptor.opentelemetry";

  public static final AttributeKey<String> INVOCATION_ID =
      AttributeKey.stringKey("restate.invocation.id");
  public static final AttributeKey<String> INVOCATION_TARGET =
      AttributeKey.stringKey("restate.invocation.target");
  public static final AttributeKey<String> RUN_NAME = AttributeKey.stringKey("restate.run.name");

  public static final TextMapGetter<HeadersAccessor> HEADERS_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HeadersAccessor carrier) {
          return carrier.keys();
        }

        @Nullable
        @Override
        public String get(@Nullable HeadersAccessor carrier, @NonNull String key) {
          return carrier == null ? null : carrier.get(key);
        }
      };
  public static final TextMapPropagator W3C_TRACE_CONTEXT_PROPAGATOR =
      W3CTraceContextPropagator.getInstance();

  public static Span startHandlerSpan(Tracer tracer, Context parent, HandlerRequest request) {
    String target = request.serviceName() + "/" + request.handlerName();
    return tracer
        .spanBuilder("attempt " + target)
        .setSpanKind(SpanKind.SERVER)
        .setParent(parent)
        .setAttribute(OpenTelemetryHelpers.INVOCATION_ID, request.invocationId().toString())
        .setAttribute(OpenTelemetryHelpers.INVOCATION_TARGET, target)
        .startSpan();
  }

  public static Span startRunSpan(Tracer tracer, Context parent, @Nullable String runName) {
    String name = runName != null ? runName : "";
    return tracer
        .spanBuilder("run (" + name + ")")
        .setParent(parent)
        .setAttribute(OpenTelemetryHelpers.RUN_NAME, name)
        .startSpan();
  }

  public static Context extractHandlerContext(HeadersAccessor attemptHeaders) {
    return W3C_TRACE_CONTEXT_PROPAGATOR.extract(Context.current(), attemptHeaders, HEADERS_GETTER);
  }
}
