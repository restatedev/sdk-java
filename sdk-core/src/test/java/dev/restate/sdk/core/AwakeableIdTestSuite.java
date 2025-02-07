// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.statemachine.ProtoUtils.*;

import com.google.protobuf.ByteString;
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
    expectedAwakeableId.flip();
    String base64ExpectedAwakeableId =
        "sign_1"
            + Base64.getUrlEncoder().encodeToString(expectedAwakeableId.array());

    return Stream.of(
        returnAwakeableId()
            .withInput(
                    startMessage(1).setDebugId(debugId).setId(ByteString.copyFrom(serializedId)),
                inputMessage())
                .expectingOutput(
                        outputMessage(base64ExpectedAwakeableId)
                ));
  }

  private byte[] serializeUUID(UUID uuid) {
    ByteBuffer serializedId = ByteBuffer.allocate(16);
    serializedId.putLong(uuid.getMostSignificantBits());
    serializedId.putLong(uuid.getLeastSignificantBits());
    serializedId.flip();
    return serializedId.array();
  }
}
