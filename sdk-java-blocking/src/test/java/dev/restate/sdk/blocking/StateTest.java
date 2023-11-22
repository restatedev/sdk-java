// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.Serde;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.impl.StateTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class StateTest extends StateTestSuite {

  private static class GetState extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String state =
          restateContext().get(StateKey.of("STATE", CoreSerdes.STRING_UTF8)).orElse("Unknown");

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getState() {
    return new GetState();
  }

  private static class GetAndSetState extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String state = ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8)).get();

      ctx.set(StateKey.of("STATE", CoreSerdes.STRING_UTF8), request.getName());

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getAndSetState() {
    return new GetAndSetState();
  }

  private static class SetNullState extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext()
          .set(
              StateKey.of(
                  "STATE",
                  Serde.<String>using(
                      l -> {
                        throw new IllegalStateException("Unexpected call to serde fn");
                      },
                      l -> {
                        throw new IllegalStateException("Unexpected call to serde fn");
                      })),
              null);

      responseObserver.onNext(greetingResponse(""));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService setNullState() {
    return new SetNullState();
  }
}
