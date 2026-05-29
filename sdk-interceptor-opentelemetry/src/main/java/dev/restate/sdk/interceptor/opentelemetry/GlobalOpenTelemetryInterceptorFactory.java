// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry;

import dev.restate.sdk.interceptor.HandlerInterceptor;
import dev.restate.sdk.interceptor.RunInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.jspecify.annotations.Nullable;

/** SPI default using {@link GlobalOpenTelemetry}. */
public final class GlobalOpenTelemetryInterceptorFactory
    implements HandlerInterceptor.Factory, RunInterceptor.Factory {

  public GlobalOpenTelemetryInterceptorFactory() {}

  @Override
  public @Nullable HandlerInterceptor createHandlerInterceptor() {
    OpenTelemetry otel = GlobalOpenTelemetry.get();
    if (otel == OpenTelemetry.noop()) {
      return null;
    }
    return new OpenTelemetryInterceptorFactory(otel).createHandlerInterceptor();
  }

  @Override
  public @Nullable RunInterceptor createRunInterceptor() {
    OpenTelemetry otel = GlobalOpenTelemetry.get();
    if (otel == OpenTelemetry.noop()) {
      return null;
    }
    return new OpenTelemetryInterceptorFactory(otel).createRunInterceptor();
  }
}
