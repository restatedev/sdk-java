package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.blocking.Awaitable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.stream.Stream;

public class DeferredTest extends CoreTestRunner {

  private static class ReverseAwaitOrder extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<GreetingResponse> a1 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));
      Awaitable<GreetingResponse> a2 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Till"));

      String a2Res = a2.await().getMessage();
      ctx.set(StateKey.of("A2", TypeTag.STRING_UTF8), a2Res);

      String a1Res = a1.await().getMessage();

      responseObserver.onNext(greetingResponse(a1Res + "-" + a2Res));
      responseObserver.onCompleted();
    }
  }

  private static class AwaitTwiceTheSameAwaitable extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<GreetingResponse> a =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));

      responseObserver.onNext(
          greetingResponse(a.await().getMessage() + "-" + a.await().getMessage()));
      responseObserver.onCompleted();
    }
  }

  private static class AwaitAll extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<GreetingResponse> a1 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));
      Awaitable<GreetingResponse> a2 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Till"));

      Awaitable.all(a1, a2).await();

      responseObserver.onNext(
          greetingResponse(a1.await().getMessage() + "-" + a2.await().getMessage()));
      responseObserver.onCompleted();
    }
  }

  private static class AwaitAny extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<GreetingResponse> a1 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"));
      Awaitable<GreetingResponse> a2 =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Till"));

      GreetingResponse res = (GreetingResponse) Awaitable.any(a1, a2).await();

      responseObserver.onNext(res);
      responseObserver.onCompleted();
    }
  }

  private static class CombineAnyWithAll extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<String> a1 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a2 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a3 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a4 = ctx.awakeable(TypeTag.STRING_UTF8);

      Awaitable<Object> a12 = Awaitable.any(a1, a2);
      Awaitable<Object> a23 = Awaitable.any(a2, a3);
      Awaitable<Object> a34 = Awaitable.any(a3, a4);
      Awaitable.all(a12, a23, a34).await();

      responseObserver.onNext(greetingResponse(a12.await() + (String) a23.await() + a34.await()));
      responseObserver.onCompleted();
    }
  }

  private static class AwaitOnAlreadyResolvedAwaitables extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      Awaitable<String> a1 = ctx.awakeable(TypeTag.STRING_UTF8);
      Awaitable<String> a2 = ctx.awakeable(TypeTag.STRING_UTF8);

      Awaitable<Void> a12 = Awaitable.all(a1, a2);
      Awaitable<Void> a12and1 = Awaitable.all(a12, a1);
      Awaitable<Void> a121and12 = Awaitable.all(a12and1, a12);

      a12and1.await();
      a121and12.await();

      responseObserver.onNext(greetingResponse(a1.await() + a2.await()));
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<CoreTestRunner.TestDefinition> definitions() {
    return Stream.of(
        // --- Reverse await order
        testInvocation(new ReverseAwaitOrder(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                suspensionMessage(2))
            .named("None completed"),
        testInvocation(new ReverseAwaitOrder(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new ReverseAwaitOrder(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new ReverseAwaitOrder(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new ReverseAwaitOrder(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitTwiceTheSameAwaitable(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                outputMessage(greetingResponse("FRANCESCO-FRANCESCO"))),

        // --- All combinator
        testInvocation(new AwaitAll(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                suspensionMessage(1, 2))
            .named("No completions will suspend"),
        testInvocation(new AwaitAll(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAll(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAll(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAll(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAll(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAll(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAny(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")),
                suspensionMessage(1, 2))
            .named("No completions will suspend"),
        testInvocation(new AwaitAny(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAny(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAny(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAny(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new AwaitAny(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new CombineAnyWithAll(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new CombineAnyWithAll(), GreeterGrpc.getGreetMethod())
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

        // --- Compose nested and resolved all should work
        testInvocation(new AwaitOnAlreadyResolvedAwaitables(), GreeterGrpc.getGreetMethod())
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
                }));
  }
}
