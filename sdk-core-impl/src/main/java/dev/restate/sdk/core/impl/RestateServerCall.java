package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class RestateServerCall extends ServerCall<MessageLite, MessageLite> {

  private static final Logger LOG = LogManager.getLogger(RestateServerCall.class);

  private final MethodDescriptor<MessageLite, MessageLite> methodDescriptor;
  private final SyscallsInternal syscalls;

  private ServerCall.Listener<MessageLite> listener;
  private int requestCount;
  private int inputPollRequests;

  RestateServerCall(
      MethodDescriptor<MessageLite, MessageLite> methodDescriptor, SyscallsInternal syscalls) {
    this.methodDescriptor = methodDescriptor;
    this.syscalls = syscalls;

    this.requestCount = 0;
  }

  void setListener(Listener<MessageLite> listener) {
    this.listener = listener;
    this.listener.onReady();

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

  @Override
  public void sendHeaders(Metadata headers) {
    // We don't support trailers, nor headers!
  }

  @Override
  public void sendMessage(MessageLite message) {
    syscalls.writeOutput(
        message, () -> LOG.trace("Wrote output message:\n{}", message), this::onError);
  }

  @Override
  public void close(Status status, Metadata trailers) {
    if (status.isOk() || Util.containsSuspendedException(status.getCause())) {
      listener.onComplete();
      syscalls.close();
    } else {
      // Let's cancel the listener first
      listener.onCancel();

      Optional<Throwable> protocolException =
          Util.findCause(status.getCause(), t -> t instanceof ProtocolException);
      if (protocolException.isPresent()) {
        // If it's a protocol exception, we propagate the failure to syscalls, which will propagate
        // it to the network layer
        syscalls.fail((ProtocolException) protocolException.get());
      } else {
        // If not a protocol exception, then it's an exception coming from user which we write on
        // the journal
        syscalls.writeOutput(
            status.asRuntimeException(),
            () -> {
              LOG.trace("Closed correctly with non ok status {}", status);
              syscalls.close();
            },
            this::onError);
      }
    }
  }

  @Override
  public boolean isCancelled() {
    return syscalls.getStateMachine().isClosed();
  }

  @Override
  public MethodDescriptor<MessageLite, MessageLite> getMethodDescriptor() {
    return methodDescriptor;
  }

  private void pollInput() {
    // Because we don't do streaming ATM, this is simple to implement, and
    // we can assume the poll input entry is always the first entry here
    // In the streaming case, when getting an entry we should check whether there's other elements
    // in the input stream or not. If that's the case, we should recurse the invocations to
    // pollInput.
    // Consider that request count is a hint, and not an exact number of elements the upstream gRPC
    // code wants.
    // For unary calls, this value is equal to 2 for java generated stubs.
    // Kotlin stubs will perform new requests in a loop.

    requestCount--;
    inputPollRequests++;
    if ((methodDescriptor.getType() == MethodDescriptor.MethodType.UNARY
            || methodDescriptor.getType() == MethodDescriptor.MethodType.SERVER_STREAMING)
        && inputPollRequests > 1) {
      // Unary input cases will skip pollInput after the first request
      return;
    }

    // read, then in callback invoke listener with unary call
    syscalls.pollInput(
        b -> methodDescriptor.parseRequest(b.newInput()),
        deferredValue ->
            syscalls.resolveDeferred(
                deferredValue,
                result -> {
                  Objects.requireNonNull(listener);

                  LOG.trace("Read input message:\n{}", result.getResult());
                  listener.onMessage(result.getResult());
                  listener.onHalfClose();
                },
                this::onError),
        this::onError);
  }

  private void onError(Throwable cause) {
    LOG.warn("Error in RestateServerCall", cause);
  }
}
