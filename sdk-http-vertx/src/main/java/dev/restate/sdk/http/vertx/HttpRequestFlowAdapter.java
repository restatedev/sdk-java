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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Flow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class HttpRequestFlowAdapter implements InvocationFlow.InvocationInputPublisher {

  private static final Logger LOG = LogManager.getLogger(HttpRequestFlowAdapter.class);

  private final HttpServerRequest httpServerRequest;

  private Flow.Subscriber<? super ByteBuffer> inputMessagesSubscriber;
  private long subscriberRequest = 0;
  private final Queue<ByteBuffer> buffers;

  HttpRequestFlowAdapter(HttpServerRequest httpServerRequest) {
    this.httpServerRequest = httpServerRequest;
    this.buffers = new ArrayDeque<>();
  }

  @Override
  public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
    this.inputMessagesSubscriber = subscriber;
    this.inputMessagesSubscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long l) {
            handleSubscriptionRequest(l);
          }

          @Override
          public void cancel() {
            closeRequest();
          }
        });

    // Set request handlers
    this.httpServerRequest.handler(this::handleIncomingBuffer);
    this.httpServerRequest.exceptionHandler(this::handleRequestFailure);
    this.httpServerRequest.endHandler(this::handleRequestEnd);
  }

  private void closeRequest() {
    if (!this.httpServerRequest.isEnded()) {
      this.httpServerRequest.end();
    }
  }

  private void handleSubscriptionRequest(long l) {
    if (l == Long.MAX_VALUE) {
      this.subscriberRequest = l;
    } else {
      this.subscriberRequest += l;
      // Overflow check
      if (this.subscriberRequest < 0) {
        this.subscriberRequest = Long.MAX_VALUE;
      }
    }

    tryProgress();
  }

  private void handleIncomingBuffer(Buffer buffer) {
    // Fast path
    if (this.buffers.isEmpty() && this.subscriberRequest > 0) {
      this.inputMessagesSubscriber.onNext(buffer.getByteBuf().nioBuffer());
      this.subscriberRequest--;
      return;
    }

    this.buffers.add(buffer.getByteBuf().nioBuffer());
    tryProgress();
  }

  private void handleRequestFailure(Throwable e) {
    LOG.trace("Request error", e);
    this.inputMessagesSubscriber.onError(e);
  }

  private void handleRequestEnd(Void v) {
    LOG.trace("Request end");
    this.inputMessagesSubscriber.onComplete();
    this.inputMessagesSubscriber = null;
  }

  private void tryProgress() {
    while (this.subscriberRequest > 0) {
      ByteBuffer input = this.buffers.poll();
      if (input == null) {
        return;
      }
      this.subscriberRequest--;
      inputMessagesSubscriber.onNext(input);
    }
  }
}
