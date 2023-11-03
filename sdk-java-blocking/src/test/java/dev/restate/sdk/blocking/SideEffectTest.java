package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingRequest;

import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.impl.SideEffectTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

public class SideEffectTest extends SideEffectTestSuite {

  private static class SideEffect extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final String sideEffectOutput;

    SideEffect(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String result = ctx.sideEffect(CoreSerdes.STRING_UTF8, () -> this.sideEffectOutput);

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + result).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sideEffect(String sideEffectOutput) {
    return new SideEffect(sideEffectOutput);
  }

  private static class ConsecutiveSideEffect extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final String sideEffectOutput;

    ConsecutiveSideEffect(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String firstResult = ctx.sideEffect(CoreSerdes.STRING_UTF8, () -> this.sideEffectOutput);
      String secondResult = ctx.sideEffect(CoreSerdes.STRING_UTF8, firstResult::toUpperCase);

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
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String currentThread = Thread.currentThread().getName();

      String sideEffectThread =
          restateContext()
              .sideEffect(CoreSerdes.STRING_UTF8, () -> Thread.currentThread().getName());

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

  private static class SideEffectGuard extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();
      ctx.sideEffect(
          () -> ctx.oneWayCall(GreeterGrpc.getGreetMethod(), greetingRequest("something")));

      throw new IllegalStateException("This point should not be reached");
    }
  }

  @Override
  protected BindableService sideEffectGuard() {
    return new SideEffectGuard();
  }

  private static class SideEffectThenAwakeable extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.sideEffect(
          () -> {
            throw new IllegalStateException("This should be replayed");
          });
      ctx.awakeable(CoreSerdes.BYTES).await();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello").build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sideEffectThenAwakeable() {
    return new SideEffectThenAwakeable();
  }
}
