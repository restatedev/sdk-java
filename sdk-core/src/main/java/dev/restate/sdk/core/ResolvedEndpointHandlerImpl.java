// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.InvocationHandler;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

final class ResolvedEndpointHandlerImpl implements ResolvedEndpointHandler {

  private static final Logger LOG = LogManager.getLogger(RestateEndpoint.class);

  private final InvocationStateMachine stateMachine;
  private final RestateEndpoint.LoggingContextSetter loggingContextSetter;
  private final SyscallsInternal syscalls;
  private final InvocationHandler wrappedHandler;

  public ResolvedEndpointHandlerImpl(
      InvocationStateMachine stateMachine,
      RestateEndpoint.LoggingContextSetter loggingContextSetter,
      SyscallsInternal syscalls,
      InvocationHandler handler,
      @Nullable Executor userCodeExecutor) {
    this.stateMachine = stateMachine;
    this.loggingContextSetter = loggingContextSetter;
    this.syscalls = syscalls;
    this.wrappedHandler =
        userCodeExecutor == null
            ? new InvocationHandlerWrapper(handler)
            : new ExecutorSwitchingInvocationHandlerWrapper(handler, userCodeExecutor);
  }

  @Override
  public InvocationFlow.InvocationInputSubscriber input() {
    return new ExceptionCatchingInvocationInputSubscriber(stateMachine);
  }

  @Override
  public InvocationFlow.InvocationOutputPublisher output() {
    return stateMachine;
  }

  @Override
  public void start() {
    LOG.info("Start processing invocation");
    stateMachine.start(
        invocationId -> {
          // Set invocation id in logging context
          loggingContextSetter.setInvocationId(invocationId.toString());

          // pollInput then invoke the wrappedHandler
          syscalls.pollInputAndResolve(
              SyscallCallback.of(
                  pollInputReadyResult -> {
                    if (pollInputReadyResult.isSuccess()) {
                      final Object message = pollInputReadyResult.getValue();
                      LOG.trace("Read input message:\n{}", message);

                      wrappedHandler.handle(
                          syscalls,
                          pollInputReadyResult.getValue(),
                          SyscallCallback.of(this::writeOutputAndEnd, this::end));
                    } else {
                      // PollInputStream failed.
                      // This is probably a cancellation.
                      this.end(pollInputReadyResult.getFailure());
                    }
                  },
                  syscalls::fail));
        });
  }

  private void writeOutputAndEnd(ByteString output) {
    syscalls.writeOutput(
        output,
        SyscallCallback.ofVoid(
            () -> {
              LOG.trace("Wrote output message:\n{}", output);
              this.end();
            },
            syscalls::fail));
  }

  private void end() {
    this.end(null);
  }

  private void end(@Nullable Throwable exception) {
    if (exception == null || Util.containsSuspendedException(exception)) {
      syscalls.close();
    } else {
      if (Util.isTerminalException(exception)) {
        syscalls.writeOutput(
            (TerminalException) exception,
            SyscallCallback.ofVoid(
                () -> {
                  LOG.trace("Closed correctly with non ok exception", exception);
                  syscalls.close();
                },
                syscalls::fail));
      } else {
        syscalls.fail(exception);
      }
    }
  }

  private class InvocationHandlerWrapper implements InvocationHandler {

    private final InvocationHandler handler;

    private InvocationHandlerWrapper(InvocationHandler handler) {
      this.handler = handler;
    }

    @Override
    public void handle(Syscalls syscalls, ByteString input, SyscallCallback<ByteString> callback) {
      try {
        this.handler.handle(syscalls, input, callback);
      } catch (Throwable e) {
        LOG.warn("Error when processing the invocation", e);
        ResolvedEndpointHandlerImpl.this.end(e);
      }
    }
  }

  private class ExecutorSwitchingInvocationHandlerWrapper implements InvocationHandler {
    private final InvocationHandler handler;
    private final Executor userCodeExecutor;

    private ExecutorSwitchingInvocationHandlerWrapper(
        InvocationHandler handler, Executor userCodeExecutor) {
      this.handler = handler;
      this.userCodeExecutor = userCodeExecutor;
    }

    @Override
    public void handle(Syscalls syscalls, ByteString input, SyscallCallback<ByteString> callback) {
      userCodeExecutor.execute(
          () -> {
            try {
              this.handler.handle(syscalls, input, callback);
            } catch (Throwable e) {
              LOG.warn("Error when processing the invocation", e);
              ResolvedEndpointHandlerImpl.this.end(e);
            }
          });
    }
  }
}
