package dev.restate.sdk.core.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.discovery.Discovery;
import io.grpc.ServerServiceDefinition;
import java.time.Duration;
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
    Executor userExecutor = Executors.newSingleThreadExecutor();

    // Output subscriber buffers all the output messages and provides a completion future
    FlowUtils.FutureSubscriber<MessageLite> outputSubscriber = new FlowUtils.FutureSubscriber<>();

    ServerServiceDefinition svc = definition.getService().bindService();

    // Prepare server
    RestateGrpcServer.Builder builder =
        RestateGrpcServer.newBuilder(Discovery.ProtocolMode.BIDI_STREAM).withService(svc);
    RestateGrpcServer server = builder.build();

    // Start invocation
    InvocationHandler handler =
        server.resolve(
            svc.getServiceDescriptor().getName(),
            definition.getMethod(),
            io.opentelemetry.context.Context.current(),
            RestateGrpcServer.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            syscallsExecutor,
            userExecutor);

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
