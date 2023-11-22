// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.AssertUtils.*;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.core.Serde;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class StateMachineFailuresTestSuite implements TestSuite {

  protected abstract BindableService getState(AtomicInteger nonTerminalExceptionsSeen);

  protected abstract BindableService sideEffectFailure(Serde<Integer> serde);

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
  public Stream<TestDefinition> definitions() {
    AtomicInteger nonTerminalExceptionsSeenTest1 = new AtomicInteger();
    AtomicInteger nonTerminalExceptionsSeenTest2 = new AtomicInteger();

    return Stream.of(
        testInvocation(
                () -> this.getState(nonTerminalExceptionsSeenTest1), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("Something"))
            .assertingOutput(
                msgs -> {
                  assertThat(msgs)
                      .satisfiesExactly(
                          protocolExceptionErrorMessage(ProtocolException.JOURNAL_MISMATCH_CODE));
                  assertThat(nonTerminalExceptionsSeenTest1).hasValue(0);
                })
            .named("Protocol Exception"),
        testInvocation(
                () -> this.getState(nonTerminalExceptionsSeenTest2), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "This is not an integer"))
            .assertingOutput(
                msgs -> {
                  assertThat(msgs)
                      .satisfiesExactly(
                          errorMessageStartingWith(NumberFormatException.class.getCanonicalName()));
                  assertThat(nonTerminalExceptionsSeenTest2).hasValue(0);
                })
            .named("Serde error"),
        testInvocation(
                () -> this.sideEffectFailure(FAILING_SERIALIZATION_INTEGER_TYPE_TAG),
                GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .assertingOutput(
                containsOnly(
                    errorMessageStartingWith(IllegalStateException.class.getCanonicalName())))
            .named("Serde serialization error"),
        testInvocation(
                () -> this.sideEffectFailure(FAILING_DESERIALIZATION_INTEGER_TYPE_TAG),
                GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Java.SideEffectEntryMessage.newBuilder())
            .assertingOutput(
                containsOnly(
                    errorMessageStartingWith(IllegalStateException.class.getCanonicalName())))
            .named("Serde deserialization error"));
  }
}
