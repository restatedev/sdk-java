// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.CoreSerdes
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
          Service.service(name, Service.Options(Dispatchers.Unconfined)) { handler("run", runner) },
          "run")
    }

    inline fun <reified REQ, reified RES> testDefinitionForVirtualObject(
        name: String,
        noinline runner: suspend (ObjectContext, REQ) -> RES
    ): TestInvocationBuilder {
      return TestDefinitions.testInvocation(
          Service.virtualObject(name, Service.Options(Dispatchers.Unconfined)) {
            handler("run", runner)
          },
          "run")
    }

    suspend fun callGreeterGreetService(ctx: Context, parameter: String): Awaitable<String> {
      return ctx.callAsync(
          ProtoUtils.GREETER_SERVICE_TARGET,
          CoreSerdes.JSON_STRING,
          CoreSerdes.JSON_STRING,
          parameter)
    }
  }
}
