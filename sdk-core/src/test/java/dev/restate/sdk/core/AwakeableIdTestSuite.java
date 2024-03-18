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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.AwakeableEntryMessage;
import dev.restate.generated.service.protocol.Protocol.StartMessage;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
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
    expectedAwakeableId.putInt(1);
    expectedAwakeableId.rewind();
    String base64ExpectedAwakeableId =
        Entries.AWAKEABLE_IDENTIFIER_PREFIX
            + Base64.getUrlEncoder().encodeToString(expectedAwakeableId.array());

    return Stream.of(
        returnAwakeableId()
            .withInput(
                StartMessage.newBuilder()
                    .setDebugId(debugId)
                    .setId(ByteString.copyFrom(serializedId))
                    .setKnownEntries(1),
                inputMessage())
            .assertingOutput(
                messages -> {
                  assertThat(messages).element(0).isInstanceOf(AwakeableEntryMessage.class);
                  assertThat(messages)
                      .element(1)
                      .asInstanceOf(type(Protocol.OutputEntryMessage.class))
                      .extracting(out -> CoreSerdes.JSON_STRING.deserialize(out.getValue()))
                      .isEqualTo(base64ExpectedAwakeableId);
                }));
  }

  private byte[] serializeUUID(UUID uuid) {
    ByteBuffer serializedId = ByteBuffer.allocate(16);
    serializedId.putLong(uuid.getMostSignificantBits());
    serializedId.putLong(uuid.getLeastSignificantBits());
    serializedId.rewind();
    return serializedId.array();
  }
}
