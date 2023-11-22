// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.impl.MessageHeader;
import io.vertx.core.buffer.Buffer;

class MessageEncoder {

  static int encodeLength(MessageLite msg) {
    return 8 + msg.getSerializedSize();
  }

  static Buffer encode(Buffer buffer, MessageLite msg) {
    MessageHeader header = MessageHeader.fromMessage(msg);

    buffer.appendLong(header.encode());
    buffer.appendBytes(msg.toByteArray());

    return buffer;
  }
}
