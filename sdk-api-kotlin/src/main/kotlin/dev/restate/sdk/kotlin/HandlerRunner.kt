// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import com.google.protobuf.ByteString
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.common.syscalls.HandlerSpecification
import dev.restate.sdk.common.syscalls.SyscallCallback
import dev.restate.sdk.common.syscalls.Syscalls
import io.opentelemetry.extension.kotlin.asContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager

/** Adapter class for {@link InvocationHandler} to use the Kotlin API. */
class HandlerRunner<REQ, RES, CTX : Context>
internal constructor(
    private val runner: suspend (CTX, REQ) -> RES,
) : dev.restate.sdk.common.syscalls.HandlerRunner<REQ, RES, HandlerRunner.Options> {

  companion object {
    private val LOG = LogManager.getLogger()

    fun <REQ, RES, CTX : Context> of(
        runner: suspend (CTX, REQ) -> RES
    ): HandlerRunner<REQ, RES, CTX> {
      return HandlerRunner(runner)
    }

    fun <RES, CTX : Context> of(runner: suspend (CTX) -> RES): HandlerRunner<Unit, RES, CTX> {
      return HandlerRunner { ctx: CTX, _: Unit -> runner(ctx) }
    }
  }

  override fun run(
      handlerSpecification: HandlerSpecification<REQ, RES>,
      syscalls: Syscalls,
      options: Options?,
      callback: SyscallCallback<ByteString>
  ) {
    val ctx: Context = ContextImpl(syscalls)

    val scope =
        CoroutineScope(
            (options?.coroutineContext
                ?: Options.DEFAULT.coroutineContext) +
                dev.restate.sdk.common.syscalls.HandlerRunner.SYSCALLS_THREAD_LOCAL
                    .asContextElement(syscalls) +
                syscalls.request().otelContext()!!.asContextElement())
    scope.launch {
      val serializedResult: ByteString

      try {
        // Parse input
        val req: REQ
        try {
          req = handlerSpecification.requestSerde.deserialize(syscalls.request().bodyBuffer())
        } catch (e: Error) {
          throw e
        } catch (e: Throwable) {
          LOG.warn("Error when deserializing input", e)
          throw TerminalException(
              TerminalException.BAD_REQUEST_CODE, "Cannot deserialize input: " + e.message)
        }

        // Execute user code
        @Suppress("UNCHECKED_CAST") val res: RES = runner(ctx as CTX, req)

        // Serialize output
        try {
          serializedResult = handlerSpecification.responseSerde.serializeToByteString(res)
        } catch (e: Error) {
          throw e
        } catch (e: Throwable) {
          LOG.warn("Error when serializing input", e)
          throw TerminalException(
              TerminalException.INTERNAL_SERVER_ERROR_CODE, "Cannot serialize output: $e")
        }
      } catch (e: Throwable) {
        callback.onCancel(e)
        return@launch
      }

      // Complete callback
      callback.onSuccess(serializedResult)
    }
  }

  class Options(val coroutineContext: CoroutineContext) {
    companion object {
      val DEFAULT: Options = Options(Dispatchers.Default)
    }
  }
}
