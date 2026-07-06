// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.AssertUtils.assertThatDecodingMessages;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.MessageLite;
import dev.restate.common.Slice;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.legacy.InvocationInput;
import dev.restate.sdk.core.legacy.ProtoUtils;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.ThreadContext;

public final class MockRequestResponse implements TestExecutor {

  private final String stateMachineName;
  private final StateMachineFactory stateMachineFactory;

  private MockRequestResponse(String stateMachineName, StateMachineFactory stateMachineFactory) {
    this.stateMachineName = stateMachineName;
    this.stateMachineFactory = stateMachineFactory;
  }

  public static Stream<TestExecutor> getExecutors() {
    return Stream.of(
        new MockRequestResponse("FFM", StateMachineFactory.Loader.FFM_FACTORY),
        new MockRequestResponse("Legacy", StateMachineFactory.Loader.LEGACY_FACTORY));
  }

  @Override
  public boolean buffered() {
    return true;
  }

  @Override
  public void executeTest(TestDefinition definition) throws Exception {
    ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
    try {

      ServiceDefinition serviceDefinition = definition.getServiceDefinition();

      // Prepare server
      Endpoint.Builder builder =
          Endpoint.builder().bind(serviceDefinition, definition.getServiceOptions());
      if (definition.isEnablePreviewContext()) {
        builder.enablePreviewContext();
      }
      EndpointRequestHandler server =
          new EndpointRequestHandler(null, builder.build(), stateMachineFactory);

      // Start invocation
      RequestProcessor handler =
          server.processorForRequest(
              "/" + serviceDefinition.getServiceName() + "/" + definition.getMethod(),
              HeadersAccessor.wrap(
                  Map.of("content-type", ProtoUtils.serviceProtocolContentTypeHeader())),
              EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
              coreExecutor,
              false);

      // Wire invocation.
      //
      // Subscribe the OUTPUT before the INPUT. Both subscriptions are dispatched onto the
      // single-threaded coreExecutor (runSubscriptionOn), which runs them FIFO. The input
      // subscription immediately starts feeding the state machine and can drive the handler to emit
      // its first output chunk; if that emit happens before the output subscriber is attached,
      // RequestProcessorImpl silently drops the chunk (outputSubscriber == null). Subscribing output
      // first guarantees outputSubscriber is set before any input flows — matching production, where
      // both adapters are wired synchronously before any (async) input arrives.
      AssertSubscriber<Slice> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);
      Multi.createFrom()
          .publisher(handler)
          .runSubscriptionOn(coreExecutor)
          .subscribe(assertSubscriber);
      Multi.createFrom()
          .iterable(definition.getInput())
          .runSubscriptionOn(coreExecutor)
          .map(ProtoUtils::invocationInputToByteString)
          .map(Slice::wrap)
          .subscribe(handler);

      // Check completed
      assertSubscriber.awaitCompletion(Duration.ofSeconds(10000));
      // Unwrap messages and decode them
      //noinspection unchecked
      assertThatDecodingMessages(assertSubscriber.getItems().toArray(Slice[]::new))
          .map(InvocationInput::message)
          .satisfies(l -> definition.getOutputAssert().accept((List<MessageLite>) l));

      // Clean logging
      ThreadContext.clearAll();
    } finally {
      // Orderly drain rather than shutdownNow().isEmpty(): runSubscriptionOn dispatches upstream
      // request/cancel signals onto coreExecutor as separate tasks that aren't ordered against the
      // output stream's onComplete (what awaitCompletion waits on). shutdownNow() would race those
      // still-queued no-op signals. shutdown()+awaitTermination lets them drain, while still
      // failing
      // on a genuinely stuck task.
      coreExecutor.shutdown();
      assertThat(coreExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Override
  public String toString() {
    return "MockRequestResponse/" + stateMachineName;
  }
}
