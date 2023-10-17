package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.Empty;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class DeferredTestSuite extends CoreTestRunner {

  protected abstract BindableService reverseAwaitOrder();

  protected abstract BindableService awaitTwiceTheSameAwaitable();

  protected abstract BindableService awaitAll();

  protected abstract BindableService awaitAny();

  protected abstract BindableService combineAnyWithAll();

  protected abstract BindableService awaitAnyIndex();

  protected abstract BindableService awaitOnAlreadyResolvedAwaitables();

  protected abstract BindableService awaitWithTimeout();

  @Override
  protected Stream<CoreTestRunner.TestDefinition> definitions() {
    return Stream.of(
        // --- Reverse await order
        testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
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
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                setStateMessage("A2", "TILL"),
                outputMessage(greetingResponse("FRANCESCO-TILL")))
            .named("A1 and A2 completed later"),
        testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(2, greetingResponse("TILL")),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                setStateMessage("A2", "TILL"),
                outputMessage(greetingResponse("FRANCESCO-TILL")))
            .named("A2 and A1 completed later"),
        testInvocation(this::reverseAwaitOrder, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(2, greetingResponse("TILL")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
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
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
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
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                outputMessage(greetingResponse("FRANCESCO-FRANCESCO"))),

        // --- All combinator
        testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
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
            .usingAllThreadingModels()
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
            .usingAllThreadingModels()
            .assertingOutput(
                msgs -> {
                  assertThat(msgs).hasSize(2);

                  assertThat(msgs)
                      .element(0, type(Java.CombinatorAwaitableEntryMessage.class))
                      .extracting(
                          Java.CombinatorAwaitableEntryMessage::getEntryIndexList,
                          list(Integer.class))
                      .containsExactlyInAnyOrder(1, 2);

                  assertThat(msgs)
                      .element(1)
                      .isEqualTo(outputMessage(greetingResponse("FRANCESCO-TILL")));
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
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("FRANCESCO-TILL")))
            .named("Replay the combinator"),
        testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")),
                completionMessage(2, greetingResponse("TILL")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                combinatorsMessage(1, 2),
                outputMessage(greetingResponse("FRANCESCO-TILL")))
            .named("Complete all asynchronously"),
        testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, new IllegalStateException("My error")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                combinatorsMessage(1),
                outputMessage(new IllegalStateException("My error")))
            .named("All fails on first failure"),
        testInvocation(this::awaitAll, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")),
                completionMessage(2, new IllegalStateException("My error")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                combinatorsMessage(1, 2),
                outputMessage(new IllegalStateException("My error")))
            .named("All fails on second failure"),

        // --- Any combinator
        testInvocation(this::awaitAny, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                suspensionMessage(1, 2))
            .named("No completions will suspend"),
        testInvocation(this::awaitAny, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder()),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Till"),
                    greetingResponse("TILL")))
            .usingAllThreadingModels()
            .expectingOutput(combinatorsMessage(2), outputMessage(greetingResponse("TILL")))
            .named("Only one completion will generate the combinators message"),
        testInvocation(this::awaitAny, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder()),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(
                    GreeterGrpc.getGreetMethod(),
                    greetingRequest("Till"),
                    new IllegalStateException("My error")))
            .usingAllThreadingModels()
            .expectingOutput(
                combinatorsMessage(2), outputMessage(new IllegalStateException("My error")))
            .named("Only one failure will generate the combinators message"),
        testInvocation(this::awaitAny, GreeterGrpc.getGreetMethod())
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
            .usingAllThreadingModels()
            .assertingOutput(
                msgs -> {
                  assertThat(msgs).hasSize(2);

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
                })
            .named("Everything completed will generate the combinators message"),
        testInvocation(this::awaitAny, GreeterGrpc.getGreetMethod())
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
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("TILL")))
            .named("Replay the combinator"),
        testInvocation(this::awaitAny, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                combinatorsMessage(1),
                outputMessage(greetingResponse("FRANCESCO")))
            .named("Complete any asynchronously"),

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
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("223"))),
        testInvocation(this::combineAnyWithAll, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(6),
                inputMessage(GreetingRequest.newBuilder()),
                awakeable("1"),
                awakeable("2"),
                awakeable("3"),
                awakeable("4"),
                combinatorsMessage(3, 2))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("233")))
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
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("0"))),
        testInvocation(this::awaitAnyIndex, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(6),
                inputMessage(GreetingRequest.newBuilder()),
                awakeable("1"),
                awakeable("2"),
                awakeable("3"),
                awakeable("4"),
                combinatorsMessage(3, 2))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("1")))
            .named("Complete all"),

        // --- Compose nested and resolved all should work
        testInvocation(this::awaitOnAlreadyResolvedAwaitables, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(3),
                inputMessage(GreetingRequest.newBuilder()),
                awakeable("1"),
                awakeable("2"))
            .usingAllThreadingModels()
            .assertingOutput(
                msgs -> {
                  assertThat(msgs).hasSize(3);

                  assertThat(msgs)
                      .element(0, type(Java.CombinatorAwaitableEntryMessage.class))
                      .extracting(
                          Java.CombinatorAwaitableEntryMessage::getEntryIndexList,
                          list(Integer.class))
                      .containsExactlyInAnyOrder(1, 2);

                  assertThat(msgs).element(1).isEqualTo(combinatorsMessage());
                  assertThat(msgs).element(2).isEqualTo(outputMessage(greetingResponse("12")));
                }),

        // --- Await with timeout
        testInvocation(this::awaitWithTimeout, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .assertingOutput(
                messages -> {
                  assertThat(messages).hasSize(4);
                  assertThat(messages)
                      .element(0)
                      .isEqualTo(
                          invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build());
                  assertThat(messages).element(1).isInstanceOf(Protocol.SleepEntryMessage.class);
                  assertThat(messages).element(2).isEqualTo(combinatorsMessage(1));
                  assertThat(messages)
                      .element(3)
                      .isEqualTo(outputMessage(greetingResponse("FRANCESCO")));
                }),
        testInvocation(this::awaitWithTimeout, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                Protocol.CompletionMessage.newBuilder()
                    .setEntryIndex(2)
                    .setEmpty(Empty.getDefaultInstance()))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .assertingOutput(
                messages -> {
                  assertThat(messages).hasSize(4);
                  assertThat(messages)
                      .element(0)
                      .isEqualTo(
                          invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build());
                  assertThat(messages).element(1).isInstanceOf(Protocol.SleepEntryMessage.class);
                  assertThat(messages).element(2).isEqualTo(combinatorsMessage(2));
                  assertThat(messages)
                      .element(3)
                      .isEqualTo(outputMessage(greetingResponse("timeout")));
                })
            .named("Fires timeout"));
  }
}
