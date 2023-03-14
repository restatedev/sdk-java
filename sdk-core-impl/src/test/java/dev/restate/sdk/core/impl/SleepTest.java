package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Empty;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

public class SleepTest extends CoreTestRunner {

  Long startTime = System.currentTimeMillis();

  private static class SleepGreeter extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.sleep(Duration.ofMillis(1000));

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello").build());
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new SleepGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites.get(0)).isInstanceOf(Protocol.SleepEntryMessage.class);
                  Protocol.SleepEntryMessage msg = (Protocol.SleepEntryMessage) messageLites.get(0);
                  assertThat(msg.getWakeUpTime()).isGreaterThanOrEqualTo(startTime + 1000);
                  assertThat(msg.getWakeUpTime())
                      .isLessThanOrEqualTo(Instant.now().toEpochMilli() + 1000);

                  assertThat(messageLites.get(1)).isInstanceOf(Protocol.SuspensionMessage.class);
                })
            .named("Sleep 1000 ms not completed"),
        testInvocation(new SleepGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .setResult(Empty.getDefaultInstance())
                    .build())
            .usingAllThreadingModels()
            .expectingOutput(
                outputMessage(GreetingResponse.newBuilder().setMessage("Hello").build()))
            .named("Sleep 1000 ms still sleeping"),
        testInvocation(new SleepGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .build())
            .usingAllThreadingModels()
            .expectingOutput(suspensionMessage(1))
            .named("Sleep 1000 ms sleep completed"));
  }
}
