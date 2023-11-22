// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;
import static dev.restate.sdk.core.impl.TestDefinitions.testInvocation;

import dev.restate.sdk.core.impl.TestDefinitions;
import dev.restate.sdk.core.impl.TestDefinitions.TestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class GrpcChannelAdapterTest implements TestSuite {

  private static class InvokeUsingGeneratedClient extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();
      GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(ctx.grpcChannel());
      String response =
          client.greet(GreetingRequest.newBuilder().setName("Francesco").build()).getMessage();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(response).build());
      responseObserver.onCompleted();
    }
  }

  private static class InvokeUsingGeneratedFutureClient extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();
      GreeterGrpc.GreeterFutureStub client = GreeterGrpc.newFutureStub(ctx.grpcChannel());
      String response;
      try {
        response =
            client
                .greet(GreetingRequest.newBuilder().setName("Francesco").build())
                .get()
                .getMessage();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(response).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new InvokeUsingGeneratedClient(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                outputMessage(greetingResponse("FRANCESCO"))),
        testInvocation(new InvokeUsingGeneratedClient(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                suspensionMessage(1))
            .named("Suspending"),
        testInvocation(new InvokeUsingGeneratedFutureClient(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                outputMessage(greetingResponse("FRANCESCO"))),
        testInvocation(new InvokeUsingGeneratedFutureClient(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                suspensionMessage(1))
            .named("Suspending"));
  }
}
