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
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.sdk.types.TerminalException
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import io.opentelemetry.extension.kotlin.asContextElement
import java.util.concurrent.CompletableFuture
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
    private val options: Options
) : dev.restate.sdk.endpoint.definition.HandlerRunner<REQ, RES> {

  companion object {
    private val LOG = LogManager.getLogger(HandlerRunner::class.java)

    fun <REQ, RES, CTX : Context> of(
        contextSerdeFactory: SerdeFactory,
        options: Options = Options.DEFAULT,
        runner: suspend (CTX, REQ) -> RES,
    ): HandlerRunner<REQ, RES, CTX> {
      return HandlerRunner(runner, contextSerdeFactory, options)
    }

    fun <RES, CTX : Context> of(
        contextSerdeFactory: SerdeFactory,
        options: Options = Options.DEFAULT,
        runner: suspend (CTX) -> RES,
    ): HandlerRunner<Unit, RES, CTX> {
      return HandlerRunner({ ctx: CTX, _: Unit -> runner(ctx) }, contextSerdeFactory, options)
    }
  }

  override fun run(
      handlerContext: HandlerContext,
      requestSerde: Serde<REQ>,
      responseSerde: Serde<RES>,
  ): CompletableFuture<Slice> {
    val ctx: Context = ContextImpl(handlerContext, contextSerdeFactory)

    val scope =
        CoroutineScope(
            options.coroutineContext +
                dev.restate.sdk.endpoint.definition.HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL
                    .asContextElement(handlerContext) +
                handlerContext.request().otelContext()!!.asContextElement())

    val completableFuture = CompletableFuture<Slice>()

    scope.launch {
      val serializedResult: Slice

      try {
        // Parse input
        val req: REQ
        try {
          req = requestSerde.deserialize(handlerContext.request().body)
        } catch (e: Throwable) {
          LOG.warn("Error deserializing request", e)
          completableFuture.completeExceptionally(
              throw TerminalException(
                  TerminalException.BAD_REQUEST_CODE, "Cannot deserialize request: " + e.message))
          return@launch
        }

        // Execute user code
        @Suppress("UNCHECKED_CAST") val res: RES = runner(ctx as CTX, req)

        // Serialize output
        try {
          serializedResult = responseSerde.serialize(res)
        } catch (e: Throwable) {
          LOG.warn("Error when serializing response", e)
          completableFuture.completeExceptionally(e)
          return@launch
        }
      } catch (e: Throwable) {
        completableFuture.completeExceptionally(e)
        return@launch
      }

      // Complete callback
      completableFuture.complete(serializedResult)
    }

    return completableFuture
  }

  data class Options(val coroutineContext: CoroutineContext) :
      dev.restate.sdk.endpoint.definition.HandlerRunner.Options {
    companion object {
      val DEFAULT: Options = Options(Dispatchers.Default)
    }
  }
}
