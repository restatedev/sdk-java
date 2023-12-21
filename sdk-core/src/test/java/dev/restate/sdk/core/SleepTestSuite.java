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
import static org.assertj.core.api.InstanceOfAssertFactories.LONG;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.Empty;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import io.grpc.BindableService;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class SleepTestSuite implements TestDefinitions.TestSuite {

  Long startTime = System.currentTimeMillis();

  protected abstract BindableService sleepGreeter();

  protected abstract BindableService manySleeps();

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(greetingRequest("Till")))
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites)
                      .element(0)
                      .asInstanceOf(type(Protocol.SleepEntryMessage.class))
                      .extracting(Protocol.SleepEntryMessage::getWakeUpTime, LONG)
                      .isGreaterThanOrEqualTo(startTime + 1000)
                      .isLessThanOrEqualTo(Instant.now().toEpochMilli() + 1000);

                  assertThat(messageLites)
                      .element(1)
                      .isInstanceOf(Protocol.SuspensionMessage.class);
                })
            .named("Sleep 1000 ms not completed"),
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(greetingRequest("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .setEmpty(Empty.getDefaultInstance())
                    .build())
            .expectingOutput(outputMessage(greetingResponse("Hello")), END_MESSAGE)
            .named("Sleep 1000 ms sleep completed"),
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(greetingRequest("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .build())
            .expectingOutput(suspensionMessage(1))
            .named("Sleep 1000 ms still sleeping"),
        testInvocation(this::manySleeps, GreeterGrpc.getGreetMethod())
            .withInput(
                Stream.concat(
                        Stream.of(startMessage(11), inputMessage(greetingRequest("Till"))),
                        IntStream.rangeClosed(1, 10)
                            .mapToObj(
                                i ->
                                    (i % 3 == 0)
                                        ? Protocol.SleepEntryMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .setEmpty(Empty.getDefaultInstance())
                                            .build()
                                        : Protocol.SleepEntryMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .build()))
                    .toArray(MessageLiteOrBuilder[]::new))
            .expectingOutput(suspensionMessage(1, 2, 4, 5, 7, 8, 10))
            .named("Sleep 1000 ms sleep completed"),
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(greetingRequest("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .setFailure(
                        Util.toProtocolFailure(TerminalException.Code.CANCELLED, "canceled"))
                    .build())
            .expectingOutput(
                outputMessage(TerminalException.Code.CANCELLED, "canceled"), END_MESSAGE)
            .named("Failed sleep"),
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(greetingRequest("Till")),
                completionMessage(
                    1, new TerminalException(TerminalException.Code.CANCELLED, "canceled")))
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites)
                      .element(0)
                      .isInstanceOf(Protocol.SleepEntryMessage.class);
                  assertThat(messageLites)
                      .element(1)
                      .isEqualTo(outputMessage(TerminalException.Code.CANCELLED, "canceled"));
                  assertThat(messageLites).element(2).isEqualTo(END_MESSAGE);
                })
            .named("Failing sleep"));
  }
}
