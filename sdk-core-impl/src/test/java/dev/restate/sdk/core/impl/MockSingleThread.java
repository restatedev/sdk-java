package dev.restate.sdk.core.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.core.impl.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.impl.TestDefinitions.TestExecutor;
import io.grpc.ServerServiceDefinition;
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

    ServerServiceDefinition svc = definition.getService().bindService();

    // Start invocation
    RestateGrpcServer server =
        RestateGrpcServer.newBuilder(Discovery.ProtocolMode.BIDI_STREAM).withService(svc).build();
    InvocationHandler handler =
        server.resolve(
            svc.getServiceDescriptor().getName(),
            definition.getMethod(),
            io.opentelemetry.context.Context.current(),
            RestateGrpcServer.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            null,
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
        .succeedsWithin(Duration.ZERO)
        .satisfies(definition.getOutputAssert());
    assertThat(inputPublisher.isSubscriptionCancelled()).isTrue();

    // Clean logging
    ThreadContext.clearAll();
  }
}
