package dev.restate.sdk.vertx;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.restate.sdk.core.impl.InvocationFlow;
import dev.restate.sdk.core.impl.MessageHeader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import java.util.ArrayDeque;
import java.util.Queue;

class MessageDecoder {

  private enum State {
    WAITING_HEADER,
    WAITING_PAYLOAD,
    FAILED
  }

  private final Queue<InvocationFlow.InvocationInput> parsedMessages;
  private final ByteBuf internalBuffer;

  private State state;
  private MessageHeader lastParsedMessageHeader;
  private RuntimeException lastParsingFailure;

  MessageDecoder() {
    this.parsedMessages = new ArrayDeque<>();
    this.internalBuffer = Unpooled.compositeBuffer();

    this.state = State.WAITING_HEADER;
    this.lastParsedMessageHeader = null;
    this.lastParsingFailure = null;
  }

  InvocationFlow.InvocationInput poll() {
    if (this.state == State.FAILED) {
      throw lastParsingFailure;
    }
    return this.parsedMessages.poll();
  }

  void offer(Buffer buffer) {
    if (this.state != State.FAILED) {
      this.internalBuffer.writeBytes(buffer.getByteBuf());
      this.tryConsumeInternalBuffer();
    }
  }

  // -- Internal methods to handle decoding

  private void tryConsumeInternalBuffer() {
    while (this.state != State.FAILED && this.internalBuffer.readableBytes() >= wantBytes()) {
      if (state == State.WAITING_HEADER) {
        try {
          this.lastParsedMessageHeader = MessageHeader.parse(this.internalBuffer.readLong());
          this.state = State.WAITING_PAYLOAD;
        } catch (RuntimeException e) {
          this.lastParsingFailure = e;
          this.state = State.FAILED;
        }
      } else {
        try {
          this.parsedMessages.offer(
              InvocationFlow.InvocationInput.of(
                  this.lastParsedMessageHeader,
                  this.lastParsedMessageHeader
                      .getType()
                      .messageParser()
                      .parseFrom(
                          this.internalBuffer
                              .readBytes(this.lastParsedMessageHeader.getLength())
                              .nioBuffer())));
          this.state = State.WAITING_HEADER;
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
}
