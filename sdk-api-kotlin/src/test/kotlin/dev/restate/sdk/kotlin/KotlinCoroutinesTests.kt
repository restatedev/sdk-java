// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.endpoint.HandlerType
import dev.restate.sdk.endpoint.ServiceType
import dev.restate.sdk.endpoint.HandlerDefinition
import dev.restate.sdk.endpoint.HandlerSpecification
import dev.restate.sdk.endpoint.ServiceDefinition
import dev.restate.sdk.core.*
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers

class KotlinCoroutinesTests : TestRunner() {
  override fun executors(): Stream<TestExecutor> {
    return Stream.of(MockSingleThread.INSTANCE, MockMultiThreaded.INSTANCE)
  }

  public override fun definitions(): Stream<TestDefinitions.TestSuite> {
    return Stream.of(
        AwakeableIdTest(),
        DeferredTest(),
        EagerStateTest(),
        StateTest(),
        InvocationIdTest(),
        OnlyInputAndOutputTest(),
        PromiseTest(),
        SideEffectTest(),
        SleepTest(),
        StateMachineFailuresTest(),
        UserFailuresTest(),
        RandomTest())
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
                      HandlerSpecification.of(
                          "run", HandlerType.SHARED, KtSerdes.json(), KtSerdes.json()),
                      HandlerRunner.of(runner)))),
          HandlerRunner.Options(Dispatchers.Unconfined),
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
                      HandlerSpecification.of(
                          "run", HandlerType.EXCLUSIVE, KtSerdes.json(), KtSerdes.json()),
                      HandlerRunner.of(runner)))),
          HandlerRunner.Options(Dispatchers.Unconfined),
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
                      HandlerSpecification.of(
                          "run", HandlerType.WORKFLOW, KtSerdes.json(), KtSerdes.json()),
                      HandlerRunner.of(runner)))),
          HandlerRunner.Options(Dispatchers.Unconfined),
          "run")
    }

    suspend fun callGreeterGreetService(ctx: Context, parameter: String): Awaitable<String> {
      return ctx.callAsync(
          ProtoUtils.GREETER_SERVICE_TARGET, TestSerdes.STRING, TestSerdes.STRING, parameter)
    }
  }
}
