package dev.restate.sdk.kotlin

import dev.restate.sdk.core.impl.MockMultiThreaded
import dev.restate.sdk.core.impl.MockSingleThread
import dev.restate.sdk.core.impl.TestDefinitions
import dev.restate.sdk.core.impl.TestDefinitions.TestExecutor
import dev.restate.sdk.core.impl.TestRunner
import java.util.stream.Stream

class KotlinCoroutinesTests : TestRunner() {
  override fun executors(): Stream<TestExecutor> {
    return Stream.of(MockSingleThread.INSTANCE, MockMultiThreaded.INSTANCE)
  }

  override fun definitions(): Stream<TestDefinitions.TestSuite> {
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
        UserFailuresTest())
  }
}
