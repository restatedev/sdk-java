// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx

import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.testInvocation
import dev.restate.sdk.core.statemachine.ProtoUtils.*
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdk.kotlin.serialization.KotlinSerializationSerdeFactory
import dev.restate.sdk.serde.jackson.JacksonSerdeFactory
import dev.restate.serde.Serde
import io.vertx.core.Vertx
import java.util.stream.Stream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import org.apache.logging.log4j.LogManager

class ThreadTrampoliningTestSuite : TestDefinitions.TestSuite {

  private val nonBlockingCoroutineName = CoroutineName("CheckContextSwitchingTestCoroutine")

  companion object {
    private val LOG = LogManager.getLogger()
  }

  private suspend fun checkNonBlockingComponentTrampolineExecutor(
      ctx: dev.restate.sdk.kotlin.Context
  ) {
    LOG.info("I am on the thread I am before executing side effect")
    check(Vertx.currentContext() == null)
    check(coroutineContext[CoroutineName] == nonBlockingCoroutineName)
    ctx.runBlock {
      LOG.info("I am on the thread I am when executing side effect")
      check(Vertx.currentContext() == null)
    }
    LOG.info("I am on the thread I am after executing side effect")
    check(coroutineContext[CoroutineName] == nonBlockingCoroutineName)
    check(Vertx.currentContext() == null)
  }

  private fun checkBlockingComponentTrampolineExecutor(
      ctx: dev.restate.sdk.Context,
      _unused: Any
  ): Void? {
    val id = Thread.currentThread().id
    check(Vertx.currentContext() == null)
    ctx.run { check(Vertx.currentContext() == null) }
    check(Thread.currentThread().id == id)
    check(Vertx.currentContext() == null)
    return null
  }

  override fun definitions(): Stream<TestDefinitions.TestDefinition> {
    return Stream.of(
        testInvocation(
                ServiceDefinition.of(
                    "CheckNonBlockingComponentTrampolineExecutor",
                    ServiceType.SERVICE,
                    listOf(
                        HandlerDefinition.of(
                            "do",
                            HandlerType.SHARED,
                            KotlinSerializationSerdeFactory.UNIT,
                            KotlinSerializationSerdeFactory.UNIT,
                            dev.restate.sdk.kotlin.HandlerRunner.of(
                                KotlinSerializationSerdeFactory(),
                                dev.restate.sdk.kotlin.HandlerRunner.Options(
                                    Dispatchers.Default + nonBlockingCoroutineName)) {
                                    ctx: dev.restate.sdk.kotlin.Context,
                                    _: Unit ->
                                  checkNonBlockingComponentTrampolineExecutor(ctx)
                                }))),
                "do")
            .withInput(startMessage(1), inputCmd())
            .onlyBidiStream()
            .expectingOutput(
                runCmd(1), proposeRunCompletion(1, Serde.VOID, null), suspensionMessage(1)),
        testInvocation(
                ServiceDefinition.of(
                    "CheckBlockingComponentTrampolineExecutor",
                    ServiceType.SERVICE,
                    listOf(
                        HandlerDefinition.of(
                            "do",
                            HandlerType.SHARED,
                            Serde.VOID,
                            Serde.VOID,
                            dev.restate.sdk.HandlerRunner.of(
                                this::checkBlockingComponentTrampolineExecutor,
                                JacksonSerdeFactory(),
                                null)))),
                "do")
            .withInput(startMessage(1), inputCmd())
            .onlyBidiStream()
            .expectingOutput(
                runCmd(1), proposeRunCompletion(1, Serde.VOID, null), suspensionMessage(1)))
  }
}
