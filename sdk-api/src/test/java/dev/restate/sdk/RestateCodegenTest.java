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
import static dev.restate.sdk.core.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.Empty;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.testservices.*;
import java.time.Duration;
import java.util.stream.Stream;

public class RestateCodegenTest implements TestSuite {

  private static class GreeterWithRestateClientAndServerCodegen
      extends GreeterRestate.GreeterRestateImplBase {

    @Override
    public GreetingResponse greet(RestateContext context, GreetingRequest request) {
      GreeterRestate.GreeterRestateClient client = GreeterRestate.newClient(context);
      client.delayed(Duration.ofSeconds(1)).greet(request);
      client.oneWay().greet(request);
      return client.greet(request).await();
    }
  }

  private static class Codegen extends CodegenRestate.CodegenRestateImplBase {

    @Override
    public MyMessage emptyInput(RestateContext context) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      return client.emptyInput().await();
    }

    @Override
    public void emptyOutput(RestateContext context, MyMessage request) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      client.emptyOutput(request).await();
    }

    @Override
    public void emptyInputOutput(RestateContext context) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      client.emptyInputOutput().await();
    }

    @Override
    public MyMessage oneWay(RestateContext context, MyMessage request) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      return client.callOneWay(request).await();
    }

    @Override
    public MyMessage delayed(RestateContext context, MyMessage request) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      return client.callDelayed(request).await();
    }
  }

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new GreeterWithRestateClientAndServerCodegen(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(greetingRequest("Francesco")),
                completionMessage(3, greetingResponse("Till")))
            .onlyUnbuffered()
            .assertingOutput(
                msgs -> {
                  assertThat(msgs)
                      .element(0)
                      .asInstanceOf(type(Protocol.BackgroundInvokeEntryMessage.class))
                      .satisfies(
                          backgroundInvokeEntryMessage -> {
                            // Check invoke time is non zero
                            assertThat(backgroundInvokeEntryMessage.getInvokeTime())
                                .isGreaterThan(0);
                            // Check background invoke header
                            assertThat(
                                    backgroundInvokeEntryMessage.toBuilder()
                                        .clearInvokeTime()
                                        .build())
                                .isEqualTo(
                                    backgroundInvokeMessage(
                                            GreeterGrpc.getGreetMethod(),
                                            greetingRequest("Francesco"))
                                        .build());
                          });
                  assertThat(msgs)
                      .elements(1, 2, 3)
                      .containsExactly(
                          backgroundInvokeMessage(
                                  GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build(),
                          invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build(),
                          outputMessage(greetingResponse("Till")));
                }),
        testInvocation(new Codegen(), CodegenGrpc.getEmptyInputMethod())
            .withInput(
                startMessage(1),
                inputMessage(Empty.getDefaultInstance()),
                completionMessage(1, MyMessage.newBuilder().setValue("Francesco")))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(CodegenGrpc.getEmptyInputMethod(), Empty.getDefaultInstance()),
                outputMessage(MyMessage.newBuilder().setValue("Francesco")))
            .named("Check Codegen::EmptyInput method is correctly generated"),
        testInvocation(new Codegen(), CodegenGrpc.getEmptyOutputMethod())
            .withInput(
                startMessage(1),
                inputMessage(MyMessage.newBuilder().setValue("Francesco")),
                completionMessage(1, Empty.getDefaultInstance()))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    CodegenGrpc.getEmptyOutputMethod(),
                    MyMessage.newBuilder().setValue("Francesco").build()),
                outputMessage(Empty.getDefaultInstance()))
            .named("Check Codegen::EmptyOutput method is correctly generated"),
        testInvocation(new Codegen(), CodegenGrpc.getEmptyInputOutputMethod())
            .withInput(
                startMessage(1),
                inputMessage(Empty.getDefaultInstance()),
                completionMessage(1, Empty.getDefaultInstance()))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(CodegenGrpc.getEmptyInputOutputMethod(), Empty.getDefaultInstance()),
                outputMessage(Empty.getDefaultInstance()))
            .named("Check Codegen::EmptyInputOutput method is correctly generated"),
        testInvocation(new Codegen(), CodegenGrpc.getOneWayMethod())
            .withInput(startMessage(1), inputMessage(MyMessage.newBuilder().setValue("Francesco")))
            .expectingOutput(
                invokeMessage(
                    CodegenGrpc.getOneWayMethod(),
                    MyMessage.newBuilder().setValue("Francesco").build()),
                suspensionMessage(1))
            .named("Check Codegen::OneWay method is correctly generated"),
        testInvocation(new Codegen(), CodegenGrpc.getDelayedMethod())
            .withInput(startMessage(1), inputMessage(MyMessage.newBuilder().setValue("Francesco")))
            .expectingOutput(
                invokeMessage(
                    CodegenGrpc.getDelayedMethod(),
                    MyMessage.newBuilder().setValue("Francesco").build()),
                suspensionMessage(1))
            .named("Check Codegen::Delayed method is correctly generated"));
  }
}
