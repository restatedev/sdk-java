// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.testInvocation;

import com.google.protobuf.ByteString;
import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.Target;
import dev.restate.sdk.core.ProtoUtils;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import java.util.stream.Stream;

public class CodegenTest implements TestSuite {

  @Service
  static class ServiceGreeter {
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

  @Service(name = "Empty")
  static class Empty {

    @Handler
    public String emptyInput(Context context) {
      var client = EmptyClient.fromContext(context);
      return client.emptyInput().await();
    }

    @Handler
    public void emptyOutput(Context context, String request) {
      var client = EmptyClient.fromContext(context);
      client.emptyOutput(request).await();
    }

    @Handler
    public void emptyInputOutput(Context context) {
      var client = EmptyClient.fromContext(context);
      client.emptyInputOutput().await();
    }
  }

  @Service(name = "PrimitiveTypes")
  static class PrimitiveTypes {

    @Handler
    public int primitiveOutput(Context context) {
      var client = PrimitiveTypesClient.fromContext(context);
      return client.primitiveOutput().await();
    }

    @Handler
    public void primitiveInput(Context context, int input) {
      var client = PrimitiveTypesClient.fromContext(context);
      client.primitiveInput(input).await();
    }
  }

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(ServiceGreeter::new, "greet")
            .withInput(startMessage(1), inputMessage("Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeter::new, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputMessage("Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeterImplementedFromInterface::new, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputMessage("Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation(Empty::new, "emptyInput")
            .withInput(startMessage(1), inputMessage(), completionMessage(1, "Till"))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("Empty", "emptyInput")),
                outputMessage("Till"),
                END_MESSAGE)
            .named("empty output"),
        testInvocation(Empty::new, "emptyOutput")
            .withInput(
                startMessage(1),
                inputMessage("Francesco"),
                completionMessage(1).setValue(ByteString.EMPTY))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("Empty", "emptyOutput"), "Francesco"),
                ProtoUtils.outputMessage(),
                END_MESSAGE)
            .named("empty output"),
        testInvocation(Empty::new, "emptyInputOutput")
            .withInput(
                startMessage(1),
                inputMessage("Francesco"),
                completionMessage(1).setValue(ByteString.EMPTY))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("Empty", "emptyInputOutput")),
                ProtoUtils.outputMessage(),
                END_MESSAGE)
            .named("empty input and empty output"),
        testInvocation(PrimitiveTypes::new, "primitiveOutput")
            .withInput(
                startMessage(1), inputMessage(), completionMessage(1, CoreSerdes.JSON_INT, 10))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("PrimitiveTypes", "primitiveOutput"), CoreSerdes.VOID, null),
                outputMessage(CoreSerdes.JSON_INT, 10),
                END_MESSAGE)
            .named("primitive output"),
        testInvocation(PrimitiveTypes::new, "primitiveInput")
            .withInput(
                startMessage(1), inputMessage(10), completionMessage(1).setValue(ByteString.EMPTY))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("PrimitiveTypes", "primitiveInput"), CoreSerdes.JSON_INT, 10),
                outputMessage(),
                END_MESSAGE)
            .named("primitive input"));
  }
}
