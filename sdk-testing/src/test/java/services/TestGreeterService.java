package services;

import dev.restate.sdk.blocking.Awaitable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.testing.testservices.*;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestGreeterService extends TestGreeterGrpc.TestGreeterImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(TestGreeterService.class);
  private static final StateKey<String> STATE = StateKey.of("STATE", TypeTag.STRING_UTF8);

  private static final StateKey<Integer> COUNTER =
      StateKey.of(
          "COUNTER",
          TypeTag.using(
              i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
              b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

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
  public void greetCount(
      TestGreetingRequest request, StreamObserver<TestGreetingResponse> responseObserver) {
    LOG.debug("Starting the greeter.greetCount method");
    RestateContext ctx = restateContext();

    Optional<Integer> optionalOldCount = ctx.get(COUNTER);

    // increment old count by one and write back to state
    var newCount = 1;
    if (optionalOldCount.isPresent()) {
      var oldCount = optionalOldCount.get();
      LOG.debug("The counter was: " + oldCount);
      newCount = oldCount + newCount;
    }

    ctx.set(COUNTER, newCount);

    LOG.debug("The new count for {} is {} ", request.getName(), newCount);

    responseObserver.onNext(
        TestGreetingResponse.newBuilder()
            .setMessage("The new count for " + request.getName() + " is " + newCount)
            .build());
    responseObserver.onCompleted();
  }


  @Override
  public void getSetClearState(TestGreetingRequest request, StreamObserver<TestGreetingResponse> responseObserver) {
    LOG.debug("Starting the greeter.getSetClearState method");
    RestateContext ctx = restateContext();

    Optional<Integer> optionalOldCount = ctx.get(COUNTER);

    // increment old count by one and write back to state
    var updatedCount = 1;
    if (optionalOldCount.isPresent()) {
      var oldCount = optionalOldCount.get();
      LOG.debug("The counter was: " + oldCount);
      updatedCount = oldCount + updatedCount;
    }

    ctx.set(COUNTER, updatedCount);

    Optional<Integer> optionalNewCount = ctx.get(COUNTER);
    LOG.debug("The counter was: " + optionalNewCount.get());

    // clear the state
    ctx.clear(COUNTER);

    // try to get the state again; it should be empty
    Optional<Integer> optionalClearedCount = ctx.get(COUNTER);

    var msg = optionalClearedCount.isPresent() ? "State did not get cleared" : "State got cleared";

    responseObserver.onNext(
            TestGreetingResponse.newBuilder()
                    .setMessage(msg)
                    .build());
    responseObserver.onCompleted();
  }

  @Override
  public void callOtherService(TestGreetingRequest request, StreamObserver<TestGreetingResponse> responseObserver) {
    RestateContext ctx = restateContext();

    Awaitable<SomeResponse> a1 =
            ctx.call(ServiceTwoGrpc.getDoSomethingMethod(), SomeRequest.newBuilder().setName("Goofy").build());

    responseObserver.onNext(
            TestGreetingResponse.newBuilder()
                    .setMessage("The new count for " + request.getName() + " is " + a1.await().getMessage())
                    .build());
    responseObserver.onCompleted();


  }
}
