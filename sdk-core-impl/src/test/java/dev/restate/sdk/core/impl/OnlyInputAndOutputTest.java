package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.stream.Stream;

class OnlyInputAndOutputTest extends CoreTestRunner {

  private static class NoSyscallsGreeter extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage("Hello " + request.getName()).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new NoSyscallsGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setInstanceKey(ByteString.copyFromUtf8("abc"))
                    .setInvocationId(ByteString.copyFromUtf8("123"))
                    .setKnownEntries(1)
                    .setKnownServiceVersion(1)
                    .build(),
                Protocol.PollInputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingRequest.newBuilder().setName("Francesco").build().toByteString())
                    .build())
            .usingAllThreadingModels()
            .expectingOutput(
                Protocol.OutputStreamEntryMessage.newBuilder()
                    .setValue(
                        GreetingResponse.newBuilder()
                            .setMessage("Hello Francesco")
                            .build()
                            .toByteString())
                    .build()));
  }
}
