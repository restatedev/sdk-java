// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.AssertUtils.exactErrorMessage;
import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.common.TerminalException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class UserFailuresTestSuite implements TestSuite {

  public static final String MY_ERROR = "my error";

  public static final String WHATEVER = "Whatever";

  protected abstract TestInvocationBuilder throwIllegalStateException();

  protected abstract TestInvocationBuilder sideEffectThrowIllegalStateException(
      AtomicInteger nonTerminalExceptionsSeen);

  protected abstract TestInvocationBuilder throwTerminalException(
      TerminalException.Code code, String message);

  protected abstract TestInvocationBuilder sideEffectThrowTerminalException(
      TerminalException.Code code, String message);

  @Override
  public Stream<TestDefinition> definitions() {
    AtomicInteger nonTerminalExceptionsSeen = new AtomicInteger();

    return Stream.of(
        // Cases returning ErrorMessage
        this.throwIllegalStateException()
            .withInput(startMessage(1), inputMessage())
            .assertingOutput(containsOnlyExactErrorMessage(new IllegalStateException("Whatever"))),
        this.sideEffectThrowIllegalStateException(nonTerminalExceptionsSeen)
            .withInput(startMessage(1), inputMessage())
            .assertingOutput(
                msgs -> {
                  assertThat(msgs)
                      .satisfiesExactly(exactErrorMessage(new IllegalStateException("Whatever")));

                  // Check the counter has not been incremented
                  assertThat(nonTerminalExceptionsSeen).hasValue(0);
                }),

        // Cases completing the invocation with OutputStreamEntry.failure
        this.throwTerminalException(TerminalException.Code.INTERNAL, MY_ERROR)
            .withInput(startMessage(1), inputMessage())
            .expectingOutput(outputMessage(TerminalException.Code.INTERNAL, MY_ERROR), END_MESSAGE)
            .named("With internal error"),
        this.throwTerminalException(TerminalException.Code.UNKNOWN, WHATEVER)
            .withInput(startMessage(1), inputMessage())
            .expectingOutput(outputMessage(TerminalException.Code.UNKNOWN, WHATEVER), END_MESSAGE)
            .named("With unknown error"),
        this.sideEffectThrowTerminalException(TerminalException.Code.INTERNAL, MY_ERROR)
            .withInput(startMessage(1), inputMessage(), ackMessage(1))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setFailure(Util.toProtocolFailure(TerminalException.Code.INTERNAL, MY_ERROR)),
                outputMessage(TerminalException.Code.INTERNAL, MY_ERROR),
                END_MESSAGE)
            .named("With internal error"),
        this.sideEffectThrowTerminalException(TerminalException.Code.UNKNOWN, WHATEVER)
            .withInput(startMessage(1), inputMessage(), ackMessage(1))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setFailure(Util.toProtocolFailure(TerminalException.Code.UNKNOWN, WHATEVER)),
                outputMessage(TerminalException.Code.UNKNOWN, WHATEVER),
                END_MESSAGE)
            .named("With unknown error"));
  }
}
