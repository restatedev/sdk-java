package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.InvocationId;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.util.stream.Stream;

class InvocationIdTest extends CoreTestRunner {

  private static class ReturnInvocationId extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onNext(greetingResponse(InvocationId.current().toString()));
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    String debugId = "my-debug-id";
    ByteString id = ByteString.copyFromUtf8(debugId);

    return Stream.of(
        testInvocation(new ReturnInvocationId(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder().setDebugId(debugId).setId(id).setKnownEntries(1),
                inputMessage(GreetingRequest.getDefaultInstance()))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(outputMessage(greetingResponse(debugId))));
  }
}
