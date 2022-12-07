package dev.restate.sdk.core.impl;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ExceptionCatchingServerCallListener<ReqT, RespT>
    extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

  private static final Logger LOG = LogManager.getLogger(ExceptionCatchingServerCallListener.class);

  private final ServerCall<ReqT, RespT> serverCall;

  ExceptionCatchingServerCallListener(
      ServerCall.Listener<ReqT> delegate, ServerCall<ReqT, RespT> serverCall) {
    super(delegate);
    this.serverCall = serverCall;
  }

  @Override
  public void onMessage(ReqT message) {
    try {
      super.onMessage(message);
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  @Override
  public void onHalfClose() {
    try {
      super.onHalfClose();
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  @Override
  public void onCancel() {
    try {
      super.onCancel();
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  @Override
  public void onComplete() {
    try {
      super.onComplete();
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  @Override
  public void onReady() {
    try {
      super.onReady();
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  private void closeWithException(Throwable e) {
    if (Util.containsSuspendedException(e)) {
      serverCall.close(Util.SUSPENDED_STATUS, new Metadata());
    } else {
      LOG.warn("Error when processing the invocation", e);
      serverCall.close(Util.toGrpcStatusErasingCause(e), new Metadata());
    }
  }
}
