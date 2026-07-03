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
 * Benchmarks the cost of spinning up a whole invocation from wire bytes to a terminal output — the
 * "initialization" round-trip: construct the VM, feed the {@code start} + {@code input} messages
 * ({@code notifyInput}), close the input stream, gate on {@code isReadyToExecute}, consume the
 * input command ({@code input}), write the output, and {@code end} the invocation, draining the
 * buffered wire output via {@code takeOutput}.
 *
 * <p>Unlike {@link SysCallBenchmark}, the VM construction is <b>inside</b> the measured body: this
 * is precisely the initialization cost we want, and for the FFM state machine it captures the
 * native {@code new_vm} + {@code Arena} setup. Only {@code close()} (native teardown) is excluded,
 * happening in the non-measured {@link TearDown}. Parameterized over both state machines ({@code
 * ffmStateMachine}) so the FFM boundary can be read against the pure-Java baseline, and over {@code
 * payloadSize} to see how the output-write copy cost scales with payload size.
 *
 * <p>Run with {@code ./gradlew :sdk-core:jmh -PjmhArgs="Initialization"} (JDK 25 toolchain, FFM
 * active).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(1)
@State(Scope.Thread)
public class EchoBenchmark {

  // Immutable inputs, computed once.
  private Slice startMessage;
  private Slice inputMessage;
  private Slice payload;
  private HeadersAccessor headersAccessor;

  @Param({"true", "false"})
  boolean ffmStateMachine;

  // Payload size in bytes — exercises how much the output-write copy cost scales with payload size.
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
  public void echo(Blackhole bh) {
    StateMachine sm =
        ffmStateMachine
            ? new FfmStateMachine(headersAccessor)
            : new LegacyStateMachine(headersAccessor, (i1, i2) -> {});
    sm.notifyInput(startMessage);
    sm.notifyInput(inputMessage);
    sm.notifyInputClosed();
    bh.consume(sm.isReadyToExecute());
    bh.consume(sm.input());
    sm.writeOutput(payload);
    sm.end();
    bh.consume(sm.takeOutput());
    sm.close();
  }
}
