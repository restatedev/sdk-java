// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ProtocolConversation.combinatorTypes;
import static dev.restate.sdk.core.ProtocolConversation.completionIds;
import static dev.restate.sdk.core.ProtocolConversation.proposeRunCompletionAck;
import static dev.restate.sdk.core.ProtocolConversation.sleepCompletion;
import static dev.restate.sdk.core.legacy.ProtoUtils.inputCmd;
import static dev.restate.sdk.core.legacy.ProtoUtils.outputCmd;
import static dev.restate.sdk.core.legacy.ProtoUtils.startMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.restate.common.function.ThrowingBiFunction;
import dev.restate.sdk.Context;
import dev.restate.sdk.DurableFuture;
import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.generated.protocolv7.ProtocolV7;
import dev.restate.sdk.core.generated.protocolv7.ProtocolV7.CombinatorType;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.serde.Serde;
import dev.restate.serde.jackson.JacksonSerdeFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Protocol V7 {@code AwaitingOnMessage}: when user code blocks on futures that need input from the
 * runtime, the SDK reports what it's awaiting, so the runtime can suspend the invocation early. It
 * must be flushed before the SDK parks, otherwise the runtime never sees it. Only the native (FFM)
 * state machine implements V7; those tests are skipped when it's unavailable.
 */
class AwaitingOnTest {

  private static ServiceDefinition service(ThrowingBiFunction<Context, Void, String> body) {
    return ServiceDefinition.of(
        "AwaitingOn",
        ServiceType.SERVICE,
        List.of(
            HandlerDefinition.of(
                "run",
                HandlerType.SHARED,
                Serde.VOID,
                TestSerdes.STRING,
                HandlerRunner.of(body, new JacksonSerdeFactory(), null))));
  }

  private static ProtocolConversation v7(ThrowingBiFunction<Context, Void, String> body)
      throws Exception {
    return ProtocolConversation.v7(service(body), "run");
  }

  @Test
  void flushedBeforeBlockingOnSingleAwait() throws Exception {
    try (var conv =
        v7(
            (ctx, in) -> {
              ctx.sleep(Duration.ofSeconds(1));
              return "done";
            })) {
      conv.send(startMessage(1), inputCmd());
      conv.expect(Protocol.SleepCommandMessage.class);

      // No more input until AwaitingOn is on the wire: if the SDK parks without flushing it,
      // expect() times out.
      var awaitingOn = conv.expect(ProtocolV7.AwaitingOnMessage.class);
      assertThat(completionIds(awaitingOn.getAwaitingOn())).containsExactly(1);
      assertThat(awaitingOn.getExecutingSideEffects()).isFalse();
      // Sent once per await point, not repeated while idle.
      conv.expectNoOutputUntilIdle();

      conv.send(sleepCompletion(1));
      assertThat(conv.expect(Protocol.OutputCommandMessage.class)).isEqualTo(outputCmd("done"));
      conv.expect(Protocol.EndMessage.class);
      conv.expectOutputClosed();
    }
  }

  @Test
  void allReportsTreeAndShrinksAfterPartialProgress() throws Exception {
    try (var conv =
        v7(
            (ctx, in) -> {
              DurableFuture.all(ctx.timer(Duration.ofSeconds(1)), ctx.timer(Duration.ofSeconds(2)))
                  .await();
              return "done";
            })) {
      conv.send(startMessage(1), inputCmd());
      conv.expect(Protocol.SleepCommandMessage.class);
      conv.expect(Protocol.SleepCommandMessage.class);

      var first = conv.expect(ProtocolV7.AwaitingOnMessage.class).getAwaitingOn();
      assertThat(completionIds(first)).containsExactlyInAnyOrder(1, 2);
      assertThat(combinatorTypes(first)).contains(CombinatorType.ALL_SUCCEEDED_OR_FIRST_FAILED);

      // Still blocked after one completion: a new AwaitingOn with what's left.
      conv.send(sleepCompletion(1));
      var second = conv.expect(ProtocolV7.AwaitingOnMessage.class).getAwaitingOn();
      assertThat(completionIds(second)).containsExactly(2);

      conv.send(sleepCompletion(2));
      assertThat(conv.expect(Protocol.OutputCommandMessage.class)).isEqualTo(outputCmd("done"));
      conv.expect(Protocol.EndMessage.class);
    }
  }

