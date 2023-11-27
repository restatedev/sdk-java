// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adapts a {@link ServerCall.Listener} to a {@link RestateServerCallListener}.
 *
 * @param <ReqT> type of the request
 * @param <RespT> type of the response
 */
class GrpcServerCallListenerAdaptor<ReqT, RespT> implements RestateServerCallListener<ReqT> {

  private static final Logger LOG = LogManager.getLogger(GrpcServerCallListenerAdaptor.class);

  private final ServerCall<ReqT, RespT> serverCall;

  private final ServerCall.Listener<ReqT> delegate;

  GrpcServerCallListenerAdaptor(
      ServerCall.Listener<ReqT> delegate, ServerCall<ReqT, RespT> serverCall) {
    this.delegate = delegate;
    this.serverCall = serverCall;
  }

  @Override
  public void onMessageAndHalfClose(ReqT message) {
    try {
      delegate.onMessage(message);
      delegate.onHalfClose();
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  @Override
  public void onCancel() {
    try {
      delegate.onCancel();
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  @Override
  public void onComplete() {
    try {
      delegate.onComplete();
    } catch (Throwable e) {
      closeWithException(e);
    }
  }

  @Override
  public void onReady() {
    try {
      delegate.onReady();
    } catch (Throwable e) {
      closeWithException(e);
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
