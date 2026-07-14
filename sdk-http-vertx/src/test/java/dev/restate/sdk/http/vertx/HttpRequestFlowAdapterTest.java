// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.common.Slice;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

/**
 * Tests for the read-side demand signaling of {@link HttpRequestFlowAdapter} (companion fix of
 * issue #614): the request stream is paused on construction and buffers are pulled via {@code
 * fetch} as the subscriber signals demand, so the internal queue stays bounded.
 */
class HttpRequestFlowAdapterTest {

  @Test
  void shouldPauseTheRequestStreamOnConstruction() {
    FakeHttpServerRequest request = new FakeHttpServerRequest();

    new HttpRequestFlowAdapter(request.proxy());

    assertThat(request.paused).isTrue();
  }

  @Test
  void shouldMirrorSubscriberDemandIntoFetch() {
    FakeHttpServerRequest request = new FakeHttpServerRequest();
    HttpRequestFlowAdapter adapter = new HttpRequestFlowAdapter(request.proxy());
    RecordingSubscriber subscriber = new RecordingSubscriber();

    adapter.subscribe(subscriber);
    subscriber.subscription.request(5);

    assertThat(request.fetches).containsExactly(5L);
  }

  @Test
  void shouldDeliverFetchedBuffersToTheSubscriber() {
    FakeHttpServerRequest request = new FakeHttpServerRequest();
    HttpRequestFlowAdapter adapter = new HttpRequestFlowAdapter(request.proxy());
    RecordingSubscriber subscriber = new RecordingSubscriber();

    adapter.subscribe(subscriber);
    subscriber.subscription.request(2);
    request.bufferHandler.handle(Buffer.buffer("hello"));
    request.bufferHandler.handle(Buffer.buffer("world"));

    assertThat(subscriber.received).hasSize(2);
    assertThat(request.fetches).containsExactly(2L);
  }

  private static final class RecordingSubscriber implements Flow.Subscriber<Slice> {
    private Flow.Subscription subscription;
    private final List<Slice> received = new ArrayList<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
    }

    @Override
    public void onNext(Slice slice) {
      received.add(slice);
    }

    @Override
    public void onError(Throwable throwable) {}

    @Override
    public void onComplete() {}
  }

  /**
   * Hand-rolled {@link HttpServerRequest} fake backed by a dynamic proxy, covering only the methods
   * the adapter touches. Avoids pulling a mocking library into the project for one test.
   */
  private static final class FakeHttpServerRequest implements InvocationHandler {
    private boolean paused = false;
    private boolean ended = false;
    private final List<Long> fetches = new ArrayList<>();
    private Handler<Buffer> bufferHandler;

    private HttpServerRequest proxy() {
      return (HttpServerRequest)
          Proxy.newProxyInstance(
              HttpServerRequest.class.getClassLoader(),
              new Class<?>[] {HttpServerRequest.class},
              this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "pause" -> {
          this.paused = true;
          yield proxy;
        }
        case "fetch" -> {
          this.fetches.add((Long) args[0]);
          yield proxy;
        }
        case "handler" -> {
          this.bufferHandler = (Handler<Buffer>) args[0];
          yield proxy;
        }
        case "exceptionHandler", "endHandler" -> proxy;
        case "isEnded" -> this.ended;
        case "toString" -> "FakeHttpServerRequest";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default ->
            throw new UnsupportedOperationException(
                "Unexpected call on the request fake: " + method.getName());
      };
    }
  }
}
