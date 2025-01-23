// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.Empty;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class DeferredTestSuite implements TestSuite {

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
            .withInput(startMessage(1), inputMessage())
            .expectingOutput(
                invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                suspensionMessage(1, 2))
            .named("No completions will suspend"),
        testInvocation
            .get()
            .withInput(
                startMessage(3),
                inputMessage(),
                invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                invokeMessage(GREETER_SERVICE_TARGET, "Till", "TILL"),
                ackMessage(3))
            .expectingOutput(combinatorsMessage(2), outputMessage("TILL"), END_MESSAGE)
            .named("Only one completion will generate the combinators message"),
        testInvocation
            .get()
            .withInput(
                startMessage(3),
                inputMessage(),
                invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                invokeMessage(GREETER_SERVICE_TARGET, "Till", "TILL"))
            .expectingOutput(combinatorsMessage(2), suspensionMessage(3))
            .named("Completed without ack will suspend"),
        testInvocation
            .get()
            .withInput(
                startMessage(3),
                inputMessage(),
                invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                invokeMessage(GREETER_SERVICE_TARGET, "Till")
                    .setFailure(
                        ExceptionUtils.toProtocolFailure(new IllegalStateException("My error"))),
                ackMessage(3))
            .expectingOutput(
                combinatorsMessage(2),
                outputMessage(new IllegalStateException("My error")),
                END_MESSAGE)
            .named("Only one failure will generate the combinators message"),
        testInvocation
            .get()
            .withInput(
                startMessage(3),
                inputMessage(),
                invokeMessage(GREETER_SERVICE_TARGET, "Francesco", "FRANCESCO"),
                invokeMessage(GREETER_SERVICE_TARGET, "Till", "TILL"),
                ackMessage(3))
            .assertingOutput(
                msgs -> {
                  assertThat(msgs).hasSize(3);

                  assertThat(msgs)
                      .element(0, type(Java.CombinatorAwaitableEntryMessage.class))
                      .extracting(
                          Java.CombinatorAwaitableEntryMessage::getEntryIndexList,
                          list(Integer.class))
                      .hasSize(1)
                      .element(0)
                      .isIn(1, 2);

                  assertThat(msgs)
                      .element(1)
                      .isIn(outputMessage("FRANCESCO"), outputMessage("TILL"));
                  assertThat(msgs).element(2).isEqualTo(END_MESSAGE);
                })
            .named("Everything completed will generate the combinators message"),
        testInvocation
            .get()
            .withInput(
                startMessage(4),
                inputMessage(),
                invokeMessage(GREETER_SERVICE_TARGET, "Francesco", "FRANCESCO"),
                invokeMessage(GREETER_SERVICE_TARGET, "Till", "TILL"),
                combinatorsMessage(2))
            .expectingOutput(outputMessage("TILL"), END_MESSAGE)
            .named("Replay the combinator"),
        testInvocation
            .get()
            .withInput(
                startMessage(1), inputMessage(), completionMessage(1, "FRANCESCO"), ackMessage(3))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                combinatorsMessage(1),
                outputMessage("FRANCESCO"),
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
                .withInput(startMessage(1), inputMessage())
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    suspensionMessage(2))
                .named("None completed"),
            this.reverseAwaitOrder()
                .withInput(
                    startMessage(1),
                    inputMessage(),
                    completionMessage(1, "FRANCESCO"),
                    completionMessage(2, "TILL"))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    setStateMessage("A2", "TILL"),
                    outputMessage("FRANCESCO-TILL"),
                    END_MESSAGE)
                .named("A1 and A2 completed later"),
            this.reverseAwaitOrder()
                .withInput(
                    startMessage(1),
                    inputMessage(),
                    completionMessage(2, "TILL"),
                    completionMessage(1, "FRANCESCO"))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    setStateMessage("A2", "TILL"),
                    outputMessage("FRANCESCO-TILL"),
                    END_MESSAGE)
                .named("A2 and A1 completed later"),
            this.reverseAwaitOrder()
                .withInput(startMessage(1), inputMessage(), completionMessage(2, "TILL"))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    setStateMessage("A2", "TILL"),
                    suspensionMessage(1))
                .named("Only A2 completed"),
            this.reverseAwaitOrder()
                .withInput(startMessage(1), inputMessage(), completionMessage(1, "FRANCESCO"))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    suspensionMessage(2))
                .named("Only A1 completed"),

            // --- Await twice the same executable
            this.awaitTwiceTheSameAwaitable()
                .withInput(startMessage(1), inputMessage(), completionMessage(1, "FRANCESCO"))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    outputMessage("FRANCESCO-FRANCESCO"),
                    END_MESSAGE),

            // --- All combinator
            this.awaitAll()
                .withInput(startMessage(1), inputMessage())
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    suspensionMessage(1, 2))
                .named("No completions will suspend"),
            this.awaitAll()
                .withInput(
                    startMessage(3),
                    inputMessage(),
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till", "TILL"))
                .expectingOutput(suspensionMessage(1))
                .named("Only one completion will suspend"),
            this.awaitAll()
                .withInput(
                    startMessage(3),
                    inputMessage(),
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco", "FRANCESCO"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till", "TILL"),
                    ackMessage(3))
                .assertingOutput(
                    msgs -> {
                      assertThat(msgs).hasSize(3);

                      assertThat(msgs)
                          .element(0, type(Java.CombinatorAwaitableEntryMessage.class))
                          .extracting(
                              Java.CombinatorAwaitableEntryMessage::getEntryIndexList,
                              list(Integer.class))
                          .containsExactlyInAnyOrder(1, 2);

                      assertThat(msgs).element(1).isEqualTo(outputMessage("FRANCESCO-TILL"));
                      assertThat(msgs).element(2).isEqualTo(END_MESSAGE);
                    })
                .named("Everything completed will generate the combinators message"),
            this.awaitAll()
                .withInput(
                    startMessage(4),
                    inputMessage(),
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco", "FRANCESCO"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till", "TILL"),
                    combinatorsMessage(1, 2))
                .expectingOutput(outputMessage("FRANCESCO-TILL"), END_MESSAGE)
                .named("Replay the combinator"),
            this.awaitAll()
                .withInput(
                    startMessage(1),
                    inputMessage(),
                    completionMessage(1, "FRANCESCO"),
                    completionMessage(2, "TILL"),
                    ackMessage(3))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    combinatorsMessage(1, 2),
                    outputMessage("FRANCESCO-TILL"),
                    END_MESSAGE)
                .named("Complete all asynchronously"),
            this.awaitAll()
                .withInput(
                    startMessage(1),
                    inputMessage(),
                    completionMessage(1, new IllegalStateException("My error")),
                    ackMessage(3))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    combinatorsMessage(1),
                    outputMessage(new IllegalStateException("My error")),
                    END_MESSAGE)
                .named("All fails on first failure"),
            this.awaitAll()
                .withInput(
                    startMessage(1),
                    inputMessage(),
                    completionMessage(1, "FRANCESCO"),
                    completionMessage(2, new IllegalStateException("My error")),
                    ackMessage(3))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GREETER_SERVICE_TARGET, "Francesco"),
                    invokeMessage(GREETER_SERVICE_TARGET, "Till"),
                    combinatorsMessage(1, 2),
                    outputMessage(new IllegalStateException("My error")),
                    END_MESSAGE)
                .named("All fails on second failure"),

            // --- Compose any with all
            this.combineAnyWithAll()
                .withInput(
                    startMessage(6),
                    inputMessage(),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(2, 3))
                .expectingOutput(outputMessage("223"), END_MESSAGE),
            this.combineAnyWithAll()
                .withInput(
                    startMessage(6),
                    inputMessage(),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(3, 2))
                .expectingOutput(outputMessage("233"), END_MESSAGE)
                .named("Inverted order"),

            // --- Await Any with index
            this.awaitAnyIndex()
                .withInput(
                    startMessage(6),
                    inputMessage(),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(1))
                .expectingOutput(outputMessage("0"), END_MESSAGE),
            this.awaitAnyIndex()
                .withInput(
                    startMessage(6),
                    inputMessage(),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(3, 2))
                .expectingOutput(outputMessage("1"), END_MESSAGE)
                .named("Complete all"),

            // --- Compose nested and resolved all should work
            this.awaitOnAlreadyResolvedAwaitables()
                .withInput(
                    startMessage(3),
                    inputMessage(),
                    awakeable("1"),
                    awakeable("2"),
                    ackMessage(3),
                    ackMessage(4))
                .assertingOutput(
                    msgs -> {
                      assertThat(msgs).hasSize(4);

                      assertThat(msgs)
                          .element(0, type(Java.CombinatorAwaitableEntryMessage.class))
                          .extracting(
                              Java.CombinatorAwaitableEntryMessage::getEntryIndexList,
                              list(Integer.class))
                          .containsExactlyInAnyOrder(1, 2);

                      assertThat(msgs).element(1).isEqualTo(combinatorsMessage());
                      assertThat(msgs).element(2).isEqualTo(outputMessage("12"));
                      assertThat(msgs).element(3).isEqualTo(END_MESSAGE);
                    }),

            // --- Await with timeout
            this.awaitWithTimeout()
                .withInput(
                    startMessage(1),
                    inputMessage(),
                    completionMessage(1, "FRANCESCO"),
                    ackMessage(3))
                .onlyUnbuffered()
                .assertingOutput(
                    messages -> {
                      assertThat(messages).hasSize(5);
                      assertThat(messages)
                          .element(0)
                          .isEqualTo(invokeMessage(GREETER_SERVICE_TARGET, "Francesco").build());
                      assertThat(messages)
                          .element(1)
                          .isInstanceOf(Protocol.SleepEntryMessage.class);
                      assertThat(messages).element(2).isEqualTo(combinatorsMessage(1));
                      assertThat(messages).element(3).isEqualTo(outputMessage("FRANCESCO"));
                      assertThat(messages).element(4).isEqualTo(END_MESSAGE);
                    }),
            this.awaitWithTimeout()
                .withInput(
                    startMessage(1),
                    inputMessage(),
                    completionMessage(2).setEmpty(Empty.getDefaultInstance()),
                    ackMessage(3))
                .onlyUnbuffered()
                .assertingOutput(
                    messages -> {
                      assertThat(messages).hasSize(5);
                      assertThat(messages)
                          .element(0)
                          .isEqualTo(invokeMessage(GREETER_SERVICE_TARGET, "Francesco").build());
                      assertThat(messages)
                          .element(1)
                          .isInstanceOf(Protocol.SleepEntryMessage.class);
                      assertThat(messages).element(2).isEqualTo(combinatorsMessage(2));
                      assertThat(messages).element(3).isEqualTo(outputMessage("timeout"));
                      assertThat(messages).element(4).isEqualTo(END_MESSAGE);
                    })
                .named("Fires timeout")));
  }
}
