// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static dev.restate.sdk.core.statemachine.ProtoUtils.END_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.common.Slice;
import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.serde.Serde;
import dev.restate.serde.jackson.JacksonSerdeFactory;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;

public class SerdeThreadTrampoliningTest implements TestDefinitions.TestSuite {

  private static final ThreadLocal<String> THREAD_LOCAL = new ThreadLocal<>();

  private static final StateKey<String> STATE =
      StateKey.of(
          "STATE",
          new Serde<String>() {
            @Override
            public Slice serialize(String value) {
              throw new IllegalStateException("Unexpected call to serialize");
            }

            @Override
            public String deserialize(@NonNull Slice value) {
              assertThreadLocal();
              return TestSerdes.STRING.deserialize(value);
            }
          });

  private static void assertThreadLocal() {
    assertThat(THREAD_LOCAL.get()).isEqualTo("UserThread");
  }

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    Executor executor = Executors.newCachedThreadPool();
    Executor wrappedExecutor =
        runnable ->
            executor.execute(
                () -> {
                  THREAD_LOCAL.set("UserThread");
                  try {
                    runnable.run();
                  } finally {
                    THREAD_LOCAL.remove();
                  }
                });

    return Stream.of(
        TestDefinitions.testInvocation(
                ServiceDefinition.of(
                    "SerdeThreadTrampolining",
                    ServiceType.VIRTUAL_OBJECT,
                    List.of(
                        HandlerDefinition.of(
                            "run",
                            HandlerType.EXCLUSIVE,
                            Serde.VOID,
                            TestSerdes.STRING,
                            HandlerRunner.of(
                                (ctx, unused) -> {
                                  assertThreadLocal();
                                  String result = ((ObjectContext) ctx).get(STATE).get();
                                  assertThreadLocal();
                                  return result;
                                },
                                new JacksonSerdeFactory(),
                                new HandlerRunner.Options().setExecutor(wrappedExecutor))))),
                "run")
            .withInput(
                startMessage(2), inputCmd("Francesco"), getEagerStateCmd(STATE.name(), "Value"))
            .expectingOutput(outputCmd("Value"), END_MESSAGE));
  }
}
