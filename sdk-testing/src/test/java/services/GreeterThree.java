package services;

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

/** Singleton service */
public class GreeterThree extends GreeterThreeGrpc.GreeterThreeImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(GreeterTwo.class);

  private static final StateKey<Integer> COUNTER =
      StateKey.of(
          "COUNTER",
          TypeTag.using(
              i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
              b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

  @Override
  public void countAllGreetings(
      GreeterThreeRequest request, StreamObserver<GreeterThreeResponse> responseObserver) {
    LOG.debug("Executing the GreeterTwo.countGreetings method");
    RestateContext ctx = restateContext();

    Optional<Integer> optionalOldCount = ctx.get(COUNTER);

    // increment old count by one and write back to state
    var newCount = 1;
    if (optionalOldCount.isPresent()) {
      var oldCount = optionalOldCount.get();
      newCount = oldCount + newCount;
    }

    ctx.set(COUNTER, newCount);

    responseObserver.onNext(
        GreeterThreeResponse.newBuilder()
            .setMessage("Hello " + request.getName() + ", you are greeter #" + newCount)
            .build());
    responseObserver.onCompleted();
  }
}
