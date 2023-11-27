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
import dev.restate.sdk.*
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.TestDefinitions.*
import dev.restate.sdk.kotlin.RestateKtService
import io.grpc.stub.StreamObserver
import io.vertx.core.Vertx
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers
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
    private class CheckNonBlockingServiceTrampolineEventLoopContext :
        dev.restate.sdk.core.testservices.GreeterGrpcKt.GreeterCoroutineImplBase(
            Dispatchers.Unconfined),
        RestateKtService {
      override suspend fun greet(
          request: dev.restate.sdk.core.testservices.GreetingRequest
      ): dev.restate.sdk.core.testservices.GreetingResponse {
        check(Vertx.currentContext().isEventLoopContext)
        restateContext().sideEffect { check(Vertx.currentContext().isEventLoopContext) }
        check(Vertx.currentContext().isEventLoopContext)
        return dev.restate.sdk.core.testservices.GreetingResponse.getDefaultInstance()
      }
    }

    private class CheckBlockingServiceTrampolineExecutor :
        dev.restate.sdk.core.testservices.GreeterGrpc.GreeterImplBase(), RestateService {
      override fun greet(
          request: dev.restate.sdk.core.testservices.GreetingRequest,
          responseObserver: StreamObserver<dev.restate.sdk.core.testservices.GreetingResponse>
      ) {
        val id = Thread.currentThread().id
        check(Vertx.currentContext() == null)
        restateContext().sideEffect {
          check(Thread.currentThread().id == id)
          check(Vertx.currentContext() == null)
        }
        check(Thread.currentThread().id == id)
        check(Vertx.currentContext() == null)
        responseObserver.onNext(
            dev.restate.sdk.core.testservices.GreetingResponse.getDefaultInstance())
        responseObserver.onCompleted()
      }
    }

    override fun definitions(): Stream<TestDefinition> {
      return Stream.of(
          testInvocation(
                  CheckNonBlockingServiceTrampolineEventLoopContext(),
                  dev.restate.sdk.core.testservices.GreeterGrpc.getGreetMethod())
              .withInput(
                  startMessage(1),
                  inputMessage(
                      dev.restate.sdk.core.testservices.GreetingRequest.getDefaultInstance()),
                  ackMessage(1))
              .onlyUnbuffered()
              .expectingOutput(
                  SideEffectEntryMessage.newBuilder().setValue(ByteString.EMPTY),
                  outputMessage(
                      dev.restate.sdk.core.testservices.GreetingResponse.getDefaultInstance())),
          testInvocation(
                  CheckBlockingServiceTrampolineExecutor(),
                  dev.restate.sdk.core.testservices.GreeterGrpc.getGreetMethod())
              .withInput(
                  startMessage(1),
                  inputMessage(
                      dev.restate.sdk.core.testservices.GreetingRequest.getDefaultInstance()),
                  ackMessage(1))
              .onlyUnbuffered()
              .expectingOutput(
                  SideEffectEntryMessage.newBuilder().setValue(ByteString.EMPTY),
                  outputMessage(
                      dev.restate.sdk.core.testservices.GreetingResponse.getDefaultInstance())))
    }
  }

  override fun definitions(): Stream<TestSuite> {
    return Stream.of(
        dev.restate.sdk.AwakeableIdTest(),
        dev.restate.sdk.DeferredTest(),
        dev.restate.sdk.EagerStateTest(),
        dev.restate.sdk.StateTest(),
        dev.restate.sdk.InvocationIdTest(),
        dev.restate.sdk.OnlyInputAndOutputTest(),
        dev.restate.sdk.SideEffectTest(),
        dev.restate.sdk.SleepTest(),
        dev.restate.sdk.StateMachineFailuresTest(),
        dev.restate.sdk.UserFailuresTest(),
        dev.restate.sdk.GrpcChannelAdapterTest(),
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
