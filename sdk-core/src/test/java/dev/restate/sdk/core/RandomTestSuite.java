// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.AssertUtils.*;
import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.testInvocation;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.util.Random;
import java.util.stream.Stream;

public abstract class RandomTestSuite implements TestSuite {

  protected abstract BindableService randomShouldBeDeterministic();

  protected abstract BindableService randomInsideSideEffect();

  @Override
  public Stream<TestDefinition> definitions() {
    String debugId = "my-id";

    int expectedRandomNumber = new Random(new InvocationIdImpl(debugId).toRandomSeed()).nextInt();
    return Stream.of(
        testInvocation(this::randomShouldBeDeterministic, GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder().setDebugId(debugId).setKnownEntries(1),
                inputMessage(GreetingRequest.getDefaultInstance()))
            .expectingOutput(
                outputMessage(greetingResponse(Integer.toString(expectedRandomNumber))),
                END_MESSAGE),
        testInvocation(this::randomInsideSideEffect, GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder().setDebugId(debugId).setKnownEntries(1),
                inputMessage(GreetingRequest.getDefaultInstance()))
            .assertingOutput(
                containsOnlyExactErrorMessage(
                    new IllegalStateException(
                        "You can't use RestateRandom inside a side effect!"))));
  }
}
