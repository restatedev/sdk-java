package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
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

      byte[] result =
          ctx.sideEffect(
              TypeTag.BYTES, () -> this.sideEffectOutput.getBytes(StandardCharsets.UTF_8));

      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage("Hello " + new String(result)).build());
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

      byte[] firstResult =
          ctx.sideEffect(
              TypeTag.BYTES, () -> this.sideEffectOutput.getBytes(StandardCharsets.UTF_8));
      byte[] secondResult =
          ctx.sideEffect(
              TypeTag.BYTES,
              () -> new String(firstResult).toUpperCase().getBytes(StandardCharsets.UTF_8));

      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage("Hello " + new String(secondResult)).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new SideEffectGreeter("Francesco"), GreeterGrpc.getGreetMethod())
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
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco"))
                    .build(),
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello Francesco")
                            .build()
                            .toByteString())
                    .build())
            .named("Simple side effect"),
        testInvocation(new ConsecutiveSideEffectGreeter("Francesco"), GreeterGrpc.getGreetMethod())
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
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco"))
                    .build())
            .named("Consecutive side effect without ack"),
        testInvocation(new ConsecutiveSideEffectGreeter("Francesco"), GreeterGrpc.getGreetMethod())
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
                Protocol.CompletionMessage.newBuilder().setEntryIndex(1).build())
            .usingAllThreadingModels()
            .expectingOutput(
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("Francesco"))
                    .build(),
                Protocol.SideEffectEntryMessage.newBuilder()
                    .setValue(ByteString.copyFromUtf8("FRANCESCO"))
                    .build(),
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello FRANCESCO")
                            .build()
                            .toByteString())
                    .build())
            .named("Consecutive side effect with ack"));
  }
}
