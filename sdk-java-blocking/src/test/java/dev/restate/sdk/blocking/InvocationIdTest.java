package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.InvocationId;
import dev.restate.sdk.core.impl.InvocationIdTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class InvocationIdTest extends InvocationIdTestSuite {

  private static class ReturnInvocationId extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onNext(greetingResponse(InvocationId.current().toString()));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService returnInvocationId() {
    return new ReturnInvocationId();
  }
}
