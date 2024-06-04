// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnsafeByteOperations;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Flow;

class MessageDecoder implements InvocationFlow.InvocationInputSubscriber {

  private enum State {
    WAITING_HEADER,
    WAITING_PAYLOAD,
    FAILED
  }

  private final Flow.Subscriber<InvocationInput> inner;
  private Flow.Subscription inputSubscription;
  private long invocationInputRequests = 0;

  private final Queue<InvocationInput> parsedMessages;
  private ByteString internalBuffer;

  private State state;
  private MessageHeader lastParsedMessageHeader;
  private RuntimeException lastParsingFailure;

  MessageDecoder(Flow.Subscriber<InvocationInput> inner) {
    this.inner = inner;
    this.parsedMessages = new ArrayDeque<>();
    this.internalBuffer = ByteString.EMPTY;

    this.state = State.WAITING_HEADER;
    this.lastParsedMessageHeader = null;
    this.lastParsingFailure = null;
  }

  // -- Subscriber methods

  @Override
  public void onSubscribe(Flow.Subscription byteBufferSubscription) {
    this.inputSubscription = byteBufferSubscription;
    this.inner.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long n) {
            // We ask for MAX VALUE, then we buffer in this class.
            //  This class could be implemented with more backpressure in mind, but for now this is
            // fine.
            byteBufferSubscription.request(Long.MAX_VALUE);
            handleSubscriptionRequest(n);
          }

          @Override
          public void cancel() {
            byteBufferSubscription.cancel();
          }
        });
  }

  @Override
  public void onNext(ByteBuffer item) {
    this.offer(UnsafeByteOperations.unsafeWrap(item));
    tryProgress();
  }

  @Override
  public void onError(Throwable throwable) {
    if (this.inputSubscription == null) {
      return;
    }
    this.inner.onError(throwable);
  }

  @Override
  public void onComplete() {
    if (this.inputSubscription == null) {
      return;
    }
    this.inner.onComplete();
  }

  private void handleSubscriptionRequest(long l) {
    if (l == Long.MAX_VALUE) {
      this.invocationInputRequests = l;
    } else {
      this.invocationInputRequests += l;
      // Overflow check
      if (this.invocationInputRequests < 0) {
        this.invocationInputRequests = Long.MAX_VALUE;
      }
    }

    tryProgress();
  }

  private void tryProgress() {
    if (this.inputSubscription == null) {
      return;
    }
    if (this.state == State.FAILED) {
      this.inner.onError(lastParsingFailure);
      this.inputSubscription.cancel();
      this.inputSubscription = null;
    }
    while (this.invocationInputRequests > 0) {
      InvocationInput input = this.parsedMessages.poll();
      if (input == null) {
        return;
      }
      this.invocationInputRequests--;
      this.inner.onNext(input);
    }
  }

  // -- Internal methods to handle decoding

  private void offer(ByteString buffer) {
    if (this.state != State.FAILED) {
      this.internalBuffer = this.internalBuffer.concat(buffer);
      this.tryConsumeInternalBuffer();
    }
  }

  private void tryConsumeInternalBuffer() {
    while (this.state != State.FAILED && this.internalBuffer.size() >= wantBytes()) {
      if (state == State.WAITING_HEADER) {
        try {
          this.lastParsedMessageHeader = MessageHeader.parse(readLongAtBeginning());
          this.state = State.WAITING_PAYLOAD;
          this.sliceInternalBuffer(8);
        } catch (RuntimeException e) {
          this.lastParsingFailure = e;
          this.state = State.FAILED;
        }
      } else {
        try {
          this.parsedMessages.offer(
              InvocationInput.of(
                  this.lastParsedMessageHeader,
                  this.lastParsedMessageHeader
                      .getType()
                      .messageParser()
                      .parseFrom(
                          this.internalBuffer.substring(
                              0, this.lastParsedMessageHeader.getLength()))));
          this.state = State.WAITING_HEADER;
          this.sliceInternalBuffer(this.lastParsedMessageHeader.getLength());
        } catch (InvalidProtocolBufferException e) {
          this.lastParsingFailure = new RuntimeException("Cannot parse the protobuf message", e);
          this.state = State.FAILED;
        } catch (RuntimeException e) {
          this.lastParsingFailure = e;
          this.state = State.FAILED;
        }
      }
    }
  }

  private int wantBytes() {
    if (state == State.WAITING_HEADER) {
      return 8;
    } else {
      return lastParsedMessageHeader.getLength();
    }
  }

  private void sliceInternalBuffer(int substring) {
    if (this.internalBuffer.size() == substring) {
      this.internalBuffer = ByteString.EMPTY;
    } else {
      this.internalBuffer = this.internalBuffer.substring(substring);
    }
  }

  private long readLongAtBeginning() {
    return ((this.internalBuffer.byteAt(7) & 0xffL)
        | ((this.internalBuffer.byteAt(6) & 0xffL) << 8)
        | ((this.internalBuffer.byteAt(5) & 0xffL) << 16)
        | ((this.internalBuffer.byteAt(4) & 0xffL) << 24)
        | ((this.internalBuffer.byteAt(3) & 0xffL) << 32)
        | ((this.internalBuffer.byteAt(2) & 0xffL) << 40)
        | ((this.internalBuffer.byteAt(1) & 0xffL) << 48)
        | ((this.internalBuffer.byteAt(0) & 0xffL) << 56));
  }
}
