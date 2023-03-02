package dev.restate.sdk.testing.services;

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

/** Keyed service. key = name */
public class GreeterTwo extends GreeterTwoGrpc.GreeterTwoImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(GreeterTwo.class);

  private static final StateKey<Integer> COUNTER =
      StateKey.of(
          "COUNTER",
          TypeTag.using(
              i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
              b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

  @Override
  public void countForwardedGreetings(
      GreeterTwoRequest request, StreamObserver<GreeterTwoResponse> responseObserver) {
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
        GreeterTwoResponse.newBuilder()
            .setMessage("Hello " + request.getName() + " #" + newCount)
            .build());
    responseObserver.onCompleted();
  }
}
