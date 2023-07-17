package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.syscalls.SyscallCallback;
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
    if (status.isOk() || Util.containsSuspendedException(status.getCause())) {
      listener.onComplete();
      syscalls.close();
    } else {
      // Let's cancel the listener first
      listener.onCancel();

      if (status.getCode() == Status.Code.UNKNOWN) {
        syscalls.fail(status.getCause());
      } else {
        // If not a protocol exception, then it's an exception coming from user which we write on
        // the journal
        syscalls.writeOutput(
            status.asRuntimeException(),
            SyscallCallback.ofVoid(
                () -> {
                  LOG.trace("Closed correctly with non ok status {}", status);
                  syscalls.close();
                },
                this::onError));
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

                          // PollInput can only be result
                          MessageLite message = deferredValue.toReadyResult().getResult();

                          LOG.trace("Read input message:\n{}", message);
                          listener.onMessageAndHalfClose(message);
                        },
                        this::onError)),
            this::onError));
  }

  private void onError(Throwable cause) {
    LOG.warn("Error in RestateServerCall", cause);
  }
}
