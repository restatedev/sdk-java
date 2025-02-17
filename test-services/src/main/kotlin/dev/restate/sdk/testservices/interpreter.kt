// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.common.CallRequest
import dev.restate.common.SendRequest
import dev.restate.common.Target
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.*
import dev.restate.sdk.testservices.contracts.Program
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

fun interpreterName(layer: Int): String {
  return "${ObjectInterpreterMetadata.SERVICE_NAME}L$layer"
}

fun interpretTarget(layer: Int, key: String): Target {
  return Target.virtualObject(interpreterName(layer), key, "interpret")
}

suspend fun <T> checkAwaitable(
    actual: Awaitable<T>,
    expected: T,
    cmdIndex: Int,
    interpreterCommand: InterpreterCommand
) {
  val result = actual.await()
  if (result != expected) {
    throw TerminalException(
        "Awaited promise mismatch. got '$result' expected '$expected'; command at index $cmdIndex was $interpreterCommand")
  }
}

suspend fun <T> checkAwaitableFails(
    actual: Awaitable<T>,
    cmdIndex: Int,
    interpreterCommand: InterpreterCommand
) {
  try {
    actual.await()
  } catch (e: TerminalException) {
    return
  }
  throw TerminalException(
      "Awaited promise mismatch. should fail but instead got ${actual.await()}; command at index $cmdIndex was $interpreterCommand")
}

fun cmdStateKey(key: Int): StateKey<String> {
  return stateKey("key-$key")
}

class ObjectInterpreterImpl(private val layer: Int) : ObjectInterpreter {
  companion object {
    private val COUNTER: StateKey<Int> = stateKey("counter")

    fun getInterpreterDefinition(layer: Int): ServiceDefinition {
      val originalDefinition =
          ObjectInterpreterServiceDefinitionFactory().create(ObjectInterpreterImpl(layer), null)
      return ServiceDefinition.of(
          interpreterName(layer), originalDefinition.serviceType, originalDefinition.handlers)
    }
  }

  private fun interpreterId(ctx: SharedObjectContext): InterpreterId {
    return InterpreterId(layer, ctx.key())
  }

  override suspend fun counter(ctx: SharedObjectContext): Int {
    return ctx.get(COUNTER) ?: 0
  }

