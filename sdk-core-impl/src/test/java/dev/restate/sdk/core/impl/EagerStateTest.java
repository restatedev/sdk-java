package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.MessageHeader.*;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.AssertionsForClassTypes.entry;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.stream.Stream;

class EagerStateTest extends CoreTestRunner {

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

  private static class Get extends GreeterGrpc.GreeterImplBase implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String state = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(state).build());
      responseObserver.onCompleted();
    }
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

  private static class GetClearAndGet extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String oldState = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      ctx.clear(StateKey.of("STATE", TypeTag.STRING_UTF8));
      assert ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).isEmpty();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage(oldState).build());
      responseObserver.onCompleted();
    }
  }

  private static final short PARTIAL_STATE = PARTIAL_STATE_FLAG;
  private static final short COMPLETE_STATE = 0;
  private static final Map.Entry<String, String> STATE_FRANCESCO = entry("STATE", "Francesco");
  private static final MessageLite INPUT_TILL = inputMessage(greetingRequest("Till"));
  private static final MessageLite GET_STATE_FRANCESCO = getStateMessage("STATE", "Francesco");
  private static final MessageLite GET_STATE_FRANCESCO_TILL =
      getStateMessage("STATE", "FrancescoTill");
  private static final MessageLite SET_STATE_FRANCESCO_TILL =
      setStateMessage("STATE", "FrancescoTill");
  private static final MessageLite OUTPUT_FRANCESCO = outputMessage(greetingResponse("Francesco"));
  private static final MessageLite OUTPUT_FRANCESCO_TILL =
      outputMessage(greetingResponse("FrancescoTill"));

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new GetEmpty(), GreeterGrpc.getGreetMethod())
            .withInput(COMPLETE_STATE, startMessage(1))
            .withInput(INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(getStateEmptyMessage("STATE"), outputMessage(greetingResponse("true")))
            .named("With complete state"),
        testInvocation(new GetEmpty(), GreeterGrpc.getGreetMethod())
            .withInput(PARTIAL_STATE, startMessage(1))
            .withInput(INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("With partial state"),
        testInvocation(new GetEmpty(), GreeterGrpc.getGreetMethod())
            .withInput(PARTIAL_STATE, startMessage(2))
            .withInput(INPUT_TILL, getStateEmptyMessage("STATE"))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("true")))
            .named("Resume with partial state"),
        testInvocation(new Get(), GreeterGrpc.getGreetMethod())
            .withInput(COMPLETE_STATE, startMessage(1, STATE_FRANCESCO))
            .withInput(INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(GET_STATE_FRANCESCO, OUTPUT_FRANCESCO)
            .named("With complete state"),
        testInvocation(new Get(), GreeterGrpc.getGreetMethod())
            .withInput(PARTIAL_STATE, startMessage(1, STATE_FRANCESCO))
            .withInput(INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(GET_STATE_FRANCESCO, OUTPUT_FRANCESCO)
            .named("With partial state"),
        testInvocation(new Get(), GreeterGrpc.getGreetMethod())
            .withInput(PARTIAL_STATE, startMessage(1))
            .withInput(INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("With partial state without the state entry"),
        testInvocation(new GetAppendAndGet(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1, STATE_FRANCESCO), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(
                GET_STATE_FRANCESCO,
                SET_STATE_FRANCESCO_TILL,
                GET_STATE_FRANCESCO_TILL,
                OUTPUT_FRANCESCO_TILL)
            .named("With state in the state_map"),
        testInvocation(new GetAppendAndGet(), GreeterGrpc.getGreetMethod())
            .withInput(PARTIAL_STATE, startMessage(1))
            .withInput(INPUT_TILL, completionMessage(1, "Francesco"))
            .usingAllThreadingModels()
            .expectingOutput(
                getStateMessage("STATE"),
                SET_STATE_FRANCESCO_TILL,
                GET_STATE_FRANCESCO_TILL,
                OUTPUT_FRANCESCO_TILL)
            .named("With partial state on the first get"),
        testInvocation(new GetClearAndGet(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1, STATE_FRANCESCO), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(
                GET_STATE_FRANCESCO,
                clearStateMessage("STATE"),
                getStateEmptyMessage("STATE"),
                OUTPUT_FRANCESCO)
            .named("With state in the state_map"),
        testInvocation(new GetClearAndGet(), GreeterGrpc.getGreetMethod())
            .withInput(PARTIAL_STATE, startMessage(1))
            .withInput(INPUT_TILL, completionMessage(1, "Francesco"))
            .usingAllThreadingModels()
            .expectingOutput(
                getStateMessage("STATE"),
                clearStateMessage("STATE"),
                getStateEmptyMessage("STATE"),
                OUTPUT_FRANCESCO)
            .named("With partial state on the first get"));
  }
}
