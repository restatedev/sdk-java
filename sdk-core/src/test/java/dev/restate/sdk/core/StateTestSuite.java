// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.testInvocation;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Empty;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class StateTestSuite implements TestDefinitions.TestSuite {

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
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", new TerminalException(TerminalException.Code.CANCELLED)))
            .expectingOutput(outputMessage(new TerminalException(TerminalException.Code.CANCELLED)))
            .named("Failed GetStateEntry"),
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                completionMessage(1, new TerminalException(TerminalException.Code.CANCELLED)))
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites.get(0)).isInstanceOf(Protocol.GetStateEntryMessage.class);
                  assertThat(messageLites.get(1))
                      .isEqualTo(
                          outputMessage(new TerminalException(TerminalException.Code.CANCELLED)));
                })
            .named("Failing GetStateEntry"),
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
            .assertingOutput(
                AssertUtils.containsOnlyExactErrorMessage(new NullPointerException())));
  }
}
