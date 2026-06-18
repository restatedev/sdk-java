// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.legacy.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

public abstract class AwakeableIdTestSuite implements TestSuite {

  protected abstract TestDefinitions.TestInvocationBuilder returnAwakeableId();

  @Override
  public Stream<TestDefinition> definitions() {
    UUID id = UUID.randomUUID();
    String debugId = id.toString();
    byte[] serializedId = serializeUUID(id);

    ByteBuffer expectedAwakeableId = ByteBuffer.allocate(serializedId.length + 4);
    expectedAwakeableId.put(serializedId);
    expectedAwakeableId.putInt(17);
    expectedAwakeableId.flip();
    String base64ExpectedAwakeableIdWithPadding =
        "sign_1" + Base64.getUrlEncoder().encodeToString(expectedAwakeableId.array());
    String base64ExpectedAwakeableIdWithoutPadding =
        "sign_1"
            + Base64.getUrlEncoder().withoutPadding().encodeToString(expectedAwakeableId.array());

    return Stream.of(
        returnAwakeableId()
            .withInput(
                startMessage(1).setDebugId(debugId).setId(ByteString.copyFrom(serializedId)),
                inputCmd())
            .assertingOutput(
                msgs -> {
                  assertThat(msgs).hasSize(2);

                  assertThat(msgs)
                      .element(0)
                      .asInstanceOf(type(Protocol.OutputCommandMessage.class))
                      .extracting(Protocol.OutputCommandMessage::getValue)
                      .extracting(Protocol.Value::getContent)
                      .extracting(ByteString::toStringUtf8, STRING)
                      .containsAnyOf(
                          base64ExpectedAwakeableIdWithPadding,
                          base64ExpectedAwakeableIdWithoutPadding);
                  assertThat(msgs).element(1).isEqualTo(END_MESSAGE);
                }));
  }

  private byte[] serializeUUID(UUID uuid) {
    ByteBuffer serializedId = ByteBuffer.allocate(16);
    serializedId.putLong(uuid.getMostSignificantBits());
    serializedId.putLong(uuid.getLeastSignificantBits());
    serializedId.flip();
    return serializedId.array();
  }
}
