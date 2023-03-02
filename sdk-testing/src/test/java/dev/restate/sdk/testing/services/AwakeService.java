package dev.restate.sdk.testing.services;

import com.google.protobuf.Empty;
import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.sdk.blocking.AwakeableHandle;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.testing.testservices.*;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Unkeyed service */
public class AwakeService extends AwakeServiceGrpc.AwakeServiceImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(GreeterTwo.class);

  @Override
  public void awake(AwakeServiceRequest request, StreamObserver<Empty> responseObserver) {
    LOG.debug("Executing the GreeterTwo.awakeTheOtherService method");
    RestateContext ctx = restateContext();
    AwakeableIdentifier identifier =
        AwakeableIdentifier.newBuilder()
            .setServiceName(request.getServiceName())
            .setInstanceKey(request.getInstanceKey())
            .setInvocationId(request.getInvocationId())
            .setEntryIndex(request.getEntryIndex())
            .build();

    AwakeableHandle awakeableHandle = ctx.awakeableHandle(identifier);
    awakeableHandle.complete(TypeTag.STRING_UTF8, "Wake up!");

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
