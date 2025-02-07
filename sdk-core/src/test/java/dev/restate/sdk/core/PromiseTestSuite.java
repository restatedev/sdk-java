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
import static dev.restate.sdk.core.TestDefinitions.*;

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
            .withInput(startMessage(1), inputMessage(), completionMessage(1, "my value"))
            .expectingOutput(getPromise(PROMISE_KEY), outputMessage("my value"), END_MESSAGE)
            .named("Completed with success"),
        this.awaitPromise(PROMISE_KEY)
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1, new TerminalException("myerror")))
            .expectingOutput(
                getPromise(PROMISE_KEY),
                outputMessage(new TerminalException("myerror")),
                END_MESSAGE)
            .named("Completed with failure"),
        // --- Peek promise
        this.awaitPeekPromise(PROMISE_KEY, "null")
            .withInput(startMessage(1), inputMessage(), completionMessage(1, "my value"))
            .expectingOutput(peekPromise(PROMISE_KEY), outputMessage("my value"), END_MESSAGE)
            .named("Completed with success"),
        this.awaitPeekPromise(PROMISE_KEY, "null")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1, new TerminalException("myerror")))
            .expectingOutput(
                peekPromise(PROMISE_KEY),
                outputMessage(new TerminalException("myerror")),
                END_MESSAGE)
            .named("Completed with failure"),
        this.awaitPeekPromise(PROMISE_KEY, "null")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1).setEmpty(Protocol.Empty.getDefaultInstance()))
            .expectingOutput(peekPromise(PROMISE_KEY), outputMessage("null"), END_MESSAGE)
            .named("Completed with null"),
        // --- Promise is completed
        this.awaitIsPromiseCompleted(PROMISE_KEY)
            .withInput(startMessage(1), inputMessage(), completionMessage(1, "my value"))
            .expectingOutput(
                peekPromise(PROMISE_KEY), outputMessage(TestSerdes.BOOLEAN, true), END_MESSAGE)
            .named("Completed with success"),
        this.awaitIsPromiseCompleted(PROMISE_KEY)
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1).setEmpty(Protocol.Empty.getDefaultInstance()))
            .expectingOutput(
                peekPromise(PROMISE_KEY), outputMessage(TestSerdes.BOOLEAN, false), END_MESSAGE)
            .named("Not completed"),
        // --- Promise resolve
        this.awaitResolvePromise(PROMISE_KEY, "my val")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1).setEmpty(Protocol.Empty.getDefaultInstance()))
            .expectingOutput(
                completePromise(PROMISE_KEY, "my val"),
                outputMessage(TestSerdes.BOOLEAN, true),
                END_MESSAGE)
            .named("resolve succeeds"),
        this.awaitResolvePromise(PROMISE_KEY, "my val")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1, new TerminalException("cannot write promise")))
            .expectingOutput(
                completePromise(PROMISE_KEY, "my val"),
                outputMessage(TestSerdes.BOOLEAN, false),
                END_MESSAGE)
            .named("resolve fails"),
        // --- Promise reject
        this.awaitRejectPromise(PROMISE_KEY, "my failure")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1).setEmpty(Protocol.Empty.getDefaultInstance()))
            .expectingOutput(
                completePromise(PROMISE_KEY, new TerminalException("my failure")),
                outputMessage(TestSerdes.BOOLEAN, true),
                END_MESSAGE)
            .named("resolve succeeds"),
        this.awaitRejectPromise(PROMISE_KEY, "my failure")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1, new TerminalException("cannot write promise")))
            .expectingOutput(
                completePromise(PROMISE_KEY, new TerminalException("my failure")),
                outputMessage(TestSerdes.BOOLEAN, false),
                END_MESSAGE)
            .named("resolve fails"));
  }
}
