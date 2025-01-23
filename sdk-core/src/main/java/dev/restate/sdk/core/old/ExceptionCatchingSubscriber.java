// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.old;

import java.util.concurrent.Flow;

class ExceptionCatchingSubscriber<T> implements Flow.Subscriber<T> {

  final Flow.Subscriber<T> invocationInputSubscriber;

  public ExceptionCatchingSubscriber(Flow.Subscriber<T> invocationInputSubscriber) {
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
  public void onNext(T t) {
    try {
      invocationInputSubscriber.onNext(t);
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
