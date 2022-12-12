package dev.restate.sdk.core.impl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.core.syscalls.DeferredResultCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReadyResultPublisherTest {

  @Test
  public void transitionOrderlyThroughTheDifferentStates() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    List<Integer> seenOrder = new ArrayList<>();
    List<Integer> publishedOrder = new ArrayList<>();
    ReadyResultPublisher readyResultPublisher = new ReadyResultPublisher(publishedOrder::add);
    DeferredResultCallback<Integer> seenOrderPushCallback =
        DeferredResultCallback.ofNonEmpty(seenOrder::add, Assertions::fail, Assertions::fail);
    DeferredResultCallback<?> finalCallback =
        DeferredResultCallback.ofNonEmpty(
            i -> Assertions.fail("Unexpected item: " + i),
            Assertions::fail,
            t -> latch.countDown());

    readyResultPublisher.subscribe(0, seenOrderPushCallback);
    readyResultPublisher.subscribe(1, seenOrderPushCallback);
    readyResultPublisher.offerResult(0, ReadyResult.success(0));
    readyResultPublisher.offerResult(1, ReadyResult.success(1));
    readyResultPublisher.offerOrder(2);
    readyResultPublisher.subscribe(2, seenOrderPushCallback);
    readyResultPublisher.subscribe(3, seenOrderPushCallback);
    readyResultPublisher.offerOrder(1);
    readyResultPublisher.offerResult(2, ReadyResult.success(2));
    readyResultPublisher.onEndReplay();
    readyResultPublisher.offerResult(3, ReadyResult.success(3));
    readyResultPublisher.subscribe(4, finalCallback);
    readyResultPublisher.onInputChannelClosed(null);

    latch.await();

    assertThat(seenOrder).containsExactly(2, 1, 0, 3);
    assertThat(publishedOrder).containsExactly(0, 3);
  }

  @Test
  public void jumpFromReplayingToSuspended() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    List<Integer> seenOrder = new ArrayList<>();
    List<Integer> publishedOrder = new ArrayList<>();
    ReadyResultPublisher readyResultPublisher = new ReadyResultPublisher(publishedOrder::add);
    DeferredResultCallback<Integer> seenOrderPushCallback =
        DeferredResultCallback.ofNonEmpty(seenOrder::add, Assertions::fail, Assertions::fail);
    DeferredResultCallback<?> finalCallback =
        DeferredResultCallback.ofNonEmpty(
            i -> Assertions.fail("Unexpected item: " + i),
            Assertions::fail,
            t -> latch.countDown());

    readyResultPublisher.subscribe(0, seenOrderPushCallback);
    readyResultPublisher.subscribe(1, seenOrderPushCallback);
    readyResultPublisher.offerResult(0, ReadyResult.success(0));
    readyResultPublisher.offerResult(1, ReadyResult.success(1));
    readyResultPublisher.offerOrder(2);
    readyResultPublisher.subscribe(2, seenOrderPushCallback);
    readyResultPublisher.subscribe(3, finalCallback);
    readyResultPublisher.offerOrder(1);
    readyResultPublisher.offerResult(2, ReadyResult.success(2));
    readyResultPublisher.onEndReplay();
    readyResultPublisher.onInputChannelClosed(null);

    latch.await();

    assertThat(seenOrder).containsExactly(2, 1, 0);
    assertThat(publishedOrder).containsExactly(0);
  }

  @Test
  public void closedShouldStillPublishResults() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    List<Integer> seenOrder = new ArrayList<>();
    List<Integer> publishedOrder = new ArrayList<>();
    ReadyResultPublisher readyResultPublisher = new ReadyResultPublisher(publishedOrder::add);
    DeferredResultCallback<Integer> seenOrderPushCallback =
        DeferredResultCallback.ofNonEmpty(seenOrder::add, Assertions::fail, Assertions::fail);
    DeferredResultCallback<?> finalCallback =
        DeferredResultCallback.ofNonEmpty(
            i -> Assertions.fail("Unexpected item: " + i),
            Assertions::fail,
            t -> latch.countDown());

    readyResultPublisher.onEndReplay();
    readyResultPublisher.onInputChannelClosed(null);
    readyResultPublisher.offerResult(0, ReadyResult.success(0));
    readyResultPublisher.offerResult(1, ReadyResult.success(1));
    readyResultPublisher.subscribe(0, seenOrderPushCallback);
    readyResultPublisher.subscribe(1, seenOrderPushCallback);
    readyResultPublisher.subscribe(2, finalCallback);

    latch.await();

    assertThat(seenOrder).containsExactly(0, 1);
    assertThat(publishedOrder).containsExactly(0, 1);
  }

  @Test
  public void closeInputThenFinishReplay() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    List<Integer> seenOrder = new ArrayList<>();
    List<Integer> publishedOrder = new ArrayList<>();
    ReadyResultPublisher readyResultPublisher = new ReadyResultPublisher(publishedOrder::add);
    DeferredResultCallback<Integer> seenOrderPushCallback =
        DeferredResultCallback.ofNonEmpty(seenOrder::add, Assertions::fail, Assertions::fail);
    DeferredResultCallback<?> finalCallback =
        DeferredResultCallback.ofNonEmpty(
            i -> Assertions.fail("Unexpected item: " + i),
            Assertions::fail,
            t -> latch.countDown());

    readyResultPublisher.onInputChannelClosed(null);
    readyResultPublisher.offerResult(0, ReadyResult.success(0));
    readyResultPublisher.subscribe(0, seenOrderPushCallback);
    readyResultPublisher.offerResult(1, ReadyResult.success(1));
    readyResultPublisher.subscribe(1, seenOrderPushCallback);
    readyResultPublisher.offerOrder(0);
    readyResultPublisher.onEndReplay();
    readyResultPublisher.subscribe(2, finalCallback);

    latch.await();

    assertThat(seenOrder).containsExactly(0, 1);
    assertThat(publishedOrder).containsExactly(1);
  }
}
