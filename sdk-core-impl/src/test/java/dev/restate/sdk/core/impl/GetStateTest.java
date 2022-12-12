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

class GetStateTest extends CoreTestRunner {

  private static class GetStateGreeter extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String state = RestateContext.current().get(StateKey.of("STATE", TypeTag.STRING_UTF8)).get();

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                orderMessage(0),
                getStateMessage("STATE", "Francesco"))
            .usingAllThreadingModels()
            .expectingOutput(
                orderMessage(2),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetStateEntry already completed, without order message published"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(4),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                orderMessage(0),
                getStateMessage("STATE", "Francesco"),
                orderMessage(2))
            .usingAllThreadingModels()
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetStateEntry already completed, with order message published"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .expectingOutput(orderMessage(0), getStateMessage("STATE"))
            .named("Without GetStateEntry"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder().setName("Till").build()),
                orderMessage(0),
                getStateMessage("STATE"))
            .usingAllThreadingModels()
            .expectingNoOutput()
            .named("With GetStateEntry not completed"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                orderMessage(0),
                getStateMessage("STATE"),
                completionMessage(2, "Francesco"))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                orderMessage(2),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetStateEntry and completed with later CompletionFrame"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                orderMessage(0),
                completionMessage(2, "Francesco"))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                getStateMessage("STATE"),
                orderMessage(2),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("Without GetStateEntry and completed with later CompletionFrame"));
  }
}
