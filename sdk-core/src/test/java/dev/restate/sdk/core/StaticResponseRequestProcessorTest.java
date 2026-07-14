// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.common.Slice;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

/**
 * The static response (discovery, health) must be emitted exactly once, no matter how the
 * subscriber shapes its demand (Reactive Streams rules 1.7/2.12). Before this test, every {@code
 * request()} call re-emitted the whole body and re-signalled completion, which duplicated the
 * discovery response as soon as a subscriber used bounded per-item requests instead of a single
 * {@code request(Long.MAX_VALUE)}.
 */
class StaticResponseRequestProcessorTest {

  @Test
  void shouldEmitTheResponseBodyExactlyOnceForBoundedDemand() {
    StaticResponseRequestProcessor processor =
        new StaticResponseRequestProcessor(200, "application/json", Slice.wrap("{\"a\":1}"));
    RecordingSubscriber subscriber = new RecordingSubscriber();

    processor.subscribe(subscriber);
    subscriber.subscription.request(1);
    // A well-behaved publisher must ignore further demand after completion
    subscriber.subscription.request(1);
    subscriber.subscription.request(Long.MAX_VALUE);

    assertThat(subscriber.received).hasSize(1);
    assertThat(subscriber.completions).isEqualTo(1);
    assertThat(subscriber.errors).isEmpty();
  }

  @Test
  void shouldEmitTheResponseBodyExactlyOnceForUnboundedDemand() {
    StaticResponseRequestProcessor processor =
        new StaticResponseRequestProcessor(200, "application/json", Slice.wrap("{\"a\":1}"));
    RecordingSubscriber subscriber = new RecordingSubscriber();

    processor.subscribe(subscriber);
    subscriber.subscription.request(Long.MAX_VALUE);

    assertThat(subscriber.received).hasSize(1);
    assertThat(subscriber.completions).isEqualTo(1);
    assertThat(subscriber.errors).isEmpty();
  }

  @Test
  void shouldSignalAnErrorForNonPositiveDemand() {
    StaticResponseRequestProcessor processor =
        new StaticResponseRequestProcessor(200, "application/json", Slice.wrap("{\"a\":1}"));
    RecordingSubscriber subscriber = new RecordingSubscriber();

    processor.subscribe(subscriber);
    subscriber.subscription.request(0);

    assertThat(subscriber.received).isEmpty();
    assertThat(subscriber.errors).hasSize(1);
  }

  private static final class RecordingSubscriber implements Flow.Subscriber<Slice> {
    private Flow.Subscription subscription;
    private final List<Slice> received = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();
    private int completions = 0;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
    }

    @Override
    public void onNext(Slice slice) {
      received.add(slice);
    }

    @Override
    public void onError(Throwable throwable) {
      errors.add(throwable);
    }

    @Override
    public void onComplete() {
      completions++;
    }
  }
}
