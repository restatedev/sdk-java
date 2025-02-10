// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.AssertUtils.*;
import static dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.MessageType;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.TerminalException;
import java.time.Duration;
import java.util.stream.Stream;

public abstract class SideEffectTestSuite implements TestDefinitions.TestSuite {

  protected abstract TestInvocationBuilder sideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder namedSideEffect(String name, String sideEffectOutput);

  protected abstract TestInvocationBuilder consecutiveSideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder checkContextSwitching();

  protected abstract TestInvocationBuilder failingSideEffect(String name, String reason);

  protected abstract TestInvocationBuilder failingSideEffectWithRetryPolicy(
      String reason, RetryPolicy retryPolicy);

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        this.sideEffect("Francesco")
            .withInput(startMessage(1), inputCmd("Till"))
            .expectingOutput(runCmd(1), proposeRunCompletion(1, "Francesco"), suspensionMessage(1))
            .named("Without optimization suspends"),
        this.sideEffect("Francesco")
            .withInput(startMessage(1), inputCmd("Till"), runCompletion(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                runCmd(1),
                proposeRunCompletion(1, "Francesco"),
                outputCmd("Hello Francesco"),
                END_MESSAGE)
            .named("Without optimization and with acks returns"),
        this.namedSideEffect("get-my-name", "Francesco")
            .withInput(startMessage(1), inputCmd("Till"))
            .expectingOutput(
                runCmd(1, "get-my-name"),
                proposeRunCompletion(1, "Francesco"),
                suspensionMessage(1)),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(1), inputCmd("Till"), runCompletion(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                runCmd(1),
                proposeRunCompletion(1, "Francesco"),
                runCmd(2),
                proposeRunCompletion(2, "FRANCESCO"),
                suspensionMessage(2))
            .named("With optimization and ack on first side effect will suspend"),
        this.consecutiveSideEffect("Francesco")
            .withInput(
                startMessage(1),
                inputCmd("Till"),
                runCompletion(1, "Francesco"),
                runCompletion(2, "FRANCESCO"))
            .onlyUnbuffered()
            .expectingOutput(
                runCmd(1),
                proposeRunCompletion(1, "Francesco"),
                runCmd(2),
                proposeRunCompletion(2, "FRANCESCO"),
                outputCmd("Hello FRANCESCO"),
                END_MESSAGE)
            .named("With optimization and ack on first and second side effect will resume"),
        this.failingSideEffect("my-side-effect", "some failure")
            .withInput(startMessage(1), inputCmd())
            .onlyUnbuffered()
            .assertingOutput(
                containsOnly(
                    errorMessage(
                        errorMessage ->
                            assertThat(errorMessage)
                                .returns(
                                    TerminalException.INTERNAL_SERVER_ERROR_CODE,
                                    Protocol.ErrorMessage::getCode)
                                .returns(1, Protocol.ErrorMessage::getRelatedCommandIndex)
                                .returns(
                                    (int) MessageType.RunCommandMessage.encode(),
                                    Protocol.ErrorMessage::getRelatedCommandType)
                                .returns(
                                    "my-side-effect", Protocol.ErrorMessage::getRelatedCommandName)
                                .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                .contains("some failure")))),
        this.failingSideEffectWithRetryPolicy(
                "some failure",
                RetryPolicy.exponential(Duration.ofMillis(100), 1.0f).setMaxAttempts(2))
            .withInput(startMessage(1).setRetryCountSinceLastStoredEntry(0), inputCmd())
            .enablePreviewContext()
            .onlyUnbuffered()
            .assertingOutput(
                containsOnly(
                    errorMessage(
                        errorMessage ->
                            assertThat(errorMessage)
                                .returns(
                                    TerminalException.INTERNAL_SERVER_ERROR_CODE,
                                    Protocol.ErrorMessage::getCode)
                                .returns(1, Protocol.ErrorMessage::getRelatedCommandIndex)
                                .returns(
                                    (int) MessageType.RunCommandMessage.encode(),
                                    Protocol.ErrorMessage::getRelatedCommandType)
                                .returns(100L, Protocol.ErrorMessage::getNextRetryDelay)
                                .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                .contains("java.lang.IllegalStateException: some failure"))))
            .named("Should fail as retryable error with the attached next retry delay"),
        this.failingSideEffectWithRetryPolicy(
                "some failure",
                RetryPolicy.exponential(Duration.ofMillis(100), 1.0f).setMaxAttempts(2))
            .withInput(
                startMessage(1).setRetryCountSinceLastStoredEntry(1),
                inputCmd(),
                runCompletion(1, 500, "java.lang.IllegalStateException: some failure"))
            .enablePreviewContext()
            .onlyUnbuffered()
            .expectingOutput(
                runCmd(1),
                proposeRunCompletion(1, 500, "java.lang.IllegalStateException: some failure"),
                outputCmd(500, "java.lang.IllegalStateException: some failure"),
                END_MESSAGE)
            .named("Should convert retryable error to terminal"),
        // --- Other tests
        this.checkContextSwitching()
            .withInput(startMessage(1), inputCmd())
            .onlyUnbuffered()
            .assertingOutput(
                actualOutputMessages -> {
                  assertThat(actualOutputMessages).element(2).isEqualTo(suspensionMessage(1));
                }));
  }
}
