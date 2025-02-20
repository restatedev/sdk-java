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
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.types.TerminalException;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class AsyncResultTestSuite implements TestSuite {

  protected abstract TestInvocationBuilder reverseAwaitOrder();

  protected abstract TestInvocationBuilder awaitTwiceTheSameAwaitable();

  protected abstract TestInvocationBuilder awaitAll();

  protected abstract TestInvocationBuilder awaitAny();

  protected abstract TestInvocationBuilder combineAnyWithAll();

  protected abstract TestInvocationBuilder awaitAnyIndex();

  protected abstract TestInvocationBuilder awaitOnAlreadyResolvedAwaitables();

  protected abstract TestInvocationBuilder awaitWithTimeout();

  protected Stream<TestDefinition> anyTestDefinitions(
      Supplier<TestInvocationBuilder> testInvocation) {
    return Stream.of(
        testInvocation
            .get()
            .withInput(startMessage(1), inputCmd())
            .expectingOutput(
                callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                suspensionMessage(2, 4))
            .named("No completions will suspend"),
        testInvocation
            .get()
            .withInput(
                startMessage(4),
                inputCmd(),
                callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                callCompletion(4, "TILL"))
            .expectingOutput(outputCmd("TILL"), END_MESSAGE)
            .named("Only one completion completes any combinator"),
        testInvocation
            .get()
            .withInput(
                startMessage(4),
                inputCmd(),
                callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                callCompletion(4, new TerminalException("My error")))
            .expectingOutput(outputCmd(new TerminalException("My error")), END_MESSAGE)
            .named("Only one failure completes any combinator"),
        testInvocation
            .get()
            .withInput(
                startMessage(5),
                inputCmd(),
                callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                callCompletion(2, "FRANCESCO"),
                callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                callCompletion(4, "TILL"))
            .assertingOutput(
                msgs -> {
                  assertThat(msgs).hasSize(2);

                  assertThat(msgs).element(0).isIn(outputCmd("FRANCESCO"), outputCmd("TILL"));
                  assertThat(msgs).element(1).isEqualTo(END_MESSAGE);
                })
            .named("Everything completed completes the any combinator"),
        testInvocation
            .get()
            .withInput(startMessage(1), inputCmd(), callCompletion(2, "FRANCESCO"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                outputCmd("FRANCESCO"),
                END_MESSAGE)
            .named("Complete any asynchronously"));
  }

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.concat(
        // --- Any combinator
        anyTestDefinitions(this::awaitAny),
        Stream.of(
            // --- Reverse await order
            this.reverseAwaitOrder()
                .withInput(startMessage(1), inputCmd())
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    suspensionMessage(4))
                .named("None completed"),
            this.reverseAwaitOrder()
                .withInput(
                    startMessage(1),
                    inputCmd(),
                    callCompletion(2, "FRANCESCO"),
                    callCompletion(4, "TILL"))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    setStateCmd("A2", "TILL"),
                    outputCmd("FRANCESCO-TILL"),
                    END_MESSAGE)
                .named("A1 and A2 completed later"),
            this.reverseAwaitOrder()
                .withInput(
                    startMessage(1),
                    inputCmd(),
                    callCompletion(4, "TILL"),
                    callCompletion(2, "FRANCESCO"))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    setStateCmd("A2", "TILL"),
                    outputCmd("FRANCESCO-TILL"),
                    END_MESSAGE)
                .named("A2 and A1 completed later in reverse order"),
            this.reverseAwaitOrder()
                .withInput(startMessage(1), inputCmd(), callCompletion(4, "TILL"))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    setStateCmd("A2", "TILL"),
                    suspensionMessage(2))
                .named("Only A2 completed"),
            this.reverseAwaitOrder()
                .withInput(startMessage(1), inputCmd(), callCompletion(2, "FRANCESCO"))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    suspensionMessage(4))
                .named("Only A1 completed"),

            // --- Await twice the same executable
            this.awaitTwiceTheSameAwaitable()
                .withInput(startMessage(1), inputCmd(), callCompletion(2, "FRANCESCO"))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    outputCmd("FRANCESCO-FRANCESCO"),
                    END_MESSAGE),

            // --- All combinator
            this.awaitAll()
                .withInput(startMessage(1), inputCmd())
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    suspensionMessage(2, 4))
                .named("No completions will suspend"),
            this.awaitAll()
                .withInput(
                    startMessage(4),
                    inputCmd(),
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    callCompletion(4, "TILL"))
                .expectingOutput(suspensionMessage(2))
                .named("Only one completion will suspend"),
            this.awaitAll()
                .withInput(
                    startMessage(3),
                    inputCmd(),
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    callCompletion(2, "FRANCESCO"),
                    callCompletion(4, "TILL"))
                .expectingOutput(outputCmd("FRANCESCO-TILL"), END_MESSAGE)
                .named("Everything completed completes the all combinator"),
            this.awaitAll()
                .withInput(
                    startMessage(1),
                    inputCmd(),
                    callCompletion(2, "FRANCESCO"),
                    callCompletion(4, "TILL"))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    outputCmd("FRANCESCO-TILL"),
                    END_MESSAGE)
                .named("Complete all asynchronously"),
            this.awaitAll()
                .withInput(
                    startMessage(1),
                    inputCmd(),
                    callCompletion(2, new IllegalStateException("My error")))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    outputCmd(new IllegalStateException("My error")),
                    END_MESSAGE)
                .named("All fails on first failure"),
            this.awaitAll()
                .withInput(
                    startMessage(1),
                    inputCmd(),
                    callCompletion(2, "FRANCESCO"),
                    callCompletion(4, new IllegalStateException("My error")))
                .onlyBidiStream()
                .expectingOutput(
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCmd(3, 4, GREETER_SERVICE_TARGET, "Till"),
                    outputCmd(new IllegalStateException("My error")),
                    END_MESSAGE)
                .named("All fails on second failure"),

            // --- Compose any with all
            this.combineAnyWithAll()
                .withInput(
                    startMessage(5),
                    inputCmd(),
                    signalNotification(17, "1"),
                    signalNotification(18, "2"),
                    signalNotification(19, "3"),
                    signalNotification(20, "4"))
                .expectingOutput(outputCmd("123"), END_MESSAGE),
            this.combineAnyWithAll()
                .withInput(
                    startMessage(5),
                    inputCmd(),
                    signalNotification(18, "2"),
                    signalNotification(17, "1"),
                    signalNotification(20, "4"),
                    signalNotification(19, "3"))
                .expectingOutput(outputCmd("224"), END_MESSAGE)
                .named("Inverted order"),

            // --- Await Any with index
            this.awaitAnyIndex()
                .withInput(
                    startMessage(5),
                    inputCmd(),
                    signalNotification(17, "1"),
                    signalNotification(18, "2"),
                    signalNotification(19, "3"),
                    signalNotification(20, "4"))
                .expectingOutput(outputCmd("0"), END_MESSAGE),
            this.awaitAnyIndex()
                .withInput(
                    startMessage(5),
                    inputCmd(),
                    signalNotification(19, "3"),
                    signalNotification(18, "2"),
                    signalNotification(17, "1"),
                    signalNotification(20, "4"))
                .expectingOutput(outputCmd("1"), END_MESSAGE)
                .named("Complete all"),

            // --- Compose nested and resolved all should work
            this.awaitOnAlreadyResolvedAwaitables()
                .withInput(
                    startMessage(3),
                    inputCmd(),
                    signalNotification(17, "1"),
                    signalNotification(18, "2"))
                .expectingOutput(outputCmd("12"), END_MESSAGE),

            // --- Await with timeout
            this.awaitWithTimeout()
                .withInput(startMessage(1), inputCmd(), callCompletion(2, "FRANCESCO"))
                .onlyBidiStream()
                .assertingOutput(
                    messages -> {
                      assertThat(messages).hasSize(4);
                      assertThat(messages)
                          .element(0)
                          .isEqualTo(callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco").build());
                      assertThat(messages)
                          .element(1)
                          .isInstanceOf(Protocol.SleepCommandMessage.class);
                      assertThat(messages).element(2).isEqualTo(outputCmd("FRANCESCO"));
                      assertThat(messages).element(3).isEqualTo(END_MESSAGE);
                    }),
            this.awaitWithTimeout()
                .withInput(
                    startMessage(1),
                    inputCmd(),
                    Protocol.SleepCompletionNotificationMessage.newBuilder()
                        .setCompletionId(3)
                        .setVoid(Protocol.Void.getDefaultInstance())
                        .build())
                .onlyBidiStream()
                .assertingOutput(
                    messages -> {
                      assertThat(messages).hasSize(4);
                      assertThat(messages)
                          .element(0)
                          .isEqualTo(callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco").build());
                      assertThat(messages)
                          .element(1)
                          .isInstanceOf(Protocol.SleepCommandMessage.class);
                      assertThat(messages).element(2).isEqualTo(outputCmd("timeout"));
                      assertThat(messages).element(3).isEqualTo(END_MESSAGE);
                    })
                .named("Fires timeout")));
  }
}
