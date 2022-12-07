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
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

class GetAndSetStateTest extends CoreTestRunner {

  private static class GetAndSetGreeter extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = RestateContext.current();

      String state =
          new String(ctx.get(StateKey.of("STATE", TypeTag.BYTES)).get(), Charset.defaultCharset());

      ctx.set(
          StateKey.of("STATE", TypeTag.BYTES), request.getName().getBytes(StandardCharsets.UTF_8));

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello " + state).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new GetAndSetGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setInstanceKey(ByteString.copyFromUtf8("abc"))
                    .setInvocationId(ByteString.copyFromUtf8("123"))
                    .setKnownEntries(3)
                    .setKnownServiceVersion(1)
                    .build(),
                Protocol.PollInputStreamEntryMessage.newBuilder()
                    .setValue(GreetingRequest.newBuilder().setName("Till").build().toByteString())
                    .build(),
                Protocol.GetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .setValue(ByteString.copyFromUtf8("Francesco"))
                    .build(),
                Protocol.SetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .setValue(ByteString.copyFromUtf8("Till"))
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
            .named("With GetState and SetState"),
        testInvocation(new GetAndSetGreeter(), GreeterGrpc.getGreetMethod())
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
                Protocol.SetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .setValue(ByteString.copyFromUtf8("Till"))
                    .build(),
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello Francesco")
                            .build()
                            .toByteString())
                    .build())
            .named("With GetState already completed"),
        testInvocation(new GetAndSetGreeter(), GreeterGrpc.getGreetMethod())
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
                Protocol.SetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("STATE"))
                    .setValue(ByteString.copyFromUtf8("Till"))
                    .build(),
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello Francesco")
                            .build()
                            .toByteString())
                    .build())
            .named("With GetState completed later"));
  }
}
