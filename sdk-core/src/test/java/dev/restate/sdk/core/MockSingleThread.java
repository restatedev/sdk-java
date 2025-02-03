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
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.impl.MessageDecoder;
import dev.restate.sdk.core.impl.ServiceProtocol;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.definition.HandlerProcessor;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.time.Duration;
import java.util.List;
import org.apache.logging.log4j.ThreadContext;

public final class MockSingleThread implements TestExecutor {

  public static final MockSingleThread INSTANCE = new MockSingleThread();

  private MockSingleThread() {}

  @Override
  public boolean buffered() {
    return true;
  }

  @Override
  public void executeTest(TestDefinition definition) {
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
    EndpointRequestHandler server = EndpointRequestHandler.forRequestResponse(builder.build());

    // Start invocation
    StaticResponseRequestProcessor handler =
        server.handleDiscoveryRequest(
            ServiceProtocol.serviceProtocolVersionToHeaderValue(
                ServiceProtocol.maxServiceProtocolVersion(definition.isEnablePreviewContext())),
            serviceDefinition.getServiceName(),
            definition.getMethod(),
            k -> null,
            io.opentelemetry.context.Context.current(),
            EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            null);

    // Wire invocation
    AssertSubscriber<InvocationInput> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);
    Multi.createFrom()
        .iterable(definition.getInput())
        .map(ProtoUtils::invocationInputToByteString)
        .subscribe(handler);
    Multi.createFrom().publisher(handler).subscribe(new MessageDecoder(assertSubscriber));

    // Check completed
    assertSubscriber.awaitCompletion(Duration.ofSeconds(1));
    // Unwrap messages
    //noinspection unchecked
    assertThat(assertSubscriber.getItems())
        .map(InvocationInput::message)
        .satisfies(l -> definition.getOutputAssert().accept((List<MessageLite>) l));

    // Clean logging
    ThreadContext.clearAll();
  }
}
