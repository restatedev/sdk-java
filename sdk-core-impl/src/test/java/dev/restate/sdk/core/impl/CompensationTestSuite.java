package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import io.grpc.BindableService;
import io.grpc.Status;
import java.util.stream.Stream;

public abstract class CompensationTestSuite extends CoreTestRunner {

  protected abstract BindableService throwManually();

  protected abstract BindableService nonTerminalErrorDoesntExecuteCompensations();

  protected abstract BindableService callCompensate();

  protected abstract BindableService illegalGetStateWithinCompensation();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::throwManually, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .expectingOutput(
                setStateMessage("message", "none"), outputMessage(Status.FAILED_PRECONDITION)),
        testInvocation(
                this::nonTerminalErrorDoesntExecuteCompensations, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .assertingOutput(containsOnlyExactErrorMessage(new RuntimeException("non-terminal"))),
        testInvocation(this::callCompensate, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, Status.FAILED_PRECONDITION))
            .usingAllThreadingModels()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                setStateMessage("message", "none"),
                outputMessage(Status.FAILED_PRECONDITION)),
        testInvocation(this::illegalGetStateWithinCompensation, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .assertingOutput(
                containsOnlyExactErrorMessage(ProtocolException.invalidCallWithinCompensation())));
  }
}
