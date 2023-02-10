package services;

import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.testing.testservices.ServiceTwoGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServiceTwo  extends ServiceTwoGrpc.ServiceTwoImplBase
        implements RestateBlockingService {

    private static final Logger LOG = LogManager.getLogger(TestGreeterService.class);

    @Override
    public void doSomething(dev.restate.sdk.testing.testservices.SomeRequest request, StreamObserver<dev.restate.sdk.testing.testservices.SomeResponse> responseObserver) {
        super.doSomething(request, responseObserver);
    }
}
