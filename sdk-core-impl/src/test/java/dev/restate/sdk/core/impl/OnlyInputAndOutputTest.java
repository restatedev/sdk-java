package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

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
                startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Francesco")))
            .usingAllThreadingModels()
            .expectingOutput(
                outputMessage(
                    GreetingResponse.newBuilder().setMessage("Hello Francesco").build())));
  }
}
