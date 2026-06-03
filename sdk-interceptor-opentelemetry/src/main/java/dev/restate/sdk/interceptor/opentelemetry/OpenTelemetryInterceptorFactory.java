// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry;

import static dev.restate.sdk.interceptor.opentelemetry.OpenTelemetryHelpers.*;

import dev.restate.sdk.interceptor.HandlerInterceptor;
import dev.restate.sdk.interceptor.RunInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jspecify.annotations.Nullable;

/**
 * OpenTelemetry interceptor factory for Java handlers. Implements both {@link
 * HandlerInterceptor.Factory} and {@link RunInterceptor.Factory} so a single registration covers
 * both invocation- and run-level spans.
 *
 * <ul>
 *   <li>Per invocation: opens an {@code attempt <target>} span (server kind) with {@code
 *       restate.invocation.id} and {@code restate.invocation.target} attributes. The parent span
 *       context is extracted from the incoming request headers via the configured {@link
 *       io.opentelemetry.context.propagation.TextMapPropagator}.
 *   <li>Per {@code ctx.run(name, ...)} call: opens a {@code run (<name>)} child span with the
 *       {@code restate.run.name} attribute.
 * </ul>
 *
 * <p>Scopes are thread-local. Use with {@link OpenTelemetryRunContextPropagator} (registered via
 * SPI by default) to propagate spans to run closures.
 */
public final class OpenTelemetryInterceptorFactory
    implements HandlerInterceptor.Factory, RunInterceptor.Factory {

  private final OpenTelemetry openTelemetry;

  public OpenTelemetryInterceptorFactory(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public @Nullable HandlerInterceptor createHandlerInterceptor() {
    if (openTelemetry == OpenTelemetry.noop()) {
      return null;
    }
    Tracer tracer = openTelemetry.getTracer(OpenTelemetryHelpers.INSTRUMENTATION_NAME);

    return (ctx, next) -> {
      Context parent = extractHandlerContext(ctx.attemptHeaders());
      Span span = OpenTelemetryHelpers.startHandlerSpan(tracer, parent, ctx.request());
      try (Scope ignored = parent.with(span).makeCurrent()) {
        next.proceed();
        span.setStatus(StatusCode.OK);
      } catch (Throwable t) {
        span.setStatus(StatusCode.ERROR);
        span.recordException(t);
        throw t;
      } finally {
        span.end();
      }
    };
  }

  @Override
  public @Nullable RunInterceptor createRunInterceptor() {
    if (openTelemetry == OpenTelemetry.noop()) {
      return null;
    }
    Tracer tracer = openTelemetry.getTracer(OpenTelemetryHelpers.INSTRUMENTATION_NAME);

    return (runCtx, next) -> {
      Context parent = Context.current();
      Span runSpan = OpenTelemetryHelpers.startRunSpan(tracer, parent, runCtx.runName());
      try (Scope ignored = parent.with(runSpan).makeCurrent()) {
        next.proceed();
        runSpan.setStatus(StatusCode.OK);
      } catch (Throwable t) {
        runSpan.setStatus(StatusCode.ERROR);
        runSpan.recordException(t);
        throw t;
      } finally {
        runSpan.end();
      }
    };
  }
}
