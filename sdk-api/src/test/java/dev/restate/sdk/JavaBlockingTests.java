// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.ProtoUtils.GREETER_SERVICE_TARGET;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.syscalls.HandlerSpecification;
import dev.restate.sdk.core.MockMultiThreaded;
import dev.restate.sdk.core.MockSingleThread;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.TestRunner;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class JavaBlockingTests extends TestRunner {

  @Override
  protected Stream<TestExecutor> executors() {
    return Stream.of(MockSingleThread.INSTANCE, MockMultiThreaded.INSTANCE);
  }

  @Override
  public Stream<TestSuite> definitions() {
    return Stream.of(
        new AwakeableIdTest(),
        new DeferredTest(),
        new EagerStateTest(),
        new StateTest(),
        new InvocationIdTest(),
        new OnlyInputAndOutputTest(),
        new SideEffectTest(),
        new SleepTest(),
        new StateMachineFailuresTest(),
        new UserFailuresTest(),
        new RandomTest());
  }

  public static <T, R> TestInvocationBuilder testDefinitionForService(
      String name, Serde<T> reqSerde, Serde<R> resSerde, BiFunction<Context, T, R> runner) {
    return TestDefinitions.testInvocation(
        Service.service(name)
            .with(HandlerSpecification.of("run", reqSerde, resSerde), runner)
            .build(Service.Options.DEFAULT),
        "run");
  }

  public static <T, R> TestInvocationBuilder testDefinitionForVirtualObject(
      String name, Serde<T> reqSerde, Serde<R> resSerde, BiFunction<ObjectContext, T, R> runner) {
    return TestDefinitions.testInvocation(
        Service.virtualObject(name)
            .withExclusive(HandlerSpecification.of("run", reqSerde, resSerde), runner)
            .build(Service.Options.DEFAULT),
        "run");
  }

  public static Awaitable<String> callGreeterGreetService(Context ctx, String parameter) {
    return ctx.call(
        GREETER_SERVICE_TARGET, CoreSerdes.JSON_STRING, CoreSerdes.JSON_STRING, parameter);
  }
}
