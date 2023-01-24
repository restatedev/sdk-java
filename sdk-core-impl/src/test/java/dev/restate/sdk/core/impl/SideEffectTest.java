package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import java.util.stream.Stream;

class SideEffectTest extends CoreTestRunner {

  private static class SideEffect extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final String sideEffectOutput;

    SideEffect(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String result = ctx.sideEffect(TypeTag.STRING_UTF8, () -> this.sideEffectOutput);

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + result).build());
      responseObserver.onCompleted();
    }
  }

  private static class ConsecutiveSideEffect extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final String sideEffectOutput;

    ConsecutiveSideEffect(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      String firstResult = ctx.sideEffect(TypeTag.STRING_UTF8, () -> this.sideEffectOutput);
      String secondResult = ctx.sideEffect(TypeTag.STRING_UTF8, firstResult::toUpperCase);

      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage("Hello " + secondResult).build());
      responseObserver.onCompleted();
    }
  }

  private static class CheckContextSwitching extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String currentThread = Thread.currentThread().getName();

      String sideEffectThread =
          restateContext().sideEffect(TypeTag.STRING_UTF8, () -> Thread.currentThread().getName());

      if (!Objects.equals(currentThread, sideEffectThread)) {
        throw new IllegalStateException(
            "Current thread and side effect thread do not match: "
                + currentThread
                + " != "
                + sideEffectThread);
      }

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello").build());
      responseObserver.onCompleted();
    }
  }

  private static class SideEffectGuard extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();
      ctx.sideEffect(
          () -> ctx.backgroundCall(GreeterGrpc.getGreetMethod(), greetingRequest("something")));

      throw new IllegalStateException("This point should not be reached");
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new SideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .expectingOutput(
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco"))),
        testInvocation(new ConsecutiveSideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .expectingOutput(
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")))
            .named("Without ack"),
        testInvocation(new ConsecutiveSideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.CompletionMessage.newBuilder().setEntryIndex(1))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")),
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("FRANCESCO")),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello FRANCESCO")))
            .named("With ack"),
        testInvocation(new CheckContextSwitching(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .assertingOutput(
                actualOutputMessages -> {
                  assertThat(actualOutputMessages).hasSize(2);
                  assertThat(actualOutputMessages)
                      .element(0)
                      .asInstanceOf(type(Protocol.SideEffectEntryMessage.class))
                      .returns(true, Protocol.SideEffectEntryMessage::hasValue);
                  assertThat(actualOutputMessages)
                      .element(1)
                      .isEqualTo(
                          Protocol.OutputStreamEntryMessage.newBuilder()
                              .setValue(
                                  GreetingResponse.newBuilder()
                                      .setMessage("Hello")
                                      .build()
                                      .toByteString())
                              .build());
                }),
        testInvocation(new SideEffectGuard(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .assertingFailure(ProtocolException.class));
  }
}
