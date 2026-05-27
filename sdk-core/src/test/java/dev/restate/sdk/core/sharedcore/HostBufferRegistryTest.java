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
  void eqComparesByteRangesAndShortCircuitsOnLengthMismatch() {
    var reg = new HostBufferRegistry();
    int idA = reg.register(Slice.wrap("hello world".getBytes())); // 11 bytes
    int idB =
        reg.register(Slice.wrap("xxhello world".getBytes())); // 13 bytes, match at offset 2

    // Equal ranges, different offsets
    assertThat(reg.eq(idA, 0, 11, idB, 2, 11)).isTrue();
    // Length mismatch → false without compare
    assertThat(reg.eq(idA, 0, 11, idB, 2, 10)).isFalse();
    // Same buffer, different sub-ranges
    assertThat(reg.eq(idA, 0, 5, idA, 6, 5)).isFalse();
    // Self-equality
    assertThat(reg.eq(idA, 0, 11, idA, 0, 11)).isTrue();

    reg.release(idA);
    reg.release(idB);
  }
}
