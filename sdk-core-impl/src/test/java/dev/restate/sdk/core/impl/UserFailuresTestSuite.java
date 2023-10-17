package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import io.grpc.BindableService;
import io.grpc.Status;
import java.util.stream.Stream;

public abstract class UserFailuresTestSuite extends CoreTestRunner {

  public static final Status INTERNAL_MY_ERROR = Status.INTERNAL.withDescription("my error");

  public static final Status UNKNOWN_MY_ERROR = Status.UNKNOWN.withDescription("Whatever");

  protected abstract BindableService throwIllegalStateException();

  protected abstract BindableService sideEffectThrowIllegalStateException();

  protected abstract BindableService throwStatusRuntimeException(Status status);

  protected abstract BindableService sideEffectThrowStatusRuntimeException(Status status);

  @Override
  protected Stream<TestDefinition> definitions() {
    return Stream.of(
        // Cases returning ErrorMessage
        testInvocation(this::throwIllegalStateException, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .assertingOutput(containsOnlyExactErrorMessage(new IllegalStateException("Whatever"))),
        testInvocation(this::sideEffectThrowIllegalStateException, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .assertingOutput(containsOnlyExactErrorMessage(new IllegalStateException("Whatever"))),

        // Cases completing the invocation with OutputStreamEntry.failure
        testInvocation(
                () -> this.throwStatusRuntimeException(INTERNAL_MY_ERROR),
                GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(INTERNAL_MY_ERROR))
            .named("With internal error"),
        testInvocation(
                () -> this.throwStatusRuntimeException(UNKNOWN_MY_ERROR),
                GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(UNKNOWN_MY_ERROR))
            .named("With unknown error"),
        testInvocation(
                () -> this.sideEffectThrowStatusRuntimeException(INTERNAL_MY_ERROR),
                GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setFailure(Util.toProtocolFailure(INTERNAL_MY_ERROR)),
                outputMessage(INTERNAL_MY_ERROR))
            .named("With internal error"),
        testInvocation(
                () -> this.sideEffectThrowStatusRuntimeException(UNKNOWN_MY_ERROR),
                GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setFailure(Util.toProtocolFailure(UNKNOWN_MY_ERROR)),
                outputMessage(UNKNOWN_MY_ERROR))
            .named("With unknown error"));
  }
}
