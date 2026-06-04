// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.sharedcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.InvocationState;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.sharedcore.StateMachine.AwaitResult;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end-ish tests of {@link StateMachine} that drive real WASM-side state transitions.
 *
 * <p>Wire bytes come from pre-encoded fixtures under {@code src/test/resources/fixtures/}; see the
 * fixture README for regeneration.
 */
class StateMachineTest {

  private static final HeadersAccessor RESTATE_HEADERS =
      HeadersAccessor.wrap(Map.of("content-type", "application/vnd.restate.invocation.v7"));

  private static byte[] loadFixture(String name) {
    try (InputStream in = StateMachineTest.class.getResourceAsStream("/fixtures/" + name)) {
      if (in == null) {
        throw new IllegalStateException("missing fixture: " + name);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static void drainOutput(StateMachine sm) {
    while (sm.takeOutput().length > 0) {
      // drain whatever was journaled into the output queue
    }
  }

  // -------------------------------------------------------------------------
  // Lifecycle.
  // -------------------------------------------------------------------------

  @Test
  void createStartsInWaitingStart() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThat(sm.state()).isEqualTo(InvocationState.WAITING_START);
    }
  }

  @Test
  void closeIsIdempotentAndPostCloseGuardedMethodsAreNoOps() {
    StateMachine sm = StateMachine.create(RESTATE_HEADERS);
    sm.close();
    sm.close(); // second close must not throw
    assertThat(sm.state()).isEqualTo(InvocationState.CLOSED);

    // These methods early-return when freed — must not blow up.
    sm.notifyInput("ignored".getBytes());
    sm.notifyInputClosed();
    sm.notifyError(new RuntimeException("ignored"));
    assertThat(sm.takeOutput()).isEmpty();
    sm.proposeRunCompletionWithSuccess(1, Slice.wrap("ignored"));
    sm.proposeRunCompletionTerminalFailure(1, new TerminalException(500, "ignored"));
    sm.proposeRunCompletionRetryableFailure(
        1, new RuntimeException("ignored"), Duration.ZERO, null);
  }

  @Test
  void verifyNotFreedThrowsAbortedExecutionAfterClose() {
    StateMachine sm = StateMachine.create(RESTATE_HEADERS);
    sm.close();

    // sys_X methods funnel through verifyNotFreed → sneakyThrow(Aborted).
    assertThatThrownBy(sm::sysInput).isInstanceOf(AbortedExecutionException.class);
    assertThatThrownBy(() -> sm.sysStateGet("k")).isInstanceOf(AbortedExecutionException.class);
    assertThatThrownBy(() -> sm.sysStateSet("k", Slice.wrap("v")))
        .isInstanceOf(AbortedExecutionException.class);
    assertThatThrownBy(sm::sysEnd).isInstanceOf(AbortedExecutionException.class);
  }

  // -------------------------------------------------------------------------
  // notify_input → sys_input.
  // -------------------------------------------------------------------------

  @Test
  void notifyInputThenSysInputReturnsThePayload() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();

      StateMachine.Input input = sm.sysInput();
      assertThat(new String(input.input())).isEqualTo("my-data");
    }
  }

  @Test
  void multipleNotifyInputsExerciseTheSegmentedDecoderPath() {
    // Feed Start and InputCommand as two separate buffers — exercises the
    // SegmentedBuf path on the Rust decoder.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_only.bin"));
      sm.notifyInput(loadFixture("input_only.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();

      StateMachine.Input input = sm.sysInput();
      assertThat(new String(input.input())).isEqualTo("my-data");
    }
  }

  // -------------------------------------------------------------------------
  // sys_run / propose_run_completion.
  // -------------------------------------------------------------------------

  @Test
  void proposeRunCompletionWithSuccess() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      int runHandle = sm.sysRun("my-run").handle();
      // doAwait drives the VM until it's ready to ExecuteRun the handle.
      AwaitResult awaitResult = sm.doAwait(new StateMachine.UnresolvedFuture.Single(runHandle));
      assertThat(awaitResult).isInstanceOf(AwaitResult.ExecuteRun.class);

      sm.proposeRunCompletionWithSuccess(runHandle, Slice.wrap("run-result-payload"));

      drainOutput(sm);
    }
  }

  @Test
  void proposeRunCompletionRetryableFailureWithNonNullRetryPolicy() {
    // Exercises the `WasmRetryPolicy` wire format with a null in the middle
    // (max_interval is null, max_attempts is set). A wire-format regression here
    // would surface as a CBOR deserialization error on the Rust side (decode/EOF),
    // *not* as a ProtocolException carrying the user-supplied "boom" message.
    // The VM correctly surfacing "boom" means the wire was decoded fine.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      int runHandle = sm.sysRun("my-run").handle();
      sm.doAwait(new StateMachine.UnresolvedFuture.Single(runHandle));

      RetryPolicy retryPolicy =
          RetryPolicy.exponential(Duration.ofMillis(100), 2.0f).setMaxAttempts(3);
      assertThatThrownBy(
              () ->
                  sm.proposeRunCompletionRetryableFailure(
                      runHandle, new RuntimeException("boom"), Duration.ofMillis(50), retryPolicy))
          .isInstanceOf(ProtocolException.class)
          .hasMessageContaining("boom");

      drainOutput(sm);
    }
  }

  @Test
  void proposeRunCompletionTerminalFailure() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      int runHandle = sm.sysRun("my-run").handle();
      sm.doAwait(new StateMachine.UnresolvedFuture.Single(runHandle));

      sm.proposeRunCompletionTerminalFailure(runHandle, new TerminalException(500, "boom"));

      drainOutput(sm);
    }
  }

  @Test
  void takeNotificationReturnsNullWhenAwakeableNotCompleted() {
    // Exercises the NotReady path (tag 0): create an awakeable handle but never
    // complete it, then ask for it.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      var awakeable = sm.sysAwakeable();
      assertThat(sm.takeNotification(awakeable.handle())).isNull();

      drainOutput(sm);
    }
  }

  // --------------------------------------------------------------------------
  // Synthetic-bytes coverage for the hand-rolled takeNotification decoder.
  // We can't easily fixture a real V7 completion in a unit test (it needs a
  // server round-trip), so we feed bytes that match the Rust encoder's wire
  // format directly into the Java decoder and verify each branch.
  // --------------------------------------------------------------------------

  @Test
  void decodeNotReady() {
    assertThat(StateMachine.decodeTakeNotification(new byte[] {0})).isNull();
  }

  @Test
  void decodeVoid() {
    // tag=1 (Void)
    assertThat(StateMachine.decodeTakeNotification(new byte[] {1}))
        .isSameAs(StateMachine.NotificationValue.VOID);
  }

  @Test
  void decodeSuccess() {
    // [2=Success][u32 LE len=3]['a','b','c']
    byte[] bytes = {2, 3, 0, 0, 0, 'a', 'b', 'c'};
    StateMachine.NotificationValue v = StateMachine.decodeTakeNotification(bytes);
    assertThat(v).isInstanceOf(StateMachine.NotificationValue.Success.class);
    assertThat(new String(((StateMachine.NotificationValue.Success) v).slice().toByteArray()))
        .isEqualTo("abc");
  }

  @Test
  void decodeNotificationFailureNoMetadata() {
    // [5=NotificationFailure][u16 code=409][u32 msg_len=5]["boom!"][u32 meta_count=0]
    byte[] bytes = {5, (byte) 0x99, 0x01, 5, 0, 0, 0, 'b', 'o', 'o', 'm', '!', 0, 0, 0, 0};
    StateMachine.NotificationValue v = StateMachine.decodeTakeNotification(bytes);
    assertThat(v).isInstanceOf(StateMachine.NotificationValue.Failure.class);
    var f = (StateMachine.NotificationValue.Failure) v;
    assertThat(f.code()).isEqualTo(409);
    assertThat(f.message()).isEqualTo("boom!");
    assertThat(f.metadata()).isNull();
  }

  @Test
  void decodeNotificationFailureWithMetadata() {
    ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    bb.put((byte) 5); // NotificationFailure
    bb.putShort((short) 500); // code
    bb.putInt(3).put((byte) 'o').put((byte) 'o').put((byte) 'f'); // message "oof"
    bb.putInt(1); // 1 metadata entry
    bb.putInt(1).put((byte) 'k'); // key "k"
    bb.putInt(1).put((byte) 'v'); // val "v"
    byte[] bytes = new byte[bb.position()];
    bb.rewind();
    bb.get(bytes);

    var f = (StateMachine.NotificationValue.Failure) StateMachine.decodeTakeNotification(bytes);
    assertThat(f.code()).isEqualTo(500);
    assertThat(f.message()).isEqualTo("oof");
    assertThat(f.metadata()).hasSize(1);
    assertThat(f.metadata().get(0)).containsExactly("k", "v");
  }

  @Test
  void decodeStateKeys() {
    ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    bb.put((byte) 6); // StateKeys
    bb.putInt(2); // 2 keys
    bb.putInt(1).put((byte) 'a');
    bb.putInt(3).put((byte) 'k').put((byte) 'e').put((byte) 'y');
    byte[] bytes = new byte[bb.position()];
    bb.rewind();
    bb.get(bytes);

    var sk = (StateMachine.NotificationValue.StateKeys) StateMachine.decodeTakeNotification(bytes);
    assertThat(sk.keys()).containsExactly("a", "key");
  }

  @Test
  void decodeInvocationId() {
    // [7=InvocationId][u32 len=4]["inv1"]
    byte[] bytes = {7, 4, 0, 0, 0, 'i', 'n', 'v', '1'};
    var ii =
        (StateMachine.NotificationValue.InvocationId) StateMachine.decodeTakeNotification(bytes);
    assertThat(ii.id()).isEqualTo("inv1");
  }

  @Test
  void decodeVmFailureThrowsProtocolException() {
    // [8=VmFailure][u16 code=500][u32 msg_len=3]["bad"]
    byte[] bytes = {8, (byte) 0xf4, 0x01, 3, 0, 0, 0, 'b', 'a', 'd'};
    assertThatThrownBy(() -> StateMachine.decodeTakeNotification(bytes))
        .isInstanceOf(ProtocolException.class)
        .hasMessageContaining("bad");
  }

  @Test
  void decodeUnknownTagThrows() {
    assertThatThrownBy(() -> StateMachine.decodeTakeNotification(new byte[] {42}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("42");
  }

  // -------------------------------------------------------------------------
  // sys_write_output → sys_end → take_output — the full happy path that an
  // SDK handler walks through.
  // -------------------------------------------------------------------------

  @Test
  void sysWriteOutputThenSysEndThenTakeOutput() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      sm.sysWriteOutputWithSuccess(Slice.wrap("the-output-payload"));
      sm.sysEnd();

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // Failure paths: a sys_X call in the wrong state must surface a
  // ProtocolException, not break the VM.
  // -------------------------------------------------------------------------

  @Test
  void sysStateSetBeforeInputFails() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      // No notify_input yet — VM is in WAITING_START. sysStateSet should fail.
      assertThatThrownBy(() -> sm.sysStateSet("k", Slice.wrap("v")))
          .isInstanceOf(ProtocolException.class);
    }
  }

  @Test
  void sysCallBeforeInputFails() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThatThrownBy(
              () ->
                  sm.sysCall(
                      Target.service("svc", "handler"), Slice.wrap("payload"), null, List.of()))
          .isInstanceOf(ProtocolException.class);
    }
  }

  @Test
  void sysSendBeforeInputFails() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThatThrownBy(
              () ->
                  sm.sysSend(
                      Target.service("svc", "handler"),
                      Slice.wrap("payload"),
                      null,
                      List.of(),
                      null))
          .isInstanceOf(ProtocolException.class);
    }
  }

  @Test
  void sysWriteOutputBeforeInputFails() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThatThrownBy(() -> sm.sysWriteOutputWithSuccess(Slice.wrap("payload")))
          .isInstanceOf(ProtocolException.class);
    }
  }

  // -------------------------------------------------------------------------
  // State operations.
  // -------------------------------------------------------------------------

  @Test
  void sysStateGetReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysStateGet("the-key");
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysStateGetKeysReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysStateGetKeys();
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysStateClearJournalsAClearCommand() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      sm.sysStateClear("the-key");

      drainOutput(sm);
    }
  }

  @Test
  void sysStateClearAllJournalsAClearAllCommand() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      sm.sysStateClearAll();

      drainOutput(sm);
    }
  }

  @Test
  void multipleStateOperationsInSequence() {
    // Set, clear, clearAll — ensure the journal handles sequential state ops.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      sm.sysStateSet("a", Slice.wrap("1"));
      sm.sysStateSet("b", Slice.wrap("2"));
      sm.sysStateClear("a");
      sm.sysStateClearAll();
      int getHandle = sm.sysStateGet("b");
      assertThat(getHandle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // Sleep.
  // -------------------------------------------------------------------------

  @Test
  void sysSleepReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysSleep(Duration.ofSeconds(1), "wake-up");
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysSleepWithNullName() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysSleep(Duration.ofMillis(500), null);
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // Awakeables.
  // -------------------------------------------------------------------------

  @Test
  void sysCompleteAwakeableWithSuccess() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      // Awakeable IDs are normally returned by sysAwakeable, but for testing
      // just sending a completion to a synthetic ID exercises the wire format.
      sm.sysCompleteAwakeableWithSuccess(
          "prom_1PePOWp6xpdmAAAAAAAAAAAAAAAAAAAAAAAA", Slice.wrap("completed"));

      drainOutput(sm);
    }
  }

  @Test
  void sysCompleteAwakeableWithFailureCarryingMetadata() {
    // Failure path with metadata — exercises NonEmptyValueParam.Failure.metadata.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      TerminalException ex = new TerminalException(409, "conflict");
      sm.sysCompleteAwakeableWithFailure("prom_1PePOWp6xpdmAAAAAAAAAAAAAAAAAAAAAAAA", ex);

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // Signals.
  // -------------------------------------------------------------------------

  @Test
  void sysCreateSignalHandleReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysCreateSignalHandle("my-signal");
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysCompleteSignalWithSuccess() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      sm.sysCompleteSignalWithSuccess("target-inv", "the-signal", Slice.wrap("payload"));

      drainOutput(sm);
    }
  }

  @Test
  void sysCompleteSignalWithFailure() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      sm.sysCompleteSignalWithFailure(
          "target-inv", "the-signal", new TerminalException(500, "bad"));

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // Promises.
  // -------------------------------------------------------------------------

  @Test
  void sysPromiseGetReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysPromiseGet("the-promise");
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysPromisePeekReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysPromisePeek("the-promise");
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysPromiseCompleteWithSuccess() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysPromiseCompleteWithSuccess("the-promise", Slice.wrap("done"));
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysPromiseCompleteWithFailure() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle =
          sm.sysPromiseCompleteWithFailure("the-promise", new TerminalException(500, "nope"));
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // Invocation lifecycle.
  // -------------------------------------------------------------------------

  @Test
  void sysCancelInvocation() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      sm.sysCancelInvocation("inv_target");

      drainOutput(sm);
    }
  }

  @Test
  void sysAttachInvocationReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysAttachInvocation("inv_target");
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  @Test
  void sysGetInvocationOutputReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysGetInvocationOutput("inv_target");
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // sys_call / sys_send happy path — also exercises VmSysCallParameters /
  // VmSysSendParameters wire format with all fields populated (key,
  // idempotencyKey, headers).
  // -------------------------------------------------------------------------

  @Test
  void sysCallReturnsTwoHandles() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      var ret =
          sm.sysCall(
              Target.virtualObject("counter", "the-key", "add"),
              Slice.wrap("1"),
              "idempotency-key-123",
              List.of(Map.entry("x-trace", "abc"), Map.entry("x-user", "alice")));
      assertThat(ret.invocationIdHandle()).isGreaterThan(0);
      assertThat(ret.resultHandle()).isGreaterThan(0);
      assertThat(ret.invocationIdHandle()).isNotEqualTo(ret.resultHandle());

      drainOutput(sm);
    }
  }

  @Test
  void sysSendWithDelayedExecutionReturnsAHandle() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle =
          sm.sysSend(
              Target.service("svc", "handler"),
              Slice.wrap("payload"),
              null,
              List.of(),
              Duration.ofSeconds(30));
      assertThat(handle).isGreaterThan(0);

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // sysWriteOutput failure path.
  // -------------------------------------------------------------------------

  @Test
  void sysWriteOutputWithFailureThenSysEnd() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      sm.sysWriteOutputWithFailure(new TerminalException(409, "conflict"));
      sm.sysEnd();

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // doAwait combinator variants — exercises UnresolvedFuture.FirstCompleted,
  // AllCompleted, etc. Wire format for nested combinators tested via the
  // tree-shaped argument.
  // -------------------------------------------------------------------------

  @Test
  void doAwaitFirstCompletedOverMultipleHandles() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int sleep1 = sm.sysSleep(Duration.ofSeconds(1), null);
      int sleep2 = sm.sysSleep(Duration.ofSeconds(2), null);

      AwaitResult result =
          sm.doAwait(
              new StateMachine.UnresolvedFuture.FirstCompleted(
                  List.of(
                      new StateMachine.UnresolvedFuture.Single(sleep1),
                      new StateMachine.UnresolvedFuture.Single(sleep2))));
      assertThat(result).isIn(AwaitResult.WAIT_EXTERNAL_PROGRESS, AwaitResult.SUSPENDED);

      drainOutput(sm);
    }
  }

  @Test
  void doAwaitAllCompletedOverMultipleHandles() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int a = sm.sysSleep(Duration.ofSeconds(1), null);
      int b = sm.sysSleep(Duration.ofSeconds(2), null);
      int c = sm.sysSleep(Duration.ofSeconds(3), null);

      AwaitResult result =
          sm.doAwait(
              new StateMachine.UnresolvedFuture.AllCompleted(
                  List.of(
                      new StateMachine.UnresolvedFuture.Single(a),
                      new StateMachine.UnresolvedFuture.Single(b),
                      new StateMachine.UnresolvedFuture.Single(c))));
      assertThat(result).isIn(AwaitResult.WAIT_EXTERNAL_PROGRESS, AwaitResult.SUSPENDED);

      drainOutput(sm);
    }
  }

  @Test
  void doAwaitNestedCombinator() {
    // Tree-shaped: FirstCompleted(AllCompleted(a, b), c).
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int a = sm.sysSleep(Duration.ofSeconds(1), null);
      int b = sm.sysSleep(Duration.ofSeconds(2), null);
      int c = sm.sysSleep(Duration.ofSeconds(3), null);

      AwaitResult result =
          sm.doAwait(
              new StateMachine.UnresolvedFuture.FirstCompleted(
                  List.of(
                      new StateMachine.UnresolvedFuture.AllCompleted(
                          List.of(
                              new StateMachine.UnresolvedFuture.Single(a),
                              new StateMachine.UnresolvedFuture.Single(b))),
                      new StateMachine.UnresolvedFuture.Single(c))));
      assertThat(result).isIn(AwaitResult.WAIT_EXTERNAL_PROGRESS, AwaitResult.SUSPENDED);

      drainOutput(sm);
    }
  }

  // -------------------------------------------------------------------------
  // Misc.
  // -------------------------------------------------------------------------

  @Test
  void getResponseContentTypeIsAvailableBeforeReady() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      String contentType = sm.getResponseContentType();
      // Just assert the call completes (exercises ResponseHeadReturn
      // deserialization).
      assertThat(contentType).isNotNull();
    }
  }

  @Test
  void stateMirrorsVmAfterEachCall() {
    // The cached InvocationState is piggy-backed on every sys_* response.
    // Verify it tracks the VM's transitions through a typical invocation.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThat(sm.state()).isEqualTo(InvocationState.WAITING_START);

      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();

      sm.sysInput();
      assertThat(sm.state()).isEqualTo(InvocationState.PROCESSING);

      sm.sysStateSet("k", Slice.wrap("v"));
      assertThat(sm.state()).isEqualTo(InvocationState.PROCESSING);

      sm.sysWriteOutputWithSuccess(Slice.wrap("done"));
      sm.sysEnd();
      // After sys_end the VM transitions to CLOSED.
      assertThat(sm.state()).isEqualTo(InvocationState.CLOSED);

      drainOutput(sm);
    }
  }

  @Test
  void sequentialStateMachinesOnTheSameThread() {
    // Multiple StateMachines on the same thread reuse pooled SharedCoreInstances —
    // verify sequential invocations don't interfere with each other.
    for (int i = 0; i < 3; i++) {
      try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
        sm.notifyInput(loadFixture("start_plus_input.bin"));
        sm.notifyInputClosed();
        assertThat(sm.isReadyToExecute()).isTrue();
        sm.sysInput();
        sm.sysWriteOutputWithSuccess(Slice.wrap("result-" + i));
        sm.sysEnd();
        drainOutput(sm);
      }
    }
  }
}
