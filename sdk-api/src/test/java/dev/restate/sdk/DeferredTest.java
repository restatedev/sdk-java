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

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.DeferredTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

public class DeferredTest extends DeferredTestSuite {

  protected TestInvocationBuilder reverseAwaitOrder() {
    return testDefinitionForVirtualObject(
        "ReverseAwaitOrder",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (context, unused) -> {
          Awaitable<String> a1 = callGreeterGreetService(context, "Francesco");
          Awaitable<String> a2 = callGreeterGreetService(context, "Till");

          String a2Res = a2.await();
          context.set(StateKey.of("A2", CoreSerdes.JSON_STRING), a2Res);

          String a1Res = a1.await();

          return a1Res + "-" + a2Res;
        });
  }

  protected TestInvocationBuilder awaitTwiceTheSameAwaitable() {
    return testDefinitionForService(
        "AwaitTwiceTheSameAwaitable",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (context, unused) -> {
          Awaitable<String> a = callGreeterGreetService(context, "Francesco");

          return a.await() + "-" + a.await();
        });
  }

  protected TestInvocationBuilder awaitAll() {
    return testDefinitionForService(
        "AwaitAll",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (context, unused) -> {
          Awaitable<String> a1 = callGreeterGreetService(context, "Francesco");
          Awaitable<String> a2 = callGreeterGreetService(context, "Till");

          Awaitable.all(a1, a2).await();

          return a1.await() + "-" + a2.await();
        });
  }

  protected TestInvocationBuilder awaitAny() {
    return testDefinitionForService(
        "AwaitAny",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (context, unused) -> {
          Awaitable<String> a1 = callGreeterGreetService(context, "Francesco");
          Awaitable<String> a2 = callGreeterGreetService(context, "Till");

          return (String) Awaitable.any(a1, a2).await();
        });
  }

  protected TestInvocationBuilder combineAnyWithAll() {
    return testDefinitionForService(
        "CombineAnyWithAll",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> {
          Awaitable<String> a1 = ctx.awakeable(CoreSerdes.JSON_STRING);
          Awaitable<String> a2 = ctx.awakeable(CoreSerdes.JSON_STRING);
          Awaitable<String> a3 = ctx.awakeable(CoreSerdes.JSON_STRING);
          Awaitable<String> a4 = ctx.awakeable(CoreSerdes.JSON_STRING);

          Awaitable<Object> a12 = Awaitable.any(a1, a2);
          Awaitable<Object> a23 = Awaitable.any(a2, a3);
          Awaitable<Object> a34 = Awaitable.any(a3, a4);
          Awaitable.all(a12, a23, a34).await();

          return a12.await() + (String) a23.await() + a34.await();
        });
  }

  protected TestInvocationBuilder awaitAnyIndex() {
    return testDefinitionForService(
        "AwaitAnyIndex",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> {
          Awaitable<String> a1 = ctx.awakeable(CoreSerdes.JSON_STRING);
          Awaitable<String> a2 = ctx.awakeable(CoreSerdes.JSON_STRING);
          Awaitable<String> a3 = ctx.awakeable(CoreSerdes.JSON_STRING);
          Awaitable<String> a4 = ctx.awakeable(CoreSerdes.JSON_STRING);

          return String.valueOf(Awaitable.any(a1, Awaitable.all(a2, a3), a4).awaitIndex());
        });
  }

  protected TestInvocationBuilder awaitOnAlreadyResolvedAwaitables() {
    return testDefinitionForService(
        "AwaitOnAlreadyResolvedAwaitables",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> {
          Awaitable<String> a1 = ctx.awakeable(CoreSerdes.JSON_STRING);
          Awaitable<String> a2 = ctx.awakeable(CoreSerdes.JSON_STRING);

          Awaitable<Void> a12 = Awaitable.all(a1, a2);
          Awaitable<Void> a12and1 = Awaitable.all(a12, a1);
          Awaitable<Void> a121and12 = Awaitable.all(a12and1, a12);

          a12and1.await();
          a121and12.await();

          return a1.await() + a2.await();
        });
  }

  protected TestInvocationBuilder awaitWithTimeout() {
    return testDefinitionForService(
        "AwaitOnAlreadyResolvedAwaitables",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
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
