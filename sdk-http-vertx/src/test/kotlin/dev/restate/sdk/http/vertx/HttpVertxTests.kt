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
import dev.restate.generated.sdk.java.Java.SideEffectEntryMessage
import dev.restate.sdk.Component
import dev.restate.sdk.Context
import dev.restate.sdk.JavaBlockingTests
import dev.restate.sdk.JavaCodegenTests
import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.TestDefinitions.*
import io.vertx.core.Vertx
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class HttpVertxTests : dev.restate.sdk.core.TestRunner() {

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

  class VertxExecutorsTest : TestSuite {
    //    private class CheckNonBlockingComponentTrampolineEventLoopContext :
    //        dev.restate.sdk.core.testservices.GreeterGrpcKt.GreeterCoroutineImplBase(
    //            Dispatchers.Unconfined),
    //        RestateKtComponent {
    //      override suspend fun greet(request: GreetingRequest): GreetingResponse {
    //        check(Vertx.currentContext().isEventLoopContext)
    //        dev.restate.sdk.kotlin.ObjectContext.current().sideEffect {
    //          check(Vertx.currentContext().isEventLoopContext)
    //        }
    //        check(Vertx.currentContext().isEventLoopContext)
    //        return GreetingResponse.getDefaultInstance()
    //      }
    //    }

    private fun checkBlockingComponentTrampolineExecutor(ctx: Context, _unused: Any): Void? {
      val id = Thread.currentThread().id
      check(Vertx.currentContext() == null)
      ctx.sideEffect {
        check(Thread.currentThread().id == id)
        check(Vertx.currentContext() == null)
      }
      check(Thread.currentThread().id == id)
      check(Vertx.currentContext() == null)
      return null
    }

    override fun definitions(): Stream<TestDefinition> {
      return Stream.of(
          //          testInvocation(
          //                  CheckNonBlockingComponentTrampolineEventLoopContext(),
          //                  GreeterGrpc.getGreetMethod())
          //              .withInput(
          //                  startMessage(1),
          //                  inputMessage(GreetingRequest.getDefaultInstance()),
          //                  ackMessage(1))
          //              .onlyUnbuffered()
          //              .expectingOutput(
          //                  SideEffectEntryMessage.newBuilder().setValue(ByteString.EMPTY),
          //                  outputMessage(GreetingResponse.getDefaultInstance()),
          //                  END_MESSAGE),
          testInvocation(
                  Component.service("CheckBlockingComponentTrampolineExecutor")
                      .with(
                          Component.HandlerSignature.of("do", CoreSerdes.VOID, CoreSerdes.VOID),
                          this::checkBlockingComponentTrampolineExecutor)
                      .build(),
                  "do")
              .withInput(startMessage(1), inputMessage(), ackMessage(1))
              .onlyUnbuffered()
              .expectingOutput(
                  SideEffectEntryMessage.newBuilder().setValue(ByteString.EMPTY),
                  outputMessage(),
                  END_MESSAGE))
    }
  }

  override fun definitions(): Stream<TestSuite> {
    return Stream.concat(
        //        Stream.concat(
        //            JavaBlockingTests().definitions(),
        //            KotlinCoroutinesTests().definitions(),
        //        ),
        Stream.concat(
            JavaBlockingTests().definitions(),
            JavaCodegenTests().definitions(),
        ),
        Stream.of(VertxExecutorsTest()))
  }
}
