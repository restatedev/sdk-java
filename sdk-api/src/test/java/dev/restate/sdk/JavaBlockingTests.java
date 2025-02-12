// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.statemachine.ProtoUtils.GREETER_SERVICE_TARGET;

import dev.restate.common.function.ThrowingBiFunction;
import dev.restate.sdk.core.MockBidiStream;
import dev.restate.sdk.core.MockRequestResponse;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.TestRunner;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.serde.Serde;
import java.util.List;
import java.util.stream.Stream;

public class JavaBlockingTests extends TestRunner {

  @Override
  protected Stream<TestExecutor> executors() {
    return Stream.of(MockRequestResponse.INSTANCE, MockBidiStream.INSTANCE);
  }

  @Override
  public Stream<TestSuite> definitions() {
    return Stream.of(
        new AwakeableIdTest(),
        new AsyncResultTest(),
        new CallTest(),
        new EagerStateTest(),
        new StateTest(),
        new InvocationIdTest(),
        new OnlyInputAndOutputTest(),
        new PromiseTest(),
        new SideEffectTest(),
        new SleepTest(),
        new StateMachineFailuresTest(),
        new UserFailuresTest(),
        new RandomTest());
  }

  public static <T, R> TestInvocationBuilder testDefinitionForService(
      String name, Serde<T> reqSerde, Serde<R> resSerde, ThrowingBiFunction<Context, T, R> runner) {
    return TestDefinitions.testInvocation(
        ServiceDefinition.of(
            name,
            ServiceType.SERVICE,
            List.of(
                HandlerDefinition.of(
                    "run", HandlerType.SHARED, reqSerde, resSerde, HandlerRunner.of(runner)))),
        "run");
  }

  public static <T, R> TestInvocationBuilder testDefinitionForVirtualObject(
      String name,
      Serde<T> reqSerde,
      Serde<R> resSerde,
      ThrowingBiFunction<ObjectContext, T, R> runner) {
    return TestDefinitions.testInvocation(
        ServiceDefinition.of(
            name,
            ServiceType.VIRTUAL_OBJECT,
            List.of(
                HandlerDefinition.of(
                    "run", HandlerType.EXCLUSIVE, reqSerde, resSerde, HandlerRunner.of(runner)))),
        "run");
  }

  public static <T, R> TestInvocationBuilder testDefinitionForWorkflow(
      String name,
      Serde<T> reqSerde,
      Serde<R> resSerde,
      ThrowingBiFunction<WorkflowContext, T, R> runner) {
    return TestDefinitions.testInvocation(
        ServiceDefinition.of(
            name,
            ServiceType.WORKFLOW,
            List.of(
                HandlerDefinition.of(
                    "run", HandlerType.WORKFLOW, reqSerde, resSerde, HandlerRunner.of(runner)))),
        "run");
  }

  public static Awaitable<String> callGreeterGreetService(Context ctx, String parameter) {
    return ctx.call(GREETER_SERVICE_TARGET, JsonSerdes.STRING, JsonSerdes.STRING, parameter);
  }
}
