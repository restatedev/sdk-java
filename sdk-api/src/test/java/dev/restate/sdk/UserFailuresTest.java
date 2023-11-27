// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.testInvocation;

import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.UserFailuresTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final AtomicInteger nonTerminalExceptionsSeen;

    private SideEffectThrowIllegalStateException(AtomicInteger nonTerminalExceptionsSeen) {
      this.nonTerminalExceptionsSeen = nonTerminalExceptionsSeen;
    }

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      try {
        restateContext()
            .sideEffect(
                () -> {
                  throw new IllegalStateException("Whatever");
                });
      } catch (Throwable e) {
        // A user should never catch Throwable!!!
        if (AbortedExecutionException.INSTANCE.equals(e)) {
          AbortedExecutionException.sneakyThrow();
        }
        if (!(e instanceof TerminalException)) {
          nonTerminalExceptionsSeen.addAndGet(1);
        }
        throw e;
      }
    }
  }

  @Override
  protected BindableService sideEffectThrowIllegalStateException(
      AtomicInteger nonTerminalExceptionsSeen) {
    return new SideEffectThrowIllegalStateException(nonTerminalExceptionsSeen);
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

  // -- Response observer is something specific to the sdk-java interface

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
