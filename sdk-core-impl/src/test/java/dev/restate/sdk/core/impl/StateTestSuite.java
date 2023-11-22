// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.testInvocation;

import com.google.protobuf.Empty;
import dev.restate.sdk.core.impl.TestDefinitions.TestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class StateTestSuite implements TestSuite {

  protected abstract BindableService getState();

  protected abstract BindableService getAndSetState();

  protected abstract BindableService setNullState();

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "Francesco"))
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetStateEntry already completed"),
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE").setEmpty(Empty.getDefaultInstance()))
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Unknown")))
            .named("With GetStateEntry already completed empty"),
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("Without GetStateEntry"),
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till").build()),
                getStateMessage("STATE"))
            .expectingOutput(suspensionMessage(1))
            .named("With GetStateEntry not completed"),
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE"),
                completionMessage(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetStateEntry and completed with later CompletionFrame"),
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                completionMessage(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                getStateMessage("STATE"),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("Without GetStateEntry and completed with later CompletionFrame"),
        testInvocation(this::getAndSetState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "Francesco"),
                setStateMessage("STATE", "Till"))
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetState and SetState"),
        testInvocation(this::getAndSetState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "Francesco"))
            .expectingOutput(
                setStateMessage("STATE", "Till"),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetState already completed"),
        testInvocation(this::getAndSetState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                completionMessage(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                getStateMessage("STATE"),
                setStateMessage("STATE", "Till"),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("With GetState completed later"),
        testInvocation(this::setNullState, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .assertingOutput(containsOnlyExactErrorMessage(new NullPointerException())));
  }
}
