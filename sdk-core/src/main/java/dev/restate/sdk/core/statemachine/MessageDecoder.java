// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnsafeByteOperations;
import dev.restate.sdk.types.Slice;
import java.util.ArrayDeque;
import java.util.Queue;
import org.jspecify.annotations.Nullable;

final class MessageDecoder {

  private enum State {
    WAITING_HEADER,
    WAITING_PAYLOAD,
    FAILED
  }

  private final Queue<InvocationInput> parsedMessages;
  private ByteString internalBuffer;

  private State state;
  private MessageHeader lastParsedMessageHeader;
  private RuntimeException lastParsingFailure;

  MessageDecoder() {
    this.parsedMessages = new ArrayDeque<>();
    this.internalBuffer = ByteString.EMPTY;

    this.state = State.WAITING_HEADER;
    this.lastParsedMessageHeader = null;
    this.lastParsingFailure = null;
  }

  // -- Subscriber methods

  public void offer(Slice item) {
    this.offer(UnsafeByteOperations.unsafeWrap(item.asReadOnlyByteBuffer()));
  }

  public @Nullable InvocationInput next() {
    if (this.state == State.FAILED) {
      throw lastParsingFailure;
    }
    return this.parsedMessages.poll();
  }

  public boolean isNextAvailable() {
    return !this.parsedMessages.isEmpty();
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
