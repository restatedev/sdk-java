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
import static dev.restate.sdk.core.ProtoUtils.greetingResponse;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.DeferredTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

public class AwaitableTest extends DeferredTestSuite {

  private static class ReverseAwaitOrder extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<GreetingResponse> a1 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));
      Awaitable<GreetingResponse> a2 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Till"));

      String a2Res = a2.await().getMessage();
      ctx.set(StateKey.of("A2", CoreSerdes.JSON_STRING), a2Res);

      String a1Res = a1.await().getMessage();

      responseObserver.onNext(greetingResponse(a1Res + "-" + a2Res));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService reverseAwaitOrder() {
    return new ReverseAwaitOrder();
  }

  private static class AwaitTwiceTheSameAwaitable extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<GreetingResponse> a =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));

      responseObserver.onNext(
          greetingResponse(a.await().getMessage() + "-" + a.await().getMessage()));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService awaitTwiceTheSameAwaitable() {
    return new AwaitTwiceTheSameAwaitable();
  }

  private static class AwaitAll extends GreeterGrpc.GreeterImplBase implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<GreetingResponse> a1 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));
      Awaitable<GreetingResponse> a2 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Till"));

      Awaitable.all(a1, a2).await();

      responseObserver.onNext(
          greetingResponse(a1.await().getMessage() + "-" + a2.await().getMessage()));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService awaitAll() {
    return new AwaitAll();
  }

  private static class AwaitAny extends GreeterGrpc.GreeterImplBase implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<GreetingResponse> a1 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));
      Awaitable<GreetingResponse> a2 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Till"));

      GreetingResponse res = (GreetingResponse) Awaitable.any(a1, a2).await();

      responseObserver.onNext(res);
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService awaitAny() {
    return new AwaitAny();
  }

  private static class CombineAnyWithAll extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<String> a1 = ctx.awakeable(CoreSerdes.JSON_STRING);
      Awaitable<String> a2 = ctx.awakeable(CoreSerdes.JSON_STRING);
      Awaitable<String> a3 = ctx.awakeable(CoreSerdes.JSON_STRING);
      Awaitable<String> a4 = ctx.awakeable(CoreSerdes.JSON_STRING);

      Awaitable<Object> a12 = Awaitable.any(a1, a2);
      Awaitable<Object> a23 = Awaitable.any(a2, a3);
      Awaitable<Object> a34 = Awaitable.any(a3, a4);
      Awaitable.all(a12, a23, a34).await();

      responseObserver.onNext(greetingResponse(a12.await() + (String) a23.await() + a34.await()));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService combineAnyWithAll() {
    return new CombineAnyWithAll();
  }

  private static class AwaitAnyIndex extends GreeterGrpc.GreeterImplBase implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<String> a1 = ctx.awakeable(CoreSerdes.JSON_STRING);
      Awaitable<String> a2 = ctx.awakeable(CoreSerdes.JSON_STRING);
      Awaitable<String> a3 = ctx.awakeable(CoreSerdes.JSON_STRING);
      Awaitable<String> a4 = ctx.awakeable(CoreSerdes.JSON_STRING);

      responseObserver.onNext(
          greetingResponse(
              String.valueOf(Awaitable.any(a1, Awaitable.all(a2, a3), a4).awaitIndex())));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService awaitAnyIndex() {
    return new AwaitAnyIndex();
  }

  private static class AwaitOnAlreadyResolvedAwaitables extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<String> a1 = ctx.awakeable(CoreSerdes.JSON_STRING);
      Awaitable<String> a2 = ctx.awakeable(CoreSerdes.JSON_STRING);

      Awaitable<Void> a12 = Awaitable.all(a1, a2);
      Awaitable<Void> a12and1 = Awaitable.all(a12, a1);
      Awaitable<Void> a121and12 = Awaitable.all(a12and1, a12);

      a12and1.await();
      a121and12.await();

      responseObserver.onNext(greetingResponse(a1.await() + a2.await()));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService awaitOnAlreadyResolvedAwaitables() {
    return new AwaitOnAlreadyResolvedAwaitables();
  }

  private static class AwaitWithTimeout extends GreeterGrpc.GreeterImplBase
      implements RestateService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      KeyedContext ctx = KeyedContext.current();

      Awaitable<GreetingResponse> call =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));

      String result;
      try {
        result = call.await(Duration.ofDays(1)).getMessage();
      } catch (TimeoutException e) {
        result = "timeout";
      }

      responseObserver.onNext(greetingResponse(result));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService awaitWithTimeout() {
    return new AwaitWithTimeout();
  }
}
