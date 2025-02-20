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

import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.serde.Serde;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class StateMachineFailuresTestSuite implements TestDefinitions.TestSuite {

  protected abstract TestInvocationBuilder getState(AtomicInteger nonTerminalExceptionsSeen);

  protected abstract TestInvocationBuilder sideEffectFailure(Serde<Integer> serde);

  private static final Serde<Integer> FAILING_SERIALIZATION_INTEGER_TYPE_TAG =
      Serde.using(
          i -> {
            throw new IllegalStateException("Cannot serialize integer");
          },
          b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8)));

  private static final Serde<Integer> FAILING_DESERIALIZATION_INTEGER_TYPE_TAG =
      Serde.using(
          i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
          b -> {
            throw new IllegalStateException("Cannot deserialize integer");
          });

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    AtomicInteger nonTerminalExceptionsSeenTest1 = new AtomicInteger();
    AtomicInteger nonTerminalExceptionsSeenTest2 = new AtomicInteger();

    return Stream.of(
        this.getState(nonTerminalExceptionsSeenTest1)
            .withInput(startMessage(2), inputCmd("Till"), getLazyStateCmd(1, "Something"))
            .assertingOutput(
                msgs -> {
                  assertThat(msgs)
                      .satisfiesExactly(
                          protocolExceptionErrorMessage(ProtocolException.JOURNAL_MISMATCH_CODE));
                  assertThat(nonTerminalExceptionsSeenTest1).hasValue(0);
                })
            .named("Protocol Exception"),
        this.getState(nonTerminalExceptionsSeenTest2)
            .withInput(
                startMessage(2),
                inputCmd("Till"),
                getLazyStateCmd(1, "STATE"),
                getLazyStateCompletion(1, "This is not an integer"))
            .assertingOutput(
                msgs -> {
                  assertThat(msgs)
                      .satisfiesExactly(
                          errorDescriptionStartingWith(
                              NumberFormatException.class.getCanonicalName()));
                  assertThat(nonTerminalExceptionsSeenTest2).hasValue(0);
                })
            .named("Serde error"),
        this.sideEffectFailure(FAILING_SERIALIZATION_INTEGER_TYPE_TAG)
            .withInput(startMessage(1), inputCmd("Till"))
            .assertingOutput(
                msgs ->
                    assertThat(msgs.get(1))
                        .satisfies(
                            errorDescriptionStartingWith(
                                IllegalStateException.class.getCanonicalName())))
            .named("Serde serialization error"),
        this.sideEffectFailure(FAILING_DESERIALIZATION_INTEGER_TYPE_TAG)
            .withInput(
                startMessage(3),
                inputCmd("Till"),
                runCmd(1),
                Protocol.RunCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setValue(Protocol.Value.getDefaultInstance())
                    .build())
            .assertingOutput(
                containsOnly(
                    errorDescriptionStartingWith(IllegalStateException.class.getCanonicalName())))
            .named("Serde deserialization error"));
  }
}
