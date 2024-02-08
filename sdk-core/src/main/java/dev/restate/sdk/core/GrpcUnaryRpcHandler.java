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
import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import dev.restate.sdk.common.syscalls.Syscalls;
import io.grpc.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class GrpcUnaryRpcHandler<Req, Res> implements RpcHandler {

  private static final Logger LOG = LogManager.getLogger(GrpcUnaryRpcHandler.class);

  private final SyscallsInternal syscalls;
  private final RestateServerCallListener<Req> restateListener;
  private final CompletableFuture<Void> serverCallReady;
  private final MethodDescriptor<Req, Res> methodDescriptor;

  GrpcUnaryRpcHandler(
      ServerMethodDefinition<Req, Res> method,
      SyscallsInternal syscalls,
      @Nullable Executor userCodeExecutor) {
    this.syscalls = syscalls;
    this.methodDescriptor = method.getMethodDescriptor();
    this.serverCallReady = new CompletableFuture<>();
    RestateServerCall<Req, Res> serverCall =
        new RestateServerCall<>(method.getMethodDescriptor(), this.syscalls, this.serverCallReady);

    // This gRPC context will be propagated to the user thread.
    // Note: from now on we cannot modify this context anymore!
    io.grpc.Context context =
        Context.current()
            .withValue(InvocationId.INVOCATION_ID_KEY, this.syscalls.invocationId())
            .withValue(Syscalls.SYSCALLS_KEY, this.syscalls);

    // Create the listener
    RestateServerCallListener<Req> listener =
        new GrpcServerCallListenerAdaptor<>(
            context, serverCall, new Metadata(), method.getServerCallHandler());

    // Wrap in the executor switcher, if needed
    if (userCodeExecutor != null) {
      listener = new ExecutorSwitchingServerCallListener<>(listener, userCodeExecutor);
    }

    this.restateListener = listener;
  }

  @Override
  public void start() {
    this.restateListener.ready();

    // Wait for the first poll input stream entry and start user code afterward
    syscalls.pollInputAndResolve(
        b -> this.methodDescriptor.parseRequest(b.newInput()),
        SyscallCallback.of(
            pollInputReadyResult -> {
              if (pollInputReadyResult.isSuccess()) {
                final Req message = pollInputReadyResult.getValue();
                LOG.trace("Read input message:\n{}", message);

                // In theory, we never need this, as once we reach this point of the code the server
                // call should be already ready.
                // But to safeguard us against possible changes in gRPC generated code and/or
                // different/custom stubs used by users,
                // we still synchronize here on ServerCall#request get invoked at least once.
                this.serverCallReady.thenAccept(unused -> this.restateListener.invoke(message));
              } else {
                // PollInputStream failed.
                // This is probably a cancellation.
                syscalls.writeOutput(
                    pollInputReadyResult.getFailure(),
                    SyscallCallback.ofVoid(
                        () -> {
                          LOG.trace(
                              "Closed correctly with non ok exception",
                              pollInputReadyResult.getFailure());
                          syscalls.close();
                        },
                        syscalls::fail));
              }
            },
            syscalls::fail));
  }

  @Override
  public void notifyClosed() {
    this.restateListener.close();
  }

  /**
   * Interface to invoke gRPC generated service code.
   *
   * <p>This interface is adapted from {@link ServerCall.Listener}.
   *
   * @param <M> type of the incoming message
   */
  interface RestateServerCallListener<M> {
    /** Invoke the service code. */
    void invoke(M message);

    /** Close the service code. */
    void close();

    /** Set the underlying listener as ready. */
    void ready();
  }

  /**
   * Adapts a {@link ServerCall.Listener} to a {@link RestateServerCallListener}.
   *
   * @param <ReqT> type of the request
   * @param <RespT> type of the response
   */
  static class GrpcServerCallListenerAdaptor<ReqT, RespT>
      implements RestateServerCallListener<ReqT> {

    private final Context context;
    private final ServerCall<ReqT, RespT> serverCall;
    private final ServerCall.Listener<ReqT> delegate;

    GrpcServerCallListenerAdaptor(
        Context context,
        ServerCall<ReqT, RespT> serverCall,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {
      this.context = context;
      this.serverCall = serverCall;

      // This emulates Contexts.interceptCall.
      // We need it because some code generators (such as kotlin) depends on the fact that startCall
      // already has the context available
      Context previous = this.context.attach();
      try {
        this.delegate = next.startCall(serverCall, headers);
      } finally {
        this.context.detach(previous);
      }
    }

    @Override
    public void invoke(ReqT message) {
      Context previous = context.attach();
      try {
        delegate.onMessage(message);
        delegate.onHalfClose();
      } catch (Throwable e) {
        closeWithException(e);
      } finally {
        context.detach(previous);
      }
    }

    @Override
    public void close() {
      Context previous = context.attach();
      try {
        delegate.onComplete();
      } catch (Throwable e) {
        closeWithException(e);
      } finally {
        context.detach(previous);
      }
    }

    @Override
    public void ready() {
      Context previous = context.attach();
      try {
        delegate.onReady();
      } catch (Throwable e) {
        closeWithException(e);
      } finally {
        context.detach(previous);
      }
    }

    private void closeWithException(Throwable e) {
      if (Util.containsSuspendedException(e)) {
        serverCall.close(Util.SUSPENDED_STATUS, new Metadata());
      } else {
        LOG.warn("Error when processing the invocation", e);
        serverCall.close(Status.UNKNOWN.withCause(e), new Metadata());
      }
    }
  }

  private static class ExecutorSwitchingServerCallListener<Req>
      implements RestateServerCallListener<Req> {

    private final RestateServerCallListener<Req> listener;
    private final Executor userExecutor;

    private ExecutorSwitchingServerCallListener(
        RestateServerCallListener<Req> listener, Executor userExecutor) {
      this.listener = listener;
      this.userExecutor = userExecutor;
    }

    @Override
    public void invoke(Req message) {
      userExecutor.execute(() -> listener.invoke(message));
    }

    // A bit of explanation why the following methods are not executed on the user executor.
    //
    // The listener methods ready/close are used purely for notification reasons, they don't execute
    // any user code.
    //
    // Running them in the userExecutor can also be problematic if the listener
    // mutates some thread local and runs tasks in parallel.
    // This is the case when using Vertx.executeBlocking with ordered = false and mutating the
    // Vert.x Context, which is shared among every task running in the executeBlocking thread pool
    // as thread local.

    @Override
    public void close() {
      listener.close();
    }

    @Override
    public void ready() {
      listener.ready();
    }
  }

  /**
   * Adapter for gRPC {@link ServerCall}.
   *
   * <h2>Threading model</h2>
   *
   * The threading model works as follows:
   *
   * <ul>
   *   <li>When created, this object is owned by the state machine executor.
   *   <li>When {@link Listener#onReady()} is invoked, {@link #request(int)} will be invoked (see
   *       ServerCalls class for Java and Kotlin grpc stubs).
   *   <li>Once {@link Listener#onHalfClose()} is invoked, the ownership of this object is passed to
   *       the user executor, which might call {@link #sendHeaders(Metadata)}, {@link
   *       #sendHeaders(Metadata)} and {@link #close(Status, Metadata)}.
   *   <li>Trampolining back to state machine executor is provided by the syscalls wrapper.
   * </ul>
   */
  static class RestateServerCall<Req, Res> extends ServerCall<Req, Res> {

    private final MethodDescriptor<Req, Res> methodDescriptor;
    private final SyscallsInternal syscalls;

    // This variable don't need to be volatile because it's accessed only by #request()
    private int inputPollRequests = 0;
    private final CompletableFuture<Void> serverCallReady;

    RestateServerCall(
        MethodDescriptor<Req, Res> methodDescriptor,
        SyscallsInternal syscalls,
        CompletableFuture<Void> serverCallReady) {
      this.methodDescriptor = methodDescriptor;
      this.syscalls = syscalls;
      this.serverCallReady = serverCallReady;
    }

    // --- Invoked in the State machine thread

    @Override
    public void request(int numMessages) {
      // Consider that request count is a hint, and not an exact number of elements the upstream
      // gRPC code wants.
      // For unary calls, this value is equal to 2 for java generated stubs, while Kotlin stubs will
      // perform new requests in a loop.

      // Because we don't do streaming now, this function is simple to implement, and we can assume
      // the poll input entry is always the first entry here.
      // In the streaming input case, when getting an entry we should check whether there's other
      // elements in the input stream or not, and recurse the invocations to pollInput.
      // We should also revisit the threading model, as this function is implemented assuming only
      // the state machine thread invokes it, which might not be the case for streaming?
      inputPollRequests++;

      // Unary input cases will skip pollInput after the first request
      if ((methodDescriptor.getType() == MethodDescriptor.MethodType.UNARY
              || methodDescriptor.getType() == MethodDescriptor.MethodType.SERVER_STREAMING)
          && inputPollRequests > 1) {
        return;
      }

      // Send readiness signal for server call
      this.serverCallReady.complete(null);
    }

    // --- Invoked in the user thread

    @Override
    public void sendHeaders(Metadata headers) {
      // We don't support trailers, nor headers!
    }

    @Override
    public void sendMessage(Res message) {
      ByteString output;
      try {
        output = ByteString.readFrom(methodDescriptor.streamResponse(message));
      } catch (IOException e) {
        syscalls.fail(e);
        return;
      }

      syscalls.writeOutput(
          output,
          SyscallCallback.ofVoid(
              () -> LOG.trace("Wrote output message:\n{}", message), syscalls::fail));
    }

    @Override
    public void close(Status status, Metadata trailers) {
      if (status.isOk() || Util.containsSuspendedException(status.getCause())) {
        syscalls.close();
      } else {
        if (Util.isTerminalException(status.getCause())) {
          syscalls.writeOutput(
              (TerminalException) status.getCause(),
              SyscallCallback.ofVoid(
                  () -> {
                    LOG.trace("Closed correctly with non ok exception", status.getCause());
                    syscalls.close();
                  },
                  syscalls::fail));
        } else {
          if (status.getCause() != null) {
            syscalls.fail(status.getCause());
          } else {
            // Just propagate status
            syscalls.fail(status.asRuntimeException());
          }
        }
      }
    }

    @Override
    public boolean isCancelled() {
      throw new UnsupportedOperationException();
    }

    @Override
    public MethodDescriptor<Req, Res> getMethodDescriptor() {
      return methodDescriptor;
    }
  }
}
