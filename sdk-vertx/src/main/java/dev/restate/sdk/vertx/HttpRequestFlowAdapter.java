package dev.restate.sdk.vertx;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.impl.InvocationFlow;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import java.util.concurrent.Flow;

class HttpRequestFlowAdapter implements InvocationFlow.InvocationInputPublisher {

  private final HttpServerRequest httpServerRequest;
  private final MessageDecoder decoder;

  private Flow.Subscriber<? super MessageLite> inputMessagesSubscriber;

  HttpRequestFlowAdapter(HttpServerRequest httpServerRequest) {
    this.httpServerRequest = httpServerRequest;

    this.decoder = new MessageDecoder();
  }

  @Override
  public void subscribe(Flow.Subscriber<? super MessageLite> subscriber) {
    this.inputMessagesSubscriber = subscriber;
    this.inputMessagesSubscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long l) {}

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

  private void handleIncomingBuffer(Buffer buffer) {
    this.decoder.offer(buffer);

    while (true) {
      MessageLite entry;
      try {
        entry = this.decoder.poll();
      } catch (RuntimeException e) {
        inputMessagesSubscriber.onError(e);
        return;
      }
      if (entry == null) {
        return;
      }
      inputMessagesSubscriber.onNext(entry);
    }
  }

  private void handleRequestFailure(Throwable e) {
    this.inputMessagesSubscriber.onError(e);
  }

  private void handleRequestEnd(Void v) {
    this.inputMessagesSubscriber.onComplete();
    this.inputMessagesSubscriber = null;
  }
}
