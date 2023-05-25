package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Empty;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.Awaitable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
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

  private static class ManySleeps extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();
      List<Awaitable<?>> collectedAwaitables = new ArrayList<>();

      for (int i = 0; i < 10; i++) {
        collectedAwaitables.add(ctx.timer(Duration.ofMillis(1000)));
      }

      Awaitable.all(
              collectedAwaitables.get(0),
              collectedAwaitables.get(1),
              collectedAwaitables.subList(2, collectedAwaitables.size()).toArray(Awaitable[]::new))
          .await();

      responseObserver.onNext(GreetingResponse.newBuilder().build());
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
            .named("Sleep 1000 ms sleep completed"),
        testInvocation(new SleepGreeter(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Protocol.SleepEntryMessage.newBuilder()
                    .setWakeUpTime(Instant.now().toEpochMilli())
                    .build())
            .usingAllThreadingModels()
            .expectingOutput(suspensionMessage(1))
            .named("Sleep 1000 ms still sleeping"),
        testInvocation(new ManySleeps(), GreeterGrpc.getGreetMethod())
            .withInput(
                Stream.concat(
                        Stream.of(
                            startMessage(11),
                            inputMessage(GreetingRequest.newBuilder().setName("Till"))),
                        IntStream.rangeClosed(1, 10)
                            .mapToObj(
                                i ->
                                    (i % 3 == 0)
                                        ? Protocol.SleepEntryMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .setResult(Empty.getDefaultInstance())
                                            .build()
                                        : Protocol.SleepEntryMessage.newBuilder()
                                            .setWakeUpTime(Instant.now().toEpochMilli())
                                            .build()))
                    .toArray(MessageLiteOrBuilder[]::new))
            .usingAllThreadingModels()
            .expectingOutput(suspensionMessage(1, 2, 4, 5, 7, 8, 10))
            .named("Sleep 1000 ms sleep completed"));
  }
}
