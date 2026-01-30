// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.common.Request
import dev.restate.common.Target
import dev.restate.common.reflections.ReflectionUtils
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceDefinitionFactories
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.*
import dev.restate.sdk.testservices.contracts.Program
import dev.restate.serde.Serde
import dev.restate.serde.kotlinx.typeTag
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

fun interpreterName(layer: Int): String {
  return "${ReflectionUtils.extractServiceName(ObjectInterpreter::class.java)}L$layer"
}

fun interpretTarget(layer: Int, key: String): Target {
  return Target.virtualObject(interpreterName(layer), key, "interpret")
}

suspend fun <T> checkAwaitable(
    actual: DurableFuture<T>,
    expected: T,
    cmdIndex: Int,
    interpreterCommand: InterpreterCommand,
) {
  val result = actual.await()
  if (result != expected) {
    throw TerminalException(
        "Awaited promise mismatch. got '$result' expected '$expected'; command at index $cmdIndex was $interpreterCommand"
    )
  }
}

suspend fun <T> checkAwaitableFails(
    actual: DurableFuture<T>,
    cmdIndex: Int,
    interpreterCommand: InterpreterCommand,
) {
  try {
    actual.await()
  } catch (e: TerminalException) {
    return
  }
  throw TerminalException(
      "Awaited promise mismatch. should fail but instead got ${actual.await()}; command at index $cmdIndex was $interpreterCommand"
  )
}

fun cmdStateKey(key: Int): StateKey<String> {
  return stateKey("key-$key")
}

class ObjectInterpreterImpl(private val layer: Int) : ObjectInterpreter {
  companion object {
    private val COUNTER: StateKey<Int> = stateKey("counter")

    fun getInterpreterDefinition(layer: Int): ServiceDefinition {
      val serviceImpl = ObjectInterpreterImpl(layer)
      val originalDefinition =
          ServiceDefinitionFactories.discover(serviceImpl).create(serviceImpl, null)
      return ServiceDefinition.of(
          interpreterName(layer),
          originalDefinition.serviceType,
          originalDefinition.handlers,
      )
    }
  }

  private suspend fun interpreterId(): InterpreterId {
    return InterpreterId(layer, key())
  }

  override suspend fun counter(): Int {
    return state().get(COUNTER) ?: 0
  }

