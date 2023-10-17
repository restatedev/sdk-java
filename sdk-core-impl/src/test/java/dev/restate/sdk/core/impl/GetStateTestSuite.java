package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import com.google.protobuf.Empty;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class GetStateTestSuite extends CoreTestRunner {

  protected abstract BindableService getStateGreeter();

  @Override
  protected Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::getStateGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "Francesco"))
            .usingAllThreadingModels()
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetStateEntry already completed"),
        testInvocation(this::getStateGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE").setEmpty(Empty.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Unknown")))
            .named("With GetStateEntry already completed empty"),
        testInvocation(this::getStateGreeter, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("Without GetStateEntry"),
        testInvocation(this::getStateGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till").build()),
                getStateMessage("STATE"))
            .usingAllThreadingModels()
            .expectingOutput(suspensionMessage(1))
            .named("With GetStateEntry not completed"),
        testInvocation(this::getStateGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE"),
                completionMessage(1, "Francesco"))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetStateEntry and completed with later CompletionFrame"),
        testInvocation(this::getStateGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                completionMessage(1, "Francesco"))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                getStateMessage("STATE"),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("Without GetStateEntry and completed with later CompletionFrame"));
  }
}
