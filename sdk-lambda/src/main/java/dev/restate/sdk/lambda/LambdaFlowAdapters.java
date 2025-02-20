// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import dev.restate.common.Slice;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

class LambdaFlowAdapters {

  static class ResultSubscriber implements Flow.Subscriber<Slice> {

    private final CompletableFuture<Void> completionFuture;
    private final ByteArrayOutputStream outputStream;
    private final WritableByteChannel channel;

    ResultSubscriber() {
      this.completionFuture = new CompletableFuture<>();
      this.outputStream = new ByteArrayOutputStream();
      this.channel = Channels.newChannel(outputStream);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(Slice item) {
      try {
        this.channel.write(item.asReadOnlyByteBuffer());
      } catch (IOException e) {
        this.completionFuture.completeExceptionally(e);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      this.completionFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
      this.completionFuture.complete(null);
    }

    public byte[] getResult() throws Throwable {
      try {
        this.completionFuture.get();
        return outputStream.toByteArray();
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    }
  }

  static class BufferedPublisher implements Flow.Publisher<Slice> {

    private Slice slice;

    BufferedPublisher(Slice slice) {
      this.slice = slice;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Slice> subscriber) {
      subscriber.onSubscribe(
          new Flow.Subscription() {
            @Override
            public void request(long l) {
              if (slice != null) {
                subscriber.onNext(slice);
                subscriber.onComplete();
                slice = null;
              }
            }

            @Override
            public void cancel() {}
          });
    }
  }
}
