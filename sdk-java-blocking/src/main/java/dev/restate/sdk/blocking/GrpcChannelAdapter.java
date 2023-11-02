package dev.restate.sdk.blocking;

import io.grpc.*;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Channel adapter for gRPC Blocking stubs.
 *
 * <p>Keep in mind that this channel should be used only with generated blocking stubs.
 */
public class GrpcChannelAdapter extends Channel {
  private final RestateContext restateContext;

  GrpcChannelAdapter(RestateContext restateContext) {
    this.restateContext = restateContext;
  }

  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    return new ClientCall<>() {
      private Listener<ResponseT> responseListener = null;
      private Awaitable<ResponseT> awaitable = null;

      @Override
      public void start(Listener<ResponseT> responseListener, Metadata headers) {
        this.responseListener = responseListener;
      }

      @Override
      public void request(int numMessages) {}

      @Override
      public void cancel(@Nullable String message, @Nullable Throwable cause) {}

      @Override
      public void halfClose() {
        var listener = Objects.requireNonNull(responseListener);
        listener.onHeaders(new Metadata()); // We pass no headers
        try {
          listener.onMessage(Objects.requireNonNull(this.awaitable).await());
          listener.onClose(Status.OK, new Metadata());
        } catch (StatusRuntimeException e) {
          listener.onClose(e.getStatus(), new Metadata());
        }
      }

      @Override
      public void sendMessage(RequestT message) {
        this.awaitable = restateContext.call(methodDescriptor, message);
      }
    };
  }

  @Override
  public String authority() {
    return "restate";
  }
}
