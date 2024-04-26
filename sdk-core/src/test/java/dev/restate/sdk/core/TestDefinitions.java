// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ProtoUtils.headerFromMessage;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.BindableService;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class TestDefinitions {

  private TestDefinitions() {}

  public interface TestDefinition {
    BindableService<?> getBindableService();

    String getMethod();

    boolean isOnlyUnbuffered();

    List<InvocationFlow.InvocationInput> getInput();

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
    if (service instanceof BindableService) {
      return new TestInvocationBuilder((BindableService<?>) service, handler);
    }

    // In case it's code generated, discover the adapter
    BindableService<?> bindableService =
        RestateEndpoint.discoverBindableServiceFactory(service).create(service);
    return new TestInvocationBuilder(bindableService, handler);
  }

  public static TestInvocationBuilder unsupported(String reason) {
    return new TestInvocationBuilder(Objects.requireNonNull(reason));
  }

  public static class TestInvocationBuilder {
    protected final @Nullable BindableService<?> service;
    protected final @Nullable String handler;
    protected final @Nullable String invalidReason;

    TestInvocationBuilder(BindableService<?> service, String handler) {
      this.service = service;
      this.handler = handler;

      this.invalidReason = null;
    }

    TestInvocationBuilder(String invalidReason) {
      this.service = null;
      this.handler = null;

      this.invalidReason = invalidReason;
    }

    public WithInputBuilder withInput(MessageLiteOrBuilder... messages) {
      if (invalidReason != null) {
        return new WithInputBuilder(invalidReason);
      }

      return new WithInputBuilder(
          service,
          handler,
          Arrays.stream(messages)
              .map(
                  msgOrBuilder -> {
                    MessageLite msg = ProtoUtils.build(msgOrBuilder);
                    return InvocationFlow.InvocationInput.of(headerFromMessage(msg), msg);
                  })
              .collect(Collectors.toList()));
    }
  }

  public static class WithInputBuilder extends TestInvocationBuilder {
    private final List<InvocationFlow.InvocationInput> input;
    private boolean onlyUnbuffered = false;

    WithInputBuilder(@Nullable String invalidReason) {
      super(invalidReason);
      this.input = Collections.emptyList();
    }

    WithInputBuilder(
        BindableService<?> service, String method, List<InvocationFlow.InvocationInput> input) {
      super(service, method);
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
                      return InvocationFlow.InvocationInput.of(headerFromMessage(msg), msg);
                    })
                .collect(Collectors.toList()));
      }
      return this;
    }

    public WithInputBuilder onlyUnbuffered() {
      this.onlyUnbuffered = true;
      return this;
    }

    public ExpectingOutputMessages expectingOutput(MessageLiteOrBuilder... messages) {
      List<MessageLite> builtMessages =
          Arrays.stream(messages).map(ProtoUtils::build).collect(Collectors.toList());
      return assertingOutput(actual -> assertThat(actual).asList().isEqualTo(builtMessages));
    }

    public ExpectingOutputMessages assertingOutput(Consumer<List<MessageLite>> messages) {
      return new ExpectingOutputMessages(
          service, invalidReason, handler, input, onlyUnbuffered, messages);
    }
  }

  public abstract static class BaseTestDefinition implements TestDefinition {
    protected final @Nullable BindableService<?> service;
    protected final @Nullable String invalidReason;
    protected final String method;
    protected final List<InvocationFlow.InvocationInput> input;
    protected final boolean onlyUnbuffered;
    protected final String named;

    private BaseTestDefinition(
        @Nullable BindableService<?> service,
        @Nullable String invalidReason,
        String method,
        List<InvocationFlow.InvocationInput> input,
        boolean onlyUnbuffered,
        String named) {
      this.service = service;
      this.invalidReason = invalidReason;
      this.method = method;
      this.input = input;
      this.onlyUnbuffered = onlyUnbuffered;
      this.named = named;
    }

    @Override
    public BindableService<?> getBindableService() {
      return Objects.requireNonNull(service);
    }

    @Override
    public String getMethod() {
      return method;
    }

    @Override
    public List<InvocationFlow.InvocationInput> getInput() {
      return input;
    }

    @Override
    public boolean isOnlyUnbuffered() {
      return onlyUnbuffered;
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
        @Nullable BindableService<?> service,
        @Nullable String invalidReason,
        String method,
        List<InvocationFlow.InvocationInput> input,
        boolean onlyUnbuffered,
        Consumer<List<MessageLite>> messagesAssert) {
      super(
          service,
          invalidReason,
          method,
          input,
          onlyUnbuffered,
          service != null
              ? service.definitions().get(0).getServiceName() + "#" + method
              : "Unknown");
      this.messagesAssert = messagesAssert;
    }

    ExpectingOutputMessages(
        @Nullable BindableService<?> service,
        @Nullable String invalidReason,
        String method,
        List<InvocationFlow.InvocationInput> input,
        boolean onlyUnbuffered,
        Consumer<List<MessageLite>> messagesAssert,
        String named) {
      super(service, invalidReason, method, input, onlyUnbuffered, named);
      this.messagesAssert = messagesAssert;
    }

    public ExpectingOutputMessages named(String name) {
      return new ExpectingOutputMessages(
          service,
          invalidReason,
          method,
          input,
          onlyUnbuffered,
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
