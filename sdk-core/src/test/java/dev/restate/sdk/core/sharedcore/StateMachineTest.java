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
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end-ish tests of {@link StateMachine} that drive real WASM-side state transitions and
 * assert the {@link HostBufferRegistry} returns to empty at the documented drain points.
 *
 * <p>The {@link SharedCoreInstance} (and its registry) is a per-thread singleton, so each test
 * shares the same registry with any other test scheduled on the same JUnit worker thread. We
 * therefore (a) brace each test with {@link BeforeEach}/{@link AfterEach} hooks that fail if the
 * registry isn't empty at the boundary, and (b) keep the registry-empty assertions tied to
 * lifecycle events the caller owns ({@link StateMachine#close()} above all) rather than
 * implementation details of when the Rust decoder happens to consume bytes.
 *
 * <p>Wire bytes come from pre-encoded fixtures under {@code src/test/resources/fixtures/}; see the
 * fixture README for regeneration.
 */
class StateMachineTest {

  private static final HeadersAccessor RESTATE_HEADERS =
      HeadersAccessor.wrap(Map.of("content-type", "application/vnd.restate.invocation.v7"));

  private static Slice loadFixture(String name) {
    try (InputStream in = StateMachineTest.class.getResourceAsStream("/fixtures/" + name)) {
      if (in == null) {
        throw new IllegalStateException("missing fixture: " + name);
      }
      return Slice.wrap(in.readAllBytes());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  // Per-thread registry — same instance is reused across all tests on this worker.
  private HostBufferRegistry registry;

  @BeforeEach
  void registryStartsEmpty() {
    registry = SharedCoreInstance.get().registry();
    assertThat(registry.entries)
        .as(
            "previous test on this worker thread leaked registry entries — host-memory leak suspected")
        .isEmpty();
  }

  @AfterEach
  void registryEndsEmpty() {
    assertThat(registry.entries)
        .as(
            "this test left registry entries behind — close() on the StateMachine should drain"
                + " everything")
        .isEmpty();
  }

  // -------------------------------------------------------------------------
  // Lifecycle: close() always drains the registry of entries it created.
  // -------------------------------------------------------------------------

  @Test
  void createWithNoCallsLeavesRegistryEmpty() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThat(sm.registry().entries).isEmpty();
      assertThat(sm.state()).isEqualTo(InvocationState.WAITING_START);
    }
  }

  @Test
  void notifyInputThenCloseDrainsTheRegistry() {
    StateMachine sm = StateMachine.create(RESTATE_HEADERS);
    sm.notifyInput(loadFixture("start_plus_input.bin"));
    sm.close();
    // vm_free drops Rust's handle → host_buffer_release runs → entries gone.
    assertThat(sm.registry().entries).isEmpty();
  }

  @Test
  void multipleNotifyInputsThenCloseDrainsTheRegistry() {
    // Feed Start and InputCommand as two separate buffers — exercises the
    // SegmentedBuf path on the Rust decoder.
    StateMachine sm = StateMachine.create(RESTATE_HEADERS);
    sm.notifyInput(loadFixture("start_only.bin"));
    sm.notifyInput(loadFixture("input_only.bin"));
    sm.close();
    assertThat(sm.registry().entries).isEmpty();
  }

  @Test
  void closeIsIdempotentAndPostCloseGuardedMethodsAreNoOps() {
    StateMachine sm = StateMachine.create(RESTATE_HEADERS);
    sm.close();
    sm.close(); // second close must not throw
    assertThat(sm.state()).isEqualTo(InvocationState.CLOSED);

    // These methods early-return when freed — must not blow up and must not
    // sneak a Slice into the registry past the freed guard.
    sm.notifyInput(Slice.wrap("ignored"));
    sm.notifyInputClosed();
    sm.notifyError(new RuntimeException("ignored"));
    assertThat(sm.takeOutput()).isNull();
    sm.proposeRunCompletionWithSuccess(1, Slice.wrap("ignored"));
    sm.proposeRunCompletionTerminalFailure(1, new TerminalException(500, "ignored"));
    sm.proposeRunCompletionRetryableFailure(
        1, new RuntimeException("ignored"), java.time.Duration.ZERO, null);

    assertThat(sm.registry().entries).isEmpty();
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

    // verifyNotFreed runs BEFORE registerSlice; nothing must leak through.
    assertThat(sm.registry().entries).isEmpty();
  }

  // -------------------------------------------------------------------------
  // notify_input → sys_input — the registry entry for the input payload is
  // released by the time sys_input returns its materialised Slice.
  // -------------------------------------------------------------------------

  @Test
  void notifyInputThenSysInputMaterialisesPayloadAndDrainsRegistry() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();

      StateMachine.Input input = sm.sysInput();
      assertThat(new String(input.slice().toByteArray())).isEqualTo("my-data");

      // sysInput materialises the payload sub-view (releasing its share) and
      // the decoder has consumed all the protocol bytes (releasing the
      // whole-handle share). Nothing should remain.
      assertThat(sm.registry().entries).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // propose_run_completion → take_output — the value passed in via Slice
  // becomes a journal entry; close() drops everything still resident.
  // -------------------------------------------------------------------------

  @Test
  void proposeRunCompletionWithSuccessThenCloseDrainsTheRegistry() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      int runHandle = sm.sysRun("my-run");
      // doAwait drives the VM until it's ready to ExecuteRun the handle.
      AwaitResult awaitResult = sm.doAwait(new StateMachine.UnresolvedFuture.Single(runHandle));
      assertThat(awaitResult).isInstanceOf(AwaitResult.ExecuteRun.class);

      sm.proposeRunCompletionWithSuccess(runHandle, Slice.wrap("run-result-payload"));

      while (sm.takeOutput() != null) {
        // drain whatever the propose journaled into the output queue
      }
      // close() drops any handle the VM still owns (e.g. the cached run
      // completion in async_results when Protocol >= V7).
    }
  }

  @Test
  void proposeRunCompletionRetryableFailureWithNonNullRetryPolicy() {
    // Exercises the array-encoded `WasmRetryPolicy` path with a null in the middle
    // (max_interval is null, max_attempts is set). A wire-format regression here
    // (e.g. NON_NULL stripping middle nulls under @JsonFormat(ARRAY)) would
    // surface as a CBOR deserialization error on the Rust side (decode/EOF),
    // *not* as a ProtocolException carrying the user-supplied "boom" message.
    // The VM correctly surfacing "boom" means the wire was decoded fine.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      int runHandle = sm.sysRun("my-run");
      sm.doAwait(new StateMachine.UnresolvedFuture.Single(runHandle));

      RetryPolicy retryPolicy =
          RetryPolicy.exponential(Duration.ofMillis(100), 2.0f).setMaxAttempts(3);
      assertThatThrownBy(
              () ->
                  sm.proposeRunCompletionRetryableFailure(
                      runHandle, new RuntimeException("boom"), Duration.ofMillis(50), retryPolicy))
          .isInstanceOf(ProtocolException.class)
          .hasMessageContaining("boom");

      while (sm.takeOutput() != null) {
        // drain
      }
    }
  }

  @Test
  void takeNotificationReturnsNullWhenAwakeableNotCompleted() {
    // Exercises the NotReady path (TakeNotificationReturn tag 0): create an
    // awakeable handle but never complete it, then ask for it.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      var awakeable = sm.sysAwakeable();
      assertThat(sm.takeNotification(awakeable.handle())).isNull();

      while (sm.takeOutput() != null) {
        // drain
      }
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
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThat(sm.decodeTakeNotification(new byte[] {0})).isNull();
    }
  }

  @Test
  void decodeVoid() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      // tag=1 (Void)
      assertThat(sm.decodeTakeNotification(new byte[] {1}))
          .isSameAs(StateMachine.NotificationValue.VOID);
    }
  }

  @Test
  void decodeSuccessInMemory() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      // [2=SuccessInMemory][u32 LE len=3]['a','b','c']
      byte[] bytes = {2, 3, 0, 0, 0, 'a', 'b', 'c'};
      StateMachine.NotificationValue v = sm.decodeTakeNotification(bytes);
      assertThat(v).isInstanceOf(StateMachine.NotificationValue.Success.class);
      assertThat(new String(((StateMachine.NotificationValue.Success) v).slice().toByteArray()))
          .isEqualTo("abc");
    }
  }

  @Test
  void decodeSuccessHost() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      int id = sm.registry().register(Slice.wrap("payload"));
      // [3=SuccessHost][u32 id][u32 offset=0][u32 len=7]
      byte[] bytes = {
        3,
        (byte) id,
        (byte) (id >>> 8),
        (byte) (id >>> 16),
        (byte) (id >>> 24),
        0,
        0,
        0,
        0,
        7,
        0,
        0,
        0
      };
      StateMachine.NotificationValue v = sm.decodeTakeNotification(bytes);
      assertThat(v).isInstanceOf(StateMachine.NotificationValue.Success.class);
      assertThat(new String(((StateMachine.NotificationValue.Success) v).slice().toByteArray()))
          .isEqualTo("payload");
      // The decoder must release the registry share it consumed.
      assertThat(sm.registry().entries).isEmpty();
    }
  }

  @Test
  void decodeSuccessHostMulti() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      int id1 = sm.registry().register(Slice.wrap("foo"));
      int id2 = sm.registry().register(Slice.wrap("bar"));
      java.nio.ByteBuffer bb =
          java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      bb.put((byte) 4); // SuccessHostMulti
      bb.putInt(2); // segment count
      bb.putInt(id1).putInt(0).putInt(3);
      bb.putInt(id2).putInt(0).putInt(3);
      byte[] bytes = new byte[bb.position()];
      bb.rewind();
      bb.get(bytes);

      StateMachine.NotificationValue v = sm.decodeTakeNotification(bytes);
      assertThat(v).isInstanceOf(StateMachine.NotificationValue.Success.class);
      assertThat(new String(((StateMachine.NotificationValue.Success) v).slice().toByteArray()))
          .isEqualTo("foobar");
      assertThat(sm.registry().entries).isEmpty();
    }
  }

  @Test
  void decodeNotificationFailureNoMetadata() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      // [5=NotificationFailure][u16 code=409][u32 msg_len=5]["boom!"][u32 meta_count=0]
      byte[] bytes = {5, (byte) 0x99, 0x01, 5, 0, 0, 0, 'b', 'o', 'o', 'm', '!', 0, 0, 0, 0};
      StateMachine.NotificationValue v = sm.decodeTakeNotification(bytes);
      assertThat(v).isInstanceOf(StateMachine.NotificationValue.Failure.class);
      var f = (StateMachine.NotificationValue.Failure) v;
      assertThat(f.code()).isEqualTo(409);
      assertThat(f.message()).isEqualTo("boom!");
      assertThat(f.metadata()).isNull();
    }
  }

  @Test
  void decodeNotificationFailureWithMetadata() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      java.nio.ByteBuffer bb =
          java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      bb.put((byte) 5); // NotificationFailure
      bb.putShort((short) 500); // code
      bb.putInt(3).put((byte) 'o').put((byte) 'o').put((byte) 'f'); // message "oof"
      bb.putInt(1); // 1 metadata entry
      bb.putInt(1).put((byte) 'k'); // key "k"
      bb.putInt(1).put((byte) 'v'); // val "v"
      byte[] bytes = new byte[bb.position()];
      bb.rewind();
      bb.get(bytes);

      var f = (StateMachine.NotificationValue.Failure) sm.decodeTakeNotification(bytes);
      assertThat(f.code()).isEqualTo(500);
      assertThat(f.message()).isEqualTo("oof");
      assertThat(f.metadata()).hasSize(1);
      assertThat(f.metadata().get(0)).containsExactly("k", "v");
    }
  }

  @Test
  void decodeStateKeys() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      java.nio.ByteBuffer bb =
          java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      bb.put((byte) 6); // StateKeys
      bb.putInt(2); // 2 keys
      bb.putInt(1).put((byte) 'a');
      bb.putInt(3).put((byte) 'k').put((byte) 'e').put((byte) 'y');
      byte[] bytes = new byte[bb.position()];
      bb.rewind();
      bb.get(bytes);

      var sk = (StateMachine.NotificationValue.StateKeys) sm.decodeTakeNotification(bytes);
      assertThat(sk.keys()).containsExactly("a", "key");
    }
  }

  @Test
  void decodeInvocationId() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      // [7=InvocationId][u32 len=4]["inv1"]
      byte[] bytes = {7, 4, 0, 0, 0, 'i', 'n', 'v', '1'};
      var ii = (StateMachine.NotificationValue.InvocationId) sm.decodeTakeNotification(bytes);
      assertThat(ii.id()).isEqualTo("inv1");
    }
  }

  @Test
  void decodeVmFailureThrowsProtocolException() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      // [8=VmFailure][u16 code=500][u32 msg_len=3]["bad"]
      byte[] bytes = {8, (byte) 0xf4, 0x01, 3, 0, 0, 0, 'b', 'a', 'd'};
      assertThatThrownBy(() -> sm.decodeTakeNotification(bytes))
          .isInstanceOf(ProtocolException.class)
          .hasMessageContaining("bad");
    }
  }

  @Test
  void proposeRunCompletionTerminalFailureDoesNotRegisterAnySlice() {
    // Pure-failure path — no Slice in, no registry interaction.
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      int runHandle = sm.sysRun("my-run");
      sm.doAwait(new StateMachine.UnresolvedFuture.Single(runHandle));

      sm.proposeRunCompletionTerminalFailure(runHandle, new TerminalException(500, "boom"));

      while (sm.takeOutput() != null) {
        // drain
      }
    }
  }

  // -------------------------------------------------------------------------
  // sys_write_output → sys_end → take_output — the full happy path that an
  // SDK handler walks through; the registry must drain to empty.
  // -------------------------------------------------------------------------

  @Test
  void sysWriteOutputThenSysEndThenTakeOutputDrainsRegistry() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      sm.sysWriteOutputWithSuccess(Slice.wrap("the-output-payload"));
      sm.sysEnd();

      while (sm.takeOutput() != null) {
        // drain
      }
    }
  }

  @Test
  void sysStateSetThenCloseDrainsRegistry() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      assertThat(sm.isReadyToExecute()).isTrue();
      sm.sysInput();

      sm.sysStateSet("my-key", Slice.wrap("my-value"));
      // The Slice is still pending in the journal; close() drops it.
    }
  }

  // -------------------------------------------------------------------------
  // Failure paths: even when a sys_X call returns a Failure (e.g. wrong
  // state) the Slice we registered must still be released — Rust's CBOR
  // decoder constructs a HostBufferHandle eagerly that drops on the error
  // path.
  // -------------------------------------------------------------------------

  @Test
  void sysStateSetBeforeInputFailsButStillReleasesRegisteredSlice() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      // No notify_input yet — VM is in WAITING_START. sysStateSet should fail.
      assertThatThrownBy(() -> sm.sysStateSet("k", Slice.wrap("v")))
          .isInstanceOf(ProtocolException.class);
    }
    // close() drains any remainder, AfterEach asserts global empty.
  }

  @Test
  void sysCallBeforeInputFailsButStillReleasesRegisteredSlice() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThatThrownBy(
              () ->
                  sm.sysCall(
                      Target.service("svc", "handler"),
                      Slice.wrap("payload"),
                      null,
                      java.util.List.of()))
          .isInstanceOf(ProtocolException.class);
    }
  }

  @Test
  void sysSendBeforeInputFailsButStillReleasesRegisteredSlice() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThatThrownBy(
              () ->
                  sm.sysSend(
                      Target.service("svc", "handler"),
                      Slice.wrap("payload"),
                      null,
                      java.util.List.of(),
                      null))
          .isInstanceOf(ProtocolException.class);
    }
  }

  @Test
  void sysWriteOutputBeforeInputFailsButStillReleasesRegisteredSlice() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      assertThatThrownBy(() -> sm.sysWriteOutputWithSuccess(Slice.wrap("payload")))
          .isInstanceOf(ProtocolException.class);
    }
  }

  // -------------------------------------------------------------------------
  // Multiple StateMachines on the same thread share the SharedCoreInstance's
  // registry — verify each instance cleans up after itself so the registry
  // stays empty between invocations.
  // -------------------------------------------------------------------------

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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      int handle = sm.sysSleep(java.time.Duration.ofSeconds(1), "wake-up");
      assertThat(handle).isGreaterThan(0);

      while (sm.takeOutput() != null) {
        // drain
      }
    }
  }

  @Test
  void sysSleepWithNullName() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int handle = sm.sysSleep(java.time.Duration.ofMillis(500), null);
      assertThat(handle).isGreaterThan(0);

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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
              java.util.List.of(
                  java.util.Map.entry("x-trace", "abc"), java.util.Map.entry("x-user", "alice")));
      assertThat(ret.invocationIdHandle()).isGreaterThan(0);
      assertThat(ret.resultHandle()).isGreaterThan(0);
      assertThat(ret.invocationIdHandle()).isNotEqualTo(ret.resultHandle());

      while (sm.takeOutput() != null) {
        // drain
      }
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
              java.util.List.of(),
              java.time.Duration.ofSeconds(30));
      assertThat(handle).isGreaterThan(0);

      while (sm.takeOutput() != null) {
        // drain
      }
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

      while (sm.takeOutput() != null) {
        // drain
      }
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

      int sleep1 = sm.sysSleep(java.time.Duration.ofSeconds(1), null);
      int sleep2 = sm.sysSleep(java.time.Duration.ofSeconds(2), null);

      AwaitResult result =
          sm.doAwait(
              new StateMachine.UnresolvedFuture.FirstCompleted(
                  java.util.List.of(
                      new StateMachine.UnresolvedFuture.Single(sleep1),
                      new StateMachine.UnresolvedFuture.Single(sleep2))));
      assertThat(result).isIn(AwaitResult.WAIT_EXTERNAL_PROGRESS, AwaitResult.SUSPENDED);

      while (sm.takeOutput() != null) {
        // drain
      }
    }
  }

  @Test
  void doAwaitAllCompletedOverMultipleHandles() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      sm.notifyInput(loadFixture("start_plus_input.bin"));
      sm.notifyInputClosed();
      sm.isReadyToExecute();
      sm.sysInput();

      int a = sm.sysSleep(java.time.Duration.ofSeconds(1), null);
      int b = sm.sysSleep(java.time.Duration.ofSeconds(2), null);
      int c = sm.sysSleep(java.time.Duration.ofSeconds(3), null);

      AwaitResult result =
          sm.doAwait(
              new StateMachine.UnresolvedFuture.AllCompleted(
                  java.util.List.of(
                      new StateMachine.UnresolvedFuture.Single(a),
                      new StateMachine.UnresolvedFuture.Single(b),
                      new StateMachine.UnresolvedFuture.Single(c))));
      assertThat(result).isIn(AwaitResult.WAIT_EXTERNAL_PROGRESS, AwaitResult.SUSPENDED);

      while (sm.takeOutput() != null) {
        // drain
      }
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

      int a = sm.sysSleep(java.time.Duration.ofSeconds(1), null);
      int b = sm.sysSleep(java.time.Duration.ofSeconds(2), null);
      int c = sm.sysSleep(java.time.Duration.ofSeconds(3), null);

      AwaitResult result =
          sm.doAwait(
              new StateMachine.UnresolvedFuture.FirstCompleted(
                  java.util.List.of(
                      new StateMachine.UnresolvedFuture.AllCompleted(
                          java.util.List.of(
                              new StateMachine.UnresolvedFuture.Single(a),
                              new StateMachine.UnresolvedFuture.Single(b))),
                      new StateMachine.UnresolvedFuture.Single(c))));
      assertThat(result).isIn(AwaitResult.WAIT_EXTERNAL_PROGRESS, AwaitResult.SUSPENDED);

      while (sm.takeOutput() != null) {
        // drain
      }
    }
  }

  // -------------------------------------------------------------------------
  // Misc.
  // -------------------------------------------------------------------------

  @Test
  void getResponseContentTypeIsAvailableBeforeReady() {
    try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
      String contentType = sm.getResponseContentType();
      // The content-type header propagates as part of the response head; for
      // V7 invocations the runtime sends back a content-type that the SDK
      // surfaces here. Just assert the call completes (exercises
      // ResponseHeadReturn deserialization).
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

      while (sm.takeOutput() != null) {
        // drain
      }
    }
  }

  // -------------------------------------------------------------------------
  // Multi-thread isolation — each thread has its own SharedCoreInstance /
  // registry. Driving a StateMachine on a worker thread mustn't touch the
  // current thread's registry.
  // -------------------------------------------------------------------------

  @Test
  void sequentialStateMachinesEachCleanUpAfterThemselves() {
    HostBufferRegistry sharedRegistry = SharedCoreInstance.get().registry();

    for (int i = 0; i < 3; i++) {
      try (StateMachine sm = StateMachine.create(RESTATE_HEADERS)) {
        sm.notifyInput(loadFixture("start_plus_input.bin"));
        sm.notifyInputClosed();
        assertThat(sm.isReadyToExecute()).isTrue();
        sm.sysInput();
        sm.sysWriteOutputWithSuccess(Slice.wrap("result-" + i));
        sm.sysEnd();
        while (sm.takeOutput() != null) {
          // drain
        }
      }
      assertThat(sharedRegistry.entries)
          .as("registry must be empty after invocation %d's close()", i)
          .isEmpty();
    }
  }
}
