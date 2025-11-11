// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.common.Slice;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Flow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ServletRequestFlowAdapter implements Flow.Publisher<Slice> {

  private static final Logger LOG = LogManager.getLogger(ServletRequestFlowAdapter.class);
  private static final int BUFFER_SIZE = 8192;

  private final HttpServletRequest request;

  private Flow.Subscriber<? super Slice> subscriber;
  private long subscriberDemand = 0;
  private final Queue<ByteBuffer> buffers;
  private boolean completed = false;

  ServletRequestFlowAdapter(HttpServletRequest request) {
    this.request = request;
    this.buffers = new ArrayDeque<>();
  }

  @Override
  public void subscribe(Flow.Subscriber<? super Slice> subscriber) {
    this.subscriber = subscriber;
    this.subscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long n) {
            handleSubscriptionRequest(n);
          }

          @Override
          public void cancel() {
            // No-op for request
          }
        });

    // Set up async read listener
    try {
      ServletInputStream inputStream = request.getInputStream();
      inputStream.setReadListener(
          new ReadListener() {
            @Override
            public void onDataAvailable() throws IOException {
              readAvailableData(inputStream);
            }

            @Override
            public void onAllDataRead() {
              handleRequestComplete();
            }

            @Override
            public void onError(Throwable t) {
              handleRequestError(t);
            }
          });
    } catch (IOException e) {
      subscriber.onError(e);
    }
  }

  private synchronized void handleSubscriptionRequest(long n) {
    if (n == Long.MAX_VALUE) {
      this.subscriberDemand = n;
    } else {
      this.subscriberDemand += n;
      // Overflow check
      if (this.subscriberDemand < 0) {
        this.subscriberDemand = Long.MAX_VALUE;
      }
    }
    tryProgress();
  }

  private synchronized void readAvailableData(ServletInputStream inputStream) throws IOException {
    while (inputStream.isReady()) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead = inputStream.read(buffer);

      if (bytesRead == -1) {
        break;
      }

      if (bytesRead > 0) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
        buffers.add(byteBuffer);
        tryProgress();
      }
    }
  }

  private synchronized void handleRequestComplete() {
    LOG.trace("Request complete");
    completed = true;
    if (buffers.isEmpty() && subscriber != null) {
      subscriber.onComplete();
      subscriber = null;
    }
  }

  private synchronized void handleRequestError(Throwable t) {
    LOG.trace("Request error", t);
    if (subscriber != null) {
      subscriber.onError(t);
      subscriber = null;
    }
  }

  private synchronized void tryProgress() {
    if (subscriber == null) {
      return;
    }

    while (subscriberDemand > 0 && !buffers.isEmpty()) {
      ByteBuffer buffer = buffers.poll();
      if (buffer != null) {
        subscriberDemand--;
        subscriber.onNext(Slice.wrap(buffer));
      }
    }

    if (completed && buffers.isEmpty()) {
      subscriber.onComplete();
      subscriber = null;
    }
  }
}
