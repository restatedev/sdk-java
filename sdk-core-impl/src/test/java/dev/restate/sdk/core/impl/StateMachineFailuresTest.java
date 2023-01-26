package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

class StateMachineFailuresTest extends CoreTestRunner {

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

  private abstract static class SideEffectFailure extends GreeterGrpc.GreeterImplBase
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

  private static class EndSideEffectSerializationFailure extends SideEffectFailure {

    private static final TypeTag<Integer> INTEGER_TYPE_TAG =
        TypeTag.using(
            i -> {
              throw new IllegalStateException("Cannot serialize integer");
            },
            b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8)));

    private EndSideEffectSerializationFailure() {
      super(INTEGER_TYPE_TAG);
    }
  }

  private static class EndSideEffectDeserializationFailure extends SideEffectFailure {

    private static final TypeTag<Integer> INTEGER_TYPE_TAG =
        TypeTag.using(
            i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
            b -> {
              throw new IllegalStateException("Cannot deserialize integer");
            });

    private EndSideEffectDeserializationFailure() {
      super(INTEGER_TYPE_TAG);
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new GetState(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("Something"))
            .usingAllThreadingModels()
            .assertingFailure(ProtocolException.class),
        testInvocation(new GetState(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "This is not an integer"))
            .usingAllThreadingModels()
            .assertingFailure(NumberFormatException.class),
        testInvocation(new EndSideEffectSerializationFailure(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .assertingFailure(IllegalStateException.class),
        testInvocation(new EndSideEffectDeserializationFailure(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Java.SideEffectEntryMessage.newBuilder())
            .usingAllThreadingModels()
            .assertingFailure(IllegalStateException.class));
  }
}
