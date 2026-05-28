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
import org.junit.jupiter.api.Test;

class HostBufferRegistryTest {

  @Test
  void registerAssignsFreshIdAndStartsAtRefcountOne() {
    var reg = new HostBufferRegistry();
    int id1 = reg.register(Slice.wrap("hello".getBytes()));
    int id2 = reg.register(Slice.wrap("world".getBytes()));
    assertThat(id1).isNotEqualTo(id2);
    assertThat(reg.entries).hasSize(2);

    reg.release(id1);
    assertThat(reg.entries).hasSize(1);
    reg.release(id2);
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void idsAreNotReusedAfterRelease() {
    var reg = new HostBufferRegistry();
    int id1 = reg.register(Slice.wrap("a".getBytes()));
    reg.release(id1);
    int id2 = reg.register(Slice.wrap("b".getBytes()));
    // Don't reuse — Rust may still hold a stale handle from a prior failed
    // decode path; reusing ids would alias bytes across handles.
    assertThat(id2).isGreaterThan(id1);
    reg.release(id2);
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void releaseDropsEntryOnlyWhenRefcountHitsZero() {
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("abc".getBytes()));
    reg.retain(id);
    reg.retain(id); // refcount = 3

    reg.release(id); // 2
    assertThat(reg.entries).hasSize(1);
    reg.release(id); // 1
    assertThat(reg.entries).hasSize(1);
    reg.release(id); // 0 → dropped
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void retainOnUnknownIdThrows() {
    var reg = new HostBufferRegistry();
    assertThatThrownBy(() -> reg.retain(99)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void releaseOnUnknownIdIsTolerated() {
    var reg = new HostBufferRegistry();
    reg.release(99); // double-release across the WASM boundary should not blow up
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void sliceOnUnknownIdThrows() {
    var reg = new HostBufferRegistry();
    assertThatThrownBy(() -> reg.slice(99, 0, 0)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void readIntoOnUnknownIdThrows() {
    var reg = new HostBufferRegistry();
    assertThatThrownBy(() -> reg.readInto(99, 0, 3, new byte[3], 0))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void readIntoRespectsOffset() {
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("abcdefgh".getBytes()));

    byte[] dst = new byte[3];
    reg.readInto(id, 2, 3, dst, 0);
    assertThat(new String(dst)).isEqualTo("cde");

    byte[] dst2 = new byte[4];
    reg.readInto(id, 4, 4, dst2, 0);
    assertThat(new String(dst2)).isEqualTo("efgh");

    reg.release(id);
  }

  @Test
  void readIntoRespectsDstOffset() {
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("abcdef".getBytes()));

    byte[] dst = new byte[8];
    // Pre-fill with marker bytes so we can see exactly which range was overwritten.
    java.util.Arrays.fill(dst, (byte) '_');
    reg.readInto(id, 1, 3, dst, 2);
    assertThat(new String(dst)).isEqualTo("__bcd___");

    reg.release(id);
  }

  @Test
  void sliceReturnsAZeroCopySubviewSharingStorage() {
    byte[] backing = "abcdefgh".getBytes();
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap(backing));

    Slice sub = reg.slice(id, 2, 4);
    assertThat(sub.readableBytes()).isEqualTo(4);
    assertThat(new String(sub.toByteArray())).isEqualTo("cdef");

    reg.release(id);
  }

  @Test
  void subSliceRemainsReadableAfterReleasingTheRegistryEntry() {
    // This is the property the BufferParam.Host path in StateMachine.toSlice
    // relies on: register → slice → release, then the caller keeps reading
    // from the sub-Slice. The sub-Slice's ByteBuffer pins the backing storage.
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("hello world".getBytes()));

    Slice sub = reg.slice(id, 0, 5);
    reg.release(id);
    assertThat(reg.entries).isEmpty();

    // sub still reads correctly
    assertThat(new String(sub.toByteArray())).isEqualTo("hello");
  }

  @Test
  void eqComparesByteRangesAndShortCircuitsOnLengthMismatch() {
    var reg = new HostBufferRegistry();
    int idA = reg.register(Slice.wrap("hello world".getBytes())); // 11 bytes
    int idB = reg.register(Slice.wrap("xxhello world".getBytes())); // 13 bytes, match at offset 2

    // Equal ranges, different offsets
    assertThat(reg.eq(idA, 0, 11, idB, 2, 11)).isTrue();
    // Length mismatch → false without compare
    assertThat(reg.eq(idA, 0, 11, idB, 2, 10)).isFalse();
    // Same buffer, different sub-ranges
    assertThat(reg.eq(idA, 0, 5, idA, 6, 5)).isFalse();
    // Self-equality
    assertThat(reg.eq(idA, 0, 11, idA, 0, 11)).isTrue();
    // Zero-length compares always equal (regardless of offsets)
    assertThat(reg.eq(idA, 0, 0, idB, 5, 0)).isTrue();

    reg.release(idA);
    reg.release(idB);
  }

  @Test
  void eqOnUnknownIdReturnsFalse() {
    // Tolerated rather than throwing — eq comparisons happen during replay,
    // sometimes against stale handles in degenerate flows.
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap("abc".getBytes()));
    assertThat(reg.eq(id, 0, 3, 999, 0, 3)).isFalse();
    assertThat(reg.eq(999, 0, 3, id, 0, 3)).isFalse();
    reg.release(id);
  }

  @Test
  void emptySliceRoundTripsWithoutCrashing() {
    var reg = new HostBufferRegistry();
    int id = reg.register(Slice.wrap(new byte[0]));

    // Zero-length read with empty dst byte[] is a no-op.
    reg.readInto(id, 0, 0, new byte[0], 0);
    Slice sub = reg.slice(id, 0, 0);
    assertThat(sub.readableBytes()).isEqualTo(0);

    reg.release(id);
    assertThat(reg.entries).isEmpty();
  }

  @Test
  void manyRegistersFollowedByManyReleasesDrainsCompletely() {
    var reg = new HostBufferRegistry();
    int[] ids = new int[64];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = reg.register(Slice.wrap(("entry-" + i).getBytes()));
    }
    assertThat(reg.entries).hasSize(64);

    // Release out of order to ensure no hidden order assumptions.
    for (int i = ids.length - 1; i >= 0; i--) {
      reg.release(ids[i]);
    }
    assertThat(reg.entries).isEmpty();
  }
}