  @Test
  void anyReportsFirstCompletedTree() throws Exception {
    try (var conv =
        v7(
            (ctx, in) ->
                "winner-"
                    + DurableFuture.any(
                            ctx.timer(Duration.ofSeconds(1)), ctx.timer(Duration.ofSeconds(2)))
                        .await())) {
      conv.send(startMessage(1), inputCmd());
      conv.expect(Protocol.SleepCommandMessage.class);
      conv.expect(Protocol.SleepCommandMessage.class);

      var tree = conv.expect(ProtocolV7.AwaitingOnMessage.class).getAwaitingOn();
      assertThat(completionIds(tree)).containsExactlyInAnyOrder(1, 2);
      assertThat(combinatorTypes(tree))
          .contains(CombinatorType.FIRST_COMPLETED)
          .doesNotContain(CombinatorType.ALL_SUCCEEDED_OR_FIRST_FAILED);

      conv.send(sleepCompletion(2));
      assertThat(conv.expect(Protocol.OutputCommandMessage.class)).isEqualTo(outputCmd("winner-1"));
      conv.expect(Protocol.EndMessage.class);
    }
  }

  @Test
  void notSentWhileRunExecutesThenSentWhileWaitingForAck() throws Exception {
    var runStarted = new CountDownLatch(1);
    var releaseRun = new CountDownLatch(1);
    try (var conv =
        v7(
            (ctx, in) ->
                ctx.run(
                    "side-effect",
                    String.class,
                    () -> {
                      runStarted.countDown();
                      releaseRun.await();
                      return "ran";
                    }))) {
      conv.send(startMessage(1), inputCmd());
      conv.expect(Protocol.RunCommandMessage.class);

      assertThat(runStarted.await(10, TimeUnit.SECONDS)).isTrue();
      // The handler awaits the run, but the run is still executing: no AwaitingOn.
      conv.expectNoOutputUntilIdle();

      releaseRun.countDown();
      conv.expect(Protocol.ProposeRunCompletionMessage.class);
      // Now it's waiting for the runtime to ack the proposal.
      var awaitingOn = conv.expect(ProtocolV7.AwaitingOnMessage.class);
      assertThat(completionIds(awaitingOn.getAwaitingOn())).containsExactly(1);
      assertThat(awaitingOn.getExecutingSideEffects()).isFalse();

      conv.send(proposeRunCompletionAck(1));
      assertThat(conv.expect(Protocol.OutputCommandMessage.class)).isEqualTo(outputCmd("ran"));
      conv.expect(Protocol.EndMessage.class);
    } finally {
      releaseRun.countDown();
    }
  }

  @Test
  void inputCloseAfterAwaitingOnSuspendsOnSameTree() throws Exception {
    try (var conv =
        v7(
            (ctx, in) -> {
              ctx.sleep(Duration.ofSeconds(1));
              return "done";
            })) {
      conv.send(startMessage(1), inputCmd());
      conv.expect(Protocol.SleepCommandMessage.class);
      var awaitingOn = conv.expect(ProtocolV7.AwaitingOnMessage.class);

      conv.closeInput();
      var suspension = conv.expect(ProtocolV7.SuspensionMessage.class);
      assertThat(suspension.getAwaitingOn()).isEqualTo(awaitingOn.getAwaitingOn());
      conv.expectOutputClosed();
    }
  }

  static Stream<Arguments> stateMachines() {
    return Stream.of(
        Arguments.of("Legacy", StateMachineFactory.Loader.LEGACY_FACTORY),
        Arguments.of("FFM", StateMachineFactory.Loader.FFM_FACTORY));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateMachines")
  void notSentBelowV7(String name, StateMachineFactory stateMachineFactory) throws Exception {
    assumeTrue(stateMachineFactory != null, name + " state machine unavailable");
    try (var conv =
        new ProtocolConversation(
            service(
                (ctx, in) -> {
                  ctx.sleep(Duration.ofSeconds(1));
                  return "done";
                }),
            "run",
            stateMachineFactory,
            ProtocolConversation.V6)) {
      conv.send(startMessage(1), inputCmd());
      conv.expect(Protocol.SleepCommandMessage.class);
      conv.expectNoOutputUntilIdle();

      conv.closeInput();
      conv.expect(Protocol.SuspensionMessage.class);
      conv.expectOutputClosed();
    }
  }
}
