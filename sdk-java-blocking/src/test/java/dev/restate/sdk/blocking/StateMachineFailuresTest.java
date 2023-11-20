package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.Serde;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.TerminalException;
import dev.restate.sdk.core.impl.StateMachineFailuresTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class StateMachineFailuresTest extends StateMachineFailuresTestSuite {

  private static class GetState extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private static final StateKey<Integer> STATE =
        StateKey.of(
            "STATE",
            Serde.using(
                i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
                b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

    private final AtomicInteger nonTerminalExceptionsSeen;

    private GetState(AtomicInteger nonTerminalExceptionsSeen) {
      this.nonTerminalExceptionsSeen = nonTerminalExceptionsSeen;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      try {
        restateContext().get(STATE);
      } catch (Throwable e) {
        // A user should never catch Throwable!!!
        if (SuspendedException.INSTANCE.equals(e)) {
          SuspendedException.sneakyThrow();
        }
        if (!(e instanceof TerminalException)) {
          nonTerminalExceptionsSeen.addAndGet(1);
        } else {
          throw e;
        }
      }
      responseObserver.onNext(greetingResponse("Francesco"));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService getState(AtomicInteger nonTerminalExceptionsSeen) {
    return new GetState(nonTerminalExceptionsSeen);
  }

  private static class SideEffectFailure extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    private final Serde<Integer> serde;

    private SideEffectFailure(Serde<Integer> serde) {
      this.serde = serde;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext().sideEffect(serde, () -> 0);

      responseObserver.onNext(greetingResponse("Francesco"));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sideEffectFailure(Serde<Integer> serde) {
    return new SideEffectFailure(serde);
  }
}
