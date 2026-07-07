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
import static dev.restate.sdk.core.TestDefinitions.*;
import static dev.restate.sdk.core.legacy.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class UserFailuresTestSuite implements TestSuite {

  public static final String MY_ERROR = "my error";

  public static final String WHATEVER = "Whatever";

  // Insertion order intentionally differs from the HashMap iteration order of these keys, so a
  // non-order-preserving deserialization of the metadata map would produce a different string.
  public static final Map<String, String> ERROR_METADATA = new LinkedHashMap<>();

  static {
    ERROR_METADATA.put("region", "eu");
    ERROR_METADATA.put("zone", "a");
    ERROR_METADATA.put("cell", "c1");
    ERROR_METADATA.put("shard", "s1");
    ERROR_METADATA.put("host", "h1");
  }

  public static final String ERROR_METADATA_ITERATED = "region:eu,zone:a,cell:c1,shard:s1,host:h1";

  protected abstract TestInvocationBuilder throwIllegalStateException();

  protected abstract TestInvocationBuilder sideEffectThrowIllegalStateException(
      AtomicInteger nonTerminalExceptionsSeen);

  protected abstract TestInvocationBuilder throwTerminalException(int code, String message);

  protected abstract TestInvocationBuilder sideEffectThrowTerminalException(
      int code, String message);

  /**
   * The returned handler must: run a {@code ctx.run}, catch the {@link TerminalException} it
   * throws, iterate the exception's metadata map appending {@code key:value} entries joined by
   * {@code ,}, and return the resulting string.
   */
  protected abstract TestInvocationBuilder sideEffectThrowTerminalExceptionReturningMetadata();

  @Override
  public Stream<TestDefinition> definitions() {
    AtomicInteger nonTerminalExceptionsSeen = new AtomicInteger();

    return Stream.of(
        // Cases returning ErrorMessage
        this.throwIllegalStateException()
            .withInput(startMessage(1), inputCmd())
            .assertingOutput(containsOnlyExactErrorMessage(new IllegalStateException("Whatever"))),
        this.sideEffectThrowIllegalStateException(nonTerminalExceptionsSeen)
            .withInput(startMessage(1), inputCmd())
            .assertingOutput(
                msgs -> {
                  assertThat(msgs.get(1))
                      .satisfies(exactErrorMessage(new IllegalStateException("Whatever")));

                  // Check the counter has not been incremented
                  assertThat(nonTerminalExceptionsSeen).hasValue(0);
                }),

        // Cases completing the invocation with OutputStreamEntry.failure
        this.throwTerminalException(TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR)
            .withInput(startMessage(1), inputCmd())
            .expectingOutput(
                outputCmd(TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR), END_MESSAGE)
            .named("With internal error"),
        this.throwTerminalException(501, WHATEVER)
            .withInput(startMessage(1), inputCmd())
            .expectingOutput(outputCmd(501, WHATEVER), END_MESSAGE)
            .named("With unknown error"),
        this.sideEffectThrowTerminalException(
                TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR)
            .withInput(startMessage(1), inputCmd())
            .expectingOutput(
                Protocol.RunCommandMessage.newBuilder().setResultCompletionId(1),
                Protocol.ProposeRunCompletionMessage.newBuilder()
                    .setResultCompletionId(1)
                    .setFailure(failure(TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR)),
                suspensionMessage(1))
            .named("With internal error"),
        this.sideEffectThrowTerminalException(501, WHATEVER)
            .withInput(startMessage(1), inputCmd())
            .expectingOutput(
                Protocol.RunCommandMessage.newBuilder().setResultCompletionId(1),
                Protocol.ProposeRunCompletionMessage.newBuilder()
                    .setResultCompletionId(1)
                    .setFailure(failure(501, WHATEVER)),
                suspensionMessage(1))
            .named("With unknown error"),
        this.sideEffectThrowTerminalException(
                TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR)
            .withInput(
                startMessage(3),
                inputCmd(),
                runCmd(1),
                runCompletion(1, TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR))
            .expectingOutput(
                outputCmd(TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR), END_MESSAGE)
            .named("With internal error during replay"),
        this.sideEffectThrowTerminalException(501, WHATEVER)
            .withInput(startMessage(3), inputCmd(), runCmd(1), runCompletion(1, 501, WHATEVER))
            .expectingOutput(outputCmd(501, WHATEVER), END_MESSAGE)
            .named("With unknown error during replay"),
        this.sideEffectThrowTerminalExceptionReturningMetadata()
            .withInput(
                startMessage(3),
                inputCmd(),
                runCmd(1),
                runCompletion(
                    1, TerminalException.INTERNAL_SERVER_ERROR_CODE, MY_ERROR, ERROR_METADATA))
            .expectingOutput(outputCmd(ERROR_METADATA_ITERATED), END_MESSAGE)
            .named("Metadata map iterates deterministically"));
  }
}
