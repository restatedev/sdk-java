// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Empty;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class SleepTestSuite implements TestSuite {

  Long startTime = System.currentTimeMillis();

  protected abstract BindableService sleepGreeter();

  protected abstract BindableService manySleeps();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites.get(0)).isInstanceOf(Protocol.SleepEntryMessage.class);
                  Protocol.SleepEntryMessage msg = (Protocol.SleepEntryMessage) messageLites.get(0);
                  assertThat(msg.getWakeUpTime()).isGreaterThanOrEqualTo(startTime + 1000);
                  assertThat(msg.getWakeUpTime())
                      .isLessThanOrEqualTo(Instant.now().toEpochMilli() + 1000);
                  assertThat(messageLites.get(1)).isInstanceOf(Protocol.SuspensionMessage.class);
                })
            .named("Sleep 1000 ms not completed"),
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .setResult(Empty.getDefaultInstance())
                    .build())
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello").build()))
            .named("Sleep 1000 ms sleep completed"),
        testInvocation(this::sleepGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .build())
            .expectingOutput(suspensionMessage(1))
            .named("Sleep 1000 ms still sleeping"),
        testInvocation(this::manySleeps, GreeterGrpc.getGreetMethod())
            .withInput(
                Stream.concat(
                        Stream.of(
                            startMessage(11),
                            inputMessage(GreetingRequest.newBuilder().setName("Till"))),
                        IntStream.rangeClosed(1, 10)
                            .mapToObj(
                                i ->
                                    (i % 3 == 0)
                                        ? Protocol.SleepEntryMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .setResult(Empty.getDefaultInstance())
                                            .build()
                                        : Protocol.SleepEntryMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .build()))
                    .toArray(MessageLiteOrBuilder[]::new))
            .expectingOutput(suspensionMessage(1, 2, 4, 5, 7, 8, 10))
            .named("Sleep 1000 ms sleep completed"));
  }
}
