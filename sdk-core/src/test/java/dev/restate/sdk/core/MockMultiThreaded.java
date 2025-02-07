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
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.core.statemachine.ProtoUtils;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.ThreadContext;

public final class MockMultiThreaded implements TestDefinitions.TestExecutor {

  public static final MockMultiThreaded INSTANCE = new MockMultiThreaded();

  private MockMultiThreaded() {}

  @Override
  public boolean buffered() {
    return false;
  }

  @Override
  public void executeTest(TestDefinitions.TestDefinition definition) {
    Executor syscallsExecutor = Executors.newSingleThreadExecutor();

    // This test infra supports only services returning one service definition
    ServiceDefinition<?> serviceDefinition = definition.getServiceDefinition();

    // Prepare server
    @SuppressWarnings("unchecked")
    Endpoint.Builder builder =
        Endpoint.builder()
            .bind(
                (ServiceDefinition<? super Object>) serviceDefinition,
                definition.getServiceOptions());
    if (definition.isEnablePreviewContext()) {
      builder.enablePreviewContext();
    }
    EndpointRequestHandler server = EndpointRequestHandler.forBidiStream(builder.build());

    // Start invocation
    RequestProcessor handler =
        server.processorForRequest(
                "/" + serviceDefinition.getServiceName() + "/" + definition.getMethod(),
                HeadersAccessor.wrap(Map.of(
                        "content-type",
                        ProtoUtils.serviceProtocolContentTypeHeader()
                )),
EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            syscallsExecutor);

    // Wire invocation
    AssertSubscriber<Slice> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);

    // Wire invocation and start it
    Multi.createFrom()
        .iterable(definition.getInput())
        .runSubscriptionOn(syscallsExecutor)
        .map(ProtoUtils::invocationInputToByteString)
            .map(Slice::wrap)
        .subscribe(handler);
    Multi.createFrom()
        .publisher(handler)
        .runSubscriptionOn(syscallsExecutor)
        .subscribe(assertSubscriber);

    // Check completed
    assertSubscriber.awaitCompletion(Duration.ofSeconds(1));

    // Unwrap messages and decode them
      //noinspection unchecked
      assertThatDecodingMessages(assertSubscriber.getItems().toArray(Slice[]::new))
        .map(InvocationInput::message)
        .satisfies(l -> definition.getOutputAssert().accept((List<MessageLite>) l));

    // Clean logging
    ThreadContext.clearAll();
  }
}
