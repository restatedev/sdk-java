package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.stream.Stream;

class UserFailuresTest extends CoreTestRunner {

  private static final Status MY_ERROR = Status.INTERNAL.withDescription("my error");

  private static class ThrowIllegalStateException extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      throw new IllegalStateException("Whatever");
    }
  }

  private static class ResponseObserverOnErrorIllegalStateException
      extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onError(new IllegalStateException("Whatever"));
    }
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

  private static class ThrowUnknownStatusRuntimeException extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      throw new StatusRuntimeException(Status.UNKNOWN.withDescription("Whatever"));
    }
  }

  private static class ThrowStatusRuntimeException extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      throw new StatusRuntimeException(MY_ERROR);
    }
  }

  private static class ResponseObserverOnErrorStatusRuntimeException
      extends GreeterGrpc.GreeterImplBase {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onError(new StatusRuntimeException(MY_ERROR));
    }
  }

  private static class SideEffectThrowStatusRuntimeException extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {
    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      restateContext()
          .sideEffect(
              () -> {
                throw new StatusRuntimeException(MY_ERROR);
              });
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        // Cases returning ErrorMessage
        testInvocation(new ThrowIllegalStateException(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .assertingOutput(containsOnlyExactErrorMessage(new IllegalStateException("Whatever"))),
        testInvocation(new SideEffectThrowIllegalStateException(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .assertingOutput(containsOnlyExactErrorMessage(new IllegalStateException("Whatever"))),

        // Cases completing the invocation with OutputStreamEntry.failure
        testInvocation(new ThrowStatusRuntimeException(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(MY_ERROR)),
        testInvocation(new ThrowUnknownStatusRuntimeException(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(Status.UNKNOWN.withDescription("Whatever"))),
        testInvocation(
                new ResponseObserverOnErrorStatusRuntimeException(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(MY_ERROR)),
        testInvocation(
                new ResponseObserverOnErrorIllegalStateException(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(Status.UNKNOWN)),
        testInvocation(new SideEffectThrowStatusRuntimeException(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()))
            .usingAllThreadingModels()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setFailure(Util.toProtocolFailure(MY_ERROR)),
                outputMessage(MY_ERROR)));
  }
}
