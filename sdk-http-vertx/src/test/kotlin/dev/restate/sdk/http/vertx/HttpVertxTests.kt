package dev.restate.sdk.http.vertx

import dev.restate.sdk.blocking.StateTest
import dev.restate.sdk.core.impl.TestDefinitions
import dev.restate.sdk.core.impl.TestDefinitions.TestExecutor
import dev.restate.sdk.core.impl.TestRunner
import io.vertx.core.Vertx
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class HttpVertxTests : TestRunner() {

  lateinit var vertx: Vertx

  @BeforeAll
  fun beforeAll() {
    vertx = Vertx.vertx()
  }

  @AfterAll
  fun afterAll() {
    vertx.close().toCompletionStage().toCompletableFuture().get()
  }

  override fun executors(): Stream<TestExecutor> {
    return Stream.of(HttpVertxTestExecutor(vertx))
  }

  override fun definitions(): Stream<TestDefinitions.TestSuite> {
    return Stream.of(
        dev.restate.sdk.blocking.AwakeableIdTest(),
        dev.restate.sdk.blocking.DeferredTest(),
        dev.restate.sdk.blocking.EagerStateTest(),
        StateTest(),
        dev.restate.sdk.blocking.InvocationIdTest(),
        dev.restate.sdk.blocking.OnlyInputAndOutputTest(),
        dev.restate.sdk.blocking.SideEffectTest(),
        dev.restate.sdk.blocking.SleepTest(),
        dev.restate.sdk.blocking.StateMachineFailuresTest(),
        dev.restate.sdk.blocking.UserFailuresTest(),
        dev.restate.sdk.kotlin.AwakeableIdTest(),
        dev.restate.sdk.kotlin.DeferredTest(),
        dev.restate.sdk.kotlin.EagerStateTest(),
        dev.restate.sdk.kotlin.StateTest(),
        dev.restate.sdk.kotlin.InvocationIdTest(),
        dev.restate.sdk.kotlin.OnlyInputAndOutputTest(),
        dev.restate.sdk.kotlin.SideEffectTest(),
        dev.restate.sdk.kotlin.SleepTest(),
        dev.restate.sdk.kotlin.StateMachineFailuresTest(),
        dev.restate.sdk.kotlin.UserFailuresTest())
  }
}
