// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.statemachine.ProtoUtils.*;

import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.util.Map;
import java.util.stream.Stream;

public abstract class CallTestSuite implements TestSuite {

  protected abstract TestDefinitions.TestInvocationBuilder oneWayCall(
      Target target, String idempotencyKey, Map<String, String> headers, Slice body);

  protected abstract TestDefinitions.TestInvocationBuilder implicitCancellation(
      Target target, Slice body);

  private static String IDEMPOTENCY_KEY = "my-idempotency-key";
  private static Map<String, String> HEADERS = Map.of("abc", "123", "fge", "456");
  private static Slice BODY = Slice.wrap("bla");

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        oneWayCall(GREETER_SERVICE_TARGET, IDEMPOTENCY_KEY, HEADERS, BODY)
            .withInput(startMessage(1), inputCmd())
            .expectingOutput(
                oneWayCallCmd(1, GREETER_SERVICE_TARGET, IDEMPOTENCY_KEY, HEADERS, BODY),
                outputCmd(),
                END_MESSAGE),
        oneWayCall(GREETER_SERVICE_TARGET, IDEMPOTENCY_KEY, HEADERS, BODY)
            .withInput(
                startMessage(3),
                inputCmd(),
                oneWayCallCmd(1, GREETER_SERVICE_TARGET, IDEMPOTENCY_KEY, HEADERS, BODY),
                callInvocationIdCompletion(1, "abc"))
            .expectingOutput(outputCmd(), END_MESSAGE)
            .named("With invocation ID completion"),
        oneWayCall(GREETER_VIRTUAL_OBJECT_TARGET, IDEMPOTENCY_KEY, HEADERS, BODY)
            .withInput(startMessage(1), inputCmd())
            .expectingOutput(
                oneWayCallCmd(1, GREETER_VIRTUAL_OBJECT_TARGET, IDEMPOTENCY_KEY, HEADERS, BODY),
                outputCmd(),
                END_MESSAGE),
        implicitCancellation(GREETER_SERVICE_TARGET, BODY)
            .withInput(
                startMessage(3),
                inputCmd(),
                callCmd(1, 2, GREETER_SERVICE_TARGET, BODY.toByteArray()),
                CANCELLATION_SIGNAL)
            .onlyBidiStream()
            .expectingOutput(Protocol.SuspensionMessage.newBuilder().addWaitingCompletions(1))
            .named("Suspends on waiting the invocation id"),
        implicitCancellation(GREETER_SERVICE_TARGET, BODY)
            .withInput(
                startMessage(4),
                inputCmd(),
                callCmd(1, 2, GREETER_SERVICE_TARGET, BODY.toByteArray()),
                CANCELLATION_SIGNAL,
                callInvocationIdCompletion(1, "my-id"))
            .onlyBidiStream()
            .expectingOutput(
                sendCancelSignal("my-id"),
                outputCmd(new TerminalException(TerminalException.CANCELLED_CODE)),
                END_MESSAGE)
            .named("Surfaces cancellation"));
  }
}
