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
import dev.restate.sdk.interceptor.HandlerInterceptor;
import dev.restate.sdk.interceptor.RunInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jspecify.annotations.NonNull;
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
 * <p>This Java variant uses thread-local OTEL scope via {@link Scope#close()}. Kotlin coroutine
 * handlers should use the suspend variant in this module to keep the OTEL context across
 * suspensions.
 */
public final class OpenTelemetryInterceptorFactory
    implements HandlerInterceptor.Factory, RunInterceptor.Factory {

  static final TextMapGetter<HeadersAccessor> HEADERS_GETTER =
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

  private final OpenTelemetry openTelemetry;

  public OpenTelemetryInterceptorFactory(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public @Nullable HandlerInterceptor createHandlerInterceptor() {
    if (openTelemetry == OpenTelemetry.noop()) {
      return null;
    }
    Tracer tracer = openTelemetry.getTracer(OpenTelemetryAttributes.INSTRUMENTATION_NAME);

    return (ctx, next) -> {
      String target = ctx.request().serviceName() + "/" + ctx.request().handlerName();
      Context parent =
          openTelemetry
              .getPropagators()
              .getTextMapPropagator()
              .extract(Context.current(), ctx.attemptHeaders(), HEADERS_GETTER);
      Span span =
          tracer
              .spanBuilder("attempt " + target)
              .setSpanKind(SpanKind.SERVER)
              .setParent(parent)
              .setAttribute(
                  OpenTelemetryAttributes.INVOCATION_ID, ctx.request().invocationId().toString())
              .setAttribute(OpenTelemetryAttributes.INVOCATION_TARGET, target)
              .startSpan();
      try (Scope ignored = parent.with(span).makeCurrent()) {
        next.proceed();
        span.setStatus(StatusCode.OK);
      } catch (Throwable t) {
        span.setStatus(StatusCode.ERROR, t.getMessage() == null ? "" : t.getMessage());
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
    Tracer tracer = openTelemetry.getTracer(OpenTelemetryAttributes.INSTRUMENTATION_NAME);

    return (runCtx, next) -> {
      String name = runCtx.runName() != null ? runCtx.runName() : "run";
      Context parent = Context.current();
      Span runSpan =
          tracer
              .spanBuilder("run (" + name + ")")
              .setParent(parent)
              .setAttribute(OpenTelemetryAttributes.RUN_NAME, name)
              .startSpan();
      try (Scope ignored = parent.with(runSpan).makeCurrent()) {
        next.proceed();
        runSpan.setStatus(StatusCode.OK);
      } catch (Throwable t) {
        runSpan.setStatus(StatusCode.ERROR, t.getMessage() == null ? "" : t.getMessage());
        runSpan.recordException(t);
        throw t;
      } finally {
        runSpan.end();
      }
    };
  }
}
