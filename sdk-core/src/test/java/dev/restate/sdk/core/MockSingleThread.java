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
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import java.time.Duration;
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
    // Output subscriber buffers all the output messages and provides a completion future
    FlowUtils.FutureSubscriber<MessageLite> outputSubscriber = new FlowUtils.FutureSubscriber<>();

    ServiceDefinition<?> serviceDefinition = definition.getServiceDefinition();

    // Prepare server
    @SuppressWarnings("unchecked")
    RestateEndpoint.Builder builder =
        RestateEndpoint.newBuilder(EndpointManifestSchema.ProtocolMode.BIDI_STREAM)
            .bind(
                (ServiceDefinition<? super Object>) serviceDefinition,
                definition.getServiceOptions());
    RestateEndpoint server = builder.build();

    // Start invocation
    ResolvedEndpointHandler handler =
        server.resolve(
            serviceDefinition.getServiceName(),
            definition.getMethod(),
            k -> null,
            io.opentelemetry.context.Context.current(),
            RestateEndpoint.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            null);

    // Create publisher
    FlowUtils.BufferedMockPublisher<InvocationFlow.InvocationInput> inputPublisher =
        new FlowUtils.BufferedMockPublisher<>(definition.getInput());

    // Wire invocation
    handler.output().subscribe(outputSubscriber);
    inputPublisher.subscribe(handler.input());

    // Start invocation
    handler.start();

    // Check completed
    assertThat(outputSubscriber.getFuture())
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(definition.getOutputAssert());
    assertThat(inputPublisher.isSubscriptionCancelled()).isTrue();

    // Clean logging
    ThreadContext.clearAll();
  }
}
