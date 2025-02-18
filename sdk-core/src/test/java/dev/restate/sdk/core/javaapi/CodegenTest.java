// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.TestDefinitions.testInvocation;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.common.Target;
import dev.restate.sdk.*;
import dev.restate.sdk.annotation.*;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.serde.Serde;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Handler
    @Shared
    String sharedGreet(SharedObjectContext context, String request) {
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

  @VirtualObject
  static class CornerCases {
    @Exclusive
    public String send(ObjectContext context, String request) {
      // Just needs to compile
      return CodegenTestCornerCasesClient.fromContext(context, request)._send("my_send").await();
    }
  }

  @Workflow
  static class WorkflowCornerCases {
    @Workflow
    public String run(WorkflowContext context, String request) {
      return null;
    }

    @Shared
    public String submit(SharedWorkflowContext context, String request) {
      // Just needs to compile
      String ignored =
          CodegenTestWorkflowCornerCasesClient.connect("invalid", request)._submit("my_send");
      CodegenTestWorkflowCornerCasesClient.connect("invalid", request).submit("my_send");
      return CodegenTestWorkflowCornerCasesClient.connect("invalid", request)
          .workflowHandle()
          .getOutput()
          .response()
          .getValue();
    }
  }

  @Service(name = "RawInputOutput")
  static class RawInputOutput {

    @Handler
    @Raw
    public byte[] rawOutput(Context context) {
      var client = RawInputOutputClient.fromContext(context);
      return client.rawOutput().await();
    }

    @Handler
    @Raw(contentType = "application/vnd.my.custom")
    public byte[] rawOutputWithCustomCT(Context context) {
      var client = RawInputOutputClient.fromContext(context);
      return client.rawOutputWithCustomCT().await();
    }

    @Handler
    public void rawInput(Context context, @Raw byte[] input) {
      var client = RawInputOutputClient.fromContext(context);
      client.rawInput(input).await();
    }

    @Handler
    public void rawInputWithCustomCt(
        Context context, @Raw(contentType = "application/vnd.my.custom") byte[] input) {
      var client = RawInputOutputClient.fromContext(context);
      client.rawInputWithCustomCt(input).await();
    }

    @Handler
    public void rawInputWithCustomAccept(
        Context context,
        @Accept("application/*") @Raw(contentType = "application/vnd.my.custom") byte[] input) {
      var client = RawInputOutputClient.fromContext(context);
      client.rawInputWithCustomCt(input).await();
    }
  }

  @Workflow(name = "MyWorkflow")
  static class MyWorkflow {

    @Workflow
    public void run(WorkflowContext context, String myInput) {
      var client = MyWorkflowClient.fromContext(context, context.key());
      client.send().sharedHandler(myInput);
    }

    @Handler
    public String sharedHandler(SharedWorkflowContext context, String myInput) {
      var client = MyWorkflowClient.fromContext(context, context.key());
      return client.sharedHandler(myInput).await();
    }
  }

  @Service
  static class CheckedException {
    @Handler
    String greet(Context context, String request) throws IOException {
      return request;
    }
  }

  @Service
  @CustomSerdeFactory(MySerdeFactory.class)
  static class CustomSerde {
    @Handler
    String greet(Context context, String request) {
      assertThat(request).isEqualTo("INPUT");
      return "output";
    }
  }

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(ServiceGreeter::new, "greet")
            .withInput(startMessage(1), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeter::new, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeter::new, "sharedGreet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeterImplementedFromInterface::new, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(Empty::new, "emptyInput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, "Till"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInput")),
                outputCmd("Till"),
                END_MESSAGE)
            .named("empty output"),
        testInvocation(Empty::new, "emptyOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyOutput"), "Francesco"),
                outputCmd(),
                END_MESSAGE)
            .named("empty output"),
        testInvocation(Empty::new, "emptyInputOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInputOutput")),
                outputCmd(),
                END_MESSAGE)
            .named("empty input and empty output"),
        testInvocation(PrimitiveTypes::new, "primitiveOutput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, TestSerdes.INT, 10))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1, 2, Target.service("PrimitiveTypes", "primitiveOutput"), Serde.VOID, null),
                outputCmd(TestSerdes.INT, 10),
                END_MESSAGE)
            .named("primitive output"),
        testInvocation(PrimitiveTypes::new, "primitiveInput")
            .withInput(startMessage(1), inputCmd(10), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1, 2, Target.service("PrimitiveTypes", "primitiveInput"), TestSerdes.INT, 10),
                outputCmd(),
                END_MESSAGE)
            .named("primitive input"),
        testInvocation(RawInputOutput::new, "rawInput")
            .withInput(
                startMessage(1),
                inputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawInput"),
                    "{{".getBytes(StandardCharsets.UTF_8)),
                outputCmd(),
                END_MESSAGE),
        testInvocation(RawInputOutput::new, "rawInputWithCustomCt")
            .withInput(
                startMessage(1),
                inputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawInputWithCustomCt"),
                    "{{".getBytes(StandardCharsets.UTF_8)),
                outputCmd(),
                END_MESSAGE),
        testInvocation(RawInputOutput::new, "rawOutput")
            .withInput(
                startMessage(1),
                inputCmd(),
                callCompletion(2, Serde.RAW, "{{".getBytes(StandardCharsets.UTF_8)))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("RawInputOutput", "rawOutput"), Serde.VOID, null),
                outputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                END_MESSAGE),
        testInvocation(RawInputOutput::new, "rawOutputWithCustomCT")
            .withInput(
                startMessage(1),
                inputCmd(),
                callCompletion(2, Serde.RAW, "{{".getBytes(StandardCharsets.UTF_8)))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawOutputWithCustomCT"),
                    Serde.VOID,
                    null),
                outputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                END_MESSAGE),
        testInvocation(CustomSerde::new, "greet")
            .withInput(startMessage(1), inputCmd(MySerdeFactory.SERDE, "input"))
            .expectingOutput(outputCmd(MySerdeFactory.SERDE, "OUTPUT"), END_MESSAGE));
  }
}
