package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingRequest;
import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.DeferredTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

class DeferredTest extends DeferredTestSuite {

  private static class ReverseAwaitOrder extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<GreetingResponse> a1 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));
      Awaitable<GreetingResponse> a2 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Till"));

      String a2Res = a2.await().getMessage();
      ctx.set(StateKey.of("A2", TypeTag.STRING_UTF8), a2Res);

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
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

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

  private static class AwaitAll extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

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

  private static class AwaitAny extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

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
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<String> a1 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a2 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a3 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a4 = ctx.awakeable(TypeTag.STRING_UTF8);

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

  private static class AwaitAnyIndex extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<String> a1 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a2 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a3 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a4 = ctx.awakeable(TypeTag.STRING_UTF8);

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
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<String> a1 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a2 = ctx.awakeable(TypeTag.STRING_UTF8);

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
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

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
