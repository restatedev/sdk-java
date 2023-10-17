package dev.restate.sdk.blocking;

import dev.restate.sdk.core.impl.SleepTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SleepTest extends SleepTestSuite {

  private static class SleepGreeter extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.sleep(Duration.ofMillis(1000));

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello").build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sleepGreeter() {
    return new SleepGreeter();
  }

  private static class ManySleeps extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();
      List<Awaitable<?>> collectedAwaitables = new ArrayList<>();

      for (int i = 0; i < 10; i++) {
        collectedAwaitables.add(ctx.timer(Duration.ofMillis(1000)));
      }

      Awaitable.all(
              collectedAwaitables.get(0),
              collectedAwaitables.get(1),
              collectedAwaitables.subList(2, collectedAwaitables.size()).toArray(Awaitable[]::new))
          .await();

      responseObserver.onNext(GreetingResponse.newBuilder().build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService manySleeps() {
    return new ManySleeps();
  }
}
