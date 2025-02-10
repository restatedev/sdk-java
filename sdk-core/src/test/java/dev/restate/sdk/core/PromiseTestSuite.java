// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.TestDefinitions.*;
import static dev.restate.sdk.core.generated.protocol.Protocol.*;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;

import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.generated.protocol.Protocol.GetPromiseCompletionNotificationMessage;
import dev.restate.sdk.core.statemachine.ProtoUtils;
import dev.restate.sdk.types.TerminalException;
import java.util.stream.Stream;

public abstract class PromiseTestSuite implements TestSuite {

  private static final String PROMISE_KEY = "my-prom";

  protected abstract TestInvocationBuilder awaitPromise(String promiseKey);

  protected abstract TestInvocationBuilder awaitPeekPromise(
      String promiseKey, String emptyCaseReturnValue);

  protected abstract TestInvocationBuilder awaitIsPromiseCompleted(String promiseKey);

  protected abstract TestInvocationBuilder awaitResolvePromise(
      String promiseKey, String completionValue);

  protected abstract TestInvocationBuilder awaitRejectPromise(
      String promiseKey, String rejectReason);

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        // --- Await promise
        this.awaitPromise(PROMISE_KEY)
            .withInput(
                startMessage(1),
                inputCmd(),
                GetPromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setValue(value("my value")))
            .expectingOutput(getPromiseCmd(1, PROMISE_KEY), outputCmd("my value"), END_MESSAGE)
            .named("Completed with success"),
        this.awaitPromise(PROMISE_KEY)
            .withInput(
                startMessage(1),
                inputCmd(),
                GetPromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setFailure(ProtoUtils.failure(new TerminalException("myerror"))))
            .expectingOutput(
                getPromiseCmd(1, PROMISE_KEY),
                outputCmd(new TerminalException("myerror")),
                END_MESSAGE)
            .named("Completed with failure"),
        // --- Peek promise
        this.awaitPeekPromise(PROMISE_KEY, "null")
            .withInput(
                startMessage(1),
                inputCmd(),
                PeekPromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setValue(value("my value")))
            .expectingOutput(peekPromiseCmd(1, PROMISE_KEY), outputCmd("my value"), END_MESSAGE)
            .named("Completed with success"),
        this.awaitPeekPromise(PROMISE_KEY, "null")
            .withInput(
                startMessage(1),
                inputCmd(),
                PeekPromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setFailure(ProtoUtils.failure(new TerminalException("myerror"))))
            .expectingOutput(
                peekPromiseCmd(1, PROMISE_KEY),
                outputCmd(new TerminalException("myerror")),
                END_MESSAGE)
            .named("Completed with failure"),
        this.awaitPeekPromise(PROMISE_KEY, "null")
            .withInput(
                startMessage(1),
                inputCmd(),
                PeekPromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setVoid(Protocol.Void.getDefaultInstance()))
            .expectingOutput(peekPromiseCmd(1, PROMISE_KEY), outputCmd("null"), END_MESSAGE)
            .named("Completed with null"),
        // --- Promise is completed
        this.awaitIsPromiseCompleted(PROMISE_KEY)
            .withInput(
                startMessage(1),
                inputCmd(),
                PeekPromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setValue(value("my value")))
            .expectingOutput(
                peekPromiseCmd(1, PROMISE_KEY), outputCmd(TestSerdes.BOOLEAN, true), END_MESSAGE)
            .named("Completed with success"),
        this.awaitIsPromiseCompleted(PROMISE_KEY)
            .withInput(
                startMessage(1),
                inputCmd(),
                PeekPromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setVoid(Protocol.Void.getDefaultInstance()))
            .expectingOutput(
                peekPromiseCmd(1, PROMISE_KEY), outputCmd(TestSerdes.BOOLEAN, false), END_MESSAGE)
            .named("Not completed"),
        // --- Promise resolve
        this.awaitResolvePromise(PROMISE_KEY, "my val")
            .withInput(
                startMessage(1),
                inputCmd(),
                CompletePromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setVoid(Protocol.Void.getDefaultInstance())
                    .build())
            .expectingOutput(
                completePromiseCmd(1, PROMISE_KEY, "my val"),
                outputCmd(TestSerdes.BOOLEAN, true),
                END_MESSAGE)
            .named("resolve succeeds"),
        this.awaitResolvePromise(PROMISE_KEY, "my val")
            .withInput(
                startMessage(1),
                inputCmd(),
                CompletePromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setFailure(failure(new TerminalException("cannot write promise")))
                    .build())
            .expectingOutput(
                completePromiseCmd(1, PROMISE_KEY, "my val"),
                outputCmd(TestSerdes.BOOLEAN, false),
                END_MESSAGE)
            .named("resolve fails"),
        // --- Promise reject
        this.awaitRejectPromise(PROMISE_KEY, "my failure")
            .withInput(
                startMessage(1),
                inputCmd(),
                CompletePromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setVoid(Protocol.Void.getDefaultInstance())
                    .build())
            .expectingOutput(
                completePromiseCmd(1, PROMISE_KEY, new TerminalException("my failure")),
                outputCmd(TestSerdes.BOOLEAN, true),
                END_MESSAGE)
            .named("resolve succeeds"),
        this.awaitRejectPromise(PROMISE_KEY, "my failure")
            .withInput(
                startMessage(1),
                inputCmd(),
                CompletePromiseCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setFailure(failure(new TerminalException("cannot write promise")))
                    .build())
            .expectingOutput(
                completePromiseCmd(1, PROMISE_KEY, new TerminalException("my failure")),
                outputCmd(TestSerdes.BOOLEAN, false),
                END_MESSAGE)
            .named("resolve fails"));
  }
}
