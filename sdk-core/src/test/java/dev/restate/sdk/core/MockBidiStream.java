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

import com.google.protobuf.MessageLite;
import dev.restate.common.Slice;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.core.statemachine.ProtoUtils;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.ThreadContext;

public final class MockBidiStream implements TestDefinitions.TestExecutor {

  public static final MockBidiStream INSTANCE = new MockBidiStream();

  private MockBidiStream() {}

  @Override
  public boolean buffered() {
    return false;
  }

  @Override
  public void executeTest(TestDefinitions.TestDefinition definition) {
    Executor coreExecutor = Executors.newSingleThreadExecutor();

    // This test infra supports only services returning one service definition
    ServiceDefinition serviceDefinition = definition.getServiceDefinition();

    // Prepare server
    Endpoint.Builder builder =
        Endpoint.builder().bind(serviceDefinition, definition.getServiceOptions());
    if (definition.isEnablePreviewContext()) {
      builder.enablePreviewContext();
    }
    EndpointRequestHandler server = EndpointRequestHandler.forBidiStream(builder.build());

    // Start invocation
    RequestProcessor handler =
        server.processorForRequest(
            "/" + serviceDefinition.getServiceName() + "/" + definition.getMethod(),
            HeadersAccessor.wrap(
                Map.of("content-type", ProtoUtils.serviceProtocolContentTypeHeader())),
            EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            coreExecutor);

    // Wire invocation
    AssertSubscriber<Slice> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);

    // Wire invocation and start it
    Multi.createFrom()
        .iterable(definition.getInput())
        .runSubscriptionOn(coreExecutor)
        .map(ProtoUtils::invocationInputToByteString)
        .map(Slice::wrap)
        .paceDemand()
        .using(inputPacer(definition.getInput()))
        .emitOn(coreExecutor)
        .subscribe(handler);
    Multi.createFrom()
        .publisher(handler)
        .runSubscriptionOn(coreExecutor)
        .subscribe(assertSubscriber);

    // Check completed
    assertSubscriber.awaitCompletion(Duration.ofSeconds(10));

    // Unwrap messages and decode them
    //noinspection unchecked
    assertThatDecodingMessages(assertSubscriber.getItems().toArray(Slice[]::new))
        .map(InvocationInput::message)
        .satisfies(l -> definition.getOutputAssert().accept((List<MessageLite>) l));

    // Clean logging
    ThreadContext.clearAll();
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
}
