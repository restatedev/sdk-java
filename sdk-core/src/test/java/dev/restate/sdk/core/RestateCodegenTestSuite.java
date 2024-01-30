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
import static dev.restate.sdk.core.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.Empty;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.testservices.*;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class RestateCodegenTestSuite implements TestSuite {

  protected abstract BindableService greeterWithRestateClientAndServerCodegen();

  protected abstract BindableService codegen();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::greeterWithRestateClientAndServerCodegen, GreeterGrpc.getGreetMethod())
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
                      .elements(1, 2, 3, 4)
                      .containsExactly(
                          backgroundInvokeMessage(
                                  GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build(),
                          invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build(),
                          outputMessage(greetingResponse("Till")),
                          END_MESSAGE);
                }),
        testInvocation(this::codegen, CodegenGrpc.getEmptyInputMethod())
            .withInput(
                startMessage(1),
                inputMessage(Empty.getDefaultInstance()),
                completionMessage(1, MyMessage.newBuilder().setValue("Francesco")))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(CodegenGrpc.getEmptyInputMethod(), Empty.getDefaultInstance()),
                outputMessage(MyMessage.newBuilder().setValue("Francesco")),
                END_MESSAGE)
            .named("Check Codegen::EmptyInput method is correctly generated"),
        testInvocation(this::codegen, CodegenGrpc.getEmptyOutputMethod())
            .withInput(
                startMessage(1),
                inputMessage(MyMessage.newBuilder().setValue("Francesco")),
                completionMessage(1, Empty.getDefaultInstance()))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    CodegenGrpc.getEmptyOutputMethod(),
                    MyMessage.newBuilder().setValue("Francesco").build()),
                outputMessage(Empty.getDefaultInstance()),
                END_MESSAGE)
            .named("Check Codegen::EmptyOutput method is correctly generated"),
        testInvocation(this::codegen, CodegenGrpc.getEmptyInputOutputMethod())
            .withInput(
                startMessage(1),
                inputMessage(Empty.getDefaultInstance()),
                completionMessage(1, Empty.getDefaultInstance()))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(CodegenGrpc.getEmptyInputOutputMethod(), Empty.getDefaultInstance()),
                outputMessage(Empty.getDefaultInstance()),
                END_MESSAGE)
            .named("Check Codegen::EmptyInputOutput method is correctly generated"),
        testInvocation(this::codegen, CodegenGrpc.getOneWayMethod())
            .withInput(startMessage(1), inputMessage(MyMessage.newBuilder().setValue("Francesco")))
            .expectingOutput(
                invokeMessage(
                    CodegenGrpc.getOneWayMethod(),
                    MyMessage.newBuilder().setValue("Francesco").build()),
                suspensionMessage(1))
            .named("Check Codegen::OneWay method is correctly generated"),
        testInvocation(this::codegen, CodegenGrpc.getDelayedMethod())
            .withInput(startMessage(1), inputMessage(MyMessage.newBuilder().setValue("Francesco")))
            .expectingOutput(
                invokeMessage(
                    CodegenGrpc.getDelayedMethod(),
                    MyMessage.newBuilder().setValue("Francesco").build()),
                suspensionMessage(1))
            .named("Check Codegen::Delayed method is correctly generated"));
  }
}
