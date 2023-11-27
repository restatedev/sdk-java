// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.testInvocation;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class InvocationIdTestSuite implements TestSuite {

  protected abstract BindableService returnInvocationId();

  @Override
  public Stream<TestDefinition> definitions() {
    String debugId = "my-debug-id";
    ByteString id = ByteString.copyFromUtf8(debugId);

    return Stream.of(
        testInvocation(this::returnInvocationId, GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder().setDebugId(debugId).setId(id).setKnownEntries(1),
                inputMessage(GreetingRequest.getDefaultInstance()))
            .onlyUnbuffered()
            .expectingOutput(outputMessage(greetingResponse(debugId))));
  }
}
