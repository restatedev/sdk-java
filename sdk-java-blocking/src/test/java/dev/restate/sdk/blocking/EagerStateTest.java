package dev.restate.sdk.blocking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.EagerStateTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class EagerStateTest extends EagerStateTestSuite {

  private static class GetEmpty extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      boolean stateIsEmpty = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).isEmpty();

      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage(String.valueOf(stateIsEmpty)).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getEmpty() {
    return new GetEmpty();
  }

  private static class Get extends GreeterGrpc.GreeterImplBase implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String state = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService get() {
    return new Get();
  }

  private static class GetAppendAndGet extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String oldState = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();
      ctx.set(StateKey.of("STATE", TypeTag.STRING_UTF8), oldState + request.getName());

      String newState = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(newState).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getAppendAndGet() {
    return new GetAppendAndGet();
  }

  private static class GetClearAndGet extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String oldState = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      ctx.clear(StateKey.of("STATE", TypeTag.STRING_UTF8));
      assertThat(ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8))).isEmpty();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(oldState).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getClearAndGet() {
    return new GetClearAndGet();
  }
}
