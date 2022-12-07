package dev.restate.sdk.vertx;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.impl.MessageHeader;
import io.vertx.core.buffer.Buffer;

public class MessageEncoder {

  public static int encodeLength(MessageLite msg) {
    return 8 + msg.getSerializedSize();
  }

  public static void encode(Buffer buffer, MessageLite msg) {
    MessageHeader header = MessageHeader.fromMessage(msg);

    buffer.appendLong(header.encode());
    buffer.appendBytes(msg.toByteArray());
  }
}
