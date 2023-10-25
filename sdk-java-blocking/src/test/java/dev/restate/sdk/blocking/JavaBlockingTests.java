package dev.restate.sdk.blocking;

import dev.restate.sdk.core.impl.MockMultiThreaded;
import dev.restate.sdk.core.impl.MockSingleThread;
import dev.restate.sdk.core.impl.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.impl.TestDefinitions.TestSuite;
import dev.restate.sdk.core.impl.TestRunner;
import java.util.stream.Stream;

public class JavaBlockingTests extends TestRunner {

  @Override
  protected Stream<TestExecutor> executors() {
    return Stream.of(MockSingleThread.INSTANCE, MockMultiThreaded.INSTANCE);
  }

  @Override
  protected Stream<TestSuite> definitions() {
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
        new GrpcChannelAdapterTest());
  }
}
