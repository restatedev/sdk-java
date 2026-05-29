// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.micrometer.kotlin

import dev.restate.sdk.endpoint.HeadersAccessor
import dev.restate.sdk.kotlin.interceptor.HandlerInterceptor
import dev.restate.sdk.kotlin.interceptor.RunInterceptor
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ContextSnapshotFactory
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.transport.ReceiverContext
import kotlinx.coroutines.withContext

private const val INVOCATION_OBSERVATION = "restate.invocation"
private const val RUN_OBSERVATION = "restate.run"

private val SNAPSHOT_FACTORY =
    ContextSnapshotFactory.builder().contextRegistry(ContextRegistry.getInstance()).build()

/**
 * Coroutine-safe Micrometer Observation interceptor factory for Kotlin handlers.
 *
 * For each invocation/run it opens an [Observation], captures the resulting Micrometer
 * [io.micrometer.context.ContextSnapshot], and installs it as a
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
      val target = "${ctx.request.serviceName()}/${ctx.request.handlerName()}"
      // ReceiverContext + the configured Propagator on the registry extracts W3C / B3 trace
      // context from the attempt headers and parents this observation accordingly.
      val recvCtx = ReceiverContext<HeadersAccessor> { headers, key -> headers.get(key) }
      recvCtx.setCarrier(ctx.attemptHeaders)
      val observation =
          Observation.createNotStarted(INVOCATION_OBSERVATION, { recvCtx }, registry)
              .contextualName("restate $target")
              .lowCardinalityKeyValue("restate.service", ctx.request.serviceName())
              .lowCardinalityKeyValue("restate.handler", ctx.request.handlerName())
              .lowCardinalityKeyValue(
                  "restate.handler.type",
                  ctx.request.handlerType()?.name ?: "UNKNOWN",
              )
              .highCardinalityKeyValue(
                  "restate.invocation.id",
                  ctx.request.invocationId().toString(),
              )
              .highCardinalityKeyValue("restate.invocation.target", target)
              .start()
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
      val name = runCtx.runName ?: "run"
      val observation =
          Observation.createNotStarted(RUN_OBSERVATION, registry)
              .contextualName("restate run $name")
              .lowCardinalityKeyValue("restate.run.name", name)
              .start()
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
