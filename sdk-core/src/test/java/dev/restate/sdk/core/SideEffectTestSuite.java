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

import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.MessageType;
import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.data.Index;

public abstract class SideEffectTestSuite implements TestDefinitions.TestSuite {

  protected abstract TestInvocationBuilder sideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder namedSideEffect(String name, String sideEffectOutput);

  protected abstract TestInvocationBuilder consecutiveSideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder checkContextSwitching();

  protected abstract TestInvocationBuilder failingSideEffect(String name, String reason);

  protected abstract TestInvocationBuilder awaitAllSideEffectWithFirstFailing(
      String firstSideEffect, String secondSideEffect, String successValue, String failureReason);

  protected abstract TestInvocationBuilder awaitAllSideEffectWithSecondFailing(
      String firstSideEffect, String secondSideEffect, String successValue, String failureReason);

  protected abstract TestInvocationBuilder failingSideEffectWithRetryPolicy(
      String reason, RetryPolicy retryPolicy);

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        this.sideEffect("Francesco")
            .withInput(startMessage(1), inputCmd("Till"))
            .expectingOutput(runCmd(1), proposeRunCompletion(1, "Francesco"), suspensionMessage(1))
            .named("Run and propose completion"),
        this.sideEffect("Francesco")
            .withInput(startMessage(3), inputCmd("Till"), runCmd(1), runCompletion(1, "Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Hello Francesco"), END_MESSAGE)
            .named("Replay from completion"),
        this.namedSideEffect("get-my-name", "Francesco")
            .withInput(startMessage(1), inputCmd("Till"))
            .expectingOutput(
                runCmd(1, "get-my-name"),
                proposeRunCompletion(1, "Francesco"),
                suspensionMessage(1)),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(3), inputCmd("Till"), runCmd(1), runCompletion(1, "Francesco"))
            .expectingOutput(runCmd(2), proposeRunCompletion(2, "FRANCESCO"), suspensionMessage(2))
            .named("Suspends on second run"),
        this.consecutiveSideEffect("Francesco")
            .withInput(
                startMessage(5),
                inputCmd("Till"),
                runCmd(1),
                runCmd(2),
                runCompletion(1, "Francesco"),
                runCompletion(2, "FRANCESCO"))
            .expectingOutput(outputCmd("Hello FRANCESCO"), END_MESSAGE)
            .named("With optimization and ack on first and second side effect will resume"),
        this.failingSideEffect("my-side-effect", "some failure")
            .withInput(startMessage(1), inputCmd())
            .assertingOutput(
                msgs ->
                    assertThat(msgs)
                        .satisfiesExactly(
                            msg -> assertThat(msg).isEqualTo(runCmd(1, "my-side-effect")),
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
                                            "my-side-effect",
                                            Protocol.ErrorMessage::getRelatedCommandName)
                                        .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                        .contains("some failure"))))
            .named("Fail on first attempt"),
        this.failingSideEffect("my-side-effect", "some failure")
            .withInput(startMessage(2), inputCmd(), runCmd(1, "my-side-effect"))
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
                                .contains("some failure"))))
            .named("Fail on second attempt"),
        this.awaitAllSideEffectWithFirstFailing(
                "first-side-effect", "second-side-effect", "Francesco", "some failure")
            .withInput(startMessage(1), inputCmd())
            .assertingOutput(
                msgs -> {
                  // The thing here is, it depends on timing. Sometimes we might get the proposal
                  // for the succeeded one, sometimes not.
                  //
                  // So we need to take that in account in the assertions.
                  assertThat(msgs).size().isBetween(3, 4);
                  assertThat(msgs)
                      .satisfies(
                          msg -> assertThat(msg).isEqualTo(runCmd(1, "first-side-effect")),
                          Index.atIndex(0));
                  assertThat(msgs)
                      .satisfies(
                          msg -> assertThat(msg).isEqualTo(runCmd(2, "second-side-effect")),
                          Index.atIndex(1));

                  if (msgs.size() == 4) {
                    // If there's four messages, the third one must be the run completion proposal
                    assertThat(msgs)
                        .satisfies(
                            msg ->
                                assertThat(msg)
                                    .isEqualTo(proposeRunCompletion(2, "Francesco").build()),
                            Index.atIndex(2));
                  }

                  // Last message must be the error
                  assertThat(msgs)
                      .satisfies(
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
                                          "first-side-effect",
                                          Protocol.ErrorMessage::getRelatedCommandName)
                                      .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                      .contains("some failure")),
                          Index.atIndex(msgs.size() - 1));
                })
            .named("Fail the first side effect"),
        this.awaitAllSideEffectWithFirstFailing(
                "first-side-effect", "second-side-effect", "Francesco", "some failure")
            .withInput(
                startMessage(3),
                inputCmd(),
                runCmd(1, "first-side-effect"),
                runCmd(2, "second-side-effect"))
            .assertingOutput(
                msgs -> {
                  // The thing here is, it depends on timing. Sometimes we might get the proposal
                  // for the succeeded one, sometimes not.
                  //
                  // So we need to take that in account in the assertions.
                  assertThat(msgs).size().isBetween(1, 2);

                  if (msgs.size() == 2) {
                    // If there's four messages, the third one must be the run completion proposal
                    assertThat(msgs)
                        .satisfies(
                            msg ->
                                assertThat(msg)
                                    .isEqualTo(proposeRunCompletion(2, "Francesco").build()),
                            Index.atIndex(0));
                  }

                  // Last message must be the error
                  assertThat(msgs)
                      .satisfies(
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
                                          "first-side-effect",
                                          Protocol.ErrorMessage::getRelatedCommandName)
                                      .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                      .contains("some failure")),
                          Index.atIndex(msgs.size() - 1));
                })
            .named("Fail the first side effect during replay"),
        this.awaitAllSideEffectWithSecondFailing(
                "first-side-effect", "second-side-effect", "Francesco", "some failure")
            .withInput(startMessage(1), inputCmd())
            .assertingOutput(
                msgs -> {
                  // The thing here is, it depends on timing. Sometimes we might get the proposal
                  // for the succeeded one, sometimes not.
                  //
                  // So we need to take that in account in the assertions.
                  assertThat(msgs).size().isBetween(3, 4);
                  assertThat(msgs)
                      .satisfies(
                          msg -> assertThat(msg).isEqualTo(runCmd(1, "first-side-effect")),
                          Index.atIndex(0));
                  assertThat(msgs)
                      .satisfies(
                          msg -> assertThat(msg).isEqualTo(runCmd(2, "second-side-effect")),
                          Index.atIndex(1));

                  if (msgs.size() == 4) {
                    // If there's four messages, the third one must be the run completion proposal
                    assertThat(msgs)
                        .satisfies(
                            msg ->
                                assertThat(msg)
                                    .isEqualTo(proposeRunCompletion(1, "Francesco").build()),
                            Index.atIndex(2));
                  }

                  // Last message must be the error
                  assertThat(msgs)
                      .satisfies(
                          errorMessage(
                              errorMessage ->
                                  assertThat(errorMessage)
                                      .returns(
                                          TerminalException.INTERNAL_SERVER_ERROR_CODE,
                                          Protocol.ErrorMessage::getCode)
                                      .returns(2, Protocol.ErrorMessage::getRelatedCommandIndex)
                                      .returns(
                                          (int) MessageType.RunCommandMessage.encode(),
                                          Protocol.ErrorMessage::getRelatedCommandType)
                                      .returns(
                                          "second-side-effect",
                                          Protocol.ErrorMessage::getRelatedCommandName)
                                      .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                      .contains("some failure")),
                          Index.atIndex(msgs.size() - 1));
                })
            .named("Fail the second side effect"),
        this.failingSideEffectWithRetryPolicy(
                "some failure",
                RetryPolicy.exponential(Duration.ofMillis(100), 1.0f).setMaxAttempts(2))
            .withInput(startMessage(1).setRetryCountSinceLastStoredEntry(0), inputCmd())
            .onlyBidiStream()
            .assertingOutput(
                msgs ->
                    assertThat(msgs)
                        .satisfiesExactly(
                            msg -> assertThat(msg).isEqualTo(runCmd(1)),
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
                                        .contains("some failure"))))
            .named("Should fail as retryable error with the attached next retry delay"),
        this.failingSideEffectWithRetryPolicy(
                "some failure",
                RetryPolicy.exponential(Duration.ofMillis(100), 1.0f).setMaxAttempts(2))
            .withInput(startMessage(2).setRetryCountSinceLastStoredEntry(1), inputCmd(), runCmd(1))
            .expectingOutput(
                proposeRunCompletion(1, 500, "java.lang.IllegalStateException: some failure"),
                suspensionMessage(1))
            .named("Should convert retryable error to terminal"),
        // --- Other tests
        this.checkContextSwitching()
            .withInput(startMessage(1), inputCmd())
            .onlyBidiStream()
            .assertingOutput(
                actualOutputMessages ->
                    assertThat(actualOutputMessages).element(2).isEqualTo(suspensionMessage(1))));
  }
}
