package dev.restate.sdk.testing;

import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.testing.testservices.TestGreeterGrpc;
import dev.restate.sdk.testing.testservices.TestGreetingRequest;
import dev.restate.sdk.testing.testservices.TestGreetingResponse;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestGreeterService extends TestGreeterGrpc.TestGreeterImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(TestGreeterService.class);
  StateKey<String> STATE = StateKey.of("STATE", TypeTag.STRING_UTF8);

  @Override
  public void greet(
      TestGreetingRequest request, StreamObserver<TestGreetingResponse> responseObserver) {
    LOG.debug("Starting the greeter.greet method");

    restateContext().set(STATE, "Till");
    String state = restateContext().get(STATE).get();

    LOG.debug("The state contained: " + state);

    responseObserver.onNext(TestGreetingResponse.newBuilder().setMessage("Hello " + state).build());
    responseObserver.onCompleted();
  }

  @Override
  public void greetCount(TestGreetingRequest request, StreamObserver<TestGreetingResponse> responseObserver) {
    super.greetCount(request, responseObserver);
  }
}
