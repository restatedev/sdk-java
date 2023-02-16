package services;

import dev.restate.sdk.blocking.Awaitable;
import dev.restate.sdk.blocking.Awakeable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.testing.testservices.*;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keyed service. key = name
 */
public class GreeterOne extends GreeterOneGrpc.GreeterOneImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(GreeterOne.class);
  private static final StateKey<String> STATE = StateKey.of("STATE", TypeTag.STRING_UTF8);

  private static final StateKey<Integer> COUNTER =
      StateKey.of(
          "COUNTER",
          TypeTag.using(
              i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
              b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

  @Override
  public void greet(
          GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.greet method");

    responseObserver.onNext(GreeterOneResponse.newBuilder().setMessage("Hello " + request.getName()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void storeAndGreet(
          GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.storeAndGreet method");

    restateContext().set(STATE, request.getName());
    String state = restateContext().get(STATE).get();

    responseObserver.onNext(GreeterOneResponse.newBuilder().setMessage("Hello " + state).build());
    responseObserver.onCompleted();
  }

  @Override
  public void countGreetings(
          GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.countGreetings method");
    RestateContext ctx = restateContext();

    Optional<Integer> optionalOldCount = ctx.get(COUNTER);

    // increment old count by one and write back to state
    var newCount = 1;
    if (optionalOldCount.isPresent()) {
      var oldCount = optionalOldCount.get();
      newCount = oldCount + newCount;
    }
    ctx.set(COUNTER, newCount);

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
            .setMessage("Hello " + request.getName() + " #" + newCount)
            .build());
    responseObserver.onCompleted();
  }


  @Override
  public void resetGreetingCounter(GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.resetGreetingCounter method");
    RestateContext ctx = restateContext();

    ctx.clear(COUNTER);

    // get the state; it should be empty
    Optional<Integer> optionalClearedCount = ctx.get(COUNTER);

    var msg = optionalClearedCount.isPresent() ? "State did not get cleared" : "State got cleared";

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
                    .setMessage(msg)
                    .build());
    responseObserver.onCompleted();
  }

  @Override
  public void forwardGreeting(GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.forwardGreeting method");
    RestateContext ctx = restateContext();

    Awaitable<GreeterTwoResponse> a1 =
            ctx.call(GreeterTwoGrpc.getCountForwardedGreetingsMethod(),
                    GreeterTwoRequest.newBuilder().setName(request.getName()).build());

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
                    .setMessage("Greeting has been forwarded to GreeterTwo. Response was: " + a1.await().getMessage())
                    .build());
    responseObserver.onCompleted();
  }

  @Override
  public void forwardBackgroundGreeting(GreeterOneRequest request,
                                        StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.forwardBackgroundGreeting method");
    RestateContext ctx = restateContext();

    ctx.backgroundCall(GreeterTwoGrpc.getCountForwardedGreetingsMethod(),
            GreeterTwoRequest.newBuilder().setName(request.getName()).build());

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
                    .setMessage("Greeting has been forwarded to GreeterTwo! Not waiting for a response.")
                    .build());
    responseObserver.onCompleted();
  }

  @Override
  public void getMultipleGreetings(GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.getMultipleGreetings method");
    RestateContext ctx = restateContext();

    Awaitable<GreeterTwoResponse> a1 =
            ctx.call(GreeterTwoGrpc.getCountForwardedGreetingsMethod(),
                    GreeterTwoRequest.newBuilder().setName(request.getName()).build());

    Awaitable<GreeterTwoResponse> a2 =
            ctx.call(GreeterTwoGrpc.getCountForwardedGreetingsMethod(),
                    GreeterTwoRequest.newBuilder().setName(request.getName()).build());

    Awaitable.all(a1, a2).await();

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
                    .setMessage("Two greetings have been forwarded to GreeterTwo! Response: "
                            + a1.await().getMessage() + ", " + a2.await().getMessage())
                    .build());
    responseObserver.onCompleted();
  }

  @Override
  public void getOneOfMultipleGreetings(GreeterOneRequest request,
                                        StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.getOneOfMultipleGreetings method");
    RestateContext ctx = restateContext();

    Awaitable<GreeterTwoResponse> a1 =
            ctx.call(GreeterTwoGrpc.getCountForwardedGreetingsMethod(),
                    GreeterTwoRequest.newBuilder().setName(request.getName()).build());

    Awaitable<GreeterTwoResponse> a2 =
            ctx.call(GreeterTwoGrpc.getCountForwardedGreetingsMethod(),
                    GreeterTwoRequest.newBuilder().setName(request.getName()).build());

    GreeterTwoResponse response = (GreeterTwoResponse) Awaitable.any(a1, a2).await();

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
                    .setMessage("Two greetings have been forwarded to GreeterTwo! Response: " + response.getMessage())
                    .build());
    responseObserver.onCompleted();
  }

  @Override
  public void failingGreet(GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.failingGreet method");
    throw new IllegalStateException("Whatever");
  }

  @Override
  public void greetWithSideEffect(GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.greetWithSideEffect method");
    RestateContext ctx = restateContext();

    ctx.sideEffect(TypeTag.STRING_UTF8, () -> "some-result");

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
                    .setMessage("Hello")
                    .build());
    responseObserver.onCompleted();
  }

  @Override
  public void sleepAndGetWokenUp(GreeterOneRequest request, StreamObserver<GreeterOneResponse> responseObserver) {
    LOG.debug("Executing the GreeterOne.sleepAndGetWokenUp method");
    RestateContext ctx = restateContext();

    // Create awakeable identifier to be woken up with
    Awakeable<String> a1 = ctx.awakeable(TypeTag.STRING_UTF8);

    // Tell GreeterTwo to wake us up with the awakeable identifier.
    AwakeServiceRequest info = AwakeServiceRequest.newBuilder()
            .setServiceName(a1.id().getServiceName())
            .setInstanceKey(a1.id().getInstanceKey())
            .setEntryIndex(a1.id().getEntryIndex())
            .setInvocationId(a1.id().getInvocationId())
            .build();
    ctx.backgroundCall(AwakeServiceGrpc.getAwakeMethod(), info);

    // Suspend until GreeterTwo wakes us up.
    String output = a1.await();

    responseObserver.onNext(
            GreeterOneResponse.newBuilder()
                    .setMessage(output)
                    .build());
    responseObserver.onCompleted();
  }
}
