// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.upcasting;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.restate.common.Slice;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.core.statemachine.MessageHeader;
import dev.restate.sdk.upcasting.Upcaster;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DefaultInvocationInputUpcaster}, ensuring payloads are upcast with proper
 * metadata propagation and that failures are handled gracefully.
 *
 * @author Milan Savic
 */
class DefaultInvocationInputUpcasterTest {

  private static final byte[] ORIG = "orig".getBytes(StandardCharsets.UTF_8);
  private static final byte[] UP = "up".getBytes(StandardCharsets.UTF_8);

  @Test
  void upcastNullInputReturnsNull() {
    var sut = new DefaultInvocationInputUpcaster(Upcaster.noop());
    assertThat(sut.upcast(null)).isNull();
  }

  @Test
  void inputCommandMessageValueIsUpcastAndMetadataContainsCoreNameAndHeaders() {
    CapturingUpcaster cap = new CapturingUpcaster(true, UP);
    var sut = new DefaultInvocationInputUpcaster(cap);

    Protocol.InputCommandMessage msg =
        Protocol.InputCommandMessage.newBuilder()
            .addHeaders(Protocol.Header.newBuilder().setKey("X-Key").setValue("ignored").build())
            .setName("entry")
            .setValue(valueOf(ORIG))
            .build();
    InvocationInput input = InvocationInput.of(MessageHeader.fromMessage(msg), msg);

    InvocationInput out = sut.doUpcast(input);

    assertThat(out.message()).isInstanceOf(Protocol.InputCommandMessage.class);
    Protocol.InputCommandMessage outMsg = (Protocol.InputCommandMessage) out.message();
    assertThat(outMsg.getValue().getContent().toByteArray()).isEqualTo(UP);

    // Metadata captured by the Upcaster should include the CORE_MESSAGE_NAME and carried header key
    assertThat(cap.lastHeaders.get()).isNotNull();
    Map<String, String> meta = cap.lastHeaders.get();
    assertThat(meta.get(Upcaster.CORE_MESSAGE_NAME_METADATA_KEY))
        .isEqualTo(Protocol.InputCommandMessage.class.getSimpleName());
    assertThat(meta).containsKey("X-Key");
    // body passed to the upcaster should be the raw value content
    assertThat(cap.lastBody.get()).isNotNull();
    assertThat(cap.lastBody.get().toByteArray()).isEqualTo(ORIG);
  }

  @Test
  void runCompletionNotificationValueIsUpcastAndCoreNamePresent() {
    CapturingUpcaster cap = new CapturingUpcaster(true, UP);
    var sut = new DefaultInvocationInputUpcaster(cap);

    Protocol.RunCompletionNotificationMessage msg =
        Protocol.RunCompletionNotificationMessage.newBuilder()
            .setCompletionId(1)
            .setValue(valueOf(ORIG))
            .build();
    InvocationInput input = InvocationInput.of(MessageHeader.fromMessage(msg), msg);

    Protocol.RunCompletionNotificationMessage out =
        (Protocol.RunCompletionNotificationMessage) sut.doUpcast(input).message();
    assertThat(out.getValue().getContent().toByteArray()).isEqualTo(UP);
    assertThat(cap.lastHeaders.get().get(Upcaster.CORE_MESSAGE_NAME_METADATA_KEY))
        .isEqualTo(Protocol.RunCompletionNotificationMessage.class.getSimpleName());
  }

  @Test
  void startMessageStateEntriesAreUpcastAndStateKeyMetadataIsPropagated() {
    CapturingUpcaster cap = new CapturingUpcaster(true, UP);
    var sut = new DefaultInvocationInputUpcaster(cap);

    Protocol.StartMessage.StateEntry stateEntry =
        Protocol.StartMessage.StateEntry.newBuilder()
            .setKey(ByteString.copyFrom("balance".getBytes(StandardCharsets.UTF_8)))
            .setValue(ByteString.copyFrom(ORIG))
            .build();
    Protocol.StartMessage msg = Protocol.StartMessage.newBuilder().addStateMap(stateEntry).build();
    InvocationInput input = InvocationInput.of(MessageHeader.fromMessage(msg), msg);

    Protocol.StartMessage out = (Protocol.StartMessage) sut.doUpcast(input).message();
    assertThat(out.getStateMap(0).getValue().toByteArray()).isEqualTo(UP);
    assertThat(cap.lastHeaders.get().get(Upcaster.CORE_MESSAGE_NAME_METADATA_KEY))
        .isEqualTo(Protocol.StartMessage.class.getSimpleName());
    assertThat(cap.lastHeaders.get().get(Upcaster.STATE_KEY_METADATA_KEY)).isEqualTo("balance");
    assertThat(cap.lastBody.get().toByteArray()).isEqualTo(ORIG);
  }

