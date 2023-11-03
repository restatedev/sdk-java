package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
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
          restateContext().get(StateKey.of("STATE", TypeTag.STRING_UTF8)).orElse("Unknown");

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

      String state = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      ctx.set(StateKey.of("STATE", TypeTag.STRING_UTF8), request.getName());

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
                  TypeTag.<String>using(
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