// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry;

import dev.restate.sdk.endpoint.HeadersAccessor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Standard OpenTelemetry attribute keys used by the Restate interceptors. */
@org.jetbrains.annotations.ApiStatus.Internal
public final class OpenTelemetryAttributes {
  private OpenTelemetryAttributes() {}

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
}
