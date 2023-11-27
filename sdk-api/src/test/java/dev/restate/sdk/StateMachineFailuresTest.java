// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.ProtoUtils.greetingResponse;

import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.StateMachineFailuresTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class StateMachineFailuresTest extends StateMachineFailuresTestSuite {

  private static class GetState extends GreeterGrpc.GreeterImplBase implements RestateService {

    private static final StateKey<Integer> STATE =
        StateKey.of(
            "STATE",
            Serde.using(
                i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
                b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

    private final AtomicInteger nonTerminalExceptionsSeen;

    private GetState(AtomicInteger nonTerminalExceptionsSeen) {
      this.nonTerminalExceptionsSeen = nonTerminalExceptionsSeen;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      try {
        restateContext().get(STATE);
      } catch (Throwable e) {
        // A user should never catch Throwable!!!
        if (AbortedExecutionException.INSTANCE.equals(e)) {
          AbortedExecutionException.sneakyThrow();
        }
        if (!(e instanceof TerminalException)) {
          nonTerminalExceptionsSeen.addAndGet(1);
        } else {
          throw e;
        }
      }
      responseObserver.onNext(greetingResponse("Francesco"));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getState(AtomicInteger nonTerminalExceptionsSeen) {
    return new GetState(nonTerminalExceptionsSeen);
  }

  private static class SideEffectFailure extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    private final Serde<Integer> serde;

    private SideEffectFailure(Serde<Integer> serde) {
      this.serde = serde;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext().sideEffect(serde, () -> 0);

      responseObserver.onNext(greetingResponse("Francesco"));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sideEffectFailure(Serde<Integer> serde) {
    return new SideEffectFailure(serde);
  }
}
