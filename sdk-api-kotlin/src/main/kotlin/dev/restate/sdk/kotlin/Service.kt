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
    handlers: Map<String, HandlerDefinition<*, *, Options>>,
    private val options: Options
) : BindableService<Service.Options> {
  private val serviceDefinition =
      ServiceDefinition(
          fqsn,
          if (isKeyed) ServiceType.VIRTUAL_OBJECT else ServiceType.SERVICE,
          handlers.values.toList())

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
    private val handlers: MutableMap<String, HandlerDefinition<*, *, Options>> = mutableMapOf()

    fun <REQ, RES> sharedHandler(
        name: String,
        requestSerde: Serde<REQ>,
        responseSerde: Serde<RES>,
        runner: suspend (SharedObjectContext, REQ) -> RES
    ): VirtualObjectBuilder {
      handlers[name] =
          HandlerDefinition(
              HandlerSpecification.of(name, HandlerType.SHARED, requestSerde, responseSerde),
              Handler(runner))
      return this
    }

    inline fun <reified REQ, reified RES> sharedHandler(
        name: String,
        noinline runner: suspend (SharedObjectContext, REQ) -> RES
    ) = this.sharedHandler(name, KtSerdes.json(), KtSerdes.json(), runner)

    fun <REQ, RES> exclusiveHandler(
        name: String,
        requestSerde: Serde<REQ>,
        responseSerde: Serde<RES>,
        runner: suspend (ObjectContext, REQ) -> RES
    ): VirtualObjectBuilder {
      handlers[name] =
          HandlerDefinition(
              HandlerSpecification.of(name, HandlerType.EXCLUSIVE, requestSerde, responseSerde),
              Handler(runner))
      return this
    }

    inline fun <reified REQ, reified RES> exclusiveHandler(
        name: String,
        noinline runner: suspend (ObjectContext, REQ) -> RES
    ) = this.exclusiveHandler(name, KtSerdes.json(), KtSerdes.json(), runner)

    fun build(options: Options) = Service(this.name, true, this.handlers, options)
  }

  class ServiceBuilder internal constructor(private val name: String) {
    private val handlers: MutableMap<String, HandlerDefinition<*, *, Options>> = mutableMapOf()

    fun <REQ, RES> handler(
        name: String,
        requestSerde: Serde<REQ>,
        responseSerde: Serde<RES>,
        runner: suspend (SharedObjectContext, REQ) -> RES
    ): ServiceBuilder {
      handlers[name] =
          HandlerDefinition(
              HandlerSpecification.of(name, HandlerType.SHARED, requestSerde, responseSerde),
              Handler(runner))
      return this
    }

    inline fun <reified REQ, reified RES> handler(
        name: String,
        noinline runner: suspend (Context, REQ) -> RES
    ) = this.handler(name, KtSerdes.json(), KtSerdes.json(), runner)

    fun build(options: Options) = Service(this.name, false, this.handlers, options)
  }

  class Handler<REQ, RES, CTX : Context>(
      private val runner: suspend (CTX, REQ) -> RES,
  ) : InvocationHandler<REQ, RES, Options> {

    companion object {
      private val LOG = LogManager.getLogger()
    }

    override fun handle(
        handlerSpecification: HandlerSpecification<REQ, RES>,
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
  }

  class Options(val coroutineContext: CoroutineContext) {
    companion object {
      val DEFAULT: Options = Options(Dispatchers.Default)
    }
  }
}
