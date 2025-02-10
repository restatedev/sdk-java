// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.core.statemachine.MessageHeader;
import dev.restate.sdk.core.statemachine.ProtoUtils;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceDefinitionFactories;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;

public final class TestDefinitions {

  private TestDefinitions() {}

  public interface TestDefinition {
    ServiceDefinition<?> getServiceDefinition();

    Object getServiceOptions();

    String getMethod();

    boolean isOnlyUnbuffered();

    boolean isEnablePreviewContext();

    List<InvocationInput> getInput();

    Consumer<List<MessageLite>> getOutputAssert();

    String getTestCaseName();

    default boolean isValid() {
      return this.getInvalidReason() == null;
    }

    @Nullable String getInvalidReason();
  }

  public interface TestSuite {
    Stream<TestDefinition> definitions();
  }

  public interface TestExecutor {
    boolean buffered();

    void executeTest(TestDefinition definition);
  }

  public static TestInvocationBuilder testInvocation(Supplier<Object> svcSupplier, String handler) {
    Object service;
    try {
      service = svcSupplier.get();
    } catch (UnsupportedOperationException e) {
      return new TestInvocationBuilder(Objects.requireNonNull(e.getMessage()));
    }
    return testInvocation(service, handler);
  }

  public static TestInvocationBuilder testInvocation(Object service, String handler) {
    if (service instanceof ServiceDefinition) {
      return new TestInvocationBuilder((ServiceDefinition<?>) service, null, handler);
    }

    // In case it's code generated, discover the adapter
    ServiceDefinition<?> serviceDefinition =
        ServiceDefinitionFactories.discover(service).create(service);
    return new TestInvocationBuilder(serviceDefinition, null, handler);
  }

  public static <O> TestInvocationBuilder testInvocation(
      ServiceDefinition<O> service, O options, String handler) {
    return new TestInvocationBuilder(service, options, handler);
  }

  public static TestInvocationBuilder unsupported(String reason) {
    return new TestInvocationBuilder(Objects.requireNonNull(reason));
  }

  public static class TestInvocationBuilder {
    protected final @Nullable ServiceDefinition<?> service;
    protected final @Nullable Object options;
    protected final @Nullable String handler;
    protected final @Nullable String invalidReason;

    TestInvocationBuilder(ServiceDefinition<?> service, @Nullable Object options, String handler) {
      this.service = service;
      this.options = options;
      this.handler = handler;

      this.invalidReason = null;
    }

    TestInvocationBuilder(String invalidReason) {
      this.service = null;
      this.options = null;
      this.handler = null;

      this.invalidReason = invalidReason;
    }

    public WithInputBuilder withInput(Stream<MessageLiteOrBuilder> messages) {
      if (invalidReason != null) {
        return new WithInputBuilder(invalidReason);
      }

      return new WithInputBuilder(
          service,
          options,
          handler,
          messages
              .map(
                  msgOrBuilder -> {
                    MessageLite msg = ProtoUtils.build(msgOrBuilder);
                    return InvocationInput.of(MessageHeader.fromMessage(msg), msg);
                  })
              .collect(Collectors.toList()));
    }

    public WithInputBuilder withInput(MessageLiteOrBuilder... messages) {
      return withInput(Arrays.stream(messages));
    }
  }

  public static class WithInputBuilder extends TestInvocationBuilder {
    private final List<InvocationInput> input;
    private boolean onlyUnbuffered = false;
    private boolean enablePreviewContext = false;

    WithInputBuilder(@Nullable String invalidReason) {
      super(invalidReason);
      this.input = Collections.emptyList();
    }

    WithInputBuilder(
        ServiceDefinition<?> service,
        @Nullable Object options,
        String method,
        List<InvocationInput> input) {
      super(service, options, method);
      this.input = new ArrayList<>(input);
    }

    @Override
    public WithInputBuilder withInput(MessageLiteOrBuilder... messages) {
      if (this.invalidReason == null) {
        this.input.addAll(
            Arrays.stream(messages)
                .map(
                    msgOrBuilder -> {
                      MessageLite msg = ProtoUtils.build(msgOrBuilder);
                      return InvocationInput.of(MessageHeader.fromMessage(msg), msg);
                    })
                .toList());
      }
      return this;
    }

    public WithInputBuilder onlyUnbuffered() {
      this.onlyUnbuffered = true;
      return this;
    }

