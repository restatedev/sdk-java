package services;

import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.testing.testservices.ServiceTwoGrpc;
import dev.restate.sdk.testing.testservices.SomeResponse;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class ServiceTwo  extends ServiceTwoGrpc.ServiceTwoImplBase
        implements RestateBlockingService {

    private static final Logger LOG = LogManager.getLogger(ServiceTwo.class);

    private static final StateKey<Integer> COUNTER =
            StateKey.of(
                    "COUNTER",
                    TypeTag.using(
                            i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
                            b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

    @Override
    public void doSomething(dev.restate.sdk.testing.testservices.SomeRequest request, StreamObserver<dev.restate.sdk.testing.testservices.SomeResponse> responseObserver) {
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
                SomeResponse.newBuilder()
                        .setMessage("The new count for " + request.getName() + " is " + newCount)
                        .build());
        responseObserver.onCompleted();
    }
}
