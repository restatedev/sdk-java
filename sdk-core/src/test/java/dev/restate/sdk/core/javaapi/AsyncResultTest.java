// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.javaapi.JavaAPITests.*;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.DurableFuture;
import dev.restate.sdk.Select;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TimeoutException;
import dev.restate.sdk.core.AsyncResultTestSuite;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.serde.Serde;
import java.time.Duration;
import java.util.stream.Stream;

public class AsyncResultTest extends AsyncResultTestSuite {

  @Override
  protected TestInvocationBuilder reverseAwaitOrder() {
    return testDefinitionForVirtualObject(
        "ReverseAwaitOrder",
        Serde.VOID,
        TestSerdes.STRING,
        (context, unused) -> {
          DurableFuture<String> a1 = callGreeterGreetService(context, "Francesco");
          DurableFuture<String> a2 = callGreeterGreetService(context, "Till");

          String a2Res = a2.await();
          context.set(StateKey.of("A2", TestSerdes.STRING), a2Res);

          String a1Res = a1.await();

          return a1Res + "-" + a2Res;
        });
  }

  @Override
  protected TestInvocationBuilder awaitTwiceTheSameAwaitable() {
    return testDefinitionForService(
        "AwaitTwiceTheSameAwaitable",
        Serde.VOID,
        TestSerdes.STRING,
        (context, unused) -> {
          DurableFuture<String> a = callGreeterGreetService(context, "Francesco");

          return a.await() + "-" + a.await();
        });
  }

  @Override
  protected TestInvocationBuilder awaitAll() {
    return testDefinitionForService(
        "AwaitAll",
        Serde.VOID,
        TestSerdes.STRING,
        (context, unused) -> {
          DurableFuture<String> a1 = callGreeterGreetService(context, "Francesco");
          DurableFuture<String> a2 = callGreeterGreetService(context, "Till");

          DurableFuture.all(a1, a2).await();

          return a1.await() + "-" + a2.await();
        });
  }

  @Override
  protected TestInvocationBuilder awaitAny() {
    return testDefinitionForService(
        "AwaitAny",
        Serde.VOID,
        TestSerdes.STRING,
        (context, unused) -> {
          DurableFuture<String> a1 = callGreeterGreetService(context, "Francesco");
          DurableFuture<String> a2 = callGreeterGreetService(context, "Till");

          return Select.<String>select().or(a1).or(a2).await();
        });
  }

  @Override
  protected TestInvocationBuilder combineAnyWithAll() {
    return testDefinitionForService(
        "CombineAnyWithAll",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> {
          DurableFuture<String> a1 = ctx.awakeable(String.class);
          DurableFuture<String> a2 = ctx.awakeable(String.class);
          DurableFuture<String> a3 = ctx.awakeable(String.class);
          DurableFuture<String> a4 = ctx.awakeable(String.class);

          DurableFuture<String> a12 = Select.<String>select().or(a1).or(a2);
          DurableFuture<String> a23 = Select.<String>select().or(a2).or(a3);
          DurableFuture<String> a34 = Select.<String>select().or(a3).or(a4);
          DurableFuture<String> result =
              DurableFuture.all(a12, a23, a34).map(v -> a12.await() + a23.await() + a34.await());

          return result.await();
        });
  }

  @Override
  protected TestInvocationBuilder awaitAnyIndex() {
    return testDefinitionForService(
        "AwaitAnyIndex",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> {
          DurableFuture<String> a1 = ctx.awakeable(String.class);
          DurableFuture<String> a2 = ctx.awakeable(String.class);
          DurableFuture<String> a3 = ctx.awakeable(String.class);
          DurableFuture<String> a4 = ctx.awakeable(String.class);

          return String.valueOf(DurableFuture.any(a1, DurableFuture.all(a2, a3), a4).await());
        });
  }

  @Override
  protected TestInvocationBuilder awaitOnAlreadyResolvedAwaitables() {
    return testDefinitionForService(
        "AwaitOnAlreadyResolvedAwaitables",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> {
          DurableFuture<String> a1 = ctx.awakeable(String.class);
          DurableFuture<String> a2 = ctx.awakeable(String.class);

          DurableFuture<Void> a12 = DurableFuture.all(a1, a2);
          DurableFuture<Void> a12and1 = DurableFuture.all(a12, a1);
          DurableFuture<Void> a121and12 = DurableFuture.all(a12and1, a12);

          a12and1.await();
          a121and12.await();

          return a1.await() + a2.await();
        });
  }

  @Override
  protected TestInvocationBuilder awaitWithTimeout() {
    return testDefinitionForService(
        "AwaitWithTimeout",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> {
          DurableFuture<String> call = callGreeterGreetService(ctx, "Francesco");

          String result;
          try {
            result = call.await(Duration.ofDays(1));
          } catch (TimeoutException e) {
            result = "timeout";
          }

          return result;
        });
  }

  private TestInvocationBuilder checkAwaitableMapThread() {
    return testDefinitionForService(
        "CheckAwaitableThread",
        Serde.VOID,
        Serde.VOID,
        (ctx, unused) -> {
          var currentThreadName = Thread.currentThread().getName().split("-");
          var currentThreadPool = currentThreadName[0] + "-" + currentThreadName[1];

          callGreeterGreetService(ctx, "Francesco")
              .map(
                  u -> {
                    assertThat(Thread.currentThread().getName()).startsWith(currentThreadPool);
                    return null;
                  })
              .await();

          return null;
        });
  }

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.concat(
        super.definitions(),
        Stream.of(
            this.checkAwaitableMapThread()
                .withInput(
                    startMessage(3),
                    inputCmd(),
                    callCmd(1, 2, GREETER_SERVICE_TARGET, "Francesco"),
                    callCompletion(2, "FRANCESCO"))
                .onlyBidiStream()
                .expectingOutput(outputCmd(), END_MESSAGE)
                .named("Check map constraints")));
  }
}
