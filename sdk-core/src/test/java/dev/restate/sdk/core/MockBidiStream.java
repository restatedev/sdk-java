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
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.legacy.InvocationInput;
import dev.restate.sdk.core.legacy.ProtoUtils;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.mutiny.subscription.DemandPacer;
import io.smallrye.mutiny.subscription.FixedDemandPacer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.ThreadContext;

public final class MockBidiStream implements TestDefinitions.TestExecutor {

  private final String stateMachineName;
  private final StateMachineFactory stateMachineFactory;

  private MockBidiStream(String stateMachineName, StateMachineFactory stateMachineFactory) {
    this.stateMachineName = stateMachineName;
    this.stateMachineFactory = stateMachineFactory;
  }

  public static Stream<TestDefinitions.TestExecutor> getExecutors() {
    return Stream.of(
        new MockBidiStream("FFM", StateMachineFactory.Loader.FFM_FACTORY),
        new MockBidiStream("Legacy", StateMachineFactory.Loader.LEGACY_FACTORY));
  }

  @Override
  public boolean buffered() {
    return false;
  }

  @Override
  public void executeTest(TestDefinitions.TestDefinition definition) throws Exception {
    ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
    try {
      // This test infra supports only services returning one service definition
      ServiceDefinition serviceDefinition = definition.getServiceDefinition();

      // Prepare server
      Endpoint.Builder builder =
          Endpoint.builder().bind(serviceDefinition, definition.getServiceOptions());
      if (definition.isEnablePreviewContext()) {
        builder.enablePreviewContext();
      }
      EndpointRequestHandler server =
          new EndpointRequestHandler(null, builder.build(), stateMachineFactory);

      // Initialize and wire the request processor on the coreExecutor thread. In production the
      // processor is created and both I/O adapters are subscribed on the same I/O thread, before
      // any
      // (async) input arrives; mirror that here so (a) the native state machine is created on the
      // thread that drives it, and (b) input cannot drive the handler to emit before the output
      // subscriber is attached.
      //
      // Within that setup, subscribe the OUTPUT before the INPUT: both subscriptions run FIFO on
      // the
      // single-threaded coreExecutor, so the output subscriber is attached before input starts
      // feeding the state machine. Otherwise RequestProcessorImpl would silently drop any output
      // chunk emitted while outputSubscriber == null.
      AssertSubscriber<Slice> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);
      try {
        coreExecutor
            .submit(
                () -> {
                  RequestProcessor handler =
                      server.processorForRequest(
                          "/" + serviceDefinition.getServiceName() + "/" + definition.getMethod(),
                          HeadersAccessor.wrap(
                              Map.of(
                                  "content-type", ProtoUtils.serviceProtocolContentTypeHeader())),
                          EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
                          coreExecutor,
                          true);
                  Multi.createFrom()
                      .publisher(handler)
                      .runSubscriptionOn(coreExecutor)
                      .subscribe(assertSubscriber);
                  Multi.createFrom()
                      .iterable(definition.getInput())
                      .runSubscriptionOn(coreExecutor)
                      .map(ProtoUtils::invocationInputToByteString)
                      .map(Slice::wrap)
                      .paceDemand()
                      .using(inputPacer(definition.getInput()))
                      .emitOn(coreExecutor)
                      .subscribe(handler);
                  return null;
                })
            .get();
      } catch (ExecutionException e) {
        if (e.getCause() instanceof Exception cause) {
          throw cause;
        }
        throw e;
      }

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

  private DemandPacer inputPacer(List<InvocationInput> input) {
    if (input.get(0).message() instanceof Protocol.StartMessage startMessage) {
      int knownEntries = startMessage.getKnownEntries();
      if (knownEntries != input.size() - 1) {
        // We're sending a journal to replay plus more stuff, let's pace after the replay ends
        return new FixedDemandPacer(knownEntries + 1, Duration.ofMillis(200));
      }
    }
    // We're only sending a journal to replay, or we're not sending start message, let's just pace
    // right in the middle
    return new FixedDemandPacer(Math.min(1, input.size() / 2), Duration.ofMillis(100));
  }

  @Override
  public String toString() {
    return "MockBidiStream/" + stateMachineName;
  }
}
