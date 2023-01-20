package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

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

  @Override
  Stream<CoreTestRunner.TestDefinition> definitions() {
    return Stream.of(
        // --- Reverse await order
        testInvocation(new ReverseAwaitOrder(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")))
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
                setStateMessage("A2", "TILL"))
            .named("Only A2 completed"),
        testInvocation(new ReverseAwaitOrder(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")))
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
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")))
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
            .expectingNoOutput()
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
            .expectingOutput(
                combinatorsMessage(1, 2), outputMessage(greetingResponse("FRANCESCO-TILL")))
            .named("Everything completed will generate the combinators message"),
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
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")))
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
            .expectingOutput(combinatorsMessage(1), outputMessage(greetingResponse("FRANCESCO")))
            .named("Everything completed will generate the combinators message"),
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
            .named("Complete any asynchronously"));
  }
}
