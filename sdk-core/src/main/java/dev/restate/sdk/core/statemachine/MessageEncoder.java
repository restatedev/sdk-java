// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.types.Slice;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

final class MessageEncoder implements Flow.Subscriber<MessageLite> {

  private final Flow.Subscriber<? super Slice> inner;

  MessageEncoder(Flow.Subscriber<? super Slice> inner) {
    this.inner = inner;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    inner.onSubscribe(subscription);
  }

  @Override
  public void onNext(MessageLite item) {
    // We could pool those buffers somehow?
    ByteBuffer buffer = ByteBuffer.allocate(MessageEncoder.encodeLength(item));
    MessageEncoder.encode(buffer, item);
    inner.onNext(Slice.wrap(buffer));
  }

  @Override
  public void onError(Throwable throwable) {
    inner.onError(throwable);
  }

  @Override
  public void onComplete() {
    inner.onComplete();
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
