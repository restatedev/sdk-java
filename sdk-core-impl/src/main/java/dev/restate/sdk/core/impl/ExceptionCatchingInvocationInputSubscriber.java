package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import java.util.concurrent.Flow;

class ExceptionCatchingInvocationInputSubscriber
    implements InvocationFlow.InvocationInputSubscriber {

  InvocationFlow.InvocationInputSubscriber invocationInputSubscriber;

  public ExceptionCatchingInvocationInputSubscriber(
      InvocationFlow.InvocationInputSubscriber invocationInputSubscriber) {
    this.invocationInputSubscriber = invocationInputSubscriber;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    try {
      invocationInputSubscriber.onSubscribe(subscription);
    } catch (Throwable throwable) {
      invocationInputSubscriber.onError(throwable);
      throw throwable;
    }
  }

  @Override
  public void onNext(MessageLite messageLite) {
    try {
      invocationInputSubscriber.onNext(messageLite);
    } catch (Throwable throwable) {
      invocationInputSubscriber.onError(throwable);
      throw throwable;
    }
  }

  @Override
  public void onError(Throwable throwable) {
    invocationInputSubscriber.onError(throwable);
  }

  @Override
  public void onComplete() {
    invocationInputSubscriber.onComplete();
  }
}
