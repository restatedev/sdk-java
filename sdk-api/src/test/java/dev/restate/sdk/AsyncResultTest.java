// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.JavaBlockingTests.*;

import dev.restate.sdk.core.AsyncResultTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.types.StateKey;
import dev.restate.sdk.types.TimeoutException;
import dev.restate.serde.Serde;
import java.time.Duration;

public class AsyncResultTest extends AsyncResultTestSuite {

  @Override
  protected TestInvocationBuilder reverseAwaitOrder() {
    return testDefinitionForVirtualObject(
        "ReverseAwaitOrder",
        Serde.VOID,
        JsonSerdes.STRING,
        (context, unused) -> {
          Awaitable<String> a1 = callGreeterGreetService(context, "Francesco");
          Awaitable<String> a2 = callGreeterGreetService(context, "Till");

          String a2Res = a2.await();
          context.set(StateKey.of("A2", JsonSerdes.STRING), a2Res);

          String a1Res = a1.await();

          return a1Res + "-" + a2Res;
        });
  }

  @Override
  protected TestInvocationBuilder awaitTwiceTheSameAwaitable() {
    return testDefinitionForService(
        "AwaitTwiceTheSameAwaitable",
        Serde.VOID,
        JsonSerdes.STRING,
        (context, unused) -> {
          Awaitable<String> a = callGreeterGreetService(context, "Francesco");

          return a.await() + "-" + a.await();
        });
  }

  @Override
  protected TestInvocationBuilder awaitAll() {
    return testDefinitionForService(
        "AwaitAll",
        Serde.VOID,
        JsonSerdes.STRING,
        (context, unused) -> {
          Awaitable<String> a1 = callGreeterGreetService(context, "Francesco");
          Awaitable<String> a2 = callGreeterGreetService(context, "Till");

          Awaitable.all(a1, a2).await();

          return a1.await() + "-" + a2.await();
        });
  }

  @Override
  protected TestInvocationBuilder awaitAny() {
    return testDefinitionForService(
        "AwaitAny",
        Serde.VOID,
        JsonSerdes.STRING,
        (context, unused) -> {
          Awaitable<String> a1 = callGreeterGreetService(context, "Francesco");
          Awaitable<String> a2 = callGreeterGreetService(context, "Till");

          var anyRes = Awaitable.any(a1, a2).await();
          return (anyRes == 0) ? a1.await() : a2.await();
        });
  }

  @Override
  protected TestInvocationBuilder combineAnyWithAll() {
    return testDefinitionForService(
        "CombineAnyWithAll",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          Awaitable<String> a1 = ctx.awakeable(JsonSerdes.STRING);
          Awaitable<String> a2 = ctx.awakeable(JsonSerdes.STRING);
          Awaitable<String> a3 = ctx.awakeable(JsonSerdes.STRING);
          Awaitable<String> a4 = ctx.awakeable(JsonSerdes.STRING);

          Awaitable<String> a12 = Awaitable.any(a1, a2).map(i -> i == 0 ? a1.await() : a2.await());
          Awaitable<String> a23 = Awaitable.any(a2, a3).map(i -> i == 0 ? a2.await() : a3.await());
          Awaitable<String> a34 = Awaitable.any(a3, a4).map(i -> i == 0 ? a3.await() : a4.await());
          Awaitable.all(a12, a23, a34).await();

          return a12.await() + a23.await() + a34.await();
        });
  }

  @Override
  protected TestInvocationBuilder awaitAnyIndex() {
    return testDefinitionForService(
        "AwaitAnyIndex",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          Awaitable<String> a1 = ctx.awakeable(JsonSerdes.STRING);
          Awaitable<String> a2 = ctx.awakeable(JsonSerdes.STRING);
          Awaitable<String> a3 = ctx.awakeable(JsonSerdes.STRING);
          Awaitable<String> a4 = ctx.awakeable(JsonSerdes.STRING);

          return String.valueOf(Awaitable.any(a1, Awaitable.all(a2, a3), a4).await());
        });
  }

  @Override
  protected TestInvocationBuilder awaitOnAlreadyResolvedAwaitables() {
    return testDefinitionForService(
        "AwaitOnAlreadyResolvedAwaitables",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          Awaitable<String> a1 = ctx.awakeable(JsonSerdes.STRING);
          Awaitable<String> a2 = ctx.awakeable(JsonSerdes.STRING);

          Awaitable<Void> a12 = Awaitable.all(a1, a2);
          Awaitable<Void> a12and1 = Awaitable.all(a12, a1);
          Awaitable<Void> a121and12 = Awaitable.all(a12and1, a12);

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
        JsonSerdes.STRING,
        (ctx, unused) -> {
          Awaitable<String> call = callGreeterGreetService(ctx, "Francesco");

          String result;
          try {
            result = call.await(Duration.ofDays(1));
          } catch (TimeoutException e) {
            result = "timeout";
          }

          return result;
        });
  }
}
