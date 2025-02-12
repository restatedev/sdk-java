// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LONG;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class SleepTestSuite implements TestDefinitions.TestSuite {

  final Long startTime = System.currentTimeMillis();

  protected abstract TestInvocationBuilder sleepGreeter();

  protected abstract TestInvocationBuilder manySleeps();

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        this.sleepGreeter()
            .withInput(startMessage(1), inputCmd("Till"))
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites)
                      .element(0)
                      .asInstanceOf(type(Protocol.SleepCommandMessage.class))
                      .extracting(Protocol.SleepCommandMessage::getWakeUpTime, LONG)
                      .isGreaterThanOrEqualTo(startTime + 1000)
                      .isLessThanOrEqualTo(Instant.now().toEpochMilli() + 1000);

                  assertThat(messageLites)
                      .element(1)
                      .isInstanceOf(Protocol.SuspensionMessage.class);
                })
            .named("Sleep 1000 ms not completed"),
        this.sleepGreeter()
            .withInput(
                startMessage(2),
                inputCmd("Till"),
                Protocol.SleepCommandMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .setResultCompletionId(1),
                Protocol.SleepCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setVoid(Protocol.Void.getDefaultInstance()))
            .expectingOutput(outputCmd("Hello"), END_MESSAGE)
            .named("Sleep 1000 ms sleep completed"),
        this.sleepGreeter()
            .withInput(
                startMessage(2),
                inputCmd("Till"),
                Protocol.SleepCommandMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .build())
            .expectingOutput(suspensionMessage(1))
            .named("Sleep 1000 ms still sleeping"),
        this.manySleeps()
            .withInput(
                Stream.concat(
                    Stream.of(startMessage(14), inputCmd("Till")),
                    IntStream.rangeClosed(1, 10)
                        .mapToObj(
                            i ->
                                (i % 3 == 0)
                                    ? Stream.<MessageLiteOrBuilder>of(
                                        Protocol.SleepCommandMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .setResultCompletionId(i),
                                        Protocol.SleepCompletionNotificationMessage.newBuilder()
                                            .setCompletionId(i)
                                            .setVoid(Protocol.Void.getDefaultInstance()))
                                    : Stream.<MessageLiteOrBuilder>of(
                                        Protocol.SleepCommandMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .setResultCompletionId(i)))
                        .flatMap(Function.identity())))
            .expectingOutput(suspensionMessage(1, 2, 4, 5, 7, 8, 10))
            .named("Sleep 1000 ms sleep completed"),
        this.sleepGreeter()
            .withInput(
                startMessage(1),
                inputCmd("Till"),
                Protocol.SleepCompletionNotificationMessage.newBuilder()
                    .setCompletionId(1)
                    .setVoid(Protocol.Void.getDefaultInstance()))
            .onlyBidiStream()
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites)
                      .element(0)
                      .isInstanceOf(Protocol.SleepCommandMessage.class);
                  assertThat(messageLites).element(1).isEqualTo(outputCmd("Hello"));
                  assertThat(messageLites).element(2).isEqualTo(END_MESSAGE);
                })
            .named("Failing sleep"));
  }
}