  override suspend fun interpret(ctx: ObjectContext, program: Program) {
    val promises: MutableMap<Int, suspend () -> Unit> = mutableMapOf()
    for ((i, cmd) in program.commands.withIndex()) {
      when (cmd) {
        is AwaitPromise -> {
          val p =
              promises.remove(cmd.index)
                  ?: throw TerminalException(
                      "ObjectInterpreterL$layer: can not find a promise for the id ${cmd.index}.")
          // Await on promise, this will under the hood check the promise result
          p()
        }
        is CallObject -> {
          val awaitable =
              ctx.call(
                  CallRequest.of(
                      interpretTarget(layer + 1, cmd.key.toString()),
                      ObjectInterpreterMetadata.Serde.INTERPRET_INPUT,
                      ObjectInterpreterMetadata.Serde.INTERPRET_OUTPUT,
                      cmd.program))
          promises[i] = { awaitable.await() }
        }
        is CallService -> {
          val expected = "hello-$i"
          val awaitable = ServiceInterpreterHelperClient.fromContext(ctx).echo(expected)
          promises[i] = { checkAwaitable(awaitable, expected, i, cmd) }
        }
        is CallSlowService -> {
          val expected = "hello-$i"
          val awaitable =
              ServiceInterpreterHelperClient.fromContext(ctx)
                  .echoLater(EchoLaterRequest(cmd.sleep, expected))
          promises[i] = { checkAwaitable(awaitable, expected, i, cmd) }
        }
        is ClearState -> {
          ctx.clear(cmdStateKey(cmd.key))
        }
        is GetState -> {
          ctx.get(cmdStateKey(cmd.key))
        }
        is IncrementStateCounter -> {
          ctx.set(COUNTER, (ctx.get(COUNTER) ?: 0) + 1)
        }
        is IncrementStateCounterIndirectly -> {
          ServiceInterpreterHelperClient.fromContext(ctx)
              .send()
              .incrementIndirectly(interpreterId(ctx))
        }
        is IncrementStateCounterViaAwakeable -> {
          // Dancing in the mooonlight!
          val awakeable = ctx.awakeable<String>()
          ServiceInterpreterHelperClient.fromContext(ctx)
              .send()
              .incrementViaAwakeableDance(
                  IncrementViaAwakeableDanceRequest(interpreterId(ctx), awakeable.id))
          val theirPromiseIdForUsToResolve = awakeable.await()
          ctx.awakeableHandle(theirPromiseIdForUsToResolve).resolve("ok")
        }
        is IncrementViaDelayedCall -> {
          ServiceInterpreterHelperClient.fromContext(ctx).send().incrementIndirectly(
              interpreterId(ctx)) {
                delay = cmd.duration.milliseconds
              }
        }
        is RecoverTerminalCall -> {
          var caught = false
          try {
            ServiceInterpreterHelperClient.fromContext(ctx).terminalFailure().await()
          } catch (e: TerminalException) {
            caught = true
          }
          if (!caught) {
            throw TerminalException(
                "Test assertion failed, was expected to get a terminal error. Layer $layer, Command $i")
          }
        }
        is RecoverTerminalCallMaybeUnAwaited -> {
          val awaitable = ServiceInterpreterHelperClient.fromContext(ctx).terminalFailure()
          promises[i] = { checkAwaitableFails(awaitable, i, cmd) }
        }
        is RejectAwakeable -> {
          val awakeable = ctx.awakeable<String>()
          promises[i] = { checkAwaitableFails(awakeable, i, cmd) }
          ServiceInterpreterHelperClient.fromContext(ctx).send().rejectAwakeable(awakeable.id)
        }
        is ResolveAwakeable -> {
          val awakeable = ctx.awakeable<String>()
          promises[i] = { checkAwaitable(awakeable, "ok", i, cmd) }
          ServiceInterpreterHelperClient.fromContext(ctx).send().resolveAwakeable(awakeable.id)
        }
        is SetState -> {
          ctx.set(cmdStateKey(cmd.key), "value-${cmd.key}")
        }
        is SideEffect -> {
          val expected = "hello-$i"
          val result = ctx.runBlock { expected }
          if (result != expected) {
            throw TerminalException("Side effect result don't match: $result != $expected")
          }
        }
        is Sleep -> {
          ctx.sleep(cmd.duration.milliseconds)
        }
        is SlowSideEffect -> {
          ctx.runBlock { kotlinx.coroutines.delay(1.milliseconds) }
        }
        is ThrowingSideEffect -> {
          ctx.runBlock {
            check(Random.nextBoolean()) { "Random failure caused by a very cool language." }
          }
        }
      }
    }
  }
}

class ServiceInterpreterHelperImpl : ServiceInterpreterHelper {
  override suspend fun ping(ctx: Context) {}

  override suspend fun echo(ctx: Context, param: String): String {
    return param
  }

  override suspend fun echoLater(ctx: Context, req: EchoLaterRequest): String {
    ctx.sleep(req.sleep.milliseconds)
    return req.parameter
  }

  override suspend fun terminalFailure(ctx: Context) {
    throw TerminalException("bye")
  }

  override suspend fun incrementIndirectly(ctx: Context, id: InterpreterId) {
    ctx.send(
        SendRequest.of(
            interpretTarget(id.layer, id.key),
            ObjectInterpreterMetadata.Serde.INTERPRET_INPUT,
            Program(listOf(IncrementStateCounter()))))
  }

  override suspend fun resolveAwakeable(ctx: Context, id: String) {
    ctx.awakeableHandle(id).resolve("ok")
  }

  override suspend fun rejectAwakeable(ctx: Context, id: String) {
    ctx.awakeableHandle(id).resolve("error")
  }

  override suspend fun incrementViaAwakeableDance(
      ctx: Context,
      req: IncrementViaAwakeableDanceRequest
  ) {
    //
    // 1. create an awakeable that we will be blocked on
    //
    val awakeable = ctx.awakeable<String>()
    //
    // 2. send our awakeable id to the interpreter via txPromise.
    //
    ctx.awakeableHandle(req.txPromiseId).resolve<String>(awakeable.id)
    //
    // 3. wait for the interpreter resolve us
    //
    awakeable.await()
    //
    // 4. to thank our interpret, let us ask it to inc its state.
    //
    ctx.send(
        SendRequest.of(
            interpretTarget(req.interpreter.layer, req.interpreter.key),
            ObjectInterpreterMetadata.Serde.INTERPRET_INPUT,
            Program(listOf(IncrementStateCounter()))))
  }
}
