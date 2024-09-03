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
import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import java.time.Duration;
import java.util.stream.Stream;

public abstract class SideEffectTestSuite implements TestDefinitions.TestSuite {

  protected abstract TestInvocationBuilder sideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder namedSideEffect(String name, String sideEffectOutput);

  protected abstract TestInvocationBuilder consecutiveSideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder checkContextSwitching();

  protected abstract TestInvocationBuilder sideEffectGuard();

  protected abstract TestInvocationBuilder failingSideEffect(String name, String reason);

  protected abstract TestInvocationBuilder failingSideEffectWithRetryPolicy(
      String reason, RetryPolicy retryPolicy);

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        this.sideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"))
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder()
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("Francesco"))),
                suspensionMessage(1))
            .named("Without optimization suspends"),
        this.sideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"), ackMessage(1))
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder()
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("Francesco"))),
                outputMessage("Hello Francesco"),
                END_MESSAGE)
            .named("Without optimization and with acks returns"),
        this.namedSideEffect("get-my-name", "Francesco")
            .withInput(startMessage(1), inputMessage("Till"))
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder()
                    .setName("get-my-name")
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("Francesco"))),
                suspensionMessage(1)),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"))
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder()
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("Francesco"))),
                suspensionMessage(1))
            .named("With optimization and without ack on first side effect will suspend"),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"), ackMessage(1))
            .onlyUnbuffered()
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder()
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("Francesco"))),
                Protocol.RunEntryMessage.newBuilder()
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("FRANCESCO"))),
                suspensionMessage(2))
            .named("With optimization and ack on first side effect will suspend"),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"), ackMessage(1), ackMessage(2))
            .onlyUnbuffered()
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder()
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("Francesco"))),
                Protocol.RunEntryMessage.newBuilder()
                    .setValue(ByteString.copyFrom(TestSerdes.STRING.serialize("FRANCESCO"))),
                outputMessage("Hello FRANCESCO"),
                END_MESSAGE)
            .named("With optimization and ack on first and second side effect will resume"),
        this.failingSideEffect("my-side-effect", "some failure")
            .withInput(startMessage(1), inputMessage())
            .onlyUnbuffered()
            .assertingOutput(
                containsOnly(
                    errorMessage(
                        errorMessage ->
                            assertThat(errorMessage)
                                .returns(
                                    TerminalException.INTERNAL_SERVER_ERROR_CODE,
                                    Protocol.ErrorMessage::getCode)
                                .returns(1, Protocol.ErrorMessage::getRelatedEntryIndex)
                                .returns(
                                    (int) MessageType.RunEntryMessage.encode(),
                                    Protocol.ErrorMessage::getRelatedEntryType)
                                .returns(
                                    "my-side-effect", Protocol.ErrorMessage::getRelatedEntryName)
                                .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                .contains("some failure")))),
        this.failingSideEffectWithRetryPolicy(
                "some failure",
                RetryPolicy.exponential(Duration.ofMillis(100), 1.0f).setMaxAttempts(2))
            .withInput(startMessage(1).setRetryCountSinceLastStoredEntry(0), inputMessage())
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
                                .returns(1, Protocol.ErrorMessage::getRelatedEntryIndex)
                                .returns(
                                    (int) MessageType.RunEntryMessage.encode(),
                                    Protocol.ErrorMessage::getRelatedEntryType)
                                .returns(100L, Protocol.ErrorMessage::getNextRetryDelay)
                                .extracting(Protocol.ErrorMessage::getMessage, STRING)
                                .contains("java.lang.IllegalStateException: some failure"))))
            .named("Should fail as retryable error with the attached next retry delay"),
        this.failingSideEffectWithRetryPolicy(
                "some failure",
                RetryPolicy.exponential(Duration.ofMillis(100), 1.0f).setMaxAttempts(2))
            .withInput(
                startMessage(1).setRetryCountSinceLastStoredEntry(1), inputMessage(), ackMessage(1))
            .enablePreviewContext()
            .onlyUnbuffered()
            .expectingOutput(
                Protocol.RunEntryMessage.newBuilder()
                    .setFailure(
                        Util.toProtocolFailure(
                            500, "java.lang.IllegalStateException: some failure")),
                outputMessage(500, "java.lang.IllegalStateException: some failure"),
                END_MESSAGE)
            .named("Should convert retryable error to terminal"),
        // --- Other tests
        this.checkContextSwitching()
            .withInput(startMessage(1), inputMessage(), ackMessage(1))
            .onlyUnbuffered()
            .assertingOutput(
                actualOutputMessages -> {
                  assertThat(actualOutputMessages).hasSize(3);
                  assertThat(actualOutputMessages)
                      .element(0)
                      .asInstanceOf(type(Protocol.RunEntryMessage.class))
                      .returns(true, Protocol.RunEntryMessage::hasValue);
                  assertThat(actualOutputMessages).element(1).isEqualTo(outputMessage("Hello"));
                  assertThat(actualOutputMessages).element(2).isEqualTo(END_MESSAGE);
                }),
        this.sideEffectGuard()
            .withInput(startMessage(1), inputMessage("Till"))
            .assertingOutput(
                containsOnlyExactErrorMessage(ProtocolException.invalidSideEffectCall())));
  }
}
