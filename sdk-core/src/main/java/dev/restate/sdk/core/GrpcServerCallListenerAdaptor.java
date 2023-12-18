// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import io.grpc.*;
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
    // We need it because some code generator depends on the fact that startCall already has the
    // context available
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
  public void cancel() {
    Context previous = context.attach();
    try {
      delegate.onCancel();
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
  public void listenerReady() {
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
