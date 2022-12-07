package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.MessageLite;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CoreTestRunner {

  abstract Stream<TestDefinition> definitions();

  Stream<Arguments> source() {
    return definitions()
        .flatMap(
            c ->
                c.getThreadingModels().stream()
                    .map(
                        threadingModel ->
                            arguments(
                                "[" + threadingModel + "] " + c.testCaseName(),
                                c.getService(),
                                c.getMethod(),
                                c.getInput(),
                                threadingModel,
                                c.getExpectedOutput())));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("source")
  void executeTest(
      String testName,
      ServerServiceDefinition svc,
      String method,
      List<MessageLite> input,
      ThreadingModel threadingModel,
      List<MessageLite> expectedOutput) {
    Executor syscallsExecutor =
        threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD
            ? Executors.newSingleThreadExecutor()
            : Runnable::run;
    Executor userExecutor =
        threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD
            ? Executors.newSingleThreadExecutor()
            : Runnable::run;

    FlowUtils.FutureSubscriber<MessageLite> outputSubscriber = new FlowUtils.FutureSubscriber<>();
    FlowUtils.MockSubscription inputSubscription = new FlowUtils.MockSubscription();

    // Start invocation
    RestateGrpcServer server = RestateGrpcServer.newBuilder().withService(svc).build();
    InvocationHandler handler =
        server.resolve(
            svc.getServiceDescriptor().getName(),
            method,
            io.opentelemetry.context.Context.current(),
            threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD
                ? TrampolineFactories.syscalls(syscallsExecutor)
                : Function.identity(),
            threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD
                ? TrampolineFactories.serverCallListener(userExecutor)
                : Function.identity());

    if (threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD) {
      // Wire invocation and start it
      syscallsExecutor.execute(
          () -> {
            handler.processor().subscribe(outputSubscriber);
            handler.processor().onSubscribe(inputSubscription);
            handler.start();
          });

      // Pipe entries
      for (MessageLite inputEntry : input) {
        syscallsExecutor.execute(() -> handler.processor().onNext(inputEntry));
      }
      // Complete the input publisher
      syscallsExecutor.execute(() -> handler.processor().onComplete());
    } else {
      // Wire invocation
      handler.processor().subscribe(outputSubscriber);
      handler.processor().onSubscribe(inputSubscription);

      // Pipe entries and complete the input publisher
      FlowUtils.pipeAndComplete(handler.processor(), input.toArray(MessageLite[]::new));

      // Start invocation
      handler.start();
    }

    Duration futureWaitTime =
        threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD
            ? Duration.ofSeconds(1)
            : Duration.ZERO;

    // Check completed
    assertThat(outputSubscriber.getFuture())
        .succeedsWithin(futureWaitTime)
        .isEqualTo(expectedOutput);
    assertThat(inputSubscription.isCancelled()).isTrue();
  }

  enum ThreadingModel {
    BUFFERED_SINGLE_THREAD,
    UNBUFFERED_MULTI_THREAD
  }

  interface TestDefinition {
    ServerServiceDefinition getService();

    String getMethod();

    List<MessageLite> getInput();

    HashSet<ThreadingModel> getThreadingModels();

    List<MessageLite> getExpectedOutput();

    String testCaseName();
  }

  /** Builder for the test cases */
  static class TestCaseBuilder {

    static TestInvocationBuilder testInvocation(ServerServiceDefinition svc, String method) {
      return new TestInvocationBuilder(svc, method);
    }

    static TestInvocationBuilder testInvocation(BindableService svc, String method) {
      return testInvocation(svc.bindService(), method);
    }

    static TestInvocationBuilder testInvocation(
        ServerServiceDefinition svc, MethodDescriptor<?, ?> method) {
      return testInvocation(svc, method.getBareMethodName());
    }

    static TestInvocationBuilder testInvocation(
        BindableService svc, MethodDescriptor<?, ?> method) {
      return testInvocation(svc.bindService(), method);
    }

    static class TestInvocationBuilder {
      private final ServerServiceDefinition svc;
      private final String method;

      TestInvocationBuilder(ServerServiceDefinition svc, String method) {
        this.svc = svc;
        this.method = method;
      }

      WithInputBuilder withInput(MessageLite... messages) {
        return new WithInputBuilder(svc, method, Arrays.asList(messages));
      }
    }

    static class WithInputBuilder {
      private final ServerServiceDefinition svc;
      private final String method;
      private final List<MessageLite> input;

      WithInputBuilder(ServerServiceDefinition svc, String method, List<MessageLite> input) {
        this.svc = svc;
        this.method = method;
        this.input = input;
      }

      UsingThreadingModelsBuilder usingThreadingModels(ThreadingModel... threadingModels) {
        return new UsingThreadingModelsBuilder(
            svc, method, input, new HashSet<>(Arrays.asList(threadingModels)));
      }

      UsingThreadingModelsBuilder usingAllThreadingModels() {
        return usingThreadingModels(ThreadingModel.values());
      }
    }

    static class UsingThreadingModelsBuilder {
      private final ServerServiceDefinition svc;
      private final String method;
      private final List<MessageLite> input;
      private final HashSet<ThreadingModel> threadingModels;

      UsingThreadingModelsBuilder(
          ServerServiceDefinition svc,
          String method,
          List<MessageLite> input,
          HashSet<ThreadingModel> threadingModels) {
        this.svc = svc;
        this.method = method;
        this.input = input;
        this.threadingModels = threadingModels;
      }

      ExpectingOutputMessages expectingOutput(MessageLite... messages) {
        return new ExpectingOutputMessages(
            svc, method, input, threadingModels, Arrays.asList(messages));
      }

      ExpectingOutputMessages expectingNoOutput() {
        return expectingOutput();
      }
    }

    static class ExpectingOutputMessages implements TestDefinition {
      private final ServerServiceDefinition svc;
      private final String method;
      private final List<MessageLite> input;
      private final HashSet<ThreadingModel> threadingModels;
      private final List<MessageLite> expectedOutput;
      private final String named;

      ExpectingOutputMessages(
          ServerServiceDefinition svc,
          String method,
          List<MessageLite> input,
          HashSet<ThreadingModel> threadingModels,
          List<MessageLite> expectedOutput) {
        this(
            svc,
            method,
            input,
            threadingModels,
            expectedOutput,
            "Test " + svc.getServiceDescriptor().getName() + "/" + method);
      }

      ExpectingOutputMessages(
          ServerServiceDefinition svc,
          String method,
          List<MessageLite> input,
          HashSet<ThreadingModel> threadingModels,
          List<MessageLite> expectedOutput,
          String named) {
        this.svc = svc;
        this.method = method;
        this.input = input;
        this.threadingModels = threadingModels;
        this.expectedOutput = expectedOutput;
        this.named = named;
      }

      ExpectingOutputMessages named(String name) {
        return new ExpectingOutputMessages(
            svc, method, input, threadingModels, expectedOutput, name);
      }

      @Override
      public ServerServiceDefinition getService() {
        return svc;
      }

      @Override
      public String getMethod() {
        return method;
      }

      @Override
      public List<MessageLite> getInput() {
        return input;
      }

      @Override
      public HashSet<ThreadingModel> getThreadingModels() {
        return threadingModels;
      }

      @Override
      public List<MessageLite> getExpectedOutput() {
        return expectedOutput;
      }

      @Override
      public String testCaseName() {
        return this.named;
      }
    }
  }
}
