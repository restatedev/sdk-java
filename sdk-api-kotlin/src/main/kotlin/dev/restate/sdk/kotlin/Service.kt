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
import dev.restate.sdk.common.*
import dev.restate.sdk.common.syscalls.*
import io.opentelemetry.extension.kotlin.asContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager

class Service
private constructor(
    fqsn: String,
    isKeyed: Boolean,
    handlers: Map<String, Handler<*, *, *>>,
    private val options: Options
) : BindableService<Service.Options> {
  private val serviceDefinition =
      ServiceDefinition(
          fqsn,
          if (isKeyed) ServiceType.VIRTUAL_OBJECT else ServiceType.SERVICE,
          handlers.values.map { obj: Handler<*, *, *> -> obj.toHandlerDefinition() })

  override fun options(): Options {
    return this.options
  }

  override fun definitions() = listOf(this.serviceDefinition)

  companion object {
    fun service(
        name: String,
        options: Options = Options.DEFAULT,
        init: ServiceBuilder.() -> Unit
    ): Service {
      val builder = ServiceBuilder(name)
      builder.init()
      return builder.build(options)
    }

    fun virtualObject(
        name: String,
        options: Options = Options.DEFAULT,
        init: VirtualObjectBuilder.() -> Unit
    ): Service {
      val builder = VirtualObjectBuilder(name)
      builder.init()
      return builder.build(options)
    }
  }

  class VirtualObjectBuilder internal constructor(private val name: String) {
    private val handlers: MutableMap<String, Handler<*, *, ObjectContext>> = mutableMapOf()

    fun <REQ, RES> sharedHandler(
        sig: HandlerSignature<REQ, RES>,
        runner: suspend (ObjectContext, REQ) -> RES
    ): VirtualObjectBuilder {
      handlers[sig.name] = Handler(sig, HandlerType.SHARED, runner)
      return this
    }

    inline fun <reified REQ, reified RES> sharedHandler(
        name: String,
        noinline runner: suspend (ObjectContext, REQ) -> RES
    ) = this.sharedHandler(HandlerSignature(name, KtSerdes.json(), KtSerdes.json()), runner)

    fun <REQ, RES> exclusiveHandler(
        sig: HandlerSignature<REQ, RES>,
        runner: suspend (ObjectContext, REQ) -> RES
    ): VirtualObjectBuilder {
      handlers[sig.name] = Handler(sig, HandlerType.EXCLUSIVE, runner)
      return this
    }

    inline fun <reified REQ, reified RES> exclusiveHandler(
        name: String,
        noinline runner: suspend (ObjectContext, REQ) -> RES
    ) = this.exclusiveHandler(HandlerSignature(name, KtSerdes.json(), KtSerdes.json()), runner)

    fun build(options: Options) = Service(this.name, true, this.handlers, options)
  }

  class ServiceBuilder internal constructor(private val name: String) {
    private val handlers: MutableMap<String, Handler<*, *, Context>> = mutableMapOf()

    fun <REQ, RES> handler(
        sig: HandlerSignature<REQ, RES>,
        runner: suspend (Context, REQ) -> RES
    ): ServiceBuilder {
      handlers[sig.name] = Handler(sig, HandlerType.SHARED, runner)
      return this
    }

    inline fun <reified REQ, reified RES> handler(
        name: String,
        noinline runner: suspend (Context, REQ) -> RES
    ) = this.handler(HandlerSignature(name, KtSerdes.json(), KtSerdes.json()), runner)

    fun build(options: Options) = Service(this.name, false, this.handlers, options)
  }

  class Handler<REQ, RES, CTX : Context>(
      private val handlerSignature: HandlerSignature<REQ, RES>,
      private val handlerType: HandlerType,
      private val runner: suspend (CTX, REQ) -> RES,
  ) : InvocationHandler<Options> {

    companion object {
      private val LOG = LogManager.getLogger()
    }

    fun toHandlerDefinition() =
        HandlerDefinition(
            handlerSignature.name,
            handlerType,
            handlerSignature.requestSerde.schema(),
            handlerSignature.responseSerde.schema(),
            this)

    override fun handle(
        syscalls: Syscalls,
        options: Options,
        callback: SyscallCallback<ByteString>
    ) {
      val ctx: Context = ContextImpl(syscalls)

      val scope =
          CoroutineScope(
              options.coroutineContext +
                  InvocationHandler.SYSCALLS_THREAD_LOCAL.asContextElement(syscalls) +
                  syscalls.request().otelContext()!!.asContextElement())
      scope.launch {
        val serializedResult: ByteString

        try {
          // Parse input
          val req: REQ
          try {
            req = handlerSignature.requestSerde.deserialize(syscalls.request().bodyBuffer())
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
            serializedResult = handlerSignature.responseSerde.serializeToByteString(res)
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
  }

  class HandlerSignature<REQ, RES>(
      val name: String,
      val requestSerde: Serde<REQ>,
      val responseSerde: Serde<RES>
  )

  class Options(val coroutineContext: CoroutineContext) {
    companion object {
      val DEFAULT: Options = Options(Dispatchers.Default)
    }
  }
}
