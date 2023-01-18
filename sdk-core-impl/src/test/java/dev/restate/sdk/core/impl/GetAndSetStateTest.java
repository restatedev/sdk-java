package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.stream.Stream;

class GetAndSetStateTest extends CoreTestRunner {

  private static class GetAndSetGreeter extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = RestateContext.current();

      String state = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      ctx.set(StateKey.of("STATE", TypeTag.STRING_UTF8), request.getName());

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new GetAndSetGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "Francesco"),
                setStateMessage("STATE", "Till"))
            .usingAllThreadingModels()
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetState and SetState"),
        testInvocation(new GetAndSetGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "Francesco"))
            .usingAllThreadingModels()
            .expectingOutput(
                setStateMessage("STATE", "Till"),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetState already completed"),
        testInvocation(new GetAndSetGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                completionMessage(1, "Francesco"))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                getStateMessage("STATE"),
                setStateMessage("STATE", "Till"),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetState completed later"));
  }
}
