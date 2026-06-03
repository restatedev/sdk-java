// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.Slice
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.sdk.kotlin.HandlerRunner.Options.Companion.DEFAULT
import dev.restate.sdk.kotlin.interceptor.HandlerInterceptor
import dev.restate.sdk.kotlin.interceptor.RunInterceptor
import dev.restate.sdk.kotlin.internal.RestateContextElement
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import io.opentelemetry.extension.kotlin.asContextElement
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager

/** Adapter class for [dev.restate.sdk.endpoint.definition.HandlerRunner] to use the Kotlin API. */
class HandlerRunner<REQ, RES, CTX : Context>
internal constructor(
    private val runner: suspend (CTX, REQ) -> RES,
    private val contextSerdeFactory: SerdeFactory,
    options: Options,
) : dev.restate.sdk.endpoint.definition.HandlerRunner<REQ, RES> {
  private val coroutineContext = options.coroutineContext
  private val handlerInterceptor =
      HandlerInterceptor.Factory.combine(options.handlerInterceptorFactories)
  private val runInterceptor = RunInterceptor.Factory.combine(options.runInterceptorFactories)

  companion object {
    private val LOG = LogManager.getLogger(HandlerRunner::class.java)

    /**
     * Factory method for [dev.restate.sdk.kotlin.HandlerRunner], used by codegen. Please note this
     * may be subject to breaking changes.
     */
    fun <REQ, RES, CTX : Context> of(
        contextSerdeFactory: SerdeFactory,
        options: Options = Options(),
        runner: suspend (CTX, REQ) -> RES,
    ): HandlerRunner<REQ, RES, CTX> {
      return HandlerRunner(runner, contextSerdeFactory, options)
    }

    /**
     * Factory method for [dev.restate.sdk.kotlin.HandlerRunner], used by codegen. Please note this
     * may be subject to breaking changes.
     */
    fun <RES, CTX : Context> of(
        contextSerdeFactory: SerdeFactory,
        options: Options = Options(),
        runner: suspend (CTX) -> RES,
    ): HandlerRunner<Unit, RES, CTX> {
      return HandlerRunner({ ctx: CTX, _: Unit -> runner(ctx) }, contextSerdeFactory, options)
    }

    /**
     * Factory method for [dev.restate.sdk.kotlin.HandlerRunner], used by codegen. Please note this
     * may be subject to breaking changes.
     */
    fun <REQ, CTX : Context> ofEmptyReturn(
        contextSerdeFactory: SerdeFactory,
        options: Options = Options(),
        runner: suspend (CTX, REQ) -> Unit,
    ): HandlerRunner<REQ, Unit, CTX> {
      return HandlerRunner(
          { ctx: CTX, req: REQ ->
            runner(ctx, req)
            Unit
          },
          contextSerdeFactory,
          options,
      )
    }

    /**
     * Factory method for [dev.restate.sdk.kotlin.HandlerRunner], used by codegen. Please note this
     * may be subject to breaking changes.
     */
    fun <CTX : Context> ofEmptyReturn(
        contextSerdeFactory: SerdeFactory,
        options: Options = Options(),
        runner: suspend (CTX) -> Unit,
    ): HandlerRunner<Unit, Unit, CTX> {
      return HandlerRunner(
          { ctx: CTX, _: Unit ->
            runner(ctx)
            Unit
          },
          contextSerdeFactory,
          options,
      )
    }
  }

  override fun run(
      handlerContext: HandlerContext,
      requestSerde: Serde<REQ>,
      responseSerde: Serde<RES>,
      onClosedInvocationStreamHook: AtomicReference<Runnable>,
  ): CompletableFuture<Slice> {
    // Interceptor chains were combined once when Options was constructed; reuse them here.
    val ctx: Context = ContextImpl(handlerContext, contextSerdeFactory, runInterceptor)

    val scope =
        CoroutineScope(
            coroutineContext +
                RestateContextElement(ctx) +
                dev.restate.sdk.endpoint.definition.HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL
                    .asContextElement(handlerContext) +
                // TODO(tracing-plumbing): deprecate, superseded by sdk-interceptor-opentelemetry
                handlerContext.request().openTelemetryContext()!!.asContextElement()
        )

    val completableFuture = CompletableFuture<Slice>()
    val job =
        scope.launch {
          val resultHolder = AtomicReference(Slice.EMPTY)

          val userBlock: suspend () -> Unit = {
            // Parse input
            val req: REQ =
                try {
                  requestSerde.deserialize(handlerContext.request().body())
                } catch (e: Exception) {
                  LOG.warn("Error deserializing request", e)
                  throw TerminalException(
                      TerminalException.BAD_REQUEST_CODE,
                      "Cannot deserialize request: " + e.message,
                  )
                }

            // Execute user code. AbortedExecutionException (Throwable, not Exception)
            // propagates naturally through this suspend frame.
            @Suppress("UNCHECKED_CAST") val res: RES = runner(ctx as CTX, req)

            // Serialize output
            try {
              resultHolder.set(responseSerde.serialize(res))
            } catch (e: Exception) {
              LOG.warn("Error when serializing response", e)
              throw e
            }
          }

          try {
            handlerInterceptor.aroundHandler(
                HandlerInterceptor.Context(
                    handlerContext.request(),
                    handlerContext.attemptHeaders(),
                ),
                userBlock,
            )
            completableFuture.complete(resultHolder.get())
          } catch (t: Throwable) {
            completableFuture.completeExceptionally(t)
          }
        }
    onClosedInvocationStreamHook.set { job.cancel() }

    return completableFuture
  }

  /**
   * [dev.restate.sdk.kotlin.HandlerRunner] options. You can override the default options to
   * configure the [CoroutineContext] to run the handler, and to register interceptor factories.
   *
   * [DEFAULT] picks up any [HandlerInterceptor.Factory] and [RunInterceptor.Factory] registered via
   * [java.util.ServiceLoader] on the classpath.
   */
  class Options(
      var coroutineContext: CoroutineContext = Dispatchers.Default,
      var handlerInterceptorFactories: MutableList<HandlerInterceptor.Factory> =
          SPI_HANDLER_FACTORIES.toMutableList(),
      var runInterceptorFactories: MutableList<RunInterceptor.Factory> =
          SPI_RUN_FACTORIES.toMutableList(),
  ) : dev.restate.sdk.endpoint.definition.HandlerRunner.Options {

    companion object {
      private val SPI_HANDLER_FACTORIES: List<HandlerInterceptor.Factory> =
          ServiceLoader.load(HandlerInterceptor.Factory::class.java).toList()
      private val SPI_RUN_FACTORIES: List<RunInterceptor.Factory> =
          ServiceLoader.load(RunInterceptor.Factory::class.java).toList()

      @kotlin.Deprecated(
          message = "Replace it with constructing Options() instead.",
          replaceWith = ReplaceWith("Options()"),
      )
      val DEFAULT: Options = Options()
    }
  }
}
