package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.testInvocation;

import dev.restate.sdk.core.TerminalException;
import dev.restate.sdk.core.impl.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.impl.UserFailuresTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
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

  private static class ThrowTerminalException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final TerminalException.Code code;
    private final String message;

    public ThrowTerminalException(TerminalException.Code code, String message) {
      this.code = code;
      this.message = message;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      throw new TerminalException(code, message);
    }
  }

  @Override
  protected BindableService throwTerminalException(TerminalException.Code code, String message) {
    return new ThrowTerminalException(code, message);
  }

  private static class SideEffectThrowTerminalException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    private final TerminalException.Code code;
    private final String message;

    private SideEffectThrowTerminalException(TerminalException.Code code, String message) {
      this.code = code;
      this.message = message;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext()
          .sideEffect(
              () -> {
                throw new TerminalException(code, message);
              });
    }
  }

  @Override
  protected BindableService sideEffectThrowTerminalException(
      TerminalException.Code code, String message) {
    return new SideEffectThrowTerminalException(code, message);
  }

  // -- Response observer is something specific to the sdk-java-blocking interface

  private static class ResponseObserverOnErrorTerminalException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onError(new TerminalException(TerminalException.Code.INTERNAL, MY_ERROR));
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
                    new ResponseObserverOnErrorTerminalException(), GreeterGrpc.getGreetMethod())
                .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
                .expectingOutput(outputMessage(TerminalException.Code.INTERNAL, MY_ERROR)),
            testInvocation(
                    new ResponseObserverOnErrorIllegalStateException(),
                    GreeterGrpc.getGreetMethod())
                .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
                .assertingOutput(
                    containsOnlyExactErrorMessage(new IllegalStateException("Whatever")))));
  }
}
