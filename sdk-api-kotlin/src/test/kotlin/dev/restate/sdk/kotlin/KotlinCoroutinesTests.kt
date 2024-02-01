// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.MockMultiThreaded
import dev.restate.sdk.core.MockSingleThread
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import dev.restate.sdk.core.TestRunner
import java.util.stream.Stream

class KotlinCoroutinesTests : TestRunner() {
  override fun executors(): Stream<TestExecutor> {
    return Stream.of(MockSingleThread.INSTANCE, MockMultiThreaded.INSTANCE)
  }

  public override fun definitions(): Stream<TestDefinitions.TestSuite> {
    return Stream.of(
        AwakeableIdTest(),
        AwaitableTest(),
        EagerStateTest(),
        StateTest(),
        InvocationIdTest(),
        OnlyInputAndOutputTest(),
        SideEffectTest(),
        SleepTest(),
        StateMachineFailuresTest(),
        UserFailuresTest(),
        RestateCodegenTest(),
        RandomTest())
  }
}
