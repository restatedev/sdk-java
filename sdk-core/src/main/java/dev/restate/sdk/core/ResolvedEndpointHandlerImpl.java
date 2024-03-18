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

  private static final Logger LOG = LogManager.getLogger(ResolvedEndpointHandlerImpl.class);

  private final InvocationStateMachine stateMachine;
  private final RestateEndpoint.LoggingContextSetter loggingContextSetter;
  private final InvocationHandler<Object> wrappedHandler;
  private final Object componentOptions;
  private final @Nullable Executor syscallsExecutor;

  public ResolvedEndpointHandlerImpl(
      InvocationStateMachine stateMachine,
      RestateEndpoint.LoggingContextSetter loggingContextSetter,
      InvocationHandler<Object> handler,
      Object componentOptions,
      @Nullable Executor syscallExecutor) {
    this.stateMachine = stateMachine;
    this.loggingContextSetter = loggingContextSetter;
    this.wrappedHandler = new InvocationHandlerWrapper<>(handler);
    this.componentOptions = componentOptions;
    this.syscallsExecutor = syscallExecutor;
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
    stateMachine.startAndConsumeInput(
        SyscallCallback.of(
            request -> {
              // Set invocation id in logging context
              loggingContextSetter.setInvocationId(request.invocationId().toString());

              // Prepare Syscalls object
              SyscallsInternal syscalls =
                  this.syscallsExecutor != null
                      ? new ExecutorSwitchingSyscalls(
                          new SyscallsImpl(request, stateMachine), this.syscallsExecutor)
                      : new SyscallsImpl(request, stateMachine);

              // pollInput then invoke the wrappedHandler
              wrappedHandler.handle(
                  syscalls,
                  componentOptions,
                  SyscallCallback.of(
                      o -> this.writeOutputAndEnd(syscalls, o), t -> this.end(syscalls, t)));
            },
            t -> {}));
  }

  private void writeOutputAndEnd(SyscallsInternal syscalls, ByteString output) {
    syscalls.writeOutput(
        output,
        SyscallCallback.ofVoid(
            () -> {
              LOG.trace("Wrote output message:\n{}", output);
              this.end(syscalls, null);
            },
            syscalls::fail));
  }

  private void end(SyscallsInternal syscalls, @Nullable Throwable exception) {
    if (exception == null || Util.containsSuspendedException(exception)) {
      syscalls.close();
    } else {
      LOG.warn("Error when processing the invocation", exception);
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

  private static class InvocationHandlerWrapper<O> implements InvocationHandler<O> {

    private final InvocationHandler<O> handler;

    private InvocationHandlerWrapper(InvocationHandler<O> handler) {
      this.handler = handler;
    }

    @Override
    public void handle(Syscalls syscalls, O options, SyscallCallback<ByteString> callback) {
      try {
        this.handler.handle(syscalls, options, callback);
      } catch (Throwable e) {
        callback.onCancel(e);
      }
    }
  }
}