  @Test
  void proposeRunCompletionValueIsUpcastFromRawBytes() {
    CapturingUpcaster cap = new CapturingUpcaster(true, UP);
    var sut = new DefaultInvocationInputUpcaster(cap);

    Protocol.ProposeRunCompletionMessage msg =
        Protocol.ProposeRunCompletionMessage.newBuilder()
            .setValue(ByteString.copyFrom(ORIG))
            .build();
    InvocationInput input = InvocationInput.of(MessageHeader.fromMessage(msg), msg);

    Protocol.ProposeRunCompletionMessage out =
        (Protocol.ProposeRunCompletionMessage) sut.doUpcast(input).message();
    assertThat(out.getValue().toByteArray()).isEqualTo(UP);
    assertThat(cap.lastHeaders.get().get(Upcaster.CORE_MESSAGE_NAME_METADATA_KEY))
        .isEqualTo(Protocol.ProposeRunCompletionMessage.class.getSimpleName());
  }

  @Test
  void messageWithoutValueIsLeftUntouched() {
    CapturingUpcaster cap = new CapturingUpcaster(true, UP);
    var sut = new DefaultInvocationInputUpcaster(cap);

    // OutputCommandMessage with failure but no value should not be transformed
    Protocol.OutputCommandMessage msg =
        Protocol.OutputCommandMessage.newBuilder()
            .setName("entry")
            .setFailure(Protocol.Failure.newBuilder().setCode(42).setMessage("boom").build())
            .build();
    InvocationInput input = InvocationInput.of(MessageHeader.fromMessage(msg), msg);

    InvocationInput out = sut.doUpcast(input);
    assertThat(out.message()).isInstanceOf(Protocol.OutputCommandMessage.class);
    Protocol.OutputCommandMessage outMsg = (Protocol.OutputCommandMessage) out.message();
    assertThat(outMsg.hasValue()).isFalse();
    assertThat(cap.lastBody.get()).isNull(); // upcaster should not have been invoked
  }

  @Test
  void publicUpcastCatchesExceptionsAndReturnsOriginalInput() {
    Upcaster throwing =
        new Upcaster() {
          @Override
          public boolean canUpcast(Slice body, Map<String, String> headers) {
            return true;
          }

          @Override
          public Slice upcast(Slice body, Map<String, String> headers) {
            throw new RuntimeException("boom");
          }
        };
    var sut = new DefaultInvocationInputUpcaster(throwing);

    Protocol.RunCompletionNotificationMessage msg =
        Protocol.RunCompletionNotificationMessage.newBuilder()
            .setCompletionId(1)
            .setValue(valueOf(ORIG))
            .build();
    InvocationInput input = InvocationInput.of(MessageHeader.fromMessage(msg), msg);

    InvocationInput out = sut.upcast(input); // use the safe wrapper
    assertThat(out).isSameAs(input); // should fall back to original input
  }

  private static Protocol.Value valueOf(byte[] content) {
    return Protocol.Value.newBuilder().setContent(ByteString.copyFrom(content)).build();
  }

  private static final class CapturingUpcaster implements Upcaster {
    private final boolean answer;
    private final byte[] replyBytes;
    private final AtomicReference<Slice> lastBody = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>();

    private CapturingUpcaster(boolean answer, byte[] replyBytes) {
      this.answer = answer;
      this.replyBytes = replyBytes;
    }

    @Override
    public boolean canUpcast(Slice body, Map<String, String> headers) {
      lastBody.set(body);
      lastHeaders.set(headers);
      return answer;
    }

    @Override
    public Slice upcast(Slice body, Map<String, String> headers) {
      lastBody.set(body);
      lastHeaders.set(headers);
      return Slice.wrap(replyBytes);
    }
  }
}
