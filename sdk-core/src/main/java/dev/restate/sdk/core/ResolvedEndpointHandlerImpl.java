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
import dev.restate.sdk.common.syscalls.*;

import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

final class ResolvedEndpointHandlerImpl implements ResolvedEndpointHandler {

  private static final Logger LOG = LogManager.getLogger(ResolvedEndpointHandlerImpl.class);

  private final InvocationStateMachine stateMachine;
  private final HandlerSpecification<Object, Object> spec;
  private final InvocationHandler<Object, Object, Object> wrappedHandler;
  private final Object componentOptions;
  private final @Nullable Executor syscallsExecutor;

  @SuppressWarnings("unchecked")
  public ResolvedEndpointHandlerImpl(
      InvocationStateMachine stateMachine,
      HandlerDefinition<?, ?, Object> handler,
      Object serviceOptions,
      @Nullable Executor syscallExecutor) {
    this.stateMachine = stateMachine;
      this.spec = (HandlerSpecification<Object, Object>) handler.getSpec();
    this.wrappedHandler =  new InvocationHandlerWrapper<>((InvocationHandler<Object, Object, Object>)handler.getHandler());
    this.componentOptions = serviceOptions;
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
    LOG.trace("Start processing invocation");
    stateMachine.startAndConsumeInput(
        SyscallCallback.of(
            request -> {
              // Prepare Syscalls object
              SyscallsInternal syscalls =
                  this.syscallsExecutor != null
                      ? new ExecutorSwitchingSyscalls(
                          new SyscallsImpl(request, stateMachine), this.syscallsExecutor)
                      : new SyscallsImpl(request, stateMachine);

              // pollInput then invoke the wrappedHandler
              wrappedHandler.handle(
                  spec,
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

  private static class InvocationHandlerWrapper<REQ, RES, O> implements InvocationHandler<REQ, RES, O> {

    private final InvocationHandler<REQ, RES, O> handler;

    private InvocationHandlerWrapper(InvocationHandler<REQ, RES, O> handler) {
      this.handler = handler;
    }

    @Override
    public void handle(HandlerSpecification<REQ, RES> spec, Syscalls syscalls, O options, SyscallCallback<ByteString> callback) {
      try {
        this.handler.handle(spec, syscalls, options, callback);
      } catch (Throwable e) {
        callback.onCancel(e);
      }
    }
  }
}
