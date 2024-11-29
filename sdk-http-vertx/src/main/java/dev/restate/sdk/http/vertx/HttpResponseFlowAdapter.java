// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.sdk.core.InvocationFlow;
import dev.restate.sdk.core.Util;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class HttpResponseFlowAdapter implements InvocationFlow.InvocationOutputSubscriber {

  private static final Logger LOG = LogManager.getLogger(HttpResponseFlowAdapter.class);

  private final HttpServerResponse httpServerResponse;

  private Flow.Subscription outputSubscription;

  HttpResponseFlowAdapter(HttpServerResponse httpServerResponse) {
    this.httpServerResponse = httpServerResponse;

    this.httpServerResponse.exceptionHandler(this::propagateWireFailure);
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.outputSubscription = subscription;
    this.outputSubscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(ByteBuffer byteBuffer) {
    if (this.httpServerResponse.ended()) {
      cancelSubscription();
      return;
    }

    // If HTTP HEADERS frame have not been sent, Vert.x will send them
    this.httpServerResponse.write(Buffer.buffer(Unpooled.wrappedBuffer(byteBuffer)));
  }

  @Override
  public void onError(Throwable throwable) {
    propagatePublisherFailure(throwable);
  }

  @Override
  public void onComplete() {
    endResponse();
  }

  // --- Private operations

  private void propagateWireFailure(Throwable e) {
    LOG.warn("Error from wire", e);
    this.endResponse();
  }

  private void propagatePublisherFailure(Throwable e) {
    if (!httpServerResponse.headWritten()) {
      // Try to write the failure in the head
      Util.findProtocolException(e)
          .ifPresentOrElse(
              pe -> httpServerResponse.setStatusCode(pe.getCode()),
              () -> httpServerResponse.setStatusCode(500));
    }
    LOG.warn("Error from publisher", e);
    this.endResponse();
  }

  private void endResponse() {
    LOG.trace("Closing response");
    if (!this.httpServerResponse.ended()) {
      this.httpServerResponse.end();
    }
    cancelSubscription();
  }

  private void cancelSubscription() {
    if (this.outputSubscription != null) {
      LOG.trace("Cancelling subscription");
      Flow.Subscription outputSubscription = this.outputSubscription;
      this.outputSubscription = null;
      outputSubscription.cancel();
    }
  }
}
