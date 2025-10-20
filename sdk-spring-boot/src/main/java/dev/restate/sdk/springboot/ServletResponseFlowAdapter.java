// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.common.Slice;
import dev.restate.sdk.core.ExceptionUtils;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Flow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ServletResponseFlowAdapter implements Flow.Subscriber<Slice> {

  private static final Logger LOG = LogManager.getLogger(ServletResponseFlowAdapter.class);

  private final HttpServletResponse response;
  private final AsyncContext asyncContext;

  private Flow.Subscription subscription;
  private final Queue<Slice> pendingWrites;
  private boolean completed = false;
  private ServletOutputStream outputStream;

  ServletResponseFlowAdapter(HttpServletResponse response, AsyncContext asyncContext) {
    this.response = response;
    this.asyncContext = asyncContext;
    this.pendingWrites = new ArrayDeque<>();
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;

    try {
      outputStream = response.getOutputStream();
      outputStream.setWriteListener(
          new WriteListener() {
            @Override
            public void onWritePossible() throws IOException {
              processWrites();
            }

            @Override
            public void onError(Throwable t) {
              handleWriteError(t);
            }
          });

      // Request initial data
      this.subscription.request(Long.MAX_VALUE);
    } catch (IOException e) {
      LOG.error("Error setting up write listener", e);
      endResponse();
    }
  }

  @Override
  public synchronized void onNext(Slice slice) {
    if (completed) {
      return;
    }

    pendingWrites.add(slice);

    try {
      processWrites();
    } catch (IOException e) {
      onError(e);
    }
  }

  @Override
  public synchronized void onError(Throwable throwable) {
    if (completed) {
      return;
    }

    if (!response.isCommitted()) {
      // Try to write the failure in the response
      ExceptionUtils.findProtocolException(throwable)
          .ifPresentOrElse(pe -> response.setStatus(pe.getCode()), () -> response.setStatus(500));
    }
    LOG.warn("Error from publisher", throwable);
    endResponse();
  }

  @Override
  public synchronized void onComplete() {
    if (completed) {
      return;
    }

    completed = true;

    try {
      processWrites();
      if (pendingWrites.isEmpty()) {
        endResponse();
      }
    } catch (IOException e) {
      LOG.error("Error completing response", e);
      endResponse();
    }
  }

  private synchronized void processWrites() throws IOException {
    while (outputStream.isReady() && !pendingWrites.isEmpty()) {
      Slice slice = pendingWrites.poll();
      if (slice != null) {
        byte[] bytes = new byte[slice.readableBytes()];
        slice.asReadOnlyByteBuffer().get(bytes);
        outputStream.write(bytes);
      }
    }

    if (completed && pendingWrites.isEmpty()) {
      endResponse();
    }
  }

  private synchronized void handleWriteError(Throwable t) {
    LOG.warn("Error from wire", t);
    endResponse();
  }

  private synchronized void endResponse() {
    if (completed && pendingWrites.isEmpty()) {
      LOG.trace("Completing async context");
      cancelSubscription();
      asyncContext.complete();
    } else if (!completed) {
      completed = true;
      cancelSubscription();
      asyncContext.complete();
    }
  }

  private void cancelSubscription() {
    if (this.subscription != null) {
      LOG.trace("Cancelling subscription");
      Flow.Subscription sub = this.subscription;
      this.subscription = null;
      sub.cancel();
    }
  }
}
