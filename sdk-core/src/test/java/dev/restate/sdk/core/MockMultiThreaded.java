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
import dev.restate.sdk.core.impl.MessageDecoder;
import dev.restate.sdk.core.impl.ServiceProtocol;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.definition.HandlerProcessor;
import dev.restate.sdk.definition.ServiceDefinition;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.time.Duration;
import java.util.List;
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
    EndpointImpl.Builder builder =
        EndpointImpl.newBuilder(EndpointManifestSchema.ProtocolMode.BIDI_STREAM)
            .bind(
                (ServiceDefinition<? super Object>) serviceDefinition,
                definition.getServiceOptions());
    if (definition.isEnablePreviewContext()) {
      builder.enablePreviewContext();
    }
    EndpointImpl server = builder.build();

    // Start invocation
    HandlerProcessor handler =
        server.resolve(
            ServiceProtocol.serviceProtocolVersionToHeaderValue(
                ServiceProtocol.maxServiceProtocolVersion(definition.isEnablePreviewContext())),
            serviceDefinition.getServiceName(),
            definition.getMethod(),
            k -> null,
            io.opentelemetry.context.Context.current(),
            EndpointImpl.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            syscallsExecutor);

    // Wire invocation
    AssertSubscriber<InvocationInput> assertSubscriber = AssertSubscriber.create(Long.MAX_VALUE);

    // Wire invocation and start it
    Multi.createFrom()
        .iterable(definition.getInput())
        .runSubscriptionOn(syscallsExecutor)
        .map(ProtoUtils::invocationInputToByteString)
        .subscribe(handler);
    Multi.createFrom()
        .publisher(handler)
        .runSubscriptionOn(syscallsExecutor)
        .subscribe(new MessageDecoder(assertSubscriber));

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
