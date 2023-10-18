package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.StateMachineFailuresTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;

public class StateMachineFailuresTest extends StateMachineFailuresTestSuite {

  private static class GetState extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private static final StateKey<Integer> STATE =
        StateKey.of(
            "STATE",
            TypeTag.using(
                i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
                b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext().get(STATE);
      responseObserver.onNext(greetingResponse("Francesco"));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getState() {
    return new GetState();
  }

  private static class SideEffectFailure extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    private final TypeTag<Integer> typeTag;

    private SideEffectFailure(TypeTag<Integer> typeTag) {
      this.typeTag = typeTag;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext().sideEffect(typeTag, () -> 0);

      responseObserver.onNext(greetingResponse("Francesco"));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sideEffectFailure(TypeTag<Integer> typeTag) {
    return new SideEffectFailure(typeTag);
  }
}
