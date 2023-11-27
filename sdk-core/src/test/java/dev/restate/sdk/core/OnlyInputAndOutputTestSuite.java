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
import static dev.restate.sdk.core.TestDefinitions.TestDefinition;
import static dev.restate.sdk.core.TestDefinitions.testInvocation;

import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class OnlyInputAndOutputTestSuite implements TestSuite {

  protected abstract BindableService noSyscallsGreeter();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::noSyscallsGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Francesco")))
            .expectingOutput(
                outputMessage(
                    GreetingResponse.newBuilder().setMessage("Hello Francesco").build())));
  }
}
