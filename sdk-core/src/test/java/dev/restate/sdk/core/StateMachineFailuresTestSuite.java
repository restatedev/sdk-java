// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.AssertUtils.errorMessageStartingWith;
import static dev.restate.sdk.core.AssertUtils.protocolExceptionErrorMessage;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.serde.Serde;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;

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
            .withInput(startMessage(2), inputMessage("Till"), getStateMessage("Something"))
            .assertingOutput(
                msgs -> {
                  Assertions.assertThat(msgs)
                      .satisfiesExactly(
                          protocolExceptionErrorMessage(ProtocolException.JOURNAL_MISMATCH_CODE));
                  assertThat(nonTerminalExceptionsSeenTest1).hasValue(0);
                })
            .named("Protocol Exception"),
        this.getState(nonTerminalExceptionsSeenTest2)
            .withInput(
                startMessage(2),
                inputMessage("Till"),
                getStateMessage("STATE", "This is not an integer"))
            .assertingOutput(
                msgs -> {
                  Assertions.assertThat(msgs)
                      .satisfiesExactly(
                          errorMessageStartingWith(NumberFormatException.class.getCanonicalName()));
                  assertThat(nonTerminalExceptionsSeenTest2).hasValue(0);
                })
            .named("Serde error"),
        this.sideEffectFailure(FAILING_SERIALIZATION_INTEGER_TYPE_TAG)
            .withInput(startMessage(1), inputMessage("Till"))
            .assertingOutput(
                AssertUtils.containsOnly(
                    errorMessageStartingWith(IllegalStateException.class.getCanonicalName())))
            .named("Serde serialization error"),
        this.sideEffectFailure(FAILING_DESERIALIZATION_INTEGER_TYPE_TAG)
            .withInput(startMessage(2), inputMessage("Till"), Protocol.RunEntryMessage.newBuilder())
            .assertingOutput(
                AssertUtils.containsOnly(
                    errorMessageStartingWith(IllegalStateException.class.getCanonicalName())))
            .named("Serde deserialization error"));
  }
}
