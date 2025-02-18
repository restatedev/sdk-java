// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.common.CallRequest
import dev.restate.sdk.core.*
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.statemachine.ProtoUtils
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.serialization.*
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers

class KotlinAPITests : TestRunner() {
  override fun executors(): Stream<TestExecutor> {
    return Stream.of(MockRequestResponse.INSTANCE, MockBidiStream.INSTANCE)
  }

  public override fun definitions(): Stream<TestDefinitions.TestSuite> {
    return Stream.of(
        AwakeableIdTest(),
        AsyncResultTest(),
        CallTest(),
        EagerStateTest(),
        StateTest(),
        InvocationIdTest(),
        OnlyInputAndOutputTest(),
        PromiseTest(),
        SideEffectTest(),
        SleepTest(),
        StateMachineFailuresTest(),
        UserFailuresTest(),
        RandomTest(),
        CodegenTest())
  }

  companion object {
    inline fun <reified REQ, reified RES> testDefinitionForService(
        name: String,
        noinline runner: suspend (Context, REQ) -> RES
    ): TestInvocationBuilder {
      return TestDefinitions.testInvocation(
          ServiceDefinition.of(
              name,
              ServiceType.SERVICE,
              listOf(
                  HandlerDefinition.of(
                      "run",
                      HandlerType.SHARED,
                      jsonSerde<REQ>(),
                      jsonSerde<RES>(),
                      HandlerRunner.of(
                          KotlinSerializationSerdeFactory(),
                          HandlerRunner.Options(Dispatchers.Unconfined),
                          runner)))),
          "run")
    }

    inline fun <reified REQ, reified RES> testDefinitionForVirtualObject(
        name: String,
        noinline runner: suspend (ObjectContext, REQ) -> RES
    ): TestInvocationBuilder {
      return TestDefinitions.testInvocation(
          ServiceDefinition.of(
              name,
              ServiceType.VIRTUAL_OBJECT,
              listOf(
                  HandlerDefinition.of(
                      "run",
                      HandlerType.EXCLUSIVE,
                      jsonSerde<REQ>(),
                      jsonSerde<RES>(),
                      HandlerRunner.of(
                          KotlinSerializationSerdeFactory(),
                          HandlerRunner.Options(Dispatchers.Unconfined),
                          runner)))),
          "run")
    }

    inline fun <reified REQ, reified RES> testDefinitionForWorkflow(
        name: String,
        noinline runner: suspend (WorkflowContext, REQ) -> RES
    ): TestInvocationBuilder {
      return TestDefinitions.testInvocation(
          ServiceDefinition.of(
              name,
              ServiceType.WORKFLOW,
              listOf(
                  HandlerDefinition.of(
                      "run",
                      HandlerType.WORKFLOW,
                      jsonSerde<REQ>(),
                      jsonSerde<RES>(),
                      HandlerRunner.of(
                          KotlinSerializationSerdeFactory(),
                          HandlerRunner.Options(Dispatchers.Unconfined),
                          runner)))),
          "run")
    }

    suspend fun callGreeterGreetService(ctx: Context, parameter: String): Awaitable<String> {
      return ctx.call(
          CallRequest.of<String, String>(
              ProtoUtils.GREETER_SERVICE_TARGET, TestSerdes.STRING, TestSerdes.STRING, parameter))
    }
  }
}
