// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.TestAbortedException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestRunner {

  protected abstract Stream<TestExecutor> executors();

  protected abstract Stream<TestSuite> definitions();

  final Stream<Arguments> source() {
    List<TestExecutor> executors = executors().toList();

    return definitions()
        .flatMap(ts -> ts.definitions().map(def -> entry(ts.getClass().getName(), def)))
        .flatMap(
            entry ->
                executors.stream()
                    .filter(
                        executor -> !entry.getValue().isOnlyUnbuffered() || !executor.buffered())
                    .map(
                        executor ->
                            arguments(
                                "["
                                    + executor.getClass().getSimpleName()
                                    + "]["
                                    + entry.getKey()
                                    + "] "
                                    + entry.getValue().getTestCaseName(),
                                executor,
                                entry.getValue())));
  }

  private static class DisableInvalidTestDefinition implements InvocationInterceptor {

    @Override
    public void interceptTestTemplateMethod(
        Invocation<Void> invocation,
        ReflectiveInvocationContext<Method> invocationContext,
        ExtensionContext extensionContext)
        throws Throwable {
      Method testMethod = extensionContext.getRequiredTestMethod();
      List<Object> arguments = invocationContext.getArguments();
      if (arguments.isEmpty()) {
        throw new ExtensionConfigurationException(
            format(
                "Can't disable based on arguments, because method %s had no parameters.",
                testMethod.getName()));
      }

      Object maybeTestDefinition = arguments.get(2);
      if (!(maybeTestDefinition instanceof TestDefinition)) {
        throw new ExtensionConfigurationException(
            format(
                "Expected second argument to be a TestDefinition, but is %s.",
                maybeTestDefinition));
      }

      if (!((TestDefinition) maybeTestDefinition).isValid()) {
        throw new TestAbortedException(
            "Disabled test definition: "
                + ((TestDefinition) maybeTestDefinition).getInvalidReason());
      }
      invocation.proceed();
    }
  }

  @ExtendWith(DisableInvalidTestDefinition.class)
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("source")
  @Execution(ExecutionMode.CONCURRENT)
  void executeTest(String testName, TestExecutor executor, TestDefinition definition) {
    executor.executeTest(definition);
  }
}
