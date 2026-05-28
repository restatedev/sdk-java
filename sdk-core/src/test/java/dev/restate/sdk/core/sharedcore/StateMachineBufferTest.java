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

import dev.restate.common.Slice;
import dev.restate.sdk.core.sharedcore.StateMachine.BufferParam;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for {@link StateMachine#toSlice} and {@link StateMachine#materialise} — the
 * Java-side glue that maps Rust-returned {@code BufferParam} variants back into {@link Slice} and
 * releases the registry refcount-share.
 */
class StateMachineBufferTest {

  @Test
  void toSliceInMemoryWrapsTheByteArray() {
    var reg = new HostBufferRegistry();
    Slice result = StateMachine.toSlice(reg, new BufferParam.InMemory("hello".getBytes()));
    assertThat(new String(result.toByteArray())).isEqualTo("hello");
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void toSliceMaterialisedReturnsTheCarriedSlice() {
    var reg = new HostBufferRegistry();
    Slice original = Slice.wrap("payload");
    Slice result = StateMachine.toSlice(reg, new BufferParam.Materialised(original));
    assertThat(result).isSameAs(original);
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void toSliceHostReturnsSubSliceAndReleasesRefcountShare() {
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("hello world".getBytes()));

    Slice result = StateMachine.toSlice(reg, new BufferParam.Host(id, 6, 5));
    assertThat(new String(result.toByteArray())).isEqualTo("world");

    // Refcount-share was transferred to Java; toSlice released it; entry dropped.
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void toSliceHostWithExtraRefcountKeepsEntryButReleasesOurShare() {
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("hello world".getBytes()));
    reg.retain(id); // simulate a second outstanding share (e.g., another reader)
    assertThat(reg.entries.get(id).refcount).isEqualTo(2);

    Slice result = StateMachine.toSlice(reg, new BufferParam.Host(id, 0, 5));
    assertThat(new String(result.toByteArray())).isEqualTo("hello");

    // Our share was released; the other share keeps the entry alive.
    assertThat(reg.entries).hasSize(1);
    assertThat(reg.entries.get(id).refcount).isEqualTo(1);

    reg.release(id);
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void toSliceHostMultiConcatenatesAndReleasesAllSegments() {
    var reg = new HostBufferRegistry();
    int id1 = reg.register(Slice.wrap("hello ".getBytes()));
    int id2 = reg.register(Slice.wrap("brave new ".getBytes()));
    int id3 = reg.register(Slice.wrap("world!".getBytes()));

    Slice result =
        StateMachine.toSlice(
            reg,
            new BufferParam.HostMulti(
                List.of(
                    new BufferParam.HostSegment(id1, 0, 6),
                    new BufferParam.HostSegment(id2, 0, 10),
                    new BufferParam.HostSegment(id3, 0, 6))));

    assertThat(new String(result.toByteArray())).isEqualTo("hello brave new world!");
    // All three segments' refcount-shares released; nothing should remain.
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void toSliceHostMultiReleasesEachOccurrenceOfASharedId() {
    // Rust's SegmentedBuf can produce multiple segments backed by the same
    // underlying handle (sub-views of one whole-handle buffer). Each segment
    // carries its own refcount-share, so toSlice must release the same id N
    // times when it appears N times.
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("abcdefghij".getBytes()));
    // Bump refcount twice so we have 3 shares total; HostMulti below carries
    // 3 segments for that id, each transferring one share.
    reg.retain(id);
    reg.retain(id);
    assertThat(reg.entries.get(id).refcount).isEqualTo(3);

    Slice result =
        StateMachine.toSlice(
            reg,
            new BufferParam.HostMulti(
                List.of(
                    new BufferParam.HostSegment(id, 0, 3),
                    new BufferParam.HostSegment(id, 3, 3),
                    new BufferParam.HostSegment(id, 6, 4))));

    assertThat(new String(result.toByteArray())).isEqualTo("abcdefghij");
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void toSliceHostMultiWithEmptySegmentsProducesEmptySlice() {
    var reg = new HostBufferRegistry();
    Slice result = StateMachine.toSlice(reg, new BufferParam.HostMulti(List.of()));
    assertThat(result.readableBytes()).isEqualTo(0);
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void materialiseInMemoryPassesThrough() {
    var reg = new HostBufferRegistry();
    BufferParam inMemory = new BufferParam.InMemory("x".getBytes());
    BufferParam result = StateMachine.materialise(reg, inMemory);
    assertThat(result).isSameAs(inMemory);
  }

  @Test
  void materialiseHostProducesMaterialisedAndReleasesEntry() {
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("hello".getBytes()));

    BufferParam result = StateMachine.materialise(reg, new BufferParam.Host(id, 0, 5));
    assertThat(result).isInstanceOf(BufferParam.Materialised.class);
    Slice carried = ((BufferParam.Materialised) result).slice();
    assertThat(new String(carried.toByteArray())).isEqualTo("hello");
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void materialiseHostMultiProducesMaterialisedAndReleasesAll() {
    var reg = new HostBufferRegistry();
    int id1 = reg.register(Slice.wrap("foo".getBytes()));
    int id2 = reg.register(Slice.wrap("bar".getBytes()));

    BufferParam result =
        StateMachine.materialise(
            reg,
            new BufferParam.HostMulti(
                List.of(
                    new BufferParam.HostSegment(id1, 0, 3),
                    new BufferParam.HostSegment(id2, 0, 3))));

    assertThat(result).isInstanceOf(BufferParam.Materialised.class);
    Slice carried = ((BufferParam.Materialised) result).slice();
    assertThat(new String(carried.toByteArray())).isEqualTo("foobar");
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void materialiseMaterialisedPassesThrough() {
    var reg = new HostBufferRegistry();
    BufferParam already = new BufferParam.Materialised(Slice.wrap("x"));
    BufferParam result = StateMachine.materialise(reg, already);
    // Already materialised → toSlice() in the Materialised branch returns the
    // carried Slice directly, but materialise() itself short-circuits the
    // Host/HostMulti branch and returns the input unchanged.
    assertThat(result).isSameAs(already);
  }
}
