// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.EagerStateTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreeterRestate;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class EagerStateTest extends EagerStateTestSuite {

  private static class GetEmpty extends GreeterGrpc.GreeterImplBase implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      boolean stateIsEmpty = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).isEmpty();

      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage(String.valueOf(stateIsEmpty)).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getEmpty() {
    return new GetEmpty();
  }

  private static class Get extends GreeterGrpc.GreeterImplBase implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      String state = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService get() {
    return new Get();
  }

  private static class GetAppendAndGet extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      String oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();
      ctx.set(StateKey.of("STATE", CoreSerdes.JSON_STRING), oldState + request.getName());

      String newState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(newState).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getAppendAndGet() {
    return new GetAppendAndGet();
  }

  private static class GetClearAndGet extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      String oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();

      ctx.clear(StateKey.of("STATE", CoreSerdes.JSON_STRING));
      assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isEmpty();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(oldState).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getClearAndGet() {
    return new GetClearAndGet();
  }

  private static class GetClearAllAndGet extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      String oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();

      ctx.clearAll();
      assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isEmpty();
      assertThat(ctx.get(StateKey.of("ANOTHER_STATE", CoreSerdes.JSON_STRING))).isEmpty();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(oldState).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getClearAllAndGet() {
    return new GetClearAllAndGet();
  }

  private static class ListKeys extends GreeterRestate.GreeterRestateImplBase {
    @Override
    public GreetingResponse greet(KeyedContext context, GreetingRequest request)
        throws TerminalException {
      return GreetingResponse.newBuilder()
          .setMessage(String.join(",", context.stateKeys()))
          .build();
    }
  }

  @Override
  protected BindableService listKeys() {
    return new ListKeys();
  }
}
