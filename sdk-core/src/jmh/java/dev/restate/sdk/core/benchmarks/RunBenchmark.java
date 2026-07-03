// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.benchmarks;

import dev.restate.common.Slice;
import dev.restate.sdk.core.StateMachine;
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
 * Benchmarks the cost of a single {@code run} round-trip — the FFM argument marshalling, the native
 * downcalls, the Rust-side journal appends, and the result decode — for the sequence a handler
 * driving a {@code ctx.run(...)} performs: schedule the run ({@code run}), make progress on its
 * future ({@code doAwait}, which returns {@code ExecuteRun}), propose the run's completion ({@code
 * proposeRunCompletion}), and drain the buffered output ({@code takeOutput}).
 *
 * <p>Shaped exactly like {@link SysCallBenchmark}: parameterized over both state machines ({@code
 * ffmStateMachine}) so the FFM boundary can be read against the pure-Java baseline, and over {@code
 * payloadSize} to see how the "allocate the run result in Rust (ownership transfer) vs. copy" cost
 * scales with payload size.
 *
 * <p>{@code run} is stateful — each op appends a command + notification handles to the VM's journal
 * — so we prime a <b>fresh</b> VM per measured op ({@link Level#Invocation}, not measured) and do a
 * single run round-trip in the measured body; the VM is discarded each op, bounding journal growth.
 *
 * <p>Run with {@code ./gradlew :sdk-core:jmh -PjmhArgs="Run"} (JDK 25 toolchain, FFM active).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(1)
@State(Scope.Thread)
public class RunBenchmark {

  // Immutable inputs, computed once.
  private Slice startMessage;
  private Slice inputMessage;
  private Slice payload;
  private HeadersAccessor headersAccessor;

  @Param({"true", "false"})
  boolean ffmStateMachine;

  // Payload size in bytes — exercises how much the copy-vs-transfer cost scales with payload size.
  @Param({"16", "1024", "4096", "16384", "65536"})
  int payloadSize;

  @Setup(Level.Trial)
  public void prepareInputs() {
    startMessage = ProtoUtils.encodeMessageToSlice(ProtoUtils.startMessage(1).build());
    inputMessage = ProtoUtils.encodeMessageToSlice(ProtoUtils.inputCmd("benchmark-input"));
    // Random payload of the parameterized length; fixed seed so runs are reproducible.
    byte[] payloadBytes = new byte[payloadSize];
    new Random().nextBytes(payloadBytes);
    payload = Slice.wrap(payloadBytes);
    headersAccessor =
        HeadersAccessor.wrap(
            Map.of(
                "content-type",
                // We use a protocol version both support
                "application/vnd.restate.invocation.v6"));
  }

  @Benchmark
  public void run(Blackhole bh) {
    StateMachine sm =
        ffmStateMachine
            ? new FfmStateMachine(headersAccessor)
            : new LegacyStateMachine(headersAccessor, (i1, i2) -> {});
    sm.notifyInput(startMessage);
    sm.notifyInput(inputMessage);
    sm.notifyInputClosed();
    sm.isReadyToExecute();
    sm.input();
    StateMachine.RunResultHandle run = sm.run("benchmark-run");
    bh.consume(sm.doAwait(new StateMachine.UnresolvedFuture.Single(run.handle())));
    sm.proposeRunCompletion(run.handle(), payload);
    bh.consume(sm.takeOutput());
    sm.close();
  }
}
