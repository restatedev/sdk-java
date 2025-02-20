// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Slice;
import java.util.concurrent.Flow;

class StaticResponseRequestProcessor implements RequestProcessor {

  private final int statusCode;
  private final String responseContentType;
  private final Slice responseBody;

  StaticResponseRequestProcessor(int statusCode, String responseContentType, Slice responseBody) {
    this.statusCode = statusCode;
    this.responseContentType = responseContentType;
    this.responseBody = responseBody;
  }

  @Override
  public int statusCode() {
    return this.statusCode;
  }

  @Override
  public String responseContentType() {
    return this.responseContentType;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super Slice> subscriber) {
    subscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long l) {
            if (l <= 0) {
              subscriber.onError(
                  new IllegalStateException("subscription request is negative: " + l));
              return;
            }
            subscriber.onNext(responseBody);
            subscriber.onComplete();
          }

          @Override
          public void cancel() {}
        });
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {}

  @Override
  public void onNext(Slice slice) {}

  @Override
  public void onError(Throwable throwable) {}

  @Override
  public void onComplete() {}
}
