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

  private static class InterleaveTwoCalls extends GreeterGrpc.GreeterImplBase
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

  @Override
  Stream<CoreTestRunner.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new InterleaveTwoCalls(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder()))
            .usingAllThreadingModels()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")))
            .named("None completed"),
        testInvocation(new InterleaveTwoCalls(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new InterleaveTwoCalls(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new InterleaveTwoCalls(), GreeterGrpc.getGreetMethod())
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
        testInvocation(new InterleaveTwoCalls(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Till")))
            .named("Only A1 completed"),
        testInvocation(new AwaitTwiceTheSameAwaitable(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder()),
                completionMessage(1, greetingResponse("FRANCESCO")))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                outputMessage(greetingResponse("FRANCESCO-FRANCESCO")))
            .named("Await twice the same awaitable"));
  }
}
