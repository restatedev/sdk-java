// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx

import com.google.protobuf.ByteString
import dev.restate.generated.service.protocol.Protocol
import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.testInvocation
import dev.restate.sdk.kotlin.runBlock
import io.vertx.core.Vertx
import java.util.stream.Stream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import org.apache.logging.log4j.LogManager

class VertxExecutorsTest : TestDefinitions.TestSuite {

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
      check(coroutineContext[CoroutineName] == nonBlockingCoroutineName)
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
    ctx.run {
      check(Thread.currentThread().id == id)
      check(Vertx.currentContext() == null)
    }
    check(Thread.currentThread().id == id)
    check(Vertx.currentContext() == null)
    return null
  }

  override fun definitions(): Stream<TestDefinitions.TestDefinition> {
    return Stream.of(
        testInvocation(
                dev.restate.sdk.kotlin.Service.service(
                    "CheckNonBlockingComponentTrampolineExecutor",
                    dev.restate.sdk.kotlin.Service.Options(
                        Dispatchers.Default + nonBlockingCoroutineName)) {
                      handler("do") { ctx, _: Unit ->
                        checkNonBlockingComponentTrampolineExecutor(ctx)
                      }
                    },
                "do")
            .withInput(startMessage(1), inputMessage(), ackMessage(1))
            .onlyUnbuffered()
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder().setValue(ByteString.EMPTY),
                outputMessage(),
                END_MESSAGE),
        testInvocation(
                dev.restate.sdk.Service.service("CheckBlockingComponentTrampolineExecutor")
                    .with(
                        dev.restate.sdk.Service.HandlerSignature.of(
                            "do", CoreSerdes.VOID, CoreSerdes.VOID),
                        this::checkBlockingComponentTrampolineExecutor)
                    .build(dev.restate.sdk.Service.Options.DEFAULT),
                "do")
            .withInput(startMessage(1), inputMessage(), ackMessage(1))
            .onlyUnbuffered()
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder().setValue(ByteString.EMPTY),
                outputMessage(),
                END_MESSAGE))
  }
}
