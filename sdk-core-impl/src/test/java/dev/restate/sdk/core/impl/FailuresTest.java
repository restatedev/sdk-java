package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.stream.Stream;

class FailuresTest extends CoreTestRunner {

  private static class FailingGreeter extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      throw new IllegalStateException("Whatever");
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new FailingGreeter(), GreeterGrpc.getGreetMethod())
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
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setFailure(Util.toProtocolFailure(new IllegalStateException("Whatever")))
                    .build()));
  }
}
