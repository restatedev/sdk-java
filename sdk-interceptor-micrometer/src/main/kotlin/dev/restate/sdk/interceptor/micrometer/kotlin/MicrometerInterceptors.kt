// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.micrometer.kotlin

import dev.restate.sdk.interceptor.micrometer.MicrometerHelpers.headersReceiverContext
import dev.restate.sdk.interceptor.micrometer.MicrometerHelpers.startHandlerObservation
import dev.restate.sdk.interceptor.micrometer.MicrometerHelpers.startRunObservation
import dev.restate.sdk.kotlin.interceptor.HandlerInterceptor
import dev.restate.sdk.kotlin.interceptor.RunInterceptor
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ContextSnapshotFactory
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.withContext

private val SNAPSHOT_FACTORY =
    ContextSnapshotFactory.builder().contextRegistry(ContextRegistry.getInstance()).build()

/**
 * Coroutine-safe Micrometer Observation interceptor factory for Kotlin handlers.
 *
 * Spans produced through the Micrometer → tracing bridge mirror those emitted by
 * `sdk-interceptor-opentelemetry`: `attempt <target>` for the handler and `run (<name>)` for each
 * `ctx.run`, with `restate.invocation.id`, `restate.invocation.target`, and `restate.run.name`
 * attributes.
 *
 * For each invocation/run it opens an [io.micrometer.observation.Observation], captures the
 * resulting Micrometer [io.micrometer.context.ContextSnapshot], and installs it as a
 * [kotlin.coroutines.CoroutineContext] element so MDC / tracing / Reactor thread-locals are
 * re-applied at every continuation.
 */
class MicrometerInterceptorFactory(
    // If you change this constructor, change the reflections in spring boot module too
    private val registry: ObservationRegistry
) : HandlerInterceptor.Factory, RunInterceptor.Factory {

  override fun createHandlerInterceptor(): HandlerInterceptor? {
    if (registry.isNoop) return null

    return HandlerInterceptor { ctx, next ->
      val recvCtx = headersReceiverContext(ctx.attemptHeaders)
      val observation = startHandlerObservation(registry, recvCtx, ctx.request)
      val scope = observation.openScope()
      val snapshot = SNAPSHOT_FACTORY.captureAll()
      try {
        withContext(MicrometerContextElement(snapshot)) { next() }
      } catch (t: Throwable) {
        observation.error(t)
        throw t
      } finally {
        scope.close()
        observation.stop()
      }
    }
  }

  override fun createRunInterceptor(): RunInterceptor? {
    if (registry.isNoop) return null

    return RunInterceptor { runCtx, next ->
      val observation = startRunObservation(registry, runCtx.runName)
      val scope = observation.openScope()
      val snapshot = SNAPSHOT_FACTORY.captureAll()
      try {
        withContext(MicrometerContextElement(snapshot)) { next() }
      } catch (t: Throwable) {
        observation.error(t)
        throw t
      } finally {
        scope.close()
        observation.stop()
      }
    }
  }
}
