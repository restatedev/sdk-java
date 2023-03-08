package dev.restate.sdk.lambda.testservices;

import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;

public class JavaCounterService extends JavaCounterGrpc.JavaCounterImplBase
    implements RestateBlockingService {

  public static final StateKey<Long> COUNTER =
      StateKey.of(
          "counter",
          TypeTag.using(
              l -> l.toString().getBytes(StandardCharsets.UTF_8),
              v -> Long.parseLong(new String(v, StandardCharsets.UTF_8))));

  @Override
  public void get(CounterRequest request, StreamObserver<GetResponse> responseObserver) {
    var count = restateContext().get(COUNTER).orElse(0L) + 1;

    throw new IllegalStateException("We shouldn't reach this point");
  }
}
