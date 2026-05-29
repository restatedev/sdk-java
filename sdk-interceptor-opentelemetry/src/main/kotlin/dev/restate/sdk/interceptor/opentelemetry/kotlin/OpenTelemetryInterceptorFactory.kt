// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry.kotlin

import dev.restate.sdk.interceptor.opentelemetry.OpenTelemetryHelpers.*
import dev.restate.sdk.kotlin.interceptor.HandlerInterceptor
import dev.restate.sdk.kotlin.interceptor.RunInterceptor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext

/**
 * Coroutine-safe OpenTelemetry interceptor factory for Kotlin handlers. Implements both
 * [HandlerInterceptor.Factory] and [RunInterceptor.Factory] so a single registration covers both
 * invocation- and run-level spans.
 *
 * The OTEL context is installed as a [kotlin.coroutines.CoroutineContext] element via
 * `withContext(...)` so it propagates across coroutine suspensions.
 */
class OpenTelemetryInterceptorFactory(private val openTelemetry: OpenTelemetry) :
    HandlerInterceptor.Factory, RunInterceptor.Factory {

  override fun createHandlerInterceptor(): HandlerInterceptor? {
    if (openTelemetry == OpenTelemetry.noop()) return null
    val tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME)

    return HandlerInterceptor { ctx, next ->
      val parent = extractHandlerContext(ctx.attemptHeaders)
      val span = startHandlerSpan(tracer, parent, ctx.request)
      try {
        withContext(parent.with(span).asContextElement()) { next() }
        span.setStatus(StatusCode.OK)
      } catch (t: Throwable) {
        span.setStatus(StatusCode.ERROR, t.message ?: "")
        span.recordException(t)
        throw t
      } finally {
        span.end()
      }
    }
  }

  override fun createRunInterceptor(): RunInterceptor? {
    if (openTelemetry == OpenTelemetry.noop()) return null
    val tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME)

    return RunInterceptor { runCtx, next ->
      val parent = Context.current()
      val span = startRunSpan(tracer, parent, runCtx.runName)
      try {
        withContext(parent.with(span).asContextElement()) { next() }
        span.setStatus(StatusCode.OK)
      } catch (t: Throwable) {
        span.setStatus(StatusCode.ERROR, t.message ?: "")
        span.recordException(t)
        throw t
      } finally {
        span.end()
      }
    }
  }
}
