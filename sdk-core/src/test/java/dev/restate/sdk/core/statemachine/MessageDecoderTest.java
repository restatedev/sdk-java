// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import static dev.restate.sdk.core.AssertUtils.assertThatDecodingMessages;
import static dev.restate.sdk.core.statemachine.ProtoUtils.inputCmd;
import static dev.restate.sdk.core.statemachine.ProtoUtils.startMessage;
import static org.assertj.core.api.Assertions.entry;

import com.google.protobuf.MessageLite;
import dev.restate.common.Slice;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.Test;

public class MessageDecoderTest {

  @Test
  void oneMessage() {
    assertThatDecodingMessages(
            ProtoUtils.encodeMessageToSlice(startMessage(1, "my-key", entry("key", "value")))
    )    .map(InvocationInput::message).containsExactly(
            startMessage(1, "my-key", entry("key", "value")).build()
    )
    ;
  }

  @Test
  void multiMessage() {
    assertThatDecodingMessages(
            ProtoUtils.encodeMessageToSlice(startMessage(1, "my-key", entry("key", "value"))),
            ProtoUtils.encodeMessageToSlice(ProtoUtils.inputCmd("my-value")))
            .map(InvocationInput::message)
            .containsExactly(
                    startMessage(1, "my-key", entry("key", "value")).build(), ProtoUtils.inputCmd("my-value"));
  }

  @Test
  void multiMessageInSingleBuffer() {
    List<MessageLite> messages =
        List.of(startMessage(1, "my-key", entry("key", "value")).build(), ProtoUtils.inputCmd("my-value"));
    ByteBuffer byteBuffer =
        ByteBuffer.allocate(messages.stream().mapToInt(MessageEncoder::encodeLength).sum());
    messages.stream().map(ProtoUtils::encodeMessageToByteBuffer).forEach(byteBuffer::put);
    byteBuffer.flip();

    assertThatDecodingMessages(
Slice.wrap(byteBuffer)
    )    .map(InvocationInput::message)     .containsExactly(
            startMessage(1, "my-key", entry("key", "value")).build(), ProtoUtils.inputCmd("my-value"));
  }

}
