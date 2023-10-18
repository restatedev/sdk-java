package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.AwakeableIdTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class AwakeableIdTest extends AwakeableIdTestSuite {

  private static class ReturnAwakeableId extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String id = restateContext().awakeable(TypeTag.STRING_UTF8).id();
      responseObserver.onNext(greetingResponse(id));
      responseObserver.onCompleted();
    }
  }

  protected BindableService returnAwakeableId() {
    return new ReturnAwakeableId();
  }
}
