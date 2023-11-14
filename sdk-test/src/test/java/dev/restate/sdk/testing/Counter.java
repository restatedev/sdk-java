package dev.restate.sdk.testing;

import com.google.protobuf.Empty;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.examples.generated.*;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Counter extends CounterGrpc.CounterImplBase implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(Counter.class);

  private static final StateKey<Long> TOTAL = StateKey.of("total", CoreSerdes.LONG);

  @Override
  public void reset(CounterRequest request, StreamObserver<Empty> responseObserver) {
    restateContext().clear(TOTAL);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void add(CounterAddRequest request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = restateContext();

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request.getValue();
    ctx.set(TOTAL, newValue);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void get(CounterRequest request, StreamObserver<GetResponse> responseObserver) {
    long currentValue = restateContext().get(TOTAL).orElse(0L);

    responseObserver.onNext(GetResponse.newBuilder().setValue(currentValue).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getAndAdd(
      CounterAddRequest request, StreamObserver<CounterUpdateResult> responseObserver) {
    LOG.info("Invoked get and add with " + request.getValue());

    RestateContext ctx = restateContext();

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request.getValue();
    ctx.set(TOTAL, newValue);

    responseObserver.onNext(
        CounterUpdateResult.newBuilder().setOldValue(currentValue).setNewValue(newValue).build());
    responseObserver.onCompleted();
  }
}
