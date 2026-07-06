// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.core.legacy.LegacyStateMachine;
import dev.restate.sdk.core.legacy.ProtoUtils;
import dev.restate.sdk.core.statemachine.ffm.FfmStateMachine;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks the cost of a single {@code sys_call} — argument marshalling (the FFM {@code
 * CallArguments} build), the native downcall, the Rust-side decode + {@code VM::sys_call}, and the
 * result decode. Parameterized over both state machines ({@code ffmStateMachine}) so the FFM
 * boundary can be read against the pure-Java baseline, and over {@code payloadSize} to see how the
 * "allocate the payload in Rust (ownership transfer) vs. copy" cost scales with payload size.
 *
 * <p>{@code sys_call} is stateful — each call appends a command + notification handles to the VM's
 * journal — so we prime a <b>fresh</b> VM per measured op ({@link Level#Invocation}, not measured)
 * and do a single {@code sys_call} (draining its buffered output via {@code takeOutput}) in the
 * measured body; the VM is discarded each op, bounding journal growth.
 *
 * <p>Run with {@code ./gradlew :sdk-core:jmh -PjmhArgs="SysCall"} (JDK 25 toolchain, FFM active).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(1)
@State(Scope.Thread)
public class TakeNotificationBenchmark {

  // Immutable inputs, computed once.
  private Slice startMessage;
  private Slice inputMessage;
  private Slice callMessage;
  private Slice notificationMessage;
  private Target target;
  private HeadersAccessor headersAccessor;

  @Param({"true", "false"})
  boolean ffmStateMachine;

  // Payload size in bytes — exercises how much the copy-vs-transfer cost scales with payload size.
  @Param({"16", "1024", "4096", "16384", "65536"})
  int payloadSize;

  @Setup(Level.Trial)
  public void prepareInputs() {
    byte[] payloadBytes = new byte[payloadSize];
    new Random().nextBytes(payloadBytes);

    target = Target.service("BenchmarkService", "benchmarkHandler");

    startMessage = ProtoUtils.encodeMessageToSlice(ProtoUtils.startMessage(3).build());
    inputMessage = ProtoUtils.encodeMessageToSlice(ProtoUtils.inputCmd("benchmark-input"));
    callMessage = ProtoUtils.encodeMessageToSlice(ProtoUtils.callCmd(1, 2, target));
    notificationMessage =
        ProtoUtils.encodeMessageToSlice(ProtoUtils.callCompletion(2, Slice.wrap(payloadBytes)));
    headersAccessor =
        HeadersAccessor.wrap(
            Map.of(
                "content-type",
                // We use a protocol version both support
                "application/vnd.restate.invocation.v6"));
  }

  @Benchmark
  public void takeNotification(Blackhole bh) {
    StateMachine sm =
        ffmStateMachine
            ? new FfmStateMachine(headersAccessor)
            : new LegacyStateMachine(headersAccessor, (i1, i2) -> {});
    sm.notifyInput(startMessage);
    sm.notifyInput(inputMessage);
    sm.notifyInput(callMessage);
    sm.notifyInput(notificationMessage);
    sm.notifyInputClosed();
    sm.isReadyToExecute();
    // Consume input and call
    sm.input();
    StateMachine.CallHandle handle = sm.call(target, Slice.EMPTY, null, null, null, null);

    // Take notification of result handle (which should be present(
    bh.consume(sm.takeNotification(handle.resultHandle()));

    sm.close();
  }
}
