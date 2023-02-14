package dev.restate.sdk.testing;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import io.grpc.BindableService;
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
abstract class TestDriver {

  /** This is implemented in the tests which extend from this mock runner */
  abstract Stream<TestDefinition> definitions();

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
  void executeTest(
      String testName,
      List<ServerServiceDefinition> services,
      List<TestInput> input,
      ThreadingModel threadingModel,
      BiConsumer<TestRestateRuntime, Duration> outputAssert) {

    // Create runtime instance
    TestRestateRuntime testRestateRuntimeStateMachine =
        new TestRestateRuntime(services, threadingModel);

    for (TestInput testInput : input){
        testRestateRuntimeStateMachine.handle(testInput);
    }

    // Check completed
    outputAssert.accept(testRestateRuntimeStateMachine, Duration.ZERO);
    Assertions.assertThat(testRestateRuntimeStateMachine.getPublisherSubscriptionCancelled()).isTrue();

  }

  enum ThreadingModel {
    BUFFERED_SINGLE_THREAD,
    UNBUFFERED_MULTI_THREAD
  }

  interface TestDefinition {

    List<ServerServiceDefinition> getServices();

    List<TestInput> getInput();

    HashSet<ThreadingModel> getThreadingModels();

    BiConsumer<TestRestateRuntime, Duration> getOutputAssert();

    String testCaseName();
  }


  /** Builder for the test cases */
  static class TestCaseBuilder {

    static class TestInvocationBuilder {

        public static TestInvocationBuilder endToEndTestInvocation() {
            return new TestInvocationBuilder();
        }

      WithServicesBuilder withServices(BindableService... services) {
        return new WithServicesBuilder(Arrays.asList(services));
      }
    }

    static class WithServicesBuilder {

      private final List<BindableService> services;

      public <T> WithServicesBuilder(List<BindableService> services) {
        this.services = services;
      }

      WithInputBuilder withInput(TestInput... messages) {
        return new WithInputBuilder(services, Arrays.asList(messages));
      }
    }
    

    static class WithInputBuilder {

      private final List<BindableService> services;
      private final List<TestInput> input;

      WithInputBuilder(List<BindableService> services, List<TestInput> input) {
        this.services = services;
        this.input = input;
      }

      UsingThreadingModelsBuilder usingThreadingModels(ThreadingModel... threadingModels) {
        return new UsingThreadingModelsBuilder(
            services, input, new HashSet<>(Arrays.asList(threadingModels)));
      }

      UsingThreadingModelsBuilder usingAllThreadingModels() {
        return usingThreadingModels(ThreadingModel.values());
      }
    }

    static class UsingThreadingModelsBuilder {
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

      ExpectingOutputMessages expectingOutput(MessageLiteOrBuilder... messages) {
        List<MessageLite> builtMessages =
            Arrays.stream(messages).map(ProtoUtils::build).collect(Collectors.toList());
        return assertingOutput(actual -> Assertions.assertThat(actual).asList().isEqualTo(builtMessages));
      }

      ExpectingOutputMessages expectingNoOutput() {
        return assertingOutput(messages -> Assertions.assertThat(messages).asList().isEmpty());
      }

      ExpectingOutputMessages assertingOutput(Consumer<List<Protocol.OutputStreamEntryMessage>> messages) {
        return new ExpectingOutputMessages(services, input, threadingModels, messages);
      }

      ExpectingFailure assertingFailure(Class<? extends Throwable> tClass) {
        return assertingFailure(t -> Assertions.assertThat(t).isInstanceOf(tClass));
      }

      ExpectingFailure assertingFailure(Consumer<Throwable> assertFailure) {
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

    static class ExpectingOutputMessages extends BaseTestDefinition {
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

      ExpectingOutputMessages named(String name) {
        return new TestCaseBuilder.ExpectingOutputMessages(
                services,
            input,
            threadingModels,
            messagesAssert,
            name);
      }

      @Override
      public BiConsumer<TestRestateRuntime, Duration> getOutputAssert() {
        return (outputSubscriber, duration) ->
            Assertions.assertThat(outputSubscriber.getFuture())
                .succeedsWithin(duration)
                .satisfies(messagesAssert::accept);
      }
    }

    static class ExpectingFailure extends BaseTestDefinition {
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

      ExpectingFailure named(String name) {
        return new ExpectingFailure(
                services,
            input,
            threadingModels,
            throwableAssert,
            name);
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
