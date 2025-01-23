// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ProtoUtils.inputMessage;
import static dev.restate.sdk.core.ProtoUtils.startMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.impl.MessageDecoder;
import dev.restate.sdk.core.impl.MessageEncoder;
import dev.restate.sdk.core.statemachine.InvocationInput;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MessageDecoderTest {

  @Test
  void oneMessage() {
    AssertSubscriber<InvocationInput> assertSubscriber = AssertSubscriber.create(1);

    Multi.createFrom()
        .item(ProtoUtils.messageToByteString(startMessage(1, "my-key", entry("key", "value"))))
        .subscribe(new MessageDecoder(assertSubscriber));

    assertThat(assertSubscriber.getLastItem().message())
        .isEqualTo(startMessage(1, "my-key", entry("key", "value")).build());
  }

  @Test
  void multiMessage() {
    AssertSubscriber<InvocationInput> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);

    Multi.createFrom()
        .items(
            ProtoUtils.messageToByteString(startMessage(1, "my-key", entry("key", "value"))),
            ProtoUtils.messageToByteString(inputMessage("my-value")))
        .subscribe(new MessageDecoder(assertSubscriber));

    assertThat(assertSubscriber.getItems())
        .map(InvocationInput::message)
        .containsExactly(
            startMessage(1, "my-key", entry("key", "value")).build(), inputMessage("my-value"));
  }

  @Test
  void multiMessageInSingleBuffer() {
    AssertSubscriber<InvocationInput> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);

    List<MessageLite> messages =
        List.of(startMessage(1, "my-key", entry("key", "value")).build(), inputMessage("my-value"));
    ByteBuffer byteBuffer =
        ByteBuffer.allocate(messages.stream().mapToInt(MessageEncoder::encodeLength).sum());
    messages.stream().map(ProtoUtils::messageToByteString).forEach(byteBuffer::put);
    byteBuffer.flip();

    Multi.createFrom().item(byteBuffer).subscribe(new MessageDecoder(assertSubscriber));

    assertThat(assertSubscriber.getItems())
        .map(InvocationInput::message)
        .containsExactly(
            startMessage(1, "my-key", entry("key", "value")).build(), inputMessage("my-value"));
  }
}
