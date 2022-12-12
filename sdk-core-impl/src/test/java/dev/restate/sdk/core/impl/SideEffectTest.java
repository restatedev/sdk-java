package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import java.util.stream.Stream;

class SideEffectTest extends CoreTestRunner {

  private static class SideEffectGreeter extends GreeterGrpc.GreeterImplBase {

    private final String sideEffectOutput;

    SideEffectGreeter(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = RestateContext.current();

      String result = ctx.sideEffect(TypeTag.STRING_UTF8, () -> this.sideEffectOutput);

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + result).build());
      responseObserver.onCompleted();
    }
  }

  private static class ConsecutiveSideEffectGreeter extends GreeterGrpc.GreeterImplBase {

    private final String sideEffectOutput;

    ConsecutiveSideEffectGreeter(String sideEffectOutput) {
      this.sideEffectOutput = sideEffectOutput;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = RestateContext.current();

      String firstResult = ctx.sideEffect(TypeTag.STRING_UTF8, () -> this.sideEffectOutput);
      String secondResult = ctx.sideEffect(TypeTag.STRING_UTF8, firstResult::toUpperCase);

      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage("Hello " + secondResult).build());
      responseObserver.onCompleted();
    }
  }

  private static class CheckCorrectThreadTrampolineGreeter extends GreeterGrpc.GreeterImplBase {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String currentThread = Thread.currentThread().getName();

      String sideEffectThread =
          RestateContext.current()
              .sideEffect(TypeTag.STRING_UTF8, () -> Thread.currentThread().getName());

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

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new SideEffectGreeter("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .expectingOutput(
                orderMessage(0),
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco")))
            .named("Simple side effect"),
        testInvocation(new ConsecutiveSideEffectGreeter("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .expectingOutput(
                orderMessage(0),
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")))
            .named("Consecutive side effect without ack"),
        testInvocation(new ConsecutiveSideEffectGreeter("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.CompletionMessage.newBuilder().setEntryIndex(2))
            .usingAllThreadingModels()
            .expectingOutput(
                orderMessage(0),
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")),
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("FRANCESCO")),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello FRANCESCO")))
            .named("Consecutive side effect with ack"),
        testInvocation(new CheckCorrectThreadTrampolineGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .assertingOutput(
                actualOutputMessages -> {
                  assertThat(actualOutputMessages).hasSize(3);
                  assertThat(actualOutputMessages)
                      .element(1)
                      .asInstanceOf(type(Protocol.SideEffectEntryMessage.class))
                      .returns(true, Protocol.SideEffectEntryMessage::hasValue);
                  assertThat(actualOutputMessages)
                      .element(2)
                      .isEqualTo(
                          Protocol.OutputStreamEntryMessage.newBuilder()
                              .setValue(
                                  GreetingResponse.newBuilder()
                                      .setMessage("Hello")
                                      .build()
                                      .toByteString())
                              .build());
                })
            .named("Check thread trampolining"));
  }
}