    public WithInputBuilder enablePreviewContext() {
      this.enablePreviewContext = true;
      return this;
    }

    public ExpectingOutputMessages expectingOutput(MessageLiteOrBuilder... messages) {
      List<MessageLite> builtMessages =
          Arrays.stream(messages).map(ProtoUtils::build).collect(Collectors.toList());
      return assertingOutput(
          actual ->
              assertThat(actual)
                  .asInstanceOf(InstanceOfAssertFactories.LIST)
                  .containsExactlyElementsOf(builtMessages));
    }

    public ExpectingOutputMessages assertingOutput(Consumer<List<MessageLite>> messages) {
      return new ExpectingOutputMessages(
          service,
          options,
          invalidReason,
          handler,
          input,
          onlyUnbuffered,
          enablePreviewContext,
          messages);
    }
  }

  public abstract static class BaseTestDefinition implements TestDefinition {
    protected final @Nullable ServiceDefinition<?> service;
    protected final @Nullable Object options;
    protected final @Nullable String invalidReason;
    protected final String method;
    protected final List<InvocationInput> input;
    protected final boolean onlyUnbuffered;
    protected final boolean enablePreviewContext;
    protected final String named;

    private BaseTestDefinition(
        @Nullable ServiceDefinition<?> service,
        @Nullable Object options,
        @Nullable String invalidReason,
        String method,
        List<InvocationInput> input,
        boolean onlyUnbuffered,
        boolean enablePreviewContext,
        String named) {
      this.service = service;
      this.options = options;
      this.invalidReason = invalidReason;
      this.method = method;
      this.input = input;
      this.onlyUnbuffered = onlyUnbuffered;
      this.enablePreviewContext = enablePreviewContext;
      this.named = named;
    }

    @Override
    public ServiceDefinition<?> getServiceDefinition() {
      return Objects.requireNonNull(service);
    }

    @Override
    public Object getServiceOptions() {
      return options;
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
    public boolean isOnlyUnbuffered() {
      return onlyUnbuffered;
    }

    @Override
    public boolean isEnablePreviewContext() {
      return enablePreviewContext;
    }

    @Override
    public String getTestCaseName() {
      return this.named;
    }

    @Override
    @Nullable
    public String getInvalidReason() {
      return invalidReason;
    }
  }

  public static class ExpectingOutputMessages extends BaseTestDefinition {
    private final Consumer<List<MessageLite>> messagesAssert;

    private ExpectingOutputMessages(
        @Nullable ServiceDefinition<?> service,
        @Nullable Object options,
        @Nullable String invalidReason,
        String method,
        List<InvocationInput> input,
        boolean onlyUnbuffered,
        boolean enablePreviewContext,
        Consumer<List<MessageLite>> messagesAssert) {
      super(
          service,
          options,
          invalidReason,
          method,
          input,
          onlyUnbuffered,
          enablePreviewContext,
          service != null ? service.getServiceName() + "#" + method : "Unknown");
      this.messagesAssert = messagesAssert;
    }

    ExpectingOutputMessages(
        @Nullable ServiceDefinition<?> service,
        @Nullable Object options,
        @Nullable String invalidReason,
        String method,
        List<InvocationInput> input,
        boolean onlyUnbuffered,
        boolean enablePreviewContext,
        Consumer<List<MessageLite>> messagesAssert,
        String named) {
      super(
          service,
          options,
          invalidReason,
          method,
          input,
          onlyUnbuffered,
          enablePreviewContext,
          named);
      this.messagesAssert = messagesAssert;
    }

    public ExpectingOutputMessages named(String name) {
      return new ExpectingOutputMessages(
          service,
          options,
          invalidReason,
          method,
          input,
          onlyUnbuffered,
          enablePreviewContext,
          messagesAssert,
          this.named + ": " + name);
    }

    @Override
    public Consumer<List<MessageLite>> getOutputAssert() {
      return outputMessages -> {
        messagesAssert.accept(outputMessages);

        // Assert the last message is either an OutputStreamEntry or a SuspensionMessage
        assertThat(outputMessages)
            .last()
            .isNotNull()
            .isInstanceOfAny(
                Protocol.ErrorMessage.class,
                Protocol.SuspensionMessage.class,
                Protocol.EndMessage.class);
      };
    }
  }
}
