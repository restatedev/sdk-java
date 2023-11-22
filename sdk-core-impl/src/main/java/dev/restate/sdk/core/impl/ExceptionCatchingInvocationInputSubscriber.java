// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

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
  public void onNext(InvocationFlow.InvocationInput invocationInput) {
    try {
      invocationInputSubscriber.onNext(invocationInput);
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
