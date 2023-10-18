package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.testInvocation;

import dev.restate.sdk.core.impl.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.impl.UserFailuresTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.stream.Stream;

public class UserFailuresTest extends UserFailuresTestSuite {

  private static class ThrowIllegalStateException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      throw new IllegalStateException("Whatever");
    }
  }

  @Override
  protected BindableService throwIllegalStateException() {
    return new ThrowIllegalStateException();
  }

  private static class SideEffectThrowIllegalStateException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext()
          .sideEffect(
              () -> {
                throw new IllegalStateException("Whatever");
              });
    }
  }

  @Override
  protected BindableService sideEffectThrowIllegalStateException() {
    return new SideEffectThrowIllegalStateException();
  }

  private static class ThrowStatusRuntimeException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final Status status;

    private ThrowStatusRuntimeException(Status status) {
      this.status = status;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      throw new StatusRuntimeException(status);
    }
  }

  @Override
  protected BindableService throwStatusRuntimeException(Status status) {
    return new ThrowStatusRuntimeException(status);
  }

  private static class SideEffectThrowStatusRuntimeException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final Status status;

    private SideEffectThrowStatusRuntimeException(Status status) {
      this.status = status;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext()
          .sideEffect(
              () -> {
                throw new StatusRuntimeException(status);
              });
    }
  }

  @Override
  protected BindableService sideEffectThrowStatusRuntimeException(Status status) {
    return new SideEffectThrowStatusRuntimeException(status);
  }

  // -- Response observer is something specific to the sdk-java-blocking interface

  private static class ResponseObserverOnErrorStatusRuntimeException
      extends GreeterGrpc.GreeterImplBase implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onError(new StatusRuntimeException(INTERNAL_MY_ERROR));
    }
  }

  private static class ResponseObserverOnErrorIllegalStateException
      extends GreeterGrpc.GreeterImplBase implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onError(new IllegalStateException("Whatever"));
    }
  }

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.concat(
        super.definitions(),
        Stream.of(
            testInvocation(
                    new ResponseObserverOnErrorStatusRuntimeException(),
                    GreeterGrpc.getGreetMethod())
                .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
                .expectingOutput(outputMessage(INTERNAL_MY_ERROR)),
            testInvocation(
                    new ResponseObserverOnErrorIllegalStateException(),
                    GreeterGrpc.getGreetMethod())
                .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
                .expectingOutput(outputMessage(Status.UNKNOWN))));
  }
}
