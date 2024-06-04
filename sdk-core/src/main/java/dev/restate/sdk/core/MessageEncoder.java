// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.MessageLite;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

class MessageEncoder implements InvocationFlow.InvocationOutputPublisher {

  private final Flow.Publisher<MessageLite> inner;

  MessageEncoder(Flow.Publisher<MessageLite> inner) {
    this.inner = inner;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
    this.inner.subscribe(
        new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            subscriber.onSubscribe(subscription);
          }

          @Override
          public void onNext(MessageLite item) {
            // We could pool those buffers somehow?
            ByteBuffer buffer = ByteBuffer.allocate(MessageEncoder.encodeLength(item));
            MessageEncoder.encode(buffer, item);
            subscriber.onNext(buffer);
          }

          @Override
          public void onError(Throwable throwable) {
            subscriber.onError(throwable);
          }

          @Override
          public void onComplete() {
            subscriber.onComplete();
          }
        });
  }

  static int encodeLength(MessageLite msg) {
    return 8 + msg.getSerializedSize();
  }

  static ByteBuffer encode(ByteBuffer buffer, MessageLite msg) {
    MessageHeader header = MessageHeader.fromMessage(msg);

    buffer.putLong(header.encode());
    buffer.put(msg.toByteString().asReadOnlyByteBuffer());

    buffer.flip();

    return buffer;
  }
}