  override suspend fun interpret(program: Program) {
    val promises: MutableMap<Int, suspend () -> Unit> = mutableMapOf()
    for ((i, cmd) in program.commands.withIndex()) {
      when (cmd) {
        is AwaitPromise -> {
          val p =
              promises.remove(cmd.index)
                  ?: throw TerminalException(
                      "ObjectInterpreterL$layer: can not find a promise for the id ${cmd.index}."
                  )
          // Await on promise, this will under the hood check the promise result
          p()
        }
        is CallObject -> {
          val awaitable =
              context()
                  .call(
                      Request.of(
                          interpretTarget(layer + 1, cmd.key.toString()),
                          typeTag<Program>(),
                          typeTag<Unit>(),
                          cmd.program,
                      )
                  )
          promises[i] = { awaitable.await() }
        }
        is CallService -> {
          val expected = "hello-$i"
          val awaitable = toService<ServiceInterpreterHelper>().request { echo(expected) }.call()
          promises[i] = { checkAwaitable(awaitable, expected, i, cmd) }
        }
        is CallSlowService -> {
          val expected = "hello-$i"
          val awaitable =
              toService<ServiceInterpreterHelper>()
                  .request { echoLater(EchoLaterRequest(cmd.sleep, expected)) }
                  .call()
          promises[i] = { checkAwaitable(awaitable, expected, i, cmd) }
        }
        is ClearState -> {
          state().clear(cmdStateKey(cmd.key))
        }
        is GetState -> {
          state().get(cmdStateKey(cmd.key))
        }
        is IncrementStateCounter -> {
          state().set(COUNTER, (state().get(COUNTER) ?: 0) + 1)
        }
        is IncrementStateCounterIndirectly -> {
          toService<ServiceInterpreterHelper>()
              .request { incrementIndirectly(interpreterId()) }
              .send()
        }
        is IncrementStateCounterViaAwakeable -> {
          // Dancing in the mooonlight!
          val awakeable = awakeable<String>()
          toService<ServiceInterpreterHelper>()
              .request {
                incrementViaAwakeableDance(
                    IncrementViaAwakeableDanceRequest(interpreterId(), awakeable.id)
                )
              }
              .send()
          val theirPromiseIdForUsToResolve = awakeable.await()
          awakeableHandle(theirPromiseIdForUsToResolve).resolve("ok")
        }
        is IncrementViaDelayedCall -> {
          toService<ServiceInterpreterHelper>()
              .request { incrementIndirectly(interpreterId()) }
              .send(delay = cmd.duration.milliseconds)
        }
        is RecoverTerminalCall -> {
          var caught = false
          try {
            service<ServiceInterpreterHelper>().terminalFailure()
          } catch (e: TerminalException) {
            caught = true
          }
          if (!caught) {
            throw TerminalException(
                "Test assertion failed, was expected to get a terminal error. Layer $layer, Command $i"
            )
          }
        }
        is RecoverTerminalCallMaybeUnAwaited -> {
          val awaitable = toService<ServiceInterpreterHelper>().request { terminalFailure() }.call()
          promises[i] = { checkAwaitableFails(awaitable, i, cmd) }
        }
        is RejectAwakeable -> {
          val awakeable = awakeable<String>()
          promises[i] = { checkAwaitableFails(awakeable, i, cmd) }
          toService<ServiceInterpreterHelper>().request { rejectAwakeable(awakeable.id) }.send()
        }
        is ResolveAwakeable -> {
          val awakeable = awakeable<String>()
          promises[i] = { checkAwaitable(awakeable, "ok", i, cmd) }
          toService<ServiceInterpreterHelper>().request { resolveAwakeable(awakeable.id) }.send()
        }
        is SetState -> {
          state().set(cmdStateKey(cmd.key), "value-${cmd.key}")
        }
        is SideEffect -> {
          val expected = "hello-$i"
          val result = runBlock { expected }
          if (result != expected) {
            throw TerminalException("Side effect result don't match: $result != $expected")
          }
        }
        is Sleep -> {
          sleep(cmd.duration.milliseconds)
        }
        is SlowSideEffect -> {
          runBlock { kotlinx.coroutines.delay(1.milliseconds) }
        }
        is ThrowingSideEffect -> {
          runBlock {
            check(Random.nextBoolean()) { "Random failure caused by a very cool language." }
          }
        }
      }
    }
  }
}

class ServiceInterpreterHelperImpl : ServiceInterpreterHelper {
  override suspend fun ping() {}

  override suspend fun echo(param: String): String {
    return param
  }

  override suspend fun echoLater(req: EchoLaterRequest): String {
    sleep(req.sleep.milliseconds)
    return req.parameter
  }

  override suspend fun terminalFailure() {
    throw TerminalException("bye")
  }

  override suspend fun incrementIndirectly(id: InterpreterId) {
    val ignored =
        context()
            .send(
                Request.of(
                    interpretTarget(id.layer, id.key),
                    typeTag<Program>(),
                    Serde.SLICE,
                    Program(listOf(IncrementStateCounter())),
                )
            )
  }

  override suspend fun resolveAwakeable(id: String) {
    awakeableHandle(id).resolve("ok")
  }

  override suspend fun rejectAwakeable(id: String) {
    awakeableHandle(id).resolve("error")
  }

  override suspend fun incrementViaAwakeableDance(
      req: IncrementViaAwakeableDanceRequest,
  ) {
    //
    // 1. create an awakeable that we will be blocked on
    //
    val awakeable = awakeable<String>()
    //
    // 2. send our awakeable id to the interpreter via txPromise.
    //
    awakeableHandle(req.txPromiseId).resolve<String>(awakeable.id)
    //
    // 3. wait for the interpreter resolve us
    //
    awakeable.await()
    //
    // 4. to thank our interpret, let us ask it to inc its state.
    //
    val ignored =
        context()
            .send(
                Request.of(
                    interpretTarget(req.interpreter.layer, req.interpreter.key),
                    typeTag<Program>(),
                    Serde.SLICE,
                    Program(listOf(IncrementStateCounter())),
                )
            )
  }
}
