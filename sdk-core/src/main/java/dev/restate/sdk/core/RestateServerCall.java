// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.ReadyResult;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class RestateServerCall extends ServerCall<MessageLite, MessageLite> {

  private static final Logger LOG = LogManager.getLogger(RestateServerCall.class);

  private final MethodDescriptor<MessageLite, MessageLite> methodDescriptor;
  private final SyscallsInternal syscalls;

  // The RestateServerCall threading model works as follows:
  // * Before the first polling, this object is owned by the state machine executor. During this
  // timeframe, only #request() and #setListener() can be invoked (the former invoked by the grpc
  // generated code)
  // * After the first polling happens, the listener is invoked. listener.halfClose() will start the
  // user code.
  // * From this point onward, the ownership of this object is passed to the user executor, which
  // might call #request(), #sendHeaders(), #sendMessage() and #close().
  // * Trampolining back to state machine executor is now provided by the syscalls wrapper.
  //
  // The listener reference is volatile in order to guarantee its visibility when the ownership of
  // this object is transferred through threads.
  private volatile RestateServerCallListener<MessageLite> listener;

  // These variables don't need to be volatile as they're accessed and mutated only by
  // #setListener() and #request()
  private int requestCount = 0;
  private int inputPollRequests = 0;

  RestateServerCall(
      MethodDescriptor<MessageLite, MessageLite> methodDescriptor, SyscallsInternal syscalls) {
    this.methodDescriptor = methodDescriptor;
    this.syscalls = syscalls;
  }

  // --- Invoked in the State machine thread

  void setListener(RestateServerCallListener<MessageLite> listener) {
    this.listener = listener;
    this.listener.listenerReady();

    if (requestCount > 0) {
      this.pollInput();
    }
  }

  @Override
  public void request(int numMessages) {
    requestCount += numMessages;

    // Don't start polling input until we have a listener
    if (listener != null) {
      this.pollInput();
    }
  }

  // --- Invoked in the user thread

  @Override
  public void sendHeaders(Metadata headers) {
    // We don't support trailers, nor headers!
  }

  @Override
  public void sendMessage(MessageLite message) {
    syscalls.writeOutput(
        message,
        SyscallCallback.ofVoid(
            () -> LOG.trace("Wrote output message:\n{}", message), this::onError));
  }

  @Override
  public void close(Status status, Metadata trailers) {
    // Let's close the listener first
    listener.close();

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
                this::onError));
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
  public MethodDescriptor<MessageLite, MessageLite> getMethodDescriptor() {
    return methodDescriptor;
  }

  private void pollInput() {
    // Because we don't do streaming now, this function is simple to implement, and
    // we can assume the poll input entry is always the first entry here.
    // In the streaming input case, when getting an entry we should check whether there's other
    // elements
    // in the input stream or not, and recurse the invocations to pollInput.
    // We should also revisit the threading model, as this function is implemented assuming only the
    // state machine
    // thread invokes it, which might not be the case for streaming?

    // Consider that request count is a hint, and not an exact number of elements the upstream gRPC
    // code wants.
    // For unary calls, this value is equal to 2 for java generated stubs.
    // Kotlin stubs will perform new requests in a loop.
    requestCount--;
    inputPollRequests++;

    // Unary input cases will skip pollInput after the first request
    if ((methodDescriptor.getType() == MethodDescriptor.MethodType.UNARY
            || methodDescriptor.getType() == MethodDescriptor.MethodType.SERVER_STREAMING)
        && inputPollRequests > 1) {
      return;
    }

    // read, then in callback invoke listener with unary call
    syscalls.pollInput(
        b -> methodDescriptor.parseRequest(b.newInput()),
        SyscallCallback.of(
            deferredValue ->
                syscalls.resolveDeferred(
                    deferredValue,
                    SyscallCallback.ofVoid(
                        () -> {
                          Objects.requireNonNull(listener);

                          final ReadyResult<MessageLite> pollInputReadyResult =
                              deferredValue.toReadyResult();

                          if (pollInputReadyResult.isSuccess()) {
                            final MessageLite message = pollInputReadyResult.getResult();
                            LOG.trace("Read input message:\n{}", message);
                            listener.invoke(message);
                          } else {
                            final TerminalException failure = pollInputReadyResult.getFailure();
                            this.close(
                                Status.UNKNOWN
                                    .withDescription(failure.getMessage())
                                    .withCause(failure),
                                new Metadata());
                          }
                        },
                        this::onError)),
            this::onError));
  }

  private void onError(Throwable cause) {
    LOG.warn("Error in RestateServerCall", cause);
  }
}
