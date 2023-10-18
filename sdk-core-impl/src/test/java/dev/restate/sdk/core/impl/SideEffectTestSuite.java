package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.ByteString;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class SideEffectTestSuite implements TestSuite {

  protected abstract BindableService sideEffect(String sideEffectOutput);

  protected abstract BindableService consecutiveSideEffect(String sideEffectOutput);

  protected abstract BindableService checkContextSwitching();

  protected abstract BindableService sideEffectGuard();

  protected abstract BindableService sideEffectThenAwakeable();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(() -> this.sideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello Francesco"))),
        testInvocation(() -> this.consecutiveSideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")),
                suspensionMessage(1))
            .named("Without ack"),
        testInvocation(() -> this.consecutiveSideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.CompletionMessage.newBuilder().setEntryIndex(1))
            .onlyUnbuffered()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco")),
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("FRANCESCO")),
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello FRANCESCO")))
            .named("With ack"),
        testInvocation(this::checkContextSwitching, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .onlyUnbuffered()
            .assertingOutput(
                actualOutputMessages -> {
                  assertThat(actualOutputMessages).hasSize(2);
                  assertThat(actualOutputMessages)
                      .element(0)
                      .asInstanceOf(type(Java.SideEffectEntryMessage.class))
                      .returns(true, Java.SideEffectEntryMessage::hasValue);
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
        testInvocation(this::sideEffectGuard, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .assertingOutput(
                containsOnlyExactErrorMessage(ProtocolException.invalidSideEffectCall())),
        testInvocation(this::sideEffectThenAwakeable, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Java.SideEffectEntryMessage.newBuilder().setValue(ByteString.copyFromUtf8("")))
            .expectingOutput(awakeable(), suspensionMessage(2)));
  }
}
