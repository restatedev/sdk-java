package dev.restate.sdk.core.impl;

import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.restate.sdk.core.impl.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.impl.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.impl.TestDefinitions.TestSuite;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestRunner {

  protected abstract Stream<TestExecutor> executors();

  protected abstract Stream<TestSuite> definitions();

  final Stream<Arguments> source() {
    List<TestExecutor> executors = executors().collect(Collectors.toList());

    return definitions()
        .flatMap(ts -> ts.definitions().map(def -> entry(ts.getClass().getName(), def)))
        .filter(e -> e.getValue().isValid())
        .flatMap(
            entry ->
                executors.stream()
                    .filter(
                        executor -> !entry.getValue().isOnlyUnbuffered() || !executor.buffered())
                    .map(
                        executor ->
                            arguments(
                                "["
                                    + executor
                                    + "]["
                                    + entry.getKey()
                                    + "] "
                                    + entry.getValue().getTestCaseName(),
                                executor,
                                entry.getValue())));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("source")
  @Execution(ExecutionMode.CONCURRENT)
  void executeTest(String testName, TestExecutor executor, TestDefinition definition) {
    executor.executeTest(definition);
  }
}
