package services;

import com.google.protobuf.Empty;
import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.testing.testservices.*;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

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

        responseObserver.onNext(GreeterTwoResponse.newBuilder().setMessage("Hello " + request.getName() + " #" + newCount).build());
        responseObserver.onCompleted();
    }

    @Override
    public void awake(AwakeServiceRequest request, StreamObserver<Empty> responseObserver) {
        LOG.debug("Executing the GreeterTwo.awakeTheOtherService method");
        RestateContext ctx = restateContext();
        AwakeableIdentifier identifier = AwakeableIdentifier.newBuilder()
                .setServiceName(request.getServiceName())
                .setInstanceKey(request.getInstanceKey())
                .setInvocationId(request.getInvocationId())
                .setEntryIndex(request.getEntryIndex())
                .build();

        ctx.completeAwakeable(identifier, TypeTag.STRING_UTF8, "Wake up!");

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
