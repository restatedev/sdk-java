// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ProtoUtils.END_MESSAGE;
import static dev.restate.sdk.core.ProtoUtils.outputMessage;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import java.util.stream.Stream;

public abstract class InvocationIdTestSuite implements TestSuite {

  protected abstract TestInvocationBuilder returnInvocationId();

  @Override
  public Stream<TestDefinition> definitions() {
    String debugId = "my-debug-id";
    ByteString id = ByteString.copyFromUtf8(debugId);

    return Stream.of(
        returnInvocationId()
            .withInput(
                Protocol.StartMessage.newBuilder().setDebugId(debugId).setId(id).setKnownEntries(1),
                ProtoUtils.inputMessage())
            .onlyUnbuffered()
            .expectingOutput(outputMessage(debugId), END_MESSAGE));
  }
}
