package dev.restate.sdk.testing;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RestateTestDriver {

  /** This is implemented in the tests which extend from this mock runner */
  protected abstract Stream<TestDefinition> definitions();

  /**
   * Takes the definitions and explodes them to have one entry per threading model If you test with
   * all threading models it creates two entries per threading model This then is used as the source
   * of arguments for the test in executeTest()
   */
  Stream<Arguments> source() {
    return definitions()
        .flatMap(
            c ->
                c.getThreadingModels().stream()
                    .map(
                        threadingModel ->
                            Arguments.arguments(
                                "[" + threadingModel + "] " + c.testCaseName(),
                                c.getServices(),
                                c.getInput(),
                                threadingModel,
                                c.getOutputAssert())));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("source")
  protected void executeTest(
      String testName,
      List<ServerServiceDefinition> services,
      List<TestInput> input,
      ThreadingModel threadingModel,
      BiConsumer<TestRestateRuntime, Duration> outputAssert) {

    // Create runtime instance
    TestRestateRuntime testRestateRuntimeStateMachine =
        TestRestateRuntime.init(services, threadingModel);

    for (TestInput testInput : input) {
      testRestateRuntimeStateMachine.handle(testInput);
    }

    // Check completed
    outputAssert.accept(testRestateRuntimeStateMachine, Duration.ZERO);
    Assertions.assertThat(testRestateRuntimeStateMachine.getPublisherSubscriptionsCancelled())
        .isTrue();

    TestRestateRuntime.close();
  }

  public enum ThreadingModel {
    BUFFERED_SINGLE_THREAD
    //    UNBUFFERED_MULTI_THREAD //TODO implement this
  }

  public static class TestInput {
    private final String method;
    private final String service;
    private final Protocol.PollInputStreamEntryMessage inputMessage;

    private TestInput(MethodDescriptor<?, ?> method, Protocol.PollInputStreamEntryMessage msg) {
      this.service = method.getServiceName();
      this.method = method.getBareMethodName();
      this.inputMessage = msg;
    }

    public static class Builder {
      public static Builder testInput() {
        return new Builder();
      }

      public WithMethod withMethod(MethodDescriptor<?, ?> method) {
        return new WithMethod(method);
      }
    }

    public static class WithMethod {
      private MethodDescriptor<?, ?> method;

      public WithMethod(MethodDescriptor<?, ?> method) {
        this.method = method;
      }

      public TestInput withMessage(MessageLiteOrBuilder msg) {
        return new TestInput(method, ProtoUtils.inputMessage(msg));
      }
    }

    public String getService() {
      return service;
    }

    public String getMethod() {
      return method;
    }

    public Protocol.PollInputStreamEntryMessage getInputMessage() {
      return inputMessage;
    }
  }

  protected interface TestDefinition {

    List<ServerServiceDefinition> getServices();

    List<TestInput> getInput();

    HashSet<ThreadingModel> getThreadingModels();

    BiConsumer<TestRestateRuntime, Duration> getOutputAssert();

    String testCaseName();
  }

  /** Builder for the test cases */
  public static class TestCaseBuilder {

    public static class TestInvocationBuilder {

      public static TestInvocationBuilder endToEndTestInvocation() {
        return new TestInvocationBuilder();
      }

      public WithServicesBuilder withServices(BindableService... services) {
        return new WithServicesBuilder(Arrays.asList(services));
      }
    }

    public static class WithServicesBuilder {

      private final List<BindableService> services;

      public <T> WithServicesBuilder(List<BindableService> services) {
        this.services = services;
      }

      public WithInputBuilder withInput(TestInput... messages) {
        return new WithInputBuilder(services, Arrays.asList(messages));
      }
    }

    public static class WithInputBuilder {

      private final List<BindableService> services;
      private final List<TestInput> input;

      WithInputBuilder(List<BindableService> services, List<TestInput> input) {
        this.services = services;
        this.input = input;
      }

      public UsingThreadingModelsBuilder usingThreadingModels(ThreadingModel... threadingModels) {
        return new UsingThreadingModelsBuilder(
            services, input, new HashSet<>(Arrays.asList(threadingModels)));
      }

      public UsingThreadingModelsBuilder usingAllThreadingModels() {
        return usingThreadingModels(ThreadingModel.values());
      }
    }

    public static class UsingThreadingModelsBuilder {
      private final List<BindableService> services;
      private final List<TestInput> input;
      private final HashSet<ThreadingModel> threadingModels;

      UsingThreadingModelsBuilder(
          List<BindableService> services,
          List<TestInput> input,
          HashSet<ThreadingModel> threadingModels) {
        this.services = services;
        this.input = input;
        this.threadingModels = threadingModels;
      }

      public ExpectingOutputMessages expectingOutput(MessageLiteOrBuilder... messages) {
        List<MessageLite> builtMessages =
            Arrays.stream(messages).map(ProtoUtils::build).collect(Collectors.toList());
        return assertingOutput(
            actual -> Assertions.assertThat(actual).asList().isEqualTo(builtMessages));
      }

      public ExpectingOutputMessages expectingNoOutput() {
        return assertingOutput(messages -> Assertions.assertThat(messages).asList().isEmpty());
      }

      public ExpectingOutputMessages assertingOutput(
          Consumer<List<Protocol.OutputStreamEntryMessage>> messages) {
        return new ExpectingOutputMessages(services, input, threadingModels, messages);
      }

      public ExpectingFailure assertingFailure(Class<? extends Throwable> tClass) {
        return assertingFailure(t -> Assertions.assertThat(t).isInstanceOf(tClass));
      }

      public ExpectingFailure assertingFailure(Consumer<Throwable> assertFailure) {
        return new ExpectingFailure(services, input, threadingModels, assertFailure);
      }
    }

    public abstract static class BaseTestDefinition implements TestDefinition {
      protected final List<BindableService> services;
      protected final List<TestInput> input;
      protected final HashSet<ThreadingModel> threadingModels;
      protected final String named;

      public BaseTestDefinition(
          List<BindableService> services,
          List<TestInput> input,
          HashSet<ThreadingModel> threadingModels) {
        // TODO pick a better default name
        this(services, input, threadingModels, services.get(0).getClass().getSimpleName());
      }

      public BaseTestDefinition(
          List<BindableService> services,
          List<TestInput> input,
          HashSet<ThreadingModel> threadingModels,
          String named) {
        this.services = services;
        this.input = input;
        this.threadingModels = threadingModels;
        this.named = named;
      }

      @Override
      public List<ServerServiceDefinition> getServices() {
        return services.stream().map(BindableService::bindService).collect(Collectors.toList());
      }

      @Override
      public List<TestInput> getInput() {
        return new ArrayList<>(input);
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

    public static class ExpectingOutputMessages extends BaseTestDefinition {
      private final Consumer<List<Protocol.OutputStreamEntryMessage>> messagesAssert;

      ExpectingOutputMessages(
          List<BindableService> services,
          List<TestInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<List<Protocol.OutputStreamEntryMessage>> messagesAssert) {
        super(services, input, threadingModels);
        this.messagesAssert = messagesAssert;
      }

      ExpectingOutputMessages(
          List<BindableService> services,
          List<TestInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<List<Protocol.OutputStreamEntryMessage>> messagesAssert,
          String named) {
        super(services, input, threadingModels, named);
        this.messagesAssert = messagesAssert;
      }

      public ExpectingOutputMessages named(String name) {
        return new TestCaseBuilder.ExpectingOutputMessages(
            services, input, threadingModels, messagesAssert, name);
      }

      @Override
      public BiConsumer<TestRestateRuntime, Duration> getOutputAssert() {
        return (outputSubscriber, duration) ->
            Assertions.assertThat(outputSubscriber.getFuture())
                .succeedsWithin(duration)
                .satisfies(messagesAssert::accept);
      }
    }

    public static class ExpectingFailure extends BaseTestDefinition {
      private final Consumer<Throwable> throwableAssert;

      ExpectingFailure(
          List<BindableService> services,
          List<TestInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<Throwable> throwableAssert) {
        super(services, input, threadingModels);
        this.throwableAssert = throwableAssert;
      }

      ExpectingFailure(
          List<BindableService> services,
          List<TestInput> input,
          HashSet<ThreadingModel> threadingModels,
          Consumer<Throwable> throwableAssert,
          String named) {
        super(services, input, threadingModels, named);
        this.throwableAssert = throwableAssert;
      }

      public ExpectingFailure named(String name) {
        return new ExpectingFailure(services, input, threadingModels, throwableAssert, name);
      }

      @Override
      public BiConsumer<TestRestateRuntime, Duration> getOutputAssert() {
        return (outputSubscriber, duration) -> {
          Assertions.assertThat(outputSubscriber.getFuture())
              .failsWithin(duration)
              .withThrowableOfType(ExecutionException.class)
              .satisfies(t -> throwableAssert.accept(t.getCause()));
          // If there was a state machine related failure, no output message should be written
          Assertions.assertThat(outputSubscriber.getTestResults())
              .doesNotHaveAnyElementsOfTypes(Protocol.OutputStreamEntryMessage.class);
        };
      }
    }
  }
}
