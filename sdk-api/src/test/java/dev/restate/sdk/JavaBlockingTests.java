// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.core.MockMultiThreaded;
import dev.restate.sdk.core.MockSingleThread;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.TestRunner;
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
        new AwaitableTest(),
        new EagerStateTest(),
        new StateTest(),
        new InvocationIdTest(),
        new OnlyInputAndOutputTest(),
        new SideEffectTest(),
        new SleepTest(),
        new StateMachineFailuresTest(),
        new UserFailuresTest(),
        new GrpcChannelAdapterTest(),
        new RestateCodegenTest(),
        new RandomTest());
  }
}
