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

import com.google.protobuf.Empty;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class DeferredTestSuite implements TestSuite {

  protected abstract BindableService reverseAwaitOrder();

  protected abstract BindableService awaitTwiceTheSameAwaitable();

  protected abstract BindableService awaitAll();

  protected abstract BindableService awaitAny();

  protected abstract BindableService combineAnyWithAll();

  protected abstract BindableService awaitAnyIndex();

  protected abstract BindableService awaitOnAlreadyResolvedAwaitables();

  protected abstract BindableService awaitWithTimeout();

  protected Stream<TestDefinition> anyTestDefinitions(Supplier<BindableService> svcSupplier) {
    return Stream.of(
        testInvocation(svcSupplier, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                suspensionMessage(1, 2))
            .named("No completions will suspend"),
        testInvocation(svcSupplier, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder()),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Till"),
                    greetingResponse("TILL")))
            .expectingOutput(
                combinatorsMessage(2), outputMessage(greetingResponse("TILL")), END_MESSAGE)
            .named("Only one completion will generate the combinators message"),
        testInvocation(svcSupplier, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder()),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Till"),
                    new IllegalStateException("My error")))
            .expectingOutput(
                combinatorsMessage(2),
                outputMessage(new IllegalStateException("My error")),
                END_MESSAGE)
            .named("Only one failure will generate the combinators message"),
        testInvocation(svcSupplier, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder()),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Francesco"),
                    greetingResponse("FRANCESCO")),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Till"),
                    greetingResponse("TILL")))
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
                      .isIn(
                          outputMessage(greetingResponse("FRANCESCO")),
                          outputMessage(greetingResponse("TILL")));
                  assertThat(msgs).element(2).isEqualTo(END_MESSAGE);
                })
            .named("Everything completed will generate the combinators message"),
        testInvocation(svcSupplier, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(4),
                inputMessage(GreetingRequest.newBuilder()),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Francesco"),
                    greetingResponse("FRANCESCO")),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Till"),
                    greetingResponse("TILL")),
                combinatorsMessage(2))
            .expectingOutput(outputMessage(greetingResponse("TILL")), END_MESSAGE)
            .named("Replay the combinator"),
        testInvocation(svcSupplier, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                combinatorsMessage(1),
                outputMessage(greetingResponse("FRANCESCO")),
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
            testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
                .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    suspensionMessage(2))
                .named("None completed"),
            testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(1, greetingResponse("FRANCESCO")),
                    completionMessage(2, greetingResponse("TILL")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    setStateMessage("A2", "TILL"),
                    outputMessage(greetingResponse("FRANCESCO-TILL")),
                    END_MESSAGE)
                .named("A1 and A2 completed later"),
            testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(2, greetingResponse("TILL")),
                    completionMessage(1, greetingResponse("FRANCESCO")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    setStateMessage("A2", "TILL"),
                    outputMessage(greetingResponse("FRANCESCO-TILL")),
                    END_MESSAGE)
                .named("A2 and A1 completed later"),
            testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(2, greetingResponse("TILL")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    setStateMessage("A2", "TILL"),
                    suspensionMessage(1))
                .named("Only A2 completed"),
            testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(1, greetingResponse("FRANCESCO")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    suspensionMessage(2))
                .named("Only A1 completed"),

            // --- Await twice the same executable
            testInvocation(this::awaitTwiceTheSameAwaitable, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(1, greetingResponse("FRANCESCO")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    outputMessage(greetingResponse("FRANCESCO-FRANCESCO")),
                    END_MESSAGE),

            // --- All combinator
            testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
                .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    suspensionMessage(1, 2))
                .named("No completions will suspend"),
            testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(3),
                    inputMessage(GreetingRequest.newBuilder()),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(
                        GreeterGrpc.getGreetMethod(),
                        greetingRequest("Till"),
                        greetingResponse("TILL")))
                .expectingOutput(suspensionMessage(1))
                .named("Only one completion will suspend"),
            testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(3),
                    inputMessage(GreetingRequest.newBuilder()),
                    invokeMessage(
                        GreeterGrpc.getGreetMethod(),
                        greetingRequest("Francesco"),
                        greetingResponse("FRANCESCO")),
                    invokeMessage(
                        GreeterGrpc.getGreetMethod(),
                        greetingRequest("Till"),
                        greetingResponse("TILL")))
                .assertingOutput(
                    msgs -> {
                      assertThat(msgs).hasSize(3);

                      assertThat(msgs)
                          .element(0, type(Java.CombinatorAwaitableEntryMessage.class))
                          .extracting(
                              Java.CombinatorAwaitableEntryMessage::getEntryIndexList,
                              list(Integer.class))
                          .containsExactlyInAnyOrder(1, 2);

                      assertThat(msgs)
                          .element(1)
                          .isEqualTo(outputMessage(greetingResponse("FRANCESCO-TILL")));
                      assertThat(msgs).element(2).isEqualTo(END_MESSAGE);
                    })
                .named("Everything completed will generate the combinators message"),
            testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(4),
                    inputMessage(GreetingRequest.newBuilder()),
                    invokeMessage(
                        GreeterGrpc.getGreetMethod(),
                        greetingRequest("Francesco"),
                        greetingResponse("FRANCESCO")),
                    invokeMessage(
                        GreeterGrpc.getGreetMethod(),
                        greetingRequest("Till"),
                        greetingResponse("TILL")),
                    combinatorsMessage(1, 2))
                .expectingOutput(outputMessage(greetingResponse("FRANCESCO-TILL")), END_MESSAGE)
                .named("Replay the combinator"),
            testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(1, greetingResponse("FRANCESCO")),
                    completionMessage(2, greetingResponse("TILL")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    combinatorsMessage(1, 2),
                    outputMessage(greetingResponse("FRANCESCO-TILL")),
                    END_MESSAGE)
                .named("Complete all asynchronously"),
            testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(1, new IllegalStateException("My error")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    combinatorsMessage(1),
                    outputMessage(new IllegalStateException("My error")),
                    END_MESSAGE)
                .named("All fails on first failure"),
            testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(1, greetingResponse("FRANCESCO")),
                    completionMessage(2, new IllegalStateException("My error")))
                .onlyUnbuffered()
                .expectingOutput(
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                    invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                    combinatorsMessage(1, 2),
                    outputMessage(new IllegalStateException("My error")),
                    END_MESSAGE)
                .named("All fails on second failure"),

            // --- Compose any with all
            testInvocation(this::combineAnyWithAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(6),
                    inputMessage(GreetingRequest.newBuilder()),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(2, 3))
                .expectingOutput(outputMessage(greetingResponse("223")), END_MESSAGE),
            testInvocation(this::combineAnyWithAll, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(6),
                    inputMessage(GreetingRequest.newBuilder()),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(3, 2))
                .expectingOutput(outputMessage(greetingResponse("233")), END_MESSAGE)
                .named("Inverted order"),

            // --- Await Any with index
            testInvocation(this::awaitAnyIndex, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(6),
                    inputMessage(GreetingRequest.newBuilder()),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(1))
                .expectingOutput(outputMessage(greetingResponse("0")), END_MESSAGE),
            testInvocation(this::awaitAnyIndex, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(6),
                    inputMessage(GreetingRequest.newBuilder()),
                    awakeable("1"),
                    awakeable("2"),
                    awakeable("3"),
                    awakeable("4"),
                    combinatorsMessage(3, 2))
                .expectingOutput(outputMessage(greetingResponse("1")), END_MESSAGE)
                .named("Complete all"),

            // --- Compose nested and resolved all should work
            testInvocation(this::awaitOnAlreadyResolvedAwaitables, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(3),
                    inputMessage(GreetingRequest.newBuilder()),
                    awakeable("1"),
                    awakeable("2"))
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
                      assertThat(msgs).element(2).isEqualTo(outputMessage(greetingResponse("12")));
                      assertThat(msgs).element(3).isEqualTo(END_MESSAGE);
                    }),

            // --- Await with timeout
            testInvocation(this::awaitWithTimeout, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    completionMessage(1, greetingResponse("FRANCESCO")))
                .onlyUnbuffered()
                .assertingOutput(
                    messages -> {
                      assertThat(messages).hasSize(5);
                      assertThat(messages)
                          .element(0)
                          .isEqualTo(
                              invokeMessage(
                                      GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                                  .build());
                      assertThat(messages)
                          .element(1)
                          .isInstanceOf(Protocol.SleepEntryMessage.class);
                      assertThat(messages).element(2).isEqualTo(combinatorsMessage(1));
                      assertThat(messages)
                          .element(3)
                          .isEqualTo(outputMessage(greetingResponse("FRANCESCO")));
                      assertThat(messages).element(4).isEqualTo(END_MESSAGE);
                    }),
            testInvocation(this::awaitWithTimeout, GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(1),
                    inputMessage(GreetingRequest.newBuilder()),
                    Protocol.CompletionMessage.newBuilder()
                        .setEntryIndex(2)
                        .setEmpty(Empty.getDefaultInstance()))
                .onlyUnbuffered()
                .assertingOutput(
                    messages -> {
                      assertThat(messages).hasSize(5);
                      assertThat(messages)
                          .element(0)
                          .isEqualTo(
                              invokeMessage(
                                      GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                                  .build());
                      assertThat(messages)
                          .element(1)
                          .isInstanceOf(Protocol.SleepEntryMessage.class);
                      assertThat(messages).element(2).isEqualTo(combinatorsMessage(2));
                      assertThat(messages)
                          .element(3)
                          .isEqualTo(outputMessage(greetingResponse("timeout")));
                      assertThat(messages).element(4).isEqualTo(END_MESSAGE);
                    })
                .named("Fires timeout")));
  }
}
