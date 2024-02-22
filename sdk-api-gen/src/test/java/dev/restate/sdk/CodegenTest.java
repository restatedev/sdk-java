// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.JsonProtoUtils.*;
import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.testInvocation;

import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import java.util.stream.Stream;

public class CodegenTest implements TestSuite {

  @Service
  static class StatelessGreeter {
    @Handler
    String greet(Context context, String request) {
      return request;
    }
  }

  @VirtualObject
  static class ObjectGreeter {
    @Exclusive
    String greet(ObjectContext context, String request) {
      return request;
    }
  }

  @VirtualObject
  public interface GreeterInterface {
    @Exclusive
    String greet(ObjectContext context, String request);
  }

  private static class ObjectGreeterImplementedFromInterface implements GreeterInterface {

    @Override
    public String greet(ObjectContext context, String request) {
      return request;
    }
  }

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(StatelessGreeter::new, "greet")
            .withInput(
                startMessage(1),
                inputMessage("Francesco"),
                completionMessage(3, greetingResponse("Till")))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeter::new, "greet")
            .withInput(
                startMessage(1),
                keyedInputMessage("slinkydeveloper", "Francesco"),
                completionMessage(3, greetingResponse("Till")))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeterImplementedFromInterface::new, "greet")
            .withInput(
                startMessage(1),
                keyedInputMessage("slinkydeveloper", "Francesco"),
                completionMessage(3, greetingResponse("Till")))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE));
  }
}
