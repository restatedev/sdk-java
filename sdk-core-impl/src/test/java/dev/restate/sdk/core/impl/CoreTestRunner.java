package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.FlowUtils.BufferedMockPublisher;
import dev.restate.sdk.core.impl.FlowUtils.FutureSubscriber;
import dev.restate.sdk.core.impl.FlowUtils.UnbufferedMockPublisher;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
                                c.getOutputAssert())));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("source")
  void executeTest(
      String testName,
      ServerServiceDefinition svc,
      String method,
      List<MessageLite> input,
      ThreadingModel threadingModel,
      BiConsumer<FutureSubscriber<MessageLite>, Duration> outputAssert) {
    Executor syscallsExecutor =
        threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD
            ? Executors.newSingleThreadExecutor()
            : null;
    Executor userExecutor =
        threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD
            ? Executors.newSingleThreadExecutor()
            : null;

    // Output subscriber buffers all the output messages and provides a completion future
    FutureSubscriber<MessageLite> outputSubscriber = new FutureSubscriber<>();

    // Start invocation
    RestateGrpcServer server = RestateGrpcServer.newBuilder().withService(svc).build();
    InvocationHandler handler =
        server.resolve(
            svc.getServiceDescriptor().getName(),
            method,
            io.opentelemetry.context.Context.current(),
            syscallsExecutor,
            userExecutor);

    if (threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD) {
      // Create publisher
      UnbufferedMockPublisher<MessageLite> inputPublisher = new UnbufferedMockPublisher<>();

      // Wire invocation and start it
      syscallsExecutor.execute(
          () -> {
            handler.output().subscribe(outputSubscriber);
            inputPublisher.subscribe(handler.input());
            handler.start();
          });

      // Pipe entries
      for (MessageLite inputEntry : input) {
        syscallsExecutor.execute(() -> inputPublisher.push(inputEntry));
      }
      // Complete the input publisher
      syscallsExecutor.execute(inputPublisher::close);

      // Check completed
      outputAssert.accept(outputSubscriber, Duration.ofSeconds(1));
      assertThat(inputPublisher.isSubscriptionCancelled()).isTrue();
    } else {
      // Create publisher
      BufferedMockPublisher<MessageLite> inputPublisher = new BufferedMockPublisher<>(input);

      // Wire invocation
      handler.output().subscribe(outputSubscriber);
      inputPublisher.subscribe(handler.input());

      // Start invocation
      handler.start();

      // Check completed
      outputAssert.accept(outputSubscriber, Duration.ZERO);
      assertThat(inputPublisher.isSubscriptionCancelled()).isTrue();
    }
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

    BiConsumer<FutureSubscriber<MessageLite>, Duration> getOutputAssert();

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

      WithInputBuilder withInput(MessageLiteOrBuilder... messages) {
        return new WithInputBuilder(svc, method, Arrays.asList(messages));
      }
    }

    static class WithInputBuilder {
      private final ServerServiceDefinition svc;
      private final String method;
      private final List<MessageLiteOrBuilder> input;

      WithInputBuilder(
          ServerServiceDefinition svc, String method, List<MessageLiteOrBuilder> input) {
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
      private final List<MessageLiteOrBuilder> input;
      private final HashSet<ThreadingModel> threadingModels;

      UsingThreadingModelsBuilder(
          ServerServiceDefinition svc,
          String method,
          List<MessageLiteOrBuilder> input,
          HashSet<ThreadingModel> threadingModels) {
        this.svc = svc;
        this.method = method;
        this.input = input;
        this.threadingModels = threadingModels;
      }

      ExpectingOutputMessages expectingOutput(MessageLiteOrBuilder... messages) {
        List<MessageLite> builtMessages =
            Arrays.stream(messages).map(ProtoUtils::build).collect(Collectors.toList());
        return assertingOutput(actual -> assertThat(actual).asList().isEqualTo(builtMessages));
      }

      ExpectingOutputMessages expectingNoOutput() {
        return assertingOutput(messages -> assertThat(messages).asList().isEmpty());
      }

      ExpectingOutputMessages assertingOutput(Consumer<List<MessageLite>> messages) {
        return new ExpectingOutputMessages(svc, method, input, threadingModels, messages);
      }

      ExpectingFailure assertingFailure(Class<? extends Throwable> tClass) {
        return assertingFailure(t -> assertThat(t).isInstanceOf(tClass));
      }

      ExpectingFailure assertingFailure(Consumer<Throwable> assertFailure) {
        return new ExpectingFailure(svc, method, input, threadingModels, assertFailure);
      }
    }

    public abstract static class BaseTestDefinition implements TestDefinition {
      protected final ServerServiceDefinition svc;
      protected final String method;
      protected final List<MessageLiteOrBuilder> input;
      protected final HashSet<ThreadingModel> threadingModels;
      protected final String named;

      public BaseTestDefinition(
          ServerServiceDefinition svc,
          String method,
          List<MessageLiteOrBuilder> input,
          HashSet<ThreadingModel> threadingModels,
          String named) {
        this.svc = svc;
        this.method = method;
        this.input = input;
        this.threadingModels = threadingModels;
        this.named = named;
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
        return input.stream().map(ProtoUtils::build).collect(Collectors.toList());
      }

      @Override
      public HashSet<ThreadingModel> getThreadingModels() {
        return threadingModels;
      }

      @Override
      public String testCaseName() {
        return this.named;
      }
    }

    static class ExpectingOutputMessages extends BaseTestDefinition {
      private final Consumer<List<MessageLite>> messagesAssert;

      ExpectingOutputMessages(
          ServerServiceDefinition svc,
          String method,
          List<MessageLiteOrBuilder> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<List<MessageLite>> messagesAssert) {
        this(
            svc,
            method,
            input,
            threadingModels,
            messagesAssert,
            "Test " + svc.getServiceDescriptor().getName() + "/" + method);
      }

      ExpectingOutputMessages(
          ServerServiceDefinition svc,
          String method,
          List<MessageLiteOrBuilder> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<List<MessageLite>> messagesAssert,
          String named) {
        super(svc, method, input, threadingModels, named);
        this.messagesAssert = messagesAssert;
      }

      ExpectingOutputMessages named(String name) {
        return new TestCaseBuilder.ExpectingOutputMessages(
            svc, method, input, threadingModels, messagesAssert, name);
      }

      @Override
      public BiConsumer<FutureSubscriber<MessageLite>, Duration> getOutputAssert() {
        return (outputSubscriber, duration) ->
            assertThat(outputSubscriber.getFuture())
                .succeedsWithin(duration)
                .satisfies(messagesAssert::accept);
      }
    }

    static class ExpectingFailure extends BaseTestDefinition {
      private final Consumer<Throwable> throwableAssert;

      ExpectingFailure(
          ServerServiceDefinition svc,
          String method,
          List<MessageLiteOrBuilder> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<Throwable> throwableAssert) {
        this(
            svc,
            method,
            input,
            threadingModels,
            throwableAssert,
            "Test " + svc.getServiceDescriptor().getName() + "/" + method);
      }

      ExpectingFailure(
          ServerServiceDefinition svc,
          String method,
          List<MessageLiteOrBuilder> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<Throwable> throwableAssert,
          String named) {
        super(svc, method, input, threadingModels, named);
        this.throwableAssert = throwableAssert;
      }

      ExpectingFailure named(String name) {
        return new ExpectingFailure(svc, method, input, threadingModels, throwableAssert, name);
      }

      @Override
      public BiConsumer<FutureSubscriber<MessageLite>, Duration> getOutputAssert() {
        return (outputSubscriber, duration) -> {
          assertThat(outputSubscriber.getFuture())
              .failsWithin(duration)
              .withThrowableOfType(ExecutionException.class)
              .satisfies(t -> throwableAssert.accept(t.getCause()));
          // If there was a state machine related failure, no output message should be written
          assertThat(outputSubscriber.getMessages())
              .doesNotHaveAnyElementsOfTypes(Protocol.OutputStreamEntryMessage.class);
        };
      }
    }
  }
}
