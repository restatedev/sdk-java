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

import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class OnlyInputAndOutputTestSuite implements TestSuite {

  protected abstract BindableService noSyscallsGreeter();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::noSyscallsGreeter, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(greetingRequest("Francesco")))
            .expectingOutput(outputMessage(greetingResponse("Hello Francesco")), END_MESSAGE),
        testInvocation(this::noSyscallsGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(new TerminalException(TerminalException.Code.CANCELLED)))
            .expectingOutput(
                outputMessage(new TerminalException(TerminalException.Code.CANCELLED)),
                END_MESSAGE));
  }
}
