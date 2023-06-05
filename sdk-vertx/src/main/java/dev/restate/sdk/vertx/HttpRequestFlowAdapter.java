package dev.restate.sdk.vertx;

import dev.restate.sdk.core.impl.InvocationFlow;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import java.util.concurrent.Flow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class HttpRequestFlowAdapter implements InvocationFlow.InvocationInputPublisher {

  private static final Logger LOG = LogManager.getLogger(HttpRequestFlowAdapter.class);

  private final HttpServerRequest httpServerRequest;
  private final MessageDecoder decoder;

  private Flow.Subscriber<? super InvocationFlow.InvocationInput> inputMessagesSubscriber;
  private long subscriberRequest = 0;

  HttpRequestFlowAdapter(HttpServerRequest httpServerRequest) {
    this.httpServerRequest = httpServerRequest;

    this.decoder = new MessageDecoder();
  }

  @Override
  public void subscribe(Flow.Subscriber<? super InvocationFlow.InvocationInput> subscriber) {
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
    this.decoder.offer(buffer);

    tryProgress();
  }

  private void handleRequestFailure(Throwable e) {
    this.inputMessagesSubscriber.onError(e);
  }

  private void handleRequestEnd(Void v) {
    LOG.trace("Request end");
    this.inputMessagesSubscriber.onComplete();
    this.inputMessagesSubscriber = null;
  }

  private void tryProgress() {
    while (this.subscriberRequest > 0) {
      InvocationFlow.InvocationInput input;
      try {
        input = this.decoder.poll();
      } catch (RuntimeException e) {
        inputMessagesSubscriber.onError(e);
        return;
      }
      if (input == null) {
        return;
      }
      LOG.trace("Received input " + input);
      this.subscriberRequest--;
      inputMessagesSubscriber.onNext(input);
    }
  }
}
