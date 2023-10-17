package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingRequest;
import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.CompensationTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class CompensationTest extends CompensationTestSuite {

  private static class ThrowManually extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.compensate(() -> ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), "none"));

      throw Status.FAILED_PRECONDITION.asRuntimeException();
    }
  }

  @Override
  protected BindableService throwManually() {
    return new ThrowManually();
  }

  private static class NonTerminalErrorDoesntExecuteCompensations
      extends GreeterGrpc.GreeterImplBase implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.compensate(() -> ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), "none"));

      throw new RuntimeException("non-terminal");
    }
  }

  @Override
  protected BindableService nonTerminalErrorDoesntExecuteCompensations() {
    return new NonTerminalErrorDoesntExecuteCompensations();
  }

  private static class CallCompensate extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.compensate(() -> ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), "none"));
      GreetingResponse greetingResponse =
          ctx.call(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")).await();
      ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), greetingResponse.getMessage());

      responseObserver.onNext(greetingResponse("ok"));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService callCompensate() {
    return new CallCompensate();
  }

  private static class IllegalGetStateWithinCompensation extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.compensate(() -> ctx.get(StateKey.of("message", TypeTag.STRING_UTF8)));

      throw Status.FAILED_PRECONDITION.asRuntimeException();
    }
  }

  @Override
  protected BindableService illegalGetStateWithinCompensation() {
    return new IllegalGetStateWithinCompensation();
  }
}
