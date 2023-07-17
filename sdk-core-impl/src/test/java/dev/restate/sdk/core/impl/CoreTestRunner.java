package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.headerFromMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.FlowUtils.BufferedMockPublisher;
import dev.restate.sdk.core.impl.FlowUtils.FutureSubscriber;
import dev.restate.sdk.core.impl.FlowUtils.UnbufferedMockPublisher;
import dev.restate.sdk.core.impl.InvocationFlow.InvocationInput;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import java.time.Duration;
import java.util.ArrayList;
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
      List<InvocationInput> input,
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
    RestateGrpcServer server =
        RestateGrpcServer.newBuilder(Discovery.ProtocolMode.BIDI_STREAM).withService(svc).build();
    InvocationHandler handler =
        server.resolve(
            svc.getServiceDescriptor().getName(),
            method,
            io.opentelemetry.context.Context.current(),
            syscallsExecutor,
            userExecutor);

    if (threadingModel == ThreadingModel.UNBUFFERED_MULTI_THREAD) {
      // Create publisher
      UnbufferedMockPublisher<InvocationInput> inputPublisher = new UnbufferedMockPublisher<>();

      // Wire invocation and start it
      syscallsExecutor.execute(
          () -> {
            handler.output().subscribe(outputSubscriber);
            inputPublisher.subscribe(handler.input());
            handler.start();
          });

      // Pipe entries
      for (InvocationInput inputEntry : input) {
        syscallsExecutor.execute(() -> inputPublisher.push(inputEntry));
      }
      // Complete the input publisher
      syscallsExecutor.execute(inputPublisher::close);

      // Check completed
      outputAssert.accept(outputSubscriber, Duration.ofSeconds(1));
      assertThat(inputPublisher.isSubscriptionCancelled()).isTrue();
    } else {
      // Create publisher
      BufferedMockPublisher<InvocationInput> inputPublisher = new BufferedMockPublisher<>(input);

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

    List<InvocationInput> getInput();

    HashSet<ThreadingModel> getThreadingModels();

    BiConsumer<FutureSubscriber<MessageLite>, Duration> getOutputAssert();

    String testCaseName();
  }

  /** Builder for the test cases */
  static class TestCaseBuilder {

    static TestInvocationBuilder testInvocation(BindableService svc, String method) {
      return new TestInvocationBuilder(svc, method);
    }

    static TestInvocationBuilder testInvocation(
        BindableService svc, MethodDescriptor<?, ?> method) {
      return testInvocation(svc, method.getBareMethodName());
    }

    static class TestInvocationBuilder {
      protected final BindableService svc;
      protected final String method;

      TestInvocationBuilder(BindableService svc, String method) {
        this.svc = svc;
        this.method = method;
      }

      WithInputBuilder withInput(short flags, MessageLiteOrBuilder msgOrBuilder) {
        MessageLite msg = ProtoUtils.build(msgOrBuilder);
        return new WithInputBuilder(
            svc,
            method,
            List.of(InvocationInput.of(headerFromMessage(msg).copyWithFlags(flags), msg)));
      }

      WithInputBuilder withInput(MessageLiteOrBuilder... messages) {
        return new WithInputBuilder(
            svc,
            method,
            Arrays.stream(messages)
                .map(
                    msgOrBuilder -> {
                      MessageLite msg = ProtoUtils.build(msgOrBuilder);
                      return InvocationInput.of(headerFromMessage(msg), msg);
                    })
                .collect(Collectors.toList()));
      }
    }

    static class WithInputBuilder extends TestInvocationBuilder {
      private final List<InvocationInput> input;

      WithInputBuilder(BindableService svc, String method, List<InvocationInput> input) {
        super(svc, method);
        this.input = new ArrayList<>(input);
      }

      WithInputBuilder withInput(short flags, MessageLiteOrBuilder msgOrBuilder) {
        MessageLite msg = ProtoUtils.build(msgOrBuilder);
        this.input.add(InvocationInput.of(headerFromMessage(msg).copyWithFlags(flags), msg));
        return this;
      }

      WithInputBuilder withInput(MessageLiteOrBuilder... messages) {
        this.input.addAll(
            Arrays.stream(messages)
                .map(
                    msgOrBuilder -> {
                      MessageLite msg = ProtoUtils.build(msgOrBuilder);
                      return InvocationInput.of(headerFromMessage(msg), msg);
                    })
                .collect(Collectors.toList()));
        return this;
      }

      UsingThreadingModelsBuilder usingThreadingModels(ThreadingModel... threadingModels) {
        return new UsingThreadingModelsBuilder(
            this.svc, this.method, input, new HashSet<>(Arrays.asList(threadingModels)));
      }

      UsingThreadingModelsBuilder usingAllThreadingModels() {
        return usingThreadingModels(ThreadingModel.values());
      }
    }

    static class UsingThreadingModelsBuilder {
      private final BindableService svc;
      private final String method;
      private final List<InvocationInput> input;
      private final HashSet<ThreadingModel> threadingModels;

      UsingThreadingModelsBuilder(
          BindableService svc,
          String method,
          List<InvocationInput> input,
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
      protected final BindableService svc;
      protected final String method;
      protected final List<InvocationInput> input;
      protected final HashSet<ThreadingModel> threadingModels;
      protected final String named;

      public BaseTestDefinition(
          BindableService svc,
          String method,
          List<InvocationInput> input,
          HashSet<ThreadingModel> threadingModels) {
        this(svc, method, input, threadingModels, svc.getClass().getSimpleName());
      }

      public BaseTestDefinition(
          BindableService svc,
          String method,
          List<InvocationInput> input,
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
        return svc.bindService();
      }

      @Override
      public String getMethod() {
        return method;
      }

      @Override
      public List<InvocationInput> getInput() {
        return input;
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
          BindableService svc,
          String method,
          List<InvocationInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<List<MessageLite>> messagesAssert) {
        super(svc, method, input, threadingModels);
        this.messagesAssert = messagesAssert;
      }

      ExpectingOutputMessages(
          BindableService svc,
          String method,
          List<InvocationInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<List<MessageLite>> messagesAssert,
          String named) {
        super(svc, method, input, threadingModels, named);
        this.messagesAssert = messagesAssert;
      }

      ExpectingOutputMessages named(String name) {
        return new TestCaseBuilder.ExpectingOutputMessages(
            svc,
            method,
            input,
            threadingModels,
            messagesAssert,
            svc.getClass().getSimpleName() + ": " + name);
      }

      @Override
      public BiConsumer<FutureSubscriber<MessageLite>, Duration> getOutputAssert() {
        return (outputSubscriber, duration) -> {
          assertThat(outputSubscriber.getFuture())
              .succeedsWithin(duration)
              .satisfies(messagesAssert::accept);

          List<MessageLite> outputMessages = outputSubscriber.getMessages();

          // Assert the last message is either an OutputStreamEntry or a SuspensionMessage
          assertThat(outputMessages)
              .last()
              .isNotNull()
              .isInstanceOfAny(
                  Protocol.OutputStreamEntryMessage.class,
                  Protocol.SuspensionMessage.class,
                  Protocol.ErrorMessage.class);
        };
      }
    }

    static class ExpectingFailure extends BaseTestDefinition {
      private final Consumer<Throwable> throwableAssert;

      ExpectingFailure(
          BindableService svc,
          String method,
          List<InvocationInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<Throwable> throwableAssert) {
        super(svc, method, input, threadingModels);
        this.throwableAssert = throwableAssert;
      }

      ExpectingFailure(
          BindableService svc,
          String method,
          List<InvocationInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<Throwable> throwableAssert,
          String named) {
        super(svc, method, input, threadingModels, named);
        this.throwableAssert = throwableAssert;
      }

      ExpectingFailure named(String name) {
        return new ExpectingFailure(
            svc,
            method,
            input,
            threadingModels,
            throwableAssert,
            svc.getClass().getSimpleName() + ": " + name);
      }

      @Override
      public BiConsumer<FutureSubscriber<MessageLite>, Duration> getOutputAssert() {
        return (outputSubscriber, duration) -> {
          assertThat(outputSubscriber.getFuture())
              .failsWithin(duration)
              .withThrowableOfType(ExecutionException.class)
              .satisfies(t -> throwableAssert.accept(t.getCause()));
          // If there was a state machine related failure, no output message or suspension should be
          // written
          assertThat(outputSubscriber.getMessages())
              .doesNotHaveAnyElementsOfTypes(
                  Protocol.OutputStreamEntryMessage.class, Protocol.SuspensionMessage.class);
        };
      }
    }
  }
}
