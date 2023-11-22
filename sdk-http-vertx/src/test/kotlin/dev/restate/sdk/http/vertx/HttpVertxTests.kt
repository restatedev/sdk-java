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
import dev.restate.sdk.blocking.RestateBlockingService
import dev.restate.sdk.core.impl.ProtoUtils.*
import dev.restate.sdk.core.impl.TestDefinitions.*
import dev.restate.sdk.core.impl.TestRunner
import dev.restate.sdk.core.impl.testservices.GreeterGrpc
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.kotlin.RestateCoroutineService
import io.grpc.stub.StreamObserver
import io.vertx.core.Vertx
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers
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

  class VertxExecutorsTest : TestSuite {
    private class CheckNonBlockingServiceTrampolineEventLoopContext :
        GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
      override suspend fun greet(request: GreetingRequest): GreetingResponse {
        check(Vertx.currentContext().isEventLoopContext)
        restateContext().sideEffect { check(Vertx.currentContext().isEventLoopContext) }
        check(Vertx.currentContext().isEventLoopContext)
        return GreetingResponse.getDefaultInstance()
      }
    }

    private class CheckBlockingServiceTrampolineExecutor :
        GreeterGrpc.GreeterImplBase(), RestateBlockingService {
      override fun greet(
          request: GreetingRequest,
          responseObserver: StreamObserver<GreetingResponse>
      ) {
        val id = Thread.currentThread().id
        check(Vertx.currentContext() == null)
        restateContext().sideEffect {
          check(Thread.currentThread().id == id)
          check(Vertx.currentContext() == null)
        }
        check(Thread.currentThread().id == id)
        check(Vertx.currentContext() == null)
        responseObserver.onNext(GreetingResponse.getDefaultInstance())
        responseObserver.onCompleted()
      }
    }

    override fun definitions(): Stream<TestDefinition> {
      return Stream.of(
          testInvocation(
                  CheckNonBlockingServiceTrampolineEventLoopContext(), GreeterGrpc.getGreetMethod())
              .withInput(
                  startMessage(1),
                  inputMessage(GreetingRequest.getDefaultInstance()),
                  ackMessage(1))
              .onlyUnbuffered()
              .expectingOutput(
                  SideEffectEntryMessage.newBuilder().setValue(ByteString.EMPTY),
                  outputMessage(GreetingResponse.getDefaultInstance())),
          testInvocation(CheckBlockingServiceTrampolineExecutor(), GreeterGrpc.getGreetMethod())
              .withInput(
                  startMessage(1),
                  inputMessage(GreetingRequest.getDefaultInstance()),
                  ackMessage(1))
              .onlyUnbuffered()
              .expectingOutput(
                  SideEffectEntryMessage.newBuilder().setValue(ByteString.EMPTY),
                  outputMessage(GreetingResponse.getDefaultInstance())))
    }
  }

  override fun definitions(): Stream<TestSuite> {
    return Stream.of(
        dev.restate.sdk.blocking.AwakeableIdTest(),
        dev.restate.sdk.blocking.DeferredTest(),
        dev.restate.sdk.blocking.EagerStateTest(),
        dev.restate.sdk.blocking.StateTest(),
        dev.restate.sdk.blocking.InvocationIdTest(),
        dev.restate.sdk.blocking.OnlyInputAndOutputTest(),
        dev.restate.sdk.blocking.SideEffectTest(),
        dev.restate.sdk.blocking.SleepTest(),
        dev.restate.sdk.blocking.StateMachineFailuresTest(),
        dev.restate.sdk.blocking.UserFailuresTest(),
        dev.restate.sdk.blocking.GrpcChannelAdapterTest(),
        dev.restate.sdk.kotlin.AwakeableIdTest(),
        dev.restate.sdk.kotlin.DeferredTest(),
        dev.restate.sdk.kotlin.EagerStateTest(),
        dev.restate.sdk.kotlin.StateTest(),
        dev.restate.sdk.kotlin.InvocationIdTest(),
        dev.restate.sdk.kotlin.OnlyInputAndOutputTest(),
        dev.restate.sdk.kotlin.SideEffectTest(),
        dev.restate.sdk.kotlin.SleepTest(),
        dev.restate.sdk.kotlin.StateMachineFailuresTest(),
        dev.restate.sdk.kotlin.UserFailuresTest(),
        VertxExecutorsTest())
  }
}
