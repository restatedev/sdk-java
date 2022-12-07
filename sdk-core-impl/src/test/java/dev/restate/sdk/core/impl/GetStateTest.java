package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.nio.charset.Charset;
import java.util.stream.Stream;

class GetStateTest extends CoreTestRunner {

  private static class GetStateGreeter extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String state =
          new String(
              RestateContext.current().get(StateKey.of("STATE", TypeTag.BYTES)).get(),
              Charset.defaultCharset());

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setInstanceKey(ByteString.copyFromUtf8("abc"))
                    .setInvocationId(ByteString.copyFromUtf8("123"))
                    .setKnownEntries(2)
                    .setKnownServiceVersion(1)
                    .build(),
                Protocol.PollInputStreamEntryMessage.newBuilder()
                    .setValue(GreetingRequest.newBuilder().setName("Till").build().toByteString())
                    .build(),
                Protocol.GetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .setValue(ByteString.copyFromUtf8("Francesco"))
                    .build())
            .usingAllThreadingModels()
            .expectingOutput(
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello Francesco")
                            .build()
                            .toByteString())
                    .build())
            .named("With GetStateEntry already completed"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setInstanceKey(ByteString.copyFromUtf8("abc"))
                    .setInvocationId(ByteString.copyFromUtf8("123"))
                    .setKnownEntries(1)
                    .setKnownServiceVersion(1)
                    .build(),
                Protocol.PollInputStreamEntryMessage.newBuilder()
                    .setValue(GreetingRequest.newBuilder().setName("Till").build().toByteString())
                    .build())
            .usingAllThreadingModels()
            .expectingOutput(
                Protocol.GetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .build())
            .named("Without GetStateEntry"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setInstanceKey(ByteString.copyFromUtf8("abc"))
                    .setInvocationId(ByteString.copyFromUtf8("123"))
                    .setKnownEntries(2)
                    .setKnownServiceVersion(1)
                    .build(),
                Protocol.PollInputStreamEntryMessage.newBuilder()
                    .setValue(GreetingRequest.newBuilder().setName("Till").build().toByteString())
                    .build(),
                Protocol.GetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .build())
            .usingAllThreadingModels()
            .expectingNoOutput()
            .named("With GetStateEntry not completed"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setInstanceKey(ByteString.copyFromUtf8("abc"))
                    .setInvocationId(ByteString.copyFromUtf8("123"))
                    .setKnownEntries(2)
                    .setKnownServiceVersion(1)
                    .build(),
                Protocol.PollInputStreamEntryMessage.newBuilder()
                    .setValue(GreetingRequest.newBuilder().setName("Till").build().toByteString())
                    .build(),
                Protocol.GetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .build(),
                Protocol.CompletionMessage.newBuilder()
                    .setEntryIndex(1)
                    .setValue(ByteString.copyFromUtf8("Francesco"))
                    .build())
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello Francesco")
                            .build()
                            .toByteString())
                    .build())
            .named("With GetStateEntry and completed with later CompletionFrame"),
        testInvocation(new GetStateGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setInstanceKey(ByteString.copyFromUtf8("abc"))
                    .setInvocationId(ByteString.copyFromUtf8("123"))
                    .setKnownEntries(1)
                    .setKnownServiceVersion(1)
                    .build(),
                Protocol.PollInputStreamEntryMessage.newBuilder()
                    .setValue(GreetingRequest.newBuilder().setName("Till").build().toByteString())
                    .build(),
                Protocol.CompletionMessage.newBuilder()
                    .setEntryIndex(1)
                    .setValue(ByteString.copyFromUtf8("Francesco"))
                    .build())
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(
                Protocol.GetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .build(),
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello Francesco")
                            .build()
                            .toByteString())
                    .build())
            .named("Without GetStateEntry and completed with later CompletionFrame"));
  }
}
