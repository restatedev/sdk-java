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
import dev.restate.sdk.common.BindableComponent;
import dev.restate.sdk.common.syscalls.ComponentDefinition;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
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

    // Output subscriber buffers all the output messages and provides a completion future
    FlowUtils.FutureSubscriber<MessageLite> outputSubscriber = new FlowUtils.FutureSubscriber<>();

    // This test infra supports only components returning one component definition
    @SuppressWarnings("unchecked")
    BindableComponent<Object> bindableComponent =
        (BindableComponent<Object>) definition.getComponent();
    List<ComponentDefinition<Object>> componentDefinition = bindableComponent.definitions();
    assertThat(componentDefinition).size().isEqualTo(1);

    // Prepare server
    RestateEndpoint.Builder builder =
        RestateEndpoint.newBuilder(DeploymentManifestSchema.ProtocolMode.BIDI_STREAM)
            .with(componentDefinition.get(0), bindableComponent.options());
    RestateEndpoint server = builder.build();

    // Start invocation
    ResolvedEndpointHandler handler =
        server.resolve(
            componentDefinition.get(0).getFullyQualifiedComponentName(),
            definition.getMethod(),
            io.opentelemetry.context.Context.current(),
            RestateEndpoint.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            syscallsExecutor);

    // Create publisher
    FlowUtils.UnbufferedMockPublisher<InvocationFlow.InvocationInput> inputPublisher =
        new FlowUtils.UnbufferedMockPublisher<>();

    // Wire invocation and start it
    syscallsExecutor.execute(
        () -> {
          handler.output().subscribe(outputSubscriber);
          inputPublisher.subscribe(handler.input());
          handler.start();
        });

    // Pipe entries
    for (InvocationFlow.InvocationInput inputEntry : definition.getInput()) {
      syscallsExecutor.execute(() -> inputPublisher.push(inputEntry));
    }
    // Complete the input publisher
    syscallsExecutor.execute(inputPublisher::close);

    // Check completed
    assertThat(outputSubscriber.getFuture())
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(definition.getOutputAssert());
    assertThat(inputPublisher.isSubscriptionCancelled()).isTrue();

    // Clean logging
    ThreadContext.clearAll();
  }
}
