package dev.restate.sdk.blocking;

import dev.restate.sdk.core.impl.OnlyInputAndOutputTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

class OnlyInputAndOutputTest extends OnlyInputAndOutputTestSuite {

  private static class NoSyscallsGreeter extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onNext(
          GreetingResponse.newBuilder().setMessage("Hello " + request.getName()).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService noSyscallsGreeter() {
    return new NoSyscallsGreeter();
  }
}
