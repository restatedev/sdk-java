// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.ProtoUtils.greetingRequest;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.core.SideEffectTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

public class SideEffectTest extends SideEffectTestSuite {

  private static class SideEffect extends GreeterGrpc.GreeterImplBase implements Component {

    private final String sideEffectOutput;

    SideEffect(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      ObjectContext ctx = ObjectContext.current();

      String result = ctx.sideEffect(CoreSerdes.JSON_STRING, () -> this.sideEffectOutput);

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + result).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sideEffect(String sideEffectOutput) {
    return new SideEffect(sideEffectOutput);
  }

  private static class ConsecutiveSideEffect extends GreeterGrpc.GreeterImplBase
      implements Component {

    private final String sideEffectOutput;

    ConsecutiveSideEffect(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      ObjectContext ctx = ObjectContext.current();

      String firstResult = ctx.sideEffect(CoreSerdes.JSON_STRING, () -> this.sideEffectOutput);
      String secondResult = ctx.sideEffect(CoreSerdes.JSON_STRING, firstResult::toUpperCase);

      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage("Hello " + secondResult).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService consecutiveSideEffect(String sideEffectOutput) {
    return new ConsecutiveSideEffect(sideEffectOutput);
  }

  private static class CheckContextSwitching extends GreeterGrpc.GreeterImplBase
      implements Component {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String currentThread = Thread.currentThread().getName();

      String sideEffectThread =
          ObjectContext.current()
              .sideEffect(CoreSerdes.JSON_STRING, () -> Thread.currentThread().getName());

      if (!Objects.equals(currentThread, sideEffectThread)) {
        throw new IllegalStateException(
            "Current thread and side effect thread do not match: "
                + currentThread
                + " != "
                + sideEffectThread);
      }

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello").build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService checkContextSwitching() {
    return new CheckContextSwitching();
  }

  private static class SideEffectGuard extends GreeterGrpc.GreeterImplBase implements Component {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      ObjectContext ctx = ObjectContext.current();
      ctx.sideEffect(
          () -> ctx.oneWayCall(GreeterGrpc.getGreetMethod(), greetingRequest("something")));

      throw new IllegalStateException("This point should not be reached");
    }
  }

  @Override
  protected BindableService sideEffectGuard() {
    return new SideEffectGuard();
  }
}
